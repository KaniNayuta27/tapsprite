package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"
)

// Live mirror hub. Phone and the WebView2 page each open a WebSocket on
// port 18766. Video is a 3-frame queue. Overflow flushes that queue and
// drops deltas until the next keyframe, then asks the phone for a new one.
// Frames are never dropped just to cap fps. Input JSON is forwarded
// immediately to the phone (TCP_NODELAY on both sockets).

type liveViewer struct {
	c        *wsConn
	mu       sync.Mutex
	queue    [][]byte
	waitKey  bool
	needSync bool
	wake     chan struct{}
	dead     chan struct{}
	once     sync.Once
}

type liveState struct {
	mu       sync.Mutex
	phone    *wsConn
	phoneID  string
	views    map[*wsConn]*liveViewer
	lastMeta []byte
	maxFps   int
}

var liveHub = &liveState{views: map[*wsConn]*liveViewer{}}

func resetLiveHub() {
	liveHub.mu.Lock()
	defer liveHub.mu.Unlock()
	if liveHub.phone != nil {
		liveHub.phone.Close()
	}
	for _, v := range liveHub.views {
		v.stop()
	}
	liveHub.phone = nil
	liveHub.phoneID = ""
	liveHub.views = map[*wsConn]*liveViewer{}
	liveHub.lastMeta = nil
	liveHub.maxFps = 30
}

// livePush appends one access unit. The queue holds at most 3 frames, in order.
// On overflow it is flushed: an incoming keyframe is kept, a delta is dropped,
// and syncReq tells the caller to ask for a new keyframe. While waitKey is set,
// further deltas are ignored so a P-frame cannot follow a hole.
func livePush(queue [][]byte, waitKey bool, incoming []byte) (out [][]byte, wait bool, syncReq bool) {
	if len(incoming) == 0 {
		return queue, waitKey, false
	}
	key := len(incoming) >= 2 && incoming[0] == 2 && incoming[1]&1 == 1
	if waitKey && !key {
		return queue, true, false
	}
	cp := append([]byte(nil), incoming...)
	if len(queue) >= 3 {
		if key {
			return [][]byte{cp}, false, true
		}
		return nil, true, true
	}
	return append(queue, cp), false, false
}

func (v *liveViewer) offer(frame []byte) {
	v.mu.Lock()
	q, wait, syncReq := livePush(v.queue, v.waitKey, frame)
	v.queue = q
	v.waitKey = wait
	if syncReq {
		v.needSync = true
	}
	v.mu.Unlock()
	select {
	case v.wake <- struct{}{}:
	default:
	}
}

func (v *liveViewer) writeNow(frame []byte) {
	if err := v.c.writeBinary(frame); err != nil {
		v.stop()
	}
}

func (v *liveViewer) stop() {
	v.once.Do(func() {
		close(v.dead)
		v.c.Close()
	})
}

func (v *liveViewer) loop() {
	defer v.stop()
	for {
		select {
		case <-v.dead:
			return
		case <-v.wake:
		}
		for {
			v.mu.Lock()
			var frame []byte
			if len(v.queue) > 0 {
				frame = v.queue[0]
				v.queue = v.queue[1:]
			}
			need := v.needSync
			v.needSync = false
			v.mu.Unlock()
			if need {
				liveHub.requestSync()
			}
			if frame == nil {
				break
			}
			if err := v.c.writeBinary(frame); err != nil {
				return
			}
		}
	}
}

func (h *liveState) addView(c *wsConn) *liveViewer {
	v := &liveViewer{c: c, wake: make(chan struct{}, 1), dead: make(chan struct{})}
	h.mu.Lock()
	h.views[c] = v
	meta := append([]byte(nil), h.lastMeta...)
	h.mu.Unlock()
	go v.loop()
	if len(meta) > 0 {
		go v.writeNow(meta)
	}
	return v
}

func (h *liveState) removeView(c *wsConn) {
	h.mu.Lock()
	v := h.views[c]
	delete(h.views, c)
	h.mu.Unlock()
	if v != nil {
		v.stop()
	}
}

func (h *liveState) setPhone(id string, c *wsConn) {
	h.mu.Lock()
	old := h.phone
	h.phone = c
	h.phoneID = id
	h.mu.Unlock()
	if old != nil && old != c {
		old.Close()
	}
}

func (h *liveState) clearPhone(c *wsConn) {
	h.mu.Lock()
	if h.phone == c {
		h.phone = nil
		h.phoneID = ""
	}
	h.mu.Unlock()
}

func (h *liveState) onPhone(msg []byte) {
	if len(msg) == 0 {
		return
	}
	h.mu.Lock()
	if msg[0] == 1 {
		h.lastMeta = append([]byte(nil), msg...)
	}
	views := make([]*liveViewer, 0, len(h.views))
	for _, v := range h.views {
		views = append(views, v)
	}
	h.mu.Unlock()
	if msg[0] == 3 && len(msg) > 1 {
		h.noteLiveLog(msg[1:])
	}
	for _, v := range views {
		if msg[0] == 2 {
			v.offer(msg)
		} else {
			frame := append([]byte(nil), msg...)
			go v.writeNow(frame)
		}
	}
}

func (h *liveState) forwardPhone(json []byte) {
	msg := make([]byte, 1+len(json))
	msg[0] = 3
	copy(msg[1:], json)
	h.mu.Lock()
	p := h.phone
	h.mu.Unlock()
	if p != nil {
		_ = p.writeBinary(msg)
	}
}

func (h *liveState) requestSync() {
	h.forwardPhone([]byte(`{"op":"sync"}`))
}

// rememberFps caches the live-page choice. 60 and above is the 60 fps mode; every other value is 30.
func (h *liveState) rememberFps(body []byte) {
	var cmd struct {
		Max int `json:"max"`
	}
	if json.Unmarshal(body, &cmd) != nil {
		return
	}
	fps := 30
	if cmd.Max >= 60 {
		fps = 60
	}
	h.mu.Lock()
	h.maxFps = fps
	h.mu.Unlock()
}

func (h *liveState) pushFps() {
	h.mu.Lock()
	fps := h.maxFps
	h.mu.Unlock()
	if fps != 60 {
		fps = 30
	}
	h.forwardPhone([]byte(fmt.Sprintf(`{"op":"fps","max":%d}`, fps)))
}

func (h *liveState) noteLiveLog(body []byte) {
	var cmd struct {
		Op  string `json:"op"`
		Msg string `json:"msg"`
	}
	if json.Unmarshal(body, &cmd) != nil || cmd.Op != "log" || cmd.Msg == "" {
		return
	}
	addLog(cmd.Msg)
}

func liveDeviceID() (string, bool) {
	srv.mu.Lock()
	defer srv.mu.Unlock()
	id := srv.selected
	if id == "" || !deviceLiveLocked(srv.devices[id]) {
		return "", false
	}
	return id, true
}

func handleLiveView(w http.ResponseWriter, r *http.Request) {
	c, err := acceptWS(w, r)
	if err != nil {
		return
	}
	v := liveHub.addView(c)
	defer liveHub.removeView(c)
	go func() {
		t := time.NewTicker(10 * time.Second)
		defer t.Stop()
		for {
			select {
			case <-v.dead:
				return
			case <-t.C:
				if err := c.writeFrame(0x9, nil); err != nil {
					v.stop()
					return
				}
			}
		}
	}()
	for {
		op, payload, err := c.readData()
		if err != nil {
			return
		}
		if op != 0x1 && op != 0x2 {
			continue
		}
		body := payload
		if op == 0x2 && len(payload) > 0 && payload[0] == 3 {
			body = payload[1:]
		}
		var cmd struct {
			Op string `json:"op"`
		}
		if json.Unmarshal(body, &cmd) != nil || cmd.Op == "" {
			continue
		}
		switch cmd.Op {
		case "start":
			id, ok := liveDeviceID()
			if !ok {
				_ = c.writeText([]byte(`{"op":"state","on":false,"err":"没有已联机设备"}`))
				continue
			}
			enqueue(id, map[string]any{"type": "control", "action": "livestart"})
			addLog("实时操控开始")
			_ = c.writeText([]byte(`{"op":"state","on":true}`))
		case "stop":
			id, ok := liveDeviceID()
			if ok {
				enqueue(id, map[string]any{"type": "control", "action": "livestop"})
			}
			liveHub.forwardPhone([]byte(`{"op":"stop"}`))
			addLog("实时操控结束")
			_ = c.writeText([]byte(`{"op":"state","on":false}`))
		case "fps":
			liveHub.rememberFps(body)
			liveHub.forwardPhone(body)
		case "pinlog":
			// Blind PIN entry. Never forward this frame and never log which key.
			addLog("密码键盘: 按下")
		default:
			liveHub.forwardPhone(body)
		}
	}
}

func handleLivePhone(w http.ResponseWriter, r *http.Request) {
	c, err := acceptWS(w, r)
	if err != nil {
		return
	}
	id := r.URL.Query().Get("id")
	liveHub.setPhone(id, c)
	defer func() {
		liveHub.clearPhone(c)
		c.Close()
	}()
	go func() {
		t := time.NewTicker(10 * time.Second)
		defer t.Stop()
		for {
			select {
			case <-c.closed:
				return
			case <-t.C:
				if err := c.writeFrame(0x9, nil); err != nil {
					c.Close()
					return
				}
			}
		}
	}()
	liveHub.requestSync()
	liveHub.pushFps()
	for {
		op, payload, err := c.readData()
		if err != nil {
			return
		}
		if op == 0x1 {
			msg := make([]byte, 1+len(payload))
			msg[0] = 3
			copy(msg[1:], payload)
			liveHub.onPhone(msg)
			continue
		}
		if op == 0x2 && len(payload) > 0 {
			liveHub.onPhone(payload)
		}
	}
}

package main

import (
	"bufio"
	"encoding/base64"
	"encoding/binary"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestExe196NewerThan195(t *testing.T) {
	if compareVer("1.1.96", "1.1.95") <= 0 {
		t.Fatal("1.1.96 must be newer than 1.1.95")
	}
	if compareVer("1.1.95", "1.1.96") >= 0 {
		t.Fatal("1.1.95 must be older than 1.1.96")
	}
	if compareVer("1.1.96", "1.1.96") != 0 {
		t.Fatal("equal")
	}
}

func TestLiveKeepFrameDropsLateDelta(t *testing.T) {
	key := []byte{2, 1, 9}
	delta := []byte{2, 0, 8}
	newer := []byte{2, 0, 3}
	q, wait, syncReq := livePush(nil, false, key)
	q, wait, syncReq = livePush(q, wait, delta)
	q, wait, syncReq = livePush(q, wait, newer)
	if wait || syncReq || len(q) != 3 {
		t.Fatalf("under cap want 3 frames wait=%v sync=%v q=%d", wait, syncReq, len(q))
	}
	if string(q[0]) != string(key) || string(q[1]) != string(delta) || string(q[2]) != string(newer) {
		t.Fatalf("order %v %v %v", q[0], q[1], q[2])
	}
	// A fourth delta flushes the queue and waits for a keyframe.
	q, wait, syncReq = livePush(q, wait, []byte{2, 0, 4})
	if !wait || !syncReq || len(q) != 0 {
		t.Fatalf("overflow delta wait=%v sync=%v len=%d", wait, syncReq, len(q))
	}
	q, wait, syncReq = livePush(q, wait, []byte{2, 0, 5})
	if !wait || syncReq || len(q) != 0 {
		t.Fatalf("delta while waiting wait=%v sync=%v len=%d", wait, syncReq, len(q))
	}
	q, wait, syncReq = livePush(q, wait, []byte{2, 1, 6})
	if wait || syncReq || len(q) != 1 || q[0][2] != 6 {
		t.Fatalf("key resumes %+v wait=%v sync=%v", q, wait, syncReq)
	}
	// Overflow that is itself a keyframe replaces the queue and still requests sync.
	q, wait, syncReq = livePush(q, wait, []byte{2, 0, 1})
	q, wait, syncReq = livePush(q, wait, []byte{2, 0, 2})
	if len(q) != 3 || wait || syncReq {
		t.Fatalf("refill %d wait=%v sync=%v", len(q), wait, syncReq)
	}
	q, wait, syncReq = livePush(q, wait, []byte{2, 1, 8})
	if wait || !syncReq || len(q) != 1 || q[0][2] != 8 {
		t.Fatalf("overflow key %+v wait=%v sync=%v", q, wait, syncReq)
	}
}

func TestLiveRememberFps(t *testing.T) {
	resetLiveHub()
	t.Cleanup(resetLiveHub)
	liveHub.rememberFps([]byte(`{"op":"fps","max":60}`))
	liveHub.mu.Lock()
	got := liveHub.maxFps
	liveHub.mu.Unlock()
	if got != 60 {
		t.Fatalf("60 -> %d", got)
	}
	liveHub.rememberFps([]byte(`{"op":"fps","max":30}`))
	liveHub.mu.Lock()
	got = liveHub.maxFps
	liveHub.mu.Unlock()
	if got != 30 {
		t.Fatalf("30 -> %d", got)
	}
	liveHub.rememberFps([]byte(`{"op":"fps","max":120}`))
	liveHub.mu.Lock()
	got = liveHub.maxFps
	liveHub.mu.Unlock()
	if got != 60 {
		t.Fatalf("120 caps at 60, got %d", got)
	}
}

func TestLivePinLogOmitsDigit(t *testing.T) {
	resetLiveHub()
	t.Cleanup(resetLiveHub)
	httpSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/live/view":
			handleLiveView(w, r)
		case "/api/live/phone":
			handleLivePhone(w, r)
		default:
			http.NotFound(w, r)
		}
	}))
	defer httpSrv.Close()

	view := dialWS(t, httpSrv.Listener.Addr().String(), "/api/live/view")
	defer view.Close()
	phone := dialWS(t, httpSrv.Listener.Addr().String(), "/api/live/phone?id=dev")
	defer phone.Close()

	// Phone connect pushes sync + fps. Drain those so a later read is only the pin result.
	drained := 0
	deadline := time.Now().Add(800 * time.Millisecond)
	for time.Now().Before(deadline) && drained < 2 {
		_ = phone.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
		_, _, err := readWS(phone)
		if err != nil {
			break
		}
		drained++
	}
	if drained < 1 {
		t.Fatal("expected startup frames on the phone socket")
	}

	srv.mu.Lock()
	before := len(srv.logs)
	srv.mu.Unlock()

	// Extra fields must not be copied into the PC log or forwarded.
	body := []byte(`{"op":"pinlog","d":"8","nx":0.4242,"ny":0.7373}`)
	if err := writeWS(view, 0x1, body, true); err != nil {
		t.Fatal(err)
	}
	var got string
	wait := time.Now().Add(2 * time.Second)
	for time.Now().Before(wait) {
		srv.mu.Lock()
		if len(srv.logs) > before {
			got = srv.logs[len(srv.logs)-1]
		}
		srv.mu.Unlock()
		if got != "" {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	if got == "" {
		t.Fatal("pinlog did not reach the log")
	}
	msg := got
	if i := strings.IndexByte(got, ' '); i >= 0 {
		msg = got[i+1:]
	}
	if msg != "密码键盘: 按下" {
		t.Fatalf("log body %q from %q", msg, got)
	}
	if strings.Contains(got, "0.4242") || strings.Contains(got, "0.7373") || strings.Contains(got, "\"8\"") || strings.Contains(msg, "8") {
		t.Fatalf("digit or coordinate leaked into log %q", got)
	}

	_ = phone.SetReadDeadline(time.Now().Add(250 * time.Millisecond))
	if _, frame, err := readWS(phone); err == nil {
		t.Fatalf("pinlog must not be forwarded, got %q", frame)
	}

	down := []byte(`{"op":"down","nx":0.25,"ny":0.5}`)
	if err := writeWS(view, 0x1, down, true); err != nil {
		t.Fatal(err)
	}
	op, frame, err := readWS(phone)
	if err != nil {
		t.Fatal(err)
	}
	if op != 0x2 || len(frame) < 2 || frame[0] != 3 || !strings.Contains(string(frame), `"op":"down"`) {
		t.Fatalf("down not forwarded op=%d %q", op, frame)
	}
}

func TestLiveWSRelay(t *testing.T) {
	resetLiveHub()
	t.Cleanup(resetLiveHub)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/live/view":
			handleLiveView(w, r)
		case "/api/live/phone":
			handleLivePhone(w, r)
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()

	view := dialWS(t, srv.Listener.Addr().String(), "/api/live/view")
	defer view.Close()
	phone := dialWS(t, srv.Listener.Addr().String(), "/api/live/phone?id=dev")
	defer phone.Close()

	payload := []byte{2, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0x00, 0x00, 0x00, 0x01, 0x65}
	if err := writeWS(phone, 0x2, payload, true); err != nil {
		t.Fatal(err)
	}
	_ = phone.SetReadDeadline(time.Now().Add(2 * time.Second))
	op, got, err := readWS(view)
	if err != nil {
		t.Fatal(err)
	}
	if op != 0x2 || string(got) != string(payload) {
		t.Fatalf("relay op=%d len=%d %v", op, len(got), got)
	}
}

type wsPipe struct {
	net.Conn
	br *bufio.Reader
}

func dialWS(t *testing.T, addr, path string) *wsPipe {
	t.Helper()
	conn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatal(err)
	}
	key := base64.StdEncoding.EncodeToString([]byte("0123456789abcdef"))
	_, err = conn.Write([]byte("GET " + path + " HTTP/1.1\r\nHost: " + addr + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n\r\n"))
	if err != nil {
		t.Fatal(err)
	}
	br := bufio.NewReader(conn)
	line, err := br.ReadString('\n')
	if err != nil || !strings.Contains(line, "101") {
		t.Fatalf("handshake %q %v", line, err)
	}
	for {
		l, err := br.ReadString('\n')
		if err != nil {
			t.Fatal(err)
		}
		if l == "\r\n" {
			break
		}
	}
	return &wsPipe{Conn: conn, br: br}
}

func writeWS(c net.Conn, opcode byte, payload []byte, mask bool) error {
	n := len(payload)
	hdr := []byte{0x80 | opcode, 0}
	if mask {
		hdr[1] |= 0x80
	}
	hdr[1] |= byte(n)
	if _, err := c.Write(hdr); err != nil {
		return err
	}
	body := append([]byte(nil), payload...)
	if mask {
		m := []byte{1, 2, 3, 4}
		if _, err := c.Write(m); err != nil {
			return err
		}
		for i := range body {
			body[i] ^= m[i&3]
		}
	}
	_, err := c.Write(body)
	return err
}

func readWS(p *wsPipe) (byte, []byte, error) {
	_ = p.SetReadDeadline(time.Now().Add(2 * time.Second))
	hdr := make([]byte, 2)
	if _, err := io.ReadFull(p.br, hdr); err != nil {
		return 0, nil, err
	}
	op := hdr[0] & 0x0f
	masked := hdr[1]&0x80 != 0
	ln := int(hdr[1] & 0x7f)
	if ln == 126 {
		b := make([]byte, 2)
		if _, err := io.ReadFull(p.br, b); err != nil {
			return 0, nil, err
		}
		ln = int(binary.BigEndian.Uint16(b))
	}
	var mask [4]byte
	if masked {
		if _, err := io.ReadFull(p.br, mask[:]); err != nil {
			return 0, nil, err
		}
	}
	buf := make([]byte, ln)
	if _, err := io.ReadFull(p.br, buf); err != nil {
		return 0, nil, err
	}
	if masked {
		for i := range buf {
			buf[i] ^= mask[i&3]
		}
	}
	if op == 0x9 {
		_ = writeWS(p.Conn, 0xA, buf, true)
		return readWS(p)
	}
	return op, buf, nil
}

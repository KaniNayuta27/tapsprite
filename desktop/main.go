package main

import (
	"bytes"
	"compress/zlib"
	"embed"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed web/ui.html web/luaparse.js web/doc0.html web/doc1.html web/doc2.html
var webFS embed.FS

const (
	httpPort = 18766
	udpPort  = 18766
	version  = "1.1.72"
)

type Device struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	A11y   bool   `json:"a11y"`
	Cap    bool   `json:"cap"`
	Emu    bool   `json:"emu"`
	IPs    string `json:"ips"`
	Online bool   `json:"online"`
	Gen    int64  `json:"gen"`
	Host   string `json:"-"`
	Seen   time.Time `json:"-"`
}

type Cmd struct {
	Type   string `json:"type"`
	Script string `json:"script,omitempty"`
	Run    bool   `json:"run,omitempty"`
	Action string `json:"action,omitempty"`
}

type Slot struct {
	X   int    `json:"x"`
	Y   int    `json:"y"`
	Hex string `json:"hex"`
}

type Server struct {
	mu       sync.Mutex
	devices  map[string]*Device
	selected string
	queues   map[string][]json.RawMessage // per device id
	script   string
	logs     []string
	logCount int
	notice   string
	noticeAt int64
	shotPNG  []byte
	shotW    int
	shotH    int
	shotRev  int64
	slots    [8]Slot
	status   string
	sub      string
	lanIP    string // single preferred LAN IPv4 for UI (never a Join of all NICs)
	undoStack [][]byte
}

var srv = &Server{
	devices: map[string]*Device{},
	queues:  map[string][]json.RawMessage{},
	status:  "触控精灵 PC " + version,
	sub:     "等待手机联机…",
}

func main() {
	go listenUDP()
	mux := http.NewServeMux()
	mux.HandleFunc("/", handleStatic)
	mux.HandleFunc("/api/hello", handleHello)
	mux.HandleFunc("/api/bye", handleBye)
	mux.HandleFunc("/api/status", handleStatus)
	mux.HandleFunc("/api/device", handleDevice)
	mux.HandleFunc("/api/pull", handlePull)
	mux.HandleFunc("/api/notice", handleNotice)
	mux.HandleFunc("/api/script", handleScript)
	mux.HandleFunc("/api/control", handleControl)
	mux.HandleFunc("/api/shot", handleShot)
	mux.HandleFunc("/api/frame", handleFrame)
	mux.HandleFunc("/api/pixel", handlePixel)
	mux.HandleFunc("/api/pushshot", handlePushShot)
	mux.HandleFunc("/api/refresh", handleRefresh)
	mux.HandleFunc("/api/crop", stubOK)
	mux.HandleFunc("/api/rotate", stubOK)
	mux.HandleFunc("/api/save", handleSave)
	mux.HandleFunc("/api/saveas", handleSave)
	mux.HandleFunc("/api/savescript", handleSaveScript)
	mux.HandleFunc("/api/slot", handleSlot)
	mux.HandleFunc("/api/undo", handleUndo)
	mux.HandleFunc("/api/findtest", handleFindTest)
	mux.HandleFunc("/api/channel", handleChannel)
	mux.HandleFunc("/api/selfupdate", handleSelfUpdate)
	mux.HandleFunc("/api/updatestatus", handleUpdateStatus)
	mux.HandleFunc("/api/quit", handleQuit)
	mux.HandleFunc("/api/fetchapk", handleFetchApk)
	mux.HandleFunc("/api/apkstatus", handleApkStatus)
	mux.HandleFunc("/api/apkfile", handleApkFile)

	// Bind all interfaces so phones can reach LAN IP (same as 0.0.0.0:18766).
	addr := fmt.Sprintf(":%d", httpPort)
	lanIP := preferredLocalIPv4()
	log.Printf("tapsprite desktop %s listening on http://0.0.0.0%s (lanIP=%s)", version, addr, lanIP)
	refreshLANSub(lanIP)
	go allowFirewall()
	scheduleStartupCleanup()

	uiURL := fmt.Sprintf("http://127.0.0.1%s/", addr)
	server := &http.Server{Addr: addr, Handler: withCORS(mux)}
	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal(err)
		}
	}()
	waitLocalHTTP(addr)
	// WebView2 Run() must own the main thread on Windows.
	runWebView(uiURL)
}

// waitLocalHTTP polls until 127.0.0.1:port accepts TCP (short timeout loop).
func waitLocalHTTP(addr string) {
	deadline := time.Now().Add(5 * time.Second)
	target := "127.0.0.1" + addr
	for time.Now().Before(deadline) {
		c, err := net.DialTimeout("tcp", target, 100*time.Millisecond)
		if err == nil {
			_ = c.Close()
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
	log.Printf("warn: local HTTP %s not ready yet; opening WebView anyway", target)
}

func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
		if r.Method == http.MethodOptions {
			w.WriteHeader(204)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func listenUDP() {
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{Port: udpPort})
	if err != nil {
		log.Printf("udp listen: %v", err)
		return
	}
	defer conn.Close()
	buf := make([]byte, 256)
	for {
		n, addr, err := conn.ReadFromUDP(buf)
		if err != nil {
			continue
		}
		msg := string(buf[:n])
		if strings.HasPrefix(msg, "TSHELLO") {
			// Reply immediately so the phone learns our IP (must not delay).
			_, _ = conn.WriteToUDP([]byte("TS?"), addr)
			ip := addr.IP.String()
			addLog("UDP 发现手机 " + ip)
			// also probe phone console port
			go func(ip string) {
				c, err := net.DialUDP("udp4", nil, &net.UDPAddr{IP: net.ParseIP(ip), Port: 18765})
				if err == nil {
					_, _ = c.Write([]byte("TS?"))
					_ = c.Close()
				}
			}(ip)
		}
	}
}

func handleStatic(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path
	if path == "/" || path == "/index.html" {
		path = "/ui.html"
	}
	path = strings.TrimPrefix(path, "/")
	b, err := readWeb(path)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	switch {
	case strings.HasSuffix(path, ".html"):
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
	case strings.HasSuffix(path, ".js"):
		w.Header().Set("Content-Type", "application/javascript; charset=utf-8")
	case strings.HasSuffix(path, ".css"):
		w.Header().Set("Content-Type", "text/css; charset=utf-8")
	}
	_, _ = w.Write(b)
}

func readJSON(r *http.Request, dst any) error {
	defer r.Body.Close()
	dec := json.NewDecoder(r.Body)
	return dec.Decode(dst)
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	enc := json.NewEncoder(w)
	_ = enc.Encode(v)
}

func handleHello(w http.ResponseWriter, r *http.Request) {
	var body map[string]any
	_ = readJSON(r, &body)
	id, _ := body["id"].(string)
	name, _ := body["name"].(string)
	if id == "" {
		id = "unknown"
	}
	if name == "" {
		name = id
	}
	a11y, _ := body["a11y"].(bool)
	capv, _ := body["cap"].(bool)
	emu, _ := body["emu"].(bool)
	ips, _ := body["ips"].(string)
	online, _ := body["online"].(bool)
	var gen int64
	switch t := body["gen"].(type) {
	case float64:
		gen = int64(t)
	}
	host, _, _ := net.SplitHostPort(r.RemoteAddr)
	srv.mu.Lock()
	d := &Device{ID: id, Name: name, A11y: a11y, Cap: capv, Emu: emu, IPs: ips, Online: online, Gen: gen, Host: host, Seen: time.Now()}
	srv.devices[id] = d
	if srv.selected == "" {
		srv.selected = id
	}
	srv.status = "已连接 " + name
	srv.mu.Unlock()
	// Re-pick display LAN IP now that a phone Host is known (same-/24 preference).
	refreshLANSub(preferredLocalIPv4())
	addLog("hello " + name + " (" + id + ") from " + host)
	writeJSON(w, map[string]any{"ok": true})
}

func handleBye(w http.ResponseWriter, r *http.Request) {
	var body map[string]any
	_ = readJSON(r, &body)
	id, _ := body["id"].(string)
	srv.mu.Lock()
	if id != "" {
		delete(srv.devices, id)
		if srv.selected == id {
			srv.selected = ""
			for k := range srv.devices {
				srv.selected = k
				break
			}
		}
	}
	srv.mu.Unlock()
	addLog("bye " + id)
	writeJSON(w, map[string]any{"ok": true})
}

func handleStatus(w http.ResponseWriter, r *http.Request) {
	srv.mu.Lock()
	defer srv.mu.Unlock()
	devs := []map[string]any{}
	for _, d := range srv.devices {
		devs = append(devs, map[string]any{
			"id": d.ID, "name": d.Name, "emu": d.Emu, "sel": d.ID == srv.selected,
			"a11y": d.A11y, "cap": d.Cap,
		})
	}
	slots := make([]Slot, len(srv.slots))
	copy(slots, srv.slots[:])
	ip := ""
	if d := srv.devices[srv.selected]; d != nil {
		ip = d.Host
	}
	lanIP := srv.lanIP
	writeJSON(w, map[string]any{
		"status":   srv.status,
		"sub":      srv.sub,
		"lanIP":    lanIP,
		"ip":       ip,
		"devices":  devs,
		"selected": srv.selected,
		"newLogs":  append([]string{}, srv.logs...),
		"logCount": srv.logCount,
		"shotRev":  srv.shotRev,
		"slots":    slots,
		"notice":   srv.notice,
		"noticeAt": srv.noticeAt,
		"version":  version,
		"update":   getUpdate(),
	})
}

func handleDevice(w http.ResponseWriter, r *http.Request) {
	var body struct {
		ID string `json:"id"`
	}
	_ = readJSON(r, &body)
	srv.mu.Lock()
	if _, ok := srv.devices[body.ID]; ok {
		srv.selected = body.ID
	}
	srv.mu.Unlock()
	writeJSON(w, map[string]any{"ok": true})
}

func handlePull(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	srv.mu.Lock()
	defer srv.mu.Unlock()
	if id == "" || srv.devices[id] == nil {
		writeJSON(w, map[string]any{"hello": true})
		return
	}
	q := srv.queues[id]
	if len(q) == 0 {
		writeJSON(w, map[string]any{"cmd": nil})
		return
	}
	cmd := q[0]
	srv.queues[id] = q[1:]
	// phone expects flat JSON with type field, not nested under cmd
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_, _ = w.Write(cmd)
}

func enqueue(id string, v any) {
	b, _ := json.Marshal(v)
	srv.mu.Lock()
	defer srv.mu.Unlock()
	if id == "" {
		id = srv.selected
	}
	if id == "" {
		return
	}
	srv.queues[id] = append(srv.queues[id], b)
}

func handleNotice(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Msg  string `json:"msg"`
		Kind string `json:"kind"`
	}
	_ = readJSON(r, &body)
	if body.Msg != "" {
		if body.Kind == "trace" {
			addLog(body.Msg)
		} else {
			srv.mu.Lock()
			srv.notice = body.Msg
			srv.noticeAt = time.Now().UnixMilli()
			srv.mu.Unlock()
			addLog("[" + body.Kind + "] " + body.Msg)
		}
	}
	writeJSON(w, map[string]any{"ok": true})
}

func handleScript(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Script string `json:"script"`
		Run    bool   `json:"run"`
	}
	_ = readJSON(r, &body)
	srv.mu.Lock()
	srv.script = body.Script
	id := srv.selected
	srv.mu.Unlock()
	enqueue(id, map[string]any{"type": "script", "script": body.Script, "run": body.Run})
	addLog(fmt.Sprintf("下发脚本 %d 字 run=%v", len(body.Script), body.Run))
	writeJSON(w, map[string]any{"ok": true})
}

func handleControl(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Action string `json:"action"`
	}
	_ = readJSON(r, &body)
	srv.mu.Lock()
	id := srv.selected
	srv.mu.Unlock()
	enqueue(id, map[string]any{"type": "control", "action": body.Action})
	addLog("control " + body.Action)
	writeJSON(w, map[string]any{"ok": true})
}

func handleShot(w http.ResponseWriter, r *http.Request) {
	srv.mu.Lock()
	id := srv.selected
	srv.mu.Unlock()
	if id == "" {
		writeJSON(w, map[string]any{"notice": "没有已联机设备", "noticeAt": time.Now().UnixMilli()})
		return
	}
	enqueue(id, map[string]any{"type": "control", "action": "shot"})
	writeJSON(w, map[string]any{"ok": true})
}

func handleFrame(w http.ResponseWriter, r *http.Request) {
	srv.mu.Lock()
	defer srv.mu.Unlock()
	if len(srv.shotPNG) == 0 {
		writeJSON(w, map[string]any{"ok": false})
		return
	}
	writeJSON(w, map[string]any{
		"ok":  true,
		"b64": base64.StdEncoding.EncodeToString(srv.shotPNG),
		"w":   srv.shotW,
		"h":   srv.shotH,
	})
}

func handlePixel(w http.ResponseWriter, r *http.Request) {
	x, _ := strconv.Atoi(r.URL.Query().Get("x"))
	y, _ := strconv.Atoi(r.URL.Query().Get("y"))
	srv.mu.Lock()
	pngBytes := append([]byte{}, srv.shotPNG...)
	srv.mu.Unlock()
	if len(pngBytes) == 0 {
		writeJSON(w, map[string]any{"ok": false})
		return
	}
	img, err := png.Decode(bytes.NewReader(pngBytes))
	if err != nil {
		writeJSON(w, map[string]any{"ok": false})
		return
	}
	b := img.Bounds()
	if x < b.Min.X || y < b.Min.Y || x >= b.Max.X || y >= b.Max.Y {
		writeJSON(w, map[string]any{"ok": false})
		return
	}
	rr, gg, bb, _ := img.At(x, y).RGBA()
	r8, g8, b8 := int(rr>>8), int(gg>>8), int(bb>>8)
	hex := fmt.Sprintf("%02X%02X%02X", r8, g8, b8)
	writeJSON(w, map[string]any{"ok": true, "x": x, "y": y, "r": r8, "g": g8, "b": b8, "hex": hex})
}

func handlePushShot(w http.ResponseWriter, r *http.Request) {
	defer r.Body.Close()
	data, err := io.ReadAll(io.LimitReader(r.Body, 40<<20))
	if err != nil {
		http.Error(w, "read", 400)
		return
	}
	ww, _ := strconv.Atoi(r.Header.Get("X-Ts-W"))
	hh, _ := strconv.Atoi(r.Header.Get("X-Ts-H"))
	mime := r.Header.Get("X-Ts-Mime")
	if mime == "" {
		mime = "rawz"
	}
	var pngBytes []byte
	switch mime {
	case "png":
		pngBytes = data
	case "rawz":
		pngBytes, err = rawzToPNG(data, ww, hh)
		if err != nil {
			addLog("rawz decode: " + err.Error())
			http.Error(w, "decode", 400)
			return
		}
	default:
		// try png magic
		if len(data) > 8 && bytes.Equal(data[:8], []byte{137, 80, 78, 71, 13, 10, 26, 10}) {
			pngBytes = data
		} else {
			pngBytes, err = rawzToPNG(data, ww, hh)
			if err != nil {
				http.Error(w, "unsupported", 415)
				return
			}
		}
	}
	srv.mu.Lock()
	if len(srv.shotPNG) > 0 {
		srv.undoStack = append(srv.undoStack, srv.shotPNG)
		if len(srv.undoStack) > 8 {
			srv.undoStack = srv.undoStack[len(srv.undoStack)-8:]
		}
	}
	srv.shotPNG = pngBytes
	srv.shotW, srv.shotH = ww, hh
	srv.shotRev++
	srv.mu.Unlock()
	addLog(fmt.Sprintf("收到截图 %dx%d %s %dKB", ww, hh, mime, len(data)/1024))
	writeJSON(w, map[string]any{"ok": true})
}

func rawzToPNG(data []byte, w, h int) ([]byte, error) {
	if w <= 0 || h <= 0 {
		return nil, fmt.Errorf("bad size %dx%d", w, h)
	}
	zr, err := zlib.NewReader(bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	defer zr.Close()
	raw, err := io.ReadAll(zr)
	if err != nil {
		return nil, err
	}
	need := w * h * 3
	if len(raw) < need {
		return nil, fmt.Errorf("short raw %d < %d", len(raw), need)
	}
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			i := (y*w + x) * 3
			img.SetRGBA(x, y, color.RGBA{R: raw[i], G: raw[i+1], B: raw[i+2], A: 255})
		}
	}
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func handleRefresh(w http.ResponseWriter, r *http.Request) {
	// probe known device IPs via UDP
	srv.mu.Lock()
	hosts := []string{}
	for _, d := range srv.devices {
		if d.Host != "" {
			hosts = append(hosts, d.Host)
		}
		for _, ip := range strings.Split(d.IPs, ",") {
			ip = strings.TrimSpace(ip)
			if ip != "" {
				hosts = append(hosts, ip)
			}
		}
	}
	srv.mu.Unlock()
	for _, ip := range hosts {
		go func(ip string) {
			c, err := net.DialUDP("udp4", nil, &net.UDPAddr{IP: net.ParseIP(ip), Port: 18765})
			if err == nil {
				_, _ = c.Write([]byte("TS?"))
				_ = c.Close()
			}
		}(ip)
	}
	writeJSON(w, map[string]any{"ok": true})
}

func handleSave(w http.ResponseWriter, r *http.Request) {
	srv.mu.Lock()
	data := append([]byte{}, srv.shotPNG...)
	srv.mu.Unlock()
	if len(data) == 0 {
		writeJSON(w, map[string]any{"ok": false, "msg": "no shot"})
		return
	}
	dir, _ := os.UserHomeDir()
	if dir == "" {
		dir = "."
	}
	outDir := filepath.Join(dir, "Downloads")
	_ = os.MkdirAll(outDir, 0o755)
	name := time.Now().Format("20060102_150405") + ".png"
	path := filepath.Join(outDir, name)
	if err := os.WriteFile(path, data, 0o644); err != nil {
		writeJSON(w, map[string]any{"ok": false, "msg": err.Error()})
		return
	}
	addLog("已保存 " + path)
	writeJSON(w, map[string]any{"ok": true, "path": path})
}

func handleSaveScript(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Text string `json:"text"`
	}
	_ = readJSON(r, &body)
	srv.mu.Lock()
	srv.script = body.Text
	srv.mu.Unlock()
	writeJSON(w, map[string]any{"ok": true})
}

func handleSlot(w http.ResponseWriter, r *http.Request) {
	var body struct {
		I int `json:"i"`
		X int `json:"x"`
		Y int `json:"y"`
	}
	_ = readJSON(r, &body)
	hex := "000000"
	// sample from current frame if possible
	srv.mu.Lock()
	pngBytes := append([]byte{}, srv.shotPNG...)
	if body.I >= 0 && body.I < len(srv.slots) {
		srv.slots[body.I] = Slot{X: body.X, Y: body.Y, Hex: hex}
	}
	slotsCopy := srv.slots
	srv.mu.Unlock()
	if len(pngBytes) > 0 {
		if img, err := png.Decode(bytes.NewReader(pngBytes)); err == nil {
			b := img.Bounds()
			if body.X >= b.Min.X && body.Y >= b.Min.Y && body.X < b.Max.X && body.Y < b.Max.Y {
				rr, gg, bb, _ := img.At(body.X, body.Y).RGBA()
				hex = fmt.Sprintf("%02X%02X%02X", rr>>8, gg>>8, bb>>8)
				srv.mu.Lock()
				if body.I >= 0 && body.I < len(srv.slots) {
					srv.slots[body.I].Hex = hex
					slotsCopy = srv.slots
				}
				srv.mu.Unlock()
			}
		}
	}
	_ = slotsCopy
	writeJSON(w, map[string]any{"ok": true, "slots": slotsCopy[:]})
}


func handleFindTest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodOptions {
		http.Error(w, "method", 405)
		return
	}
	var body struct {
		Kind   string  `json:"kind"`
		L      int     `json:"l"`
		T      int     `json:"t"`
		R      int     `json:"r"`
		B      int     `json:"b"`
		First  string  `json:"first"`
		Offset string  `json:"offset"`
		Dir    int     `json:"dir"`
		Sim    float64 `json:"sim"`
		Path   string  `json:"path"`
		Delta  string  `json:"delta"`
	}
	if err := readJSON(r, &body); err != nil {
		writeJSON(w, map[string]any{"ok": false, "err": "bad json"})
		return
	}
	srv.mu.Lock()
	pngBytes := append([]byte{}, srv.shotPNG...)
	srv.mu.Unlock()
	if len(pngBytes) == 0 {
		writeJSON(w, map[string]any{"ok": false, "err": "请先截一帧", "x": -1, "y": -1})
		return
	}
	img, err := png.Decode(bytes.NewReader(pngBytes))
	if err != nil {
		writeJSON(w, map[string]any{"ok": false, "err": "截图解码失败", "x": -1, "y": -1})
		return
	}
	kind := strings.ToLower(strings.TrimSpace(body.Kind))
	sim := body.Sim
	if sim == 0 {
		sim = 0.9
	}
	switch kind {
	case "multicolor":
		x, y := findMultiColorInImage(img, body.L, body.T, body.R, body.B, body.First, body.Offset, sim, body.Dir)
		writeJSON(w, map[string]any{"ok": true, "x": x, "y": y, "result": fmt.Sprintf("%d,%d", x, y)})
	case "color":
		x, y := findColorInImage(img, body.L, body.T, body.R, body.B, body.First, sim, body.Dir)
		writeJSON(w, map[string]any{"ok": true, "x": x, "y": y, "result": fmt.Sprintf("%d,%d", x, y)})
	case "cmpex":
		ok := cmpColorExInImage(img, body.First, sim)
		res := "0"
		if ok {
			res = "1"
		}
		writeJSON(w, map[string]any{"ok": true, "match": ok, "result": res, "x": -1, "y": -1})
	case "pic":
		x, y, msg := findPicInImage(img, body.L, body.T, body.R, body.B, body.Path, sim)
		out := map[string]any{"ok": msg == "", "x": x, "y": y, "result": fmt.Sprintf("%d,%d", x, y)}
		if msg != "" {
			out["ok"] = false
			out["err"] = msg
		}
		writeJSON(w, out)
	default:
		writeJSON(w, map[string]any{"ok": false, "err": "未知 kind"})
	}
}

func handleUndo(w http.ResponseWriter, r *http.Request) {
	srv.mu.Lock()
	defer srv.mu.Unlock()
	more := false
	if n := len(srv.undoStack); n > 0 {
		srv.shotPNG = srv.undoStack[n-1]
		srv.undoStack = srv.undoStack[:n-1]
		srv.shotRev++
		more = len(srv.undoStack) > 0
	}
	writeJSON(w, map[string]any{"ok": true, "more": more})
}

func handleQuit(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, map[string]any{"ok": true})
	go func() {
		time.Sleep(200 * time.Millisecond)
		quitWebView()
		os.Exit(0)
	}()
}

func stubOK(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, map[string]any{"ok": true, "todo": true, "path": r.URL.Path})
}

func addLog(s string) {
	srv.mu.Lock()
	defer srv.mu.Unlock()
	srv.logs = append(srv.logs, time.Now().Format("15:04:05.000")+" "+s)
	if len(srv.logs) > 200 {
		srv.logs = srv.logs[len(srv.logs)-200:]
	}
	srv.logCount++
}

func readWeb(path string) ([]byte, error) {
	b, err := webFS.ReadFile("web/" + path)
	if err != nil {
		b, err = webFS.ReadFile(path)
	}
	return b, err
}

// virtualIfaceName filters VM / tunnel / VPN adapters (case-insensitive substring).
func virtualIfaceName(name string) bool {
	n := strings.ToLower(name)
	keys := []string{
		"vmware", "vbox", "virtualbox", "hyper-v", "vethernet", "wsl", "docker",
		"tun", "tap", "zerotier", "wg", "wireguard", "vpn", "hamachi", "tailscale",
		"utun", "virbr", "veth", "hyperv", "npcap", "loopback",
	}
	for _, k := range keys {
		if strings.Contains(n, k) {
			return true
		}
	}
	return false
}

func isAPIPA(ip net.IP) bool {
	ip4 := ip.To4()
	return ip4 != nil && ip4[0] == 169 && ip4[1] == 254
}

func isRFC1918(ip net.IP) bool {
	ip4 := ip.To4()
	if ip4 == nil {
		return false
	}
	if ip4[0] == 10 {
		return true
	}
	if ip4[0] == 192 && ip4[1] == 168 {
		return true
	}
	if ip4[0] == 172 && ip4[1] >= 16 && ip4[1] <= 31 {
		return true
	}
	return false
}

func sameIPv4Slash24(a, b string) bool {
	ap := strings.Split(a, ".")
	bp := strings.Split(b, ".")
	if len(ap) != 4 || len(bp) != 4 {
		return false
	}
	return ap[0] == bp[0] && ap[1] == bp[1] && ap[2] == bp[2]
}

// defaultRouteLocalIP: classic UDP dial to a public DNS; LocalAddr is the egress NIC IP.
func defaultRouteLocalIP() string {
	c, err := net.DialTimeout("udp4", "1.1.1.1:53", 800*time.Millisecond)
	if err != nil {
		return ""
	}
	defer c.Close()
	la, ok := c.LocalAddr().(*net.UDPAddr)
	if !ok || la == nil || la.IP == nil {
		return ""
	}
	ip4 := la.IP.To4()
	if ip4 == nil || ip4.IsLoopback() || isAPIPA(ip4) {
		return ""
	}
	return ip4.String()
}

type lanCand struct {
	ip   string
	name string
	raw  net.IP
}

// listLANCandidates enumerates up, non-loopback IPv4s, dropping APIPA and virtual/tunnel NICs.
func listLANCandidates() []lanCand {
	out := []lanCand{}
	ifaces, err := net.Interfaces()
	if err != nil {
		return out
	}
	seen := map[string]bool{}
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		if virtualIfaceName(iface.Name) {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, a := range addrs {
			var ip net.IP
			switch v := a.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip == nil || ip.IsLoopback() {
				continue
			}
			ip4 := ip.To4()
			if ip4 == nil || isAPIPA(ip4) {
				continue
			}
			s := ip4.String()
			if seen[s] {
				continue
			}
			seen[s] = true
			out = append(out, lanCand{ip: s, name: iface.Name, raw: ip4})
		}
	}
	return out
}

func onlinePhoneHosts() []string {
	srv.mu.Lock()
	defer srv.mu.Unlock()
	out := []string{}
	for _, d := range srv.devices {
		if d != nil && d.Host != "" {
			h := d.Host
			// strip zone / unexpected
			if i := strings.IndexByte(h, '%'); i >= 0 {
				h = h[:i]
			}
			if net.ParseIP(h) != nil {
				out = append(out, h)
			}
		}
	}
	return out
}

func rankLANIP(ip string) int {
	// lower is better; never use "last list item" as a rule
	if strings.HasPrefix(ip, "192.168.") {
		return 0
	}
	if strings.HasPrefix(ip, "10.") {
		return 1
	}
	p := net.ParseIP(ip)
	if p != nil && isRFC1918(p) {
		return 2 // 172.16-31
	}
	return 3
}

func pickBestAmong(cands []lanCand) string {
	if len(cands) == 0 {
		return ""
	}
	best := cands[0]
	for _, c := range cands[1:] {
		if rankLANIP(c.ip) < rankLANIP(best.ip) {
			best = c
		}
	}
	return best.ip
}

// preferredLocalIPv4 picks ONE LAN IPv4 for UI / srv.sub /api/status.
//
// Combined strategy:
//  1. Filter: loopback, APIPA 169.254.*, virtual/tunnel NIC names (vmware/vbox/wsl/docker/tun/tap/vpn/…).
//  2. Prefer RFC1918 when any exist (drop public/other if private present).
//  3. Prefer default-route egress (UDP dial 1.1.1.1:53 → LocalAddr) when that address is in the candidate set.
//  4. If an online phone Host is known, prefer a local address on the same /24 (or same net segment).
//  5. If still multiple: prefer 192.168.*, then 10.*, then other RFC1918 — never lan[len-1].
func preferredLocalIPv4() string {
	cands := listLANCandidates()
	if len(cands) == 0 {
		return ""
	}
	// (2) Prefer RFC1918 pool when available.
	priv := make([]lanCand, 0, len(cands))
	for _, c := range cands {
		if isRFC1918(c.raw) {
			priv = append(priv, c)
		}
	}
	if len(priv) > 0 {
		cands = priv
	}
	set := map[string]lanCand{}
	for _, c := range cands {
		set[c.ip] = c
	}

	// (4) Phone same-/24 narrows the pool first when applicable.
	phones := onlinePhoneHosts()
	if len(phones) > 0 {
		same := make([]lanCand, 0)
		for _, c := range cands {
			for _, ph := range phones {
				if sameIPv4Slash24(c.ip, ph) {
					same = append(same, c)
					break
				}
			}
		}
		if len(same) > 0 {
			cands = same
			set = map[string]lanCand{}
			for _, c := range cands {
				set[c.ip] = c
			}
		}
	}

	// (3) Default-route egress if present in (possibly narrowed) set.
	if egress := defaultRouteLocalIP(); egress != "" {
		if _, ok := set[egress]; ok {
			return egress
		}
	}

	// (5) Stable preference order — not "last in enumeration".
	return pickBestAmong(cands)
}

func refreshLANSub(ip string) {
	srv.mu.Lock()
	defer srv.mu.Unlock()
	srv.lanIP = ip
	if ip != "" {
		// Single IP only — short tip; do not Join every NIC.
		srv.sub = fmt.Sprintf("http://%s:%d · 局域网", ip, httpPort)
	} else {
		srv.sub = fmt.Sprintf("http://0.0.0.0:%d", httpPort)
	}
}

// localIPv4s kept for diagnostics / tests; UI must use preferredLocalIPv4 instead.
func localIPv4s() []string {
	cands := listLANCandidates()
	out := make([]string, 0, len(cands))
	for _, c := range cands {
		out = append(out, c.ip)
	}
	return out
}


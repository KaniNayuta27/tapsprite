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
	version  = "1.1.68"
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
	mux.HandleFunc("/api/selfupdate", handleSelfUpdate)
	mux.HandleFunc("/api/updatestatus", handleUpdateStatus)
	mux.HandleFunc("/api/quit", handleQuit)
	mux.HandleFunc("/api/fetchapk", handleFetchApk)
	mux.HandleFunc("/api/apkstatus", handleApkStatus)
	mux.HandleFunc("/api/apkfile", handleApkFile)

	// Bind all interfaces so phones can reach LAN IP (same as 0.0.0.0:18766).
	addr := fmt.Sprintf(":%d", httpPort)
	lan := localIPv4s()
	log.Printf("tapsprite desktop %s listening on http://0.0.0.0%s (lan=%v)", version, addr, lan)
	srv.mu.Lock()
	if len(lan) > 0 {
		srv.sub = fmt.Sprintf("http://%s%s · 本机 %s", lan[0], addr, strings.Join(lan, ", "))
	} else {
		srv.sub = fmt.Sprintf("http://0.0.0.0%s", addr)
	}
	srv.mu.Unlock()
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
	srv.sub = host
	srv.mu.Unlock()
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
	writeJSON(w, map[string]any{
		"status":   srv.status,
		"sub":      srv.sub,
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

func localIPv4s() []string {
	out := []string{}
	ifaces, err := net.Interfaces()
	if err != nil {
		return out
	}
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
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
			ip = ip.To4()
			if ip == nil {
				continue
			}
			s := ip.String()
			if strings.HasPrefix(s, "169.254.") {
				continue
			}
			out = append(out, s)
		}
	}
	return out
}


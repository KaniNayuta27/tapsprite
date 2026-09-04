package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestHelloPullProtocol(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/hello", handleHello)
	mux.HandleFunc("/api/pull", handlePull)
	mux.HandleFunc("/api/bye", handleBye)
	mux.HandleFunc("/api/script", handleScript)

	// hello
	body := `{"id":"dev1","name":"phone","a11y":true,"cap":true,"emu":false,"ips":"192.168.1.2","online":true,"gen":1}`
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/hello", strings.NewReader(body))
	req.RemoteAddr = "192.168.1.2:12345"
	mux.ServeHTTP(rr, req)
	if rr.Code != 200 {
		t.Fatalf("hello status %d", rr.Code)
	}
	var hello map[string]any
	if err := json.Unmarshal(rr.Body.Bytes(), &hello); err != nil {
		t.Fatal(err)
	}
	if hello["ok"] != true {
		t.Fatalf("hello want ok:true got %v", hello)
	}

	// empty pull
	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pull?id=dev1", nil)
	mux.ServeHTTP(rr, req)
	var empty map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &empty)
	if empty["cmd"] != nil {
		t.Fatalf("empty pull want cmd:null got %v", empty)
	}

	// unknown device
	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pull?id=unknown", nil)
	mux.ServeHTTP(rr, req)
	var unk map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &unk)
	if unk["hello"] != true {
		t.Fatalf("unknown pull want hello:true got %v", unk)
	}

	// enqueue script then pull flat JSON
	srv.mu.Lock()
	srv.selected = "dev1"
	srv.mu.Unlock()
	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/script", bytes.NewReader([]byte(`{"script":"toast('hi')","run":false}`)))
	mux.ServeHTTP(rr, req)

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pull?id=dev1", nil)
	mux.ServeHTTP(rr, req)
	raw, _ := io.ReadAll(rr.Body)
	var cmd map[string]any
	if err := json.Unmarshal(raw, &cmd); err != nil {
		t.Fatal(err)
	}
	if cmd["type"] != "script" {
		t.Fatalf("want flat type=script, got %s", raw)
	}
	if _, nested := cmd["cmd"]; nested {
		t.Fatalf("must not wrap under cmd: %s", raw)
	}
}

func resetSrv() {
	srv = &Server{
		devices: map[string]*Device{},
		queues:  map[string][]json.RawMessage{},
		status:  "test",
		sub:     "sub",
		lanIP:   "192.168.1.10",
	}
}

func TestPullUpdatesA11yAndStatus(t *testing.T) {
	resetSrv()
	mux := http.NewServeMux()
	mux.HandleFunc("/api/hello", handleHello)
	mux.HandleFunc("/api/pull", handlePull)
	mux.HandleFunc("/api/status", handleStatus)

	body := `{"id":"dev1","name":"phone","a11y":true,"cap":true,"emu":false,"ips":"192.168.1.2","online":true,"gen":1}`
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/hello", strings.NewReader(body))
	req.RemoteAddr = "192.168.1.2:12345"
	mux.ServeHTTP(rr, req)
	srv.mu.Lock()
	srv.lanIP = "192.168.1.10"
	srv.mu.Unlock()

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/status", nil)
	mux.ServeHTTP(rr, req)
	var st map[string]any
	if err := json.Unmarshal(rr.Body.Bytes(), &st); err != nil {
		t.Fatal(err)
	}
	if st["a11y"] != true {
		t.Fatalf("want a11y true, got %v (%s)", st["a11y"], rr.Body.Bytes())
	}
	if st["lanIP"] != "192.168.1.10" {
		t.Fatalf("want lanIP 192.168.1.10 got %v", st["lanIP"])
	}

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pull?id=dev1&a11y=0&cap=0", nil)
	mux.ServeHTTP(rr, req)

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/status", nil)
	mux.ServeHTTP(rr, req)
	st = map[string]any{}
	if err := json.Unmarshal(rr.Body.Bytes(), &st); err != nil {
		t.Fatal(err)
	}
	if st["a11y"] != false {
		t.Fatalf("want a11y false after pull, got %v (%s)", st["a11y"], rr.Body.Bytes())
	}
}

func TestStatusA11yEmptyWhenDisconnected(t *testing.T) {
	resetSrv()
	srv.devices["dev1"] = &Device{
		ID: "dev1", Name: "phone", A11y: true, Cap: true, Online: true,
		Seen: time.Now().Add(-30 * time.Second),
	}
	srv.selected = "dev1"
	mux := http.NewServeMux()
	mux.HandleFunc("/api/status", handleStatus)
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/status", nil)
	mux.ServeHTTP(rr, req)
	var st map[string]any
	if err := json.Unmarshal(rr.Body.Bytes(), &st); err != nil {
		t.Fatal(err)
	}
	if st["a11y"] != nil {
		t.Fatalf("disconnected a11y must be null/empty, got %v", st["a11y"])
	}
	devs, _ := st["devices"].([]any)
	if len(devs) != 0 {
		t.Fatalf("stale device should not appear, got %v", st["devices"])
	}
}

func TestHelloStoresAppVersion(t *testing.T) {
	resetSrv()
	mux := http.NewServeMux()
	mux.HandleFunc("/api/hello", handleHello)
	body := `{"id":"dev1","name":"phone","a11y":true,"cap":true,"online":true,"gen":1,"versionCode":91,"versionName":"0.9.66"}`
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/hello", strings.NewReader(body))
	req.RemoteAddr = "192.168.1.2:12345"
	mux.ServeHTTP(rr, req)
	srv.mu.Lock()
	d := srv.devices["dev1"]
	srv.mu.Unlock()
	if d == nil {
		t.Fatal("device missing")
	}
	if d.VerCode != 91 || d.VerName != "0.9.66" {
		t.Fatalf("want 91/0.9.66 got %d/%s", d.VerCode, d.VerName)
	}
}

func TestShotNoPermWhenCapOff(t *testing.T) {
	resetSrv()
	srv.devices["dev1"] = &Device{
		ID: "dev1", Name: "phone", A11y: true, Cap: false, Online: true, Seen: time.Now(),
	}
	srv.selected = "dev1"
	mux := http.NewServeMux()
	mux.HandleFunc("/api/shot", handleShot)
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/shot", strings.NewReader("{}"))
	mux.ServeHTTP(rr, req)
	var out map[string]any
	if err := json.Unmarshal(rr.Body.Bytes(), &out); err != nil {
		t.Fatal(err)
	}
	if out["noperm"] != true {
		t.Fatalf("want noperm true, got %v (%s)", out["noperm"], rr.Body.Bytes())
	}
	if out["notice"] != "未开截屏权限" {
		t.Fatalf("want no-permission notice, got %v", out["notice"])
	}
	srv.mu.Lock()
	q := srv.queues["dev1"]
	srv.mu.Unlock()
	if len(q) != 1 {
		t.Fatalf("shot must still be enqueued so phone can pop permission UI, got %d", len(q))
	}
}


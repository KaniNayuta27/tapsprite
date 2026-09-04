package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
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

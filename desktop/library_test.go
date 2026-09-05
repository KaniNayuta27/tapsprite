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

func TestUIHasScriptLibrary(t *testing.T) {
	b, err := readWeb("ui.html")
	if err != nil {
		t.Fatal(err)
	}
	html := string(b)
	for _, s := range []string{"脚本库", "id=\"navLib\"", "id=\"page-lib\"", "id=\"libStart\"", "id=\"libMonaco\"", "persist: false", "library: true", "test.lua"} {
		if !strings.Contains(html, s) {
			t.Fatalf("ui.html missing %q", s)
		}
	}
}

func TestUIMonacoIndentAndFoldAssets(t *testing.T) {
	b, err := readWeb("ui.html")
	if err != nil {
		t.Fatal(err)
	}
	html := string(b)
	if strings.Contains(html, `href="/vs/editor/editor.main.css"`) {
		t.Fatal("local /vs/editor/editor.main.css must not be referenced (not shipped)")
	}
	for _, s := range []string{
		"https://unpkg.com/monaco-editor@0.45.0/min/vs/editor/editor.main.css",
		`.monaco-editor .codicon { font-family: codicon !important; }`,
		"out.push(\"\");",
		`foldingStrategy: "auto"`,
		`showFoldingControls: "always"`,
		`kind: monaco.languages.FoldingRangeKind.Region`,
	} {
		if !strings.Contains(html, s) {
			t.Fatalf("ui.html missing %q", s)
		}
	}
}

func TestLibraryListsTestLua(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/library", handleLibrary)
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/library", nil)
	mux.ServeHTTP(rr, req)
	if rr.Code != 200 {
		t.Fatalf("status %d %s", rr.Code, rr.Body.Bytes())
	}
	var out struct {
		OK    bool `json:"ok"`
		Files []struct {
			Name string `json:"name"`
		} `json:"files"`
	}
	if err := json.Unmarshal(rr.Body.Bytes(), &out); err != nil {
		t.Fatal(err)
	}
	if !out.OK {
		t.Fatalf("want ok, got %s", rr.Body.Bytes())
	}
	found := false
	for _, f := range out.Files {
		if f.Name == "test.lua" {
			found = true
			break
		}
	}
	if !found {
		t.Fatalf("test.lua missing: %s", rr.Body.Bytes())
	}
}

func TestLibraryTestLuaMatchesHomeDefault(t *testing.T) {
	want := "KeyPress(\"Home\")\nDelay(1000)\nTap(167, 775)\nDelay(2000)\nKeyPress(\"Back\")\nDelay(2000)\n"
	if defaultTestLua != want {
		t.Fatalf("defaultTestLua mismatch")
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/api/library", handleLibrary)
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/library?name=test.lua", nil)
	mux.ServeHTTP(rr, req)
	var out struct {
		OK     bool   `json:"ok"`
		Name   string `json:"name"`
		Script string `json:"script"`
	}
	if err := json.Unmarshal(rr.Body.Bytes(), &out); err != nil {
		t.Fatal(err)
	}
	if !out.OK || out.Name != "test.lua" {
		t.Fatalf("bad body %s", rr.Body.Bytes())
	}
	if out.Script != want {
		t.Fatalf("script mismatch\n got %q\nwant %q", out.Script, want)
	}
}

func TestScriptLibraryDoesNotPersist(t *testing.T) {
	resetSrv()
	srv.devices["dev1"] = &Device{ID: "dev1", Name: "phone", Online: true, Seen: time.Now()}
	srv.selected = "dev1"
	srv.script = "OLD"
	mux := http.NewServeMux()
	mux.HandleFunc("/api/script", handleScript)
	mux.HandleFunc("/api/pull", handlePull)

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/script", bytes.NewReader([]byte(`{"script":"Tip(\"lib\")","run":true,"persist":false,"library":true}`)))
	mux.ServeHTTP(rr, req)
	var ack map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &ack)
	if ack["ok"] != true {
		t.Fatalf("ack %s", rr.Body.Bytes())
	}
	if ack["persist"] != false {
		t.Fatalf("want persist false in ack, got %v", ack["persist"])
	}
	if srv.script != "OLD" {
		t.Fatalf("library run must not overwrite srv.script, got %q", srv.script)
	}

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pull?id=dev1", nil)
	mux.ServeHTTP(rr, req)
	raw, _ := io.ReadAll(rr.Body)
	var cmd map[string]any
	if err := json.Unmarshal(raw, &cmd); err != nil {
		t.Fatal(err)
	}
	if cmd["type"] != "script" {
		t.Fatalf("type %s", raw)
	}
	if cmd["script"] != "Tip(\"lib\")" {
		t.Fatalf("script %s", raw)
	}
	if cmd["run"] != true {
		t.Fatalf("run %s", raw)
	}
	if cmd["persist"] != false {
		t.Fatalf("persist %s", raw)
	}
	if cmd["library"] != true {
		t.Fatalf("library %s", raw)
	}
}

func TestScriptHomeStillPersists(t *testing.T) {
	resetSrv()
	srv.devices["dev1"] = &Device{ID: "dev1", Name: "phone", Online: true, Seen: time.Now()}
	srv.selected = "dev1"
	mux := http.NewServeMux()
	mux.HandleFunc("/api/script", handleScript)
	mux.HandleFunc("/api/pull", handlePull)

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/script", strings.NewReader(`{"script":"Tip(\"home\")","run":true}`))
	mux.ServeHTTP(rr, req)
	if srv.script != "Tip(\"home\")" {
		t.Fatalf("home send should store srv.script, got %q", srv.script)
	}

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pull?id=dev1", nil)
	mux.ServeHTTP(rr, req)
	raw, _ := io.ReadAll(rr.Body)
	var cmd map[string]any
	if err := json.Unmarshal(raw, &cmd); err != nil {
		t.Fatal(err)
	}
	if _, ok := cmd["persist"]; ok {
		t.Fatalf("home send must omit persist flag: %s", raw)
	}
	if cmd["library"] != nil {
		t.Fatalf("home send must omit library: %s", raw)
	}
	if cmd["run"] != true || cmd["type"] != "script" {
		t.Fatalf("home cmd %s", raw)
	}
}

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
	for _, s := range []string{
		"脚本库", "id=\"navLib\"", "id=\"page-lib\"", "id=\"libMonaco\"",
		"id=\"libSend\"", "id=\"libSendRun\"", "id=\"libRun\"", "id=\"libStop\"",
		"test.lua", "test2.lua",
		`api("/api/script", { script, run: false })`,
		`api("/api/script", { script, run: true })`,
		`api("/api/control", { action: "start" })`,
		`api("/api/control", { action: "stop" })`,
	} {
		if !strings.Contains(html, s) {
			t.Fatalf("ui.html missing %q", s)
		}
	}
	for _, s := range []string{`id="libStart"`, "persist: false", "library: true"} {
		if strings.Contains(html, s) {
			t.Fatalf("ui.html must not contain %q", s)
		}
	}
}

func TestUIMonacoIndentAndFoldAssets(t *testing.T) {
	b, err := readWeb("ui.html")
	if err != nil {
		t.Fatal(err)
	}
	html := string(b)
	if strings.Contains(html, "unpkg.com") {
		t.Fatal("ui.html must not load monaco from unpkg")
	}
	for _, s := range []string{
		`href="/vs/editor/editor.main.css"`,
		`src="/vs/loader.js"`,
		`paths: { vs: "/vs" }`,
		`src: url("/vs/base/browser/ui/codicons/codicon/codicon.ttf") format("truetype")`,
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

func TestMonacoVsEmbedded(t *testing.T) {
	for _, p := range []string{
		"vs/loader.js",
		"vs/editor/editor.main.css",
		"vs/editor/editor.main.js",
		"vs/editor/editor.main.nls.js",
		"vs/base/worker/workerMain.js",
		"vs/base/browser/ui/codicons/codicon/codicon.ttf",
		"vs/basic-languages/lua/lua.js",
	} {
		b, err := readWeb(p)
		if err != nil {
			t.Fatalf("missing embedded %s: %v", p, err)
		}
		if len(b) < 100 {
			t.Fatalf("%s too small (%d)", p, len(b))
		}
	}
	css, err := readWeb("vs/editor/editor.main.css")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(css), "font-family:codicon") {
		t.Fatal("editor.main.css missing codicon @font-face")
	}
}

func TestMonacoVsServed(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/", handleStatic)
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/vs/base/browser/ui/codicons/codicon/codicon.ttf", nil)
	mux.ServeHTTP(rr, req)
	if rr.Code != 200 {
		t.Fatalf("codicon.ttf status %d", rr.Code)
	}
	ct := rr.Header().Get("Content-Type")
	if !strings.Contains(ct, "font/ttf") {
		t.Fatalf("codicon Content-Type %q", ct)
	}
	if len(rr.Body.Bytes()) < 1000 {
		t.Fatalf("codicon.ttf too small %d", rr.Body.Len())
	}
	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/vs/editor/editor.main.css", nil)
	mux.ServeHTTP(rr, req)
	if rr.Code != 200 {
		t.Fatalf("editor.main.css status %d", rr.Code)
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
	found, found2 := false, false
	for _, f := range out.Files {
		if f.Name == "test.lua" {
			found = true
		}
		if f.Name == "test2.lua" {
			found2 = true
		}
	}
	if !found {
		t.Fatalf("test.lua missing: %s", rr.Body.Bytes())
	}
	if !found2 {
		t.Fatalf("test2.lua missing: %s", rr.Body.Bytes())
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

func TestLibraryTest2LuaNoTickCount(t *testing.T) {
	if strings.Contains(defaultTest2Lua, "TickCount") {
		t.Fatal("defaultTest2Lua must not contain TickCount")
	}
	b, err := scriptsFS.ReadFile("scripts/test2.lua")
	if err != nil {
		t.Fatal(err)
	}
	body := string(b)
	if strings.Contains(body, "TickCount") {
		t.Fatal("scripts/test2.lua must not contain TickCount")
	}
	if body != defaultTest2Lua {
		t.Fatalf("embedded test2.lua mismatch with defaultTest2Lua")
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/api/library", handleLibrary)
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/library?name=test2.lua", nil)
	mux.ServeHTTP(rr, req)
	var out struct {
		OK     bool   `json:"ok"`
		Name   string `json:"name"`
		Script string `json:"script"`
	}
	if err := json.Unmarshal(rr.Body.Bytes(), &out); err != nil {
		t.Fatal(err)
	}
	if !out.OK || out.Name != "test2.lua" {
		t.Fatalf("bad body %s", rr.Body.Bytes())
	}
	if out.Script != defaultTest2Lua {
		t.Fatalf("script mismatch")
	}
	if !strings.Contains(out.Script, "FindMultiColor") || !strings.Contains(out.Script, "点开始") {
		t.Fatalf("unexpected test2.lua body")
	}
}

func TestScriptLibrarySendPersists(t *testing.T) {
	resetSrv()
	srv.devices["dev1"] = &Device{ID: "dev1", Name: "phone", Online: true, Seen: time.Now()}
	srv.selected = "dev1"
	srv.script = "OLD"
	mux := http.NewServeMux()
	mux.HandleFunc("/api/script", handleScript)
	mux.HandleFunc("/api/pull", handlePull)

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/script", bytes.NewReader([]byte(`{"script":"Tip(\"lib\")","run":true}`)))
	mux.ServeHTTP(rr, req)
	var ack map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &ack)
	if ack["ok"] != true {
		t.Fatalf("ack %s", rr.Body.Bytes())
	}
	if ack["persist"] != true {
		t.Fatalf("want persist true in ack, got %v", ack["persist"])
	}
	if srv.script != "Tip(\"lib\")" {
		t.Fatalf("library send must persist srv.script, got %q", srv.script)
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
	if _, ok := cmd["persist"]; ok {
		t.Fatalf("persist send must omit persist flag: %s", raw)
	}
	if cmd["library"] != nil {
		t.Fatalf("persist send must omit library: %s", raw)
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

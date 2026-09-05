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
		"id=\"libRun\"", "id=\"libStop\"", "id=\"libStopAll\"",
		"test.lua", "test2.lua", "learn.lua",
		`library: true`,
		`persist: false`,
		`action: "libstop"`,
		`action: "libstopall"`,
		`运行中`,
		`全部停止`,
		`api("/api/control", { action: "start" })`,
		`api("/api/control", { action: "stop" })`,
	} {
		if !strings.Contains(html, s) {
			t.Fatalf("ui.html missing %q", s)
		}
	}
	for _, s := range []string{`id="libSend"`, `id="libSendRun"`, `id="libStart"`, `data-lib-start`, `data-lib-stop`, `lib-item-acts`} {
		if strings.Contains(html, s) {
			t.Fatalf("ui.html must not contain %q", s)
		}
	}
}

func TestUILibraryToolbarToastsAndGrab(t *testing.T) {
	b, err := readWeb("ui.html")
	if err != nil {
		t.Fatal(err)
	}
	html := string(b)
	for _, s := range []string{
		`id="libEdSave"`, `id="libEdCmt"`, `id="libEdUnc"`, `id="libEdFind"`,
		`id="libEdRep"`, `id="libEdChk"`, `id="libFmtBtn"`,
		`toast(n + " 已开始", "ok")`,
		`toast(n + " 已停止", "warn")`,
		`toast("全部脚本已停止", "danger")`,
		`cacheShotPixels`,
		`sampleShot`,
		`bindEdTools(window.libEd`,
	} {
		if !strings.Contains(html, s) {
			t.Fatalf("ui.html missing %q", s)
		}
	}
	for _, s := range []string{
		`拖出选区后`,
		`{ showSaveUndo(true); refreshShot(); }`,
	} {
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
	found, found2, foundLearn := false, false, false
	for _, f := range out.Files {
		if f.Name == "test.lua" {
			found = true
		}
		if f.Name == "test2.lua" {
			found2 = true
		}
		if f.Name == "learn.lua" {
			foundLearn = true
		}
	}
	if !found {
		t.Fatalf("test.lua missing: %s", rr.Body.Bytes())
	}
	if !found2 {
		t.Fatalf("test2.lua missing: %s", rr.Body.Bytes())
	}
	if !foundLearn {
		t.Fatalf("learn.lua missing: %s", rr.Body.Bytes())
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

func TestLibraryLearnLua(t *testing.T) {
	b, err := scriptsFS.ReadFile("scripts/learn.lua")
	if err != nil {
		t.Fatal(err)
	}
	body := string(b)
	for _, s := range []string{
		"Dialog.InputBox",
		"function getNumber",
		"function addTime",
		"function 恢复",
		"function 简略恢复",
		"function BackHome",
		"function bath",
		"function eat",
		"function lagCheck",
		"function isQQUI",
		"math.randomseed(TickCount())",
		"Tap2",
		"DrawCircle",
		"Image.OcrText",
		"Element.GetAll",
		"UTF8.InStr",
		"tonumber(v) or 3",
	} {
		if !strings.Contains(body, s) {
			t.Fatalf("learn.lua missing %q", s)
		}
	}
	if strings.Contains(body, "function tip") || strings.Contains(body, "FW.") || strings.Contains(body, "tipcancel") {
		t.Fatal("learn.lua must not use tip/FW")
	}
	if strings.Contains(body, ".. TickCount()") || strings.Contains(body, "& TickCount()") {
		t.Fatal("learn.lua must not concatenate TickCount in TracePrint")
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/api/library", handleLibrary)
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/library?name=learn.lua", nil)
	mux.ServeHTTP(rr, req)
	var out struct {
		OK     bool   `json:"ok"`
		Name   string `json:"name"`
		Script string `json:"script"`
	}
	if err := json.Unmarshal(rr.Body.Bytes(), &out); err != nil {
		t.Fatal(err)
	}
	if !out.OK || out.Name != "learn.lua" {
		t.Fatalf("bad body %s", rr.Body.Bytes())
	}
	if out.Script != body {
		t.Fatalf("learn.lua API body mismatch")
	}
}

func TestUIHasDialogInputBoxDocs(t *testing.T) {
	b, err := readWeb("ui.html")
	if err != nil {
		t.Fatal(err)
	}
	html := string(b)
	for _, s := range []string{
		`["Dialog.InputBox","(prompt[, default])"]`,
		`Dialog.InputBox(prompt[, default])`,
		`tonumber(v) or 3`,
	} {
		if !strings.Contains(html, s) {
			t.Fatalf("ui.html missing %q", s)
		}
	}
}

func TestScriptLibraryStartDoesNotPersist(t *testing.T) {
	resetSrv()
	srv.devices["dev1"] = &Device{ID: "dev1", Name: "phone", Online: true, Seen: time.Now()}
	srv.selected = "dev1"
	srv.script = "OLD"
	mux := http.NewServeMux()
	mux.HandleFunc("/api/script", handleScript)
	mux.HandleFunc("/api/control", handleControl)
	mux.HandleFunc("/api/pull", handlePull)
	mux.HandleFunc("/api/status", handleStatus)

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/script", bytes.NewReader([]byte(`{"script":"Tip(\"lib\")","run":true,"library":true,"persist":false,"name":"test.lua"}`)))
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
		t.Fatalf("library start must not write srv.script, got %q", srv.script)
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
		t.Fatalf("library start must send persist=false: %s", raw)
	}
	if cmd["library"] != true {
		t.Fatalf("library start must send library=true: %s", raw)
	}
	if cmd["libName"] != "test.lua" {
		t.Fatalf("libName %s", raw)
	}

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/status", nil)
	mux.ServeHTTP(rr, req)
	var st map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &st)
	libs, _ := st["libRunning"].([]any)
	found := false
	for _, x := range libs {
		if x == "test.lua" {
			found = true
		}
	}
	if !found {
		t.Fatalf("status libRunning missing test.lua: %s", rr.Body.Bytes())
	}

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/control", strings.NewReader(`{"action":"libstop","name":"test.lua","library":true}`))
	mux.ServeHTTP(rr, req)
	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pull?id=dev1", nil)
	mux.ServeHTTP(rr, req)
	raw, _ = io.ReadAll(rr.Body)
	if err := json.Unmarshal(raw, &cmd); err != nil {
		t.Fatal(err)
	}
	if cmd["type"] != "control" || cmd["action"] != "libstop" {
		t.Fatalf("libstop cmd %s", raw)
	}
	if cmd["libName"] != "test.lua" {
		t.Fatalf("libstop name %s", raw)
	}
	if cmd["library"] != true {
		t.Fatalf("libstop library %s", raw)
	}
}

func TestLibStopAllClearsRunningAndQueues(t *testing.T) {
	resetSrv()
	srv.devices["dev1"] = &Device{ID: "dev1", Name: "phone", Online: true, Seen: time.Now()}
	srv.selected = "dev1"
	srv.libRunning = map[string]bool{"test.lua": true, "learn.lua": true}
	mux := http.NewServeMux()
	mux.HandleFunc("/api/control", handleControl)
	mux.HandleFunc("/api/pull", handlePull)
	mux.HandleFunc("/api/status", handleStatus)

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/control", strings.NewReader(`{"action":"libstopall","library":true}`))
	mux.ServeHTTP(rr, req)

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/status", nil)
	mux.ServeHTTP(rr, req)
	var st map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &st)
	libs, _ := st["libRunning"].([]any)
	if len(libs) != 0 {
		t.Fatalf("libstopall must clear libRunning, got %v", libs)
	}

	got := map[string]bool{}
	for i := 0; i < 3; i++ {
		rr = httptest.NewRecorder()
		req = httptest.NewRequest(http.MethodGet, "/api/pull?id=dev1", nil)
		mux.ServeHTTP(rr, req)
		raw, _ := io.ReadAll(rr.Body)
		var cmd map[string]any
		if err := json.Unmarshal(raw, &cmd); err != nil {
			t.Fatal(err)
		}
		if cmd["type"] != "control" {
			t.Fatalf("pull %d want control, got %s", i, raw)
		}
		action, _ := cmd["action"].(string)
		got[action] = true
		if action == "libstop" {
			name, _ := cmd["libName"].(string)
			if name != "test.lua" && name != "learn.lua" {
				t.Fatalf("libstop name %s", raw)
			}
		}
	}
	if !got["libstopall"] || !got["libstop"] {
		t.Fatalf("want libstopall + per-name libstop, got %v", got)
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

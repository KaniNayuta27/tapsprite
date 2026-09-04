package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func resetUpdateState() {
	updMu.Lock()
	updBusy = false
	updState = updateStatus{Phase: "idle", Msg: ""}
	updMu.Unlock()
	apkMu.Lock()
	apkState = apkJob{}
	apkMu.Unlock()
}

func withDownloads(t *testing.T, dir string, self string) {
	t.Helper()
	prevDir := downloadsDirFn
	prevExe := executablePathFn
	downloadsDirFn = func() string { return dir }
	executablePathFn = func() (string, error) { return self, nil }
	t.Cleanup(func() {
		downloadsDirFn = prevDir
		executablePathFn = prevExe
	})
}

func writePkg(t *testing.T, path, body string, mod time.Time) {
	t.Helper()
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
	if !mod.IsZero() {
		_ = os.Chtimes(path, mod, mod)
	}
}

func TestCleanupOldExeKeepsNewestAndDeletesPrevious(t *testing.T) {
	dir := t.TempDir()
	old1 := filepath.Join(dir, "tapsprite1-1-72.exe")
	old2 := filepath.Join(dir, "tapsprite1-1-73.exe")
	neu := filepath.Join(dir, "tapsprite1-1-74.exe")
	withDownloads(t, dir, neu)
	base := time.Now().Add(-3 * time.Hour)
	writePkg(t, old1, "old72", base)
	writePkg(t, old2, "old73", base.Add(time.Hour))
	writePkg(t, neu, "new74", base.Add(2*time.Hour))

	cleanupOldDownloads("exe", neu)

	if _, err := os.Stat(old1); !os.IsNotExist(err) {
		t.Fatalf("old 1.1.72 should be deleted, stat=%v", err)
	}
	if _, err := os.Stat(old2); !os.IsNotExist(err) {
		t.Fatalf("old 1.1.73 should be deleted, stat=%v", err)
	}
	if _, err := os.Stat(neu); err != nil {
		t.Fatalf("newest exe must remain: %v", err)
	}
}

func TestCleanupOldApkKeepsNewestAndDeletesPrevious(t *testing.T) {
	dir := t.TempDir()
	old1 := filepath.Join(dir, "tapsprite0-9-63.apk")
	old2 := filepath.Join(dir, "tapsprite0-9-64.apk")
	neu := filepath.Join(dir, "tapsprite0-9-65.apk")
	withDownloads(t, dir, filepath.Join(dir, "tapsprite1-1-74.exe"))
	base := time.Now().Add(-3 * time.Hour)
	writePkg(t, old1, "apk63", base)
	writePkg(t, old2, "apk64", base.Add(time.Hour))
	writePkg(t, neu, "apk65", base.Add(2*time.Hour))

	cleanupOldDownloads("apk", neu)

	if _, err := os.Stat(old1); !os.IsNotExist(err) {
		t.Fatalf("old apk 0.9.63 should be deleted, stat=%v", err)
	}
	if _, err := os.Stat(old2); !os.IsNotExist(err) {
		t.Fatalf("old apk 0.9.64 should be deleted, stat=%v", err)
	}
	if _, err := os.Stat(neu); err != nil {
		t.Fatalf("newest apk must remain: %v", err)
	}
}

func TestCleanupKeepsRunningOldExeUntilNewProcessTakesOver(t *testing.T) {
	dir := t.TempDir()
	old1 := filepath.Join(dir, "tapsprite1-1-72.exe")
	running := filepath.Join(dir, "tapsprite1-1-73.exe")
	neu := filepath.Join(dir, "tapsprite1-1-74.exe")
	withDownloads(t, dir, running)
	base := time.Now().Add(-3 * time.Hour)
	writePkg(t, old1, "old72", base)
	writePkg(t, running, "old73", base.Add(time.Hour))
	writePkg(t, neu, "new74", base.Add(2*time.Hour))

	// Old process just launched the new dest: cannot delete itself, must still drop other old packages.
	cleanupOldDownloads("exe", neu)
	if _, err := os.Stat(old1); !os.IsNotExist(err) {
		t.Fatalf("older sibling should be deleted, stat=%v", err)
	}
	if _, err := os.Stat(running); err != nil {
		t.Fatalf("currently running exe must remain: %v", err)
	}
	if _, err := os.Stat(neu); err != nil {
		t.Fatalf("new dest must remain: %v", err)
	}

	// New process startup: keep only itself.
	executablePathFn = func() (string, error) { return neu, nil }
	cleanupOldDownloads("exe", neu)
	if _, err := os.Stat(running); !os.IsNotExist(err) {
		t.Fatalf("previous exe should be deleted after new process starts, stat=%v", err)
	}
	if _, err := os.Stat(neu); err != nil {
		t.Fatalf("new exe must remain after takeover: %v", err)
	}
}

func TestCleanupIgnoresUnrelatedFiles(t *testing.T) {
	dir := t.TempDir()
	keep := filepath.Join(dir, "notes.txt")
	old := filepath.Join(dir, "tapsprite1-1-70.exe")
	neu := filepath.Join(dir, "tapsprite1-1-74.exe")
	withDownloads(t, dir, neu)
	writePkg(t, keep, "hello", time.Time{})
	writePkg(t, old, "old", time.Now().Add(-time.Hour))
	writePkg(t, neu, "new", time.Now())
	cleanupOldDownloads("exe", neu)
	if _, err := os.Stat(keep); err != nil {
		t.Fatalf("unrelated file must not be deleted: %v", err)
	}
	if _, err := os.Stat(old); !os.IsNotExist(err) {
		t.Fatalf("old exe should be deleted")
	}
}

func TestApkUpdateNeeded(t *testing.T) {
	if apkUpdateNeeded(90, 90, "0.9.65", "0.9.65") {
		t.Fatal("same versionCode should not need update")
	}
	if apkUpdateNeeded(90, 91, "0.9.65", "0.9.66") {
		t.Fatal("older remote should not need update")
	}
	if !apkUpdateNeeded(91, 90, "0.9.66", "0.9.65") {
		t.Fatal("newer versionCode should need update")
	}
	if apkUpdateNeeded(0, 0, "0.9.65", "0.9.65") {
		t.Fatal("same name fallback should not need update")
	}
	if !apkUpdateNeeded(0, 0, "0.9.66", "0.9.65") {
		t.Fatal("newer name fallback should need update")
	}
	if !apkUpdateNeeded(91, 0, "0.9.66", "") {
		t.Fatal("unknown local must need update")
	}
}

func TestApkFileName(t *testing.T) {
	if g := apkFileName("0.9.66"); g != "tapsprite0-9-66.apk" {
		t.Fatalf("got %s", g)
	}
	if g := apkFileName("tapsprite0-9-66.apk"); g != "tapsprite0-9-66.apk" {
		t.Fatalf("got %s", g)
	}
}

func TestJsonInt(t *testing.T) {
	if jsonInt(float64(91)) != 91 {
		t.Fatal("float64")
	}
	if jsonInt("91") != 91 {
		t.Fatal("string")
	}
	if jsonInt(nil) != 0 {
		t.Fatal("nil")
	}
}

func TestApkUpdateAlreadyLatest(t *testing.T) {
	resetSrv()
	resetUpdateState()
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"versionCode":90,"versionName":"0.9.65","apk_ver":"0.9.65","apk":"http://example/x.apk"}`))
	}))
	defer ts.Close()
	old := channelURLs
	channelURLs = []string{ts.URL}
	t.Cleanup(func() { channelURLs = old })

	srv.devices["dev1"] = &Device{ID: "dev1", Name: "phone", VerCode: 90, VerName: "0.9.65", Online: true, Seen: time.Now()}
	srv.selected = "dev1"

	rr := httptest.NewRecorder()
	handleApkUpdate(rr, httptest.NewRequest(http.MethodPost, "/api/apkupdate", strings.NewReader("{}")))

	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		st := getUpdate()
		if st.Phase == "idle" && strings.Contains(st.Msg, "已是最新") {
			return
		}
		if st.Phase == "error" {
			t.Fatalf("unexpected error: %s", st.Msg)
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatalf("timeout waiting for latest, got %+v", getUpdate())
}

func TestApkUpdateEnqueuesWhenNewer(t *testing.T) {
	resetSrv()
	resetUpdateState()
	dir := t.TempDir()
	withDownloads(t, dir, filepath.Join(dir, "tapsprite1-1-76.exe"))

	payload := bytes.Repeat([]byte("a"), 12000)
	mux := http.NewServeMux()
	mux.HandleFunc("/channel", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprintf(w, `{"versionCode":91,"versionName":"0.9.66","apk_ver":"0.9.66","apk":"http://%s/app.apk"}`, r.Host)
	})
	mux.HandleFunc("/app.apk", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write(payload)
	})
	ts := httptest.NewServer(mux)
	defer ts.Close()

	old := channelURLs
	channelURLs = []string{ts.URL + "/channel"}
	t.Cleanup(func() { channelURLs = old })

	srv.devices["dev1"] = &Device{ID: "dev1", Name: "phone", VerCode: 90, VerName: "0.9.65", Online: true, Seen: time.Now()}
	srv.selected = "dev1"

	rr := httptest.NewRecorder()
	handleApkUpdate(rr, httptest.NewRequest(http.MethodPost, "/api/apkupdate", strings.NewReader("{}")))

	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		st := getUpdate()
		if st.Phase == "idle" && strings.Contains(st.Msg, "已通知手机") {
			break
		}
		if st.Phase == "error" {
			t.Fatalf("error: %s", st.Msg)
		}
		time.Sleep(20 * time.Millisecond)
	}
	if !strings.Contains(getUpdate().Msg, "已通知手机") {
		t.Fatalf("want notify msg, got %+v", getUpdate())
	}
	dest := filepath.Join(dir, "tapsprite0-9-66.apk")
	if fi, err := os.Stat(dest); err != nil || fi.Size() < 10000 {
		t.Fatalf("apk not downloaded: %v", err)
	}
	srv.mu.Lock()
	q := srv.queues["dev1"]
	srv.mu.Unlock()
	if len(q) == 0 {
		t.Fatal("expected update command queued")
	}
	var cmd map[string]any
	if err := json.Unmarshal(q[0], &cmd); err != nil {
		t.Fatal(err)
	}
	if cmd["type"] != "control" || cmd["action"] != "update" {
		t.Fatalf("want control/update got %v", cmd)
	}
}

func TestFetchApkReusesExistingFile(t *testing.T) {
	resetUpdateState()
	dir := t.TempDir()
	withDownloads(t, dir, filepath.Join(dir, "tapsprite1-1-76.exe"))
	dest := filepath.Join(dir, "tapsprite0-9-66.apk")
	if err := os.WriteFile(dest, bytes.Repeat([]byte("b"), 12000), 0o644); err != nil {
		t.Fatal(err)
	}

	body := `{"url":"http://127.0.0.1:1/missing.apk","name":"0.9.66"}`
	rr := httptest.NewRecorder()
	handleFetchApk(rr, httptest.NewRequest(http.MethodPost, "/api/fetchapk", strings.NewReader(body)))

	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		apkMu.Lock()
		ready := apkState.Ready
		path := apkState.Path
		busy := apkState.Busy
		apkMu.Unlock()
		if ready && path == dest && !busy {
			return
		}
		time.Sleep(15 * time.Millisecond)
	}
	apkMu.Lock()
	defer apkMu.Unlock()
	t.Fatalf("want reuse ready file, got busy=%v ready=%v path=%s", apkState.Busy, apkState.Ready, apkState.Path)
}

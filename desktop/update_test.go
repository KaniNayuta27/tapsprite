package main

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

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

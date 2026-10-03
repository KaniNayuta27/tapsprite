//go:build windows

package main

import (
	"os"
	"path/filepath"
	"time"
)

func writeStartupLog(msg string) {
	exe, err := os.Executable()
	dir := "."
	if err == nil {
		dir = filepath.Dir(exe)
	}
	path := filepath.Join(dir, "tapsprite-desktop.log")
	line := time.Now().Format("2006-01-02 15:04:05") + " " + msg + "\n"
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		return
	}
	_, _ = f.WriteString(line)
	_ = f.Close()
}

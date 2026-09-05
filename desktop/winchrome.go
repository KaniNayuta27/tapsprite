package main

import (
	"net/http"
	"strings"
)

func handleWin(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		writeJSON(w, map[string]any{"ok": true, "maximized": winIsMaximized()})
		return
	}
	var body struct {
		Action string `json:"action"`
	}
	_ = readJSON(r, &body)
	action := strings.ToLower(strings.TrimSpace(body.Action))
	if action == "close" {
		writeJSON(w, map[string]any{"ok": true, "maximized": winIsMaximized()})
		if f, ok := w.(http.Flusher); ok {
			f.Flush()
		}
		quitWebView()
		return
	}
	switch action {
	case "min":
		winMinimize()
	case "max":
		winToggleMax()
	case "drag":
		winDrag()
	}
	writeJSON(w, map[string]any{"ok": true, "maximized": winIsMaximized()})
}

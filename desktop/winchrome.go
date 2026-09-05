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
	switch strings.ToLower(strings.TrimSpace(body.Action)) {
	case "min":
		winMinimize()
	case "max":
		winToggleMax()
	case "close":
		quitWebView()
	case "drag":
		winDrag()
	}
	writeJSON(w, map[string]any{"ok": true, "maximized": winIsMaximized()})
}

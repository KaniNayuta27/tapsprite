package main

import (
	"embed"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

//go:embed scripts/*.lua
var scriptsFS embed.FS

// defaultTestLua matches home DEFAULT in web/ui.html exactly (including trailing newline).
const defaultTestLua = "KeyPress(\"Home\")\nDelay(1000)\nTap(167, 775)\nDelay(2000)\nKeyPress(\"Back\")\nDelay(2000)\n"

func validLibName(name string) bool {
	if name == "" {
		return false
	}
	if name != filepath.Base(name) {
		return false
	}
	if strings.Contains(name, "..") || strings.ContainsAny(name, `/\`) {
		return false
	}
	if !strings.HasSuffix(strings.ToLower(name), ".lua") {
		return false
	}
	return name != ".lua"
}

func userScriptsDir() string {
	if exe, err := os.Executable(); err == nil {
		return filepath.Join(filepath.Dir(exe), "scripts")
	}
	wd, _ := os.Getwd()
	return filepath.Join(wd, "scripts")
}

func listLibraryScripts() []string {
	seen := map[string]bool{}
	var names []string
	add := func(n string) {
		n = filepath.Base(n)
		if !validLibName(n) || seen[n] {
			return
		}
		seen[n] = true
		names = append(names, n)
	}
	if entries, err := fs.ReadDir(scriptsFS, "scripts"); err == nil {
		for _, e := range entries {
			if !e.IsDir() {
				add(e.Name())
			}
		}
	}
	if ents, err := os.ReadDir(userScriptsDir()); err == nil {
		for _, e := range ents {
			if !e.IsDir() {
				add(e.Name())
			}
		}
	}
	if !seen["test.lua"] {
		names = append(names, "test.lua")
	}
	sort.Slice(names, func(i, j int) bool {
		if names[i] == "test.lua" {
			return true
		}
		if names[j] == "test.lua" {
			return false
		}
		return names[i] < names[j]
	})
	return names
}

func readLibrary(name string) (string, error) {
	if !validLibName(name) {
		return "", os.ErrInvalid
	}
	disk := filepath.Join(userScriptsDir(), name)
	if b, err := os.ReadFile(disk); err == nil {
		return string(b), nil
	}
	if b, err := scriptsFS.ReadFile("scripts/" + name); err == nil {
		return string(b), nil
	}
	if name == "test.lua" {
		return defaultTestLua, nil
	}
	return "", os.ErrNotExist
}

func writeLibrary(name, body string) error {
	if !validLibName(name) {
		return os.ErrInvalid
	}
	dir := userScriptsDir()
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(dir, name), []byte(body), 0o644)
}

func handleLibrary(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		name := strings.TrimSpace(r.URL.Query().Get("name"))
		if name == "" {
			files := make([]map[string]any, 0)
			for _, n := range listLibraryScripts() {
				files = append(files, map[string]any{"name": n})
			}
			writeJSON(w, map[string]any{"ok": true, "files": files})
			return
		}
		body, err := readLibrary(name)
		if err != nil {
			writeJSON(w, map[string]any{"ok": false, "err": "未找到脚本"})
			return
		}
		writeJSON(w, map[string]any{"ok": true, "name": name, "script": body})
	case http.MethodPost:
		var body struct {
			Name   string `json:"name"`
			Script string `json:"script"`
		}
		_ = readJSON(r, &body)
		if err := writeLibrary(body.Name, body.Script); err != nil {
			writeJSON(w, map[string]any{"ok": false, "err": err.Error()})
			return
		}
		writeJSON(w, map[string]any{"ok": true, "name": body.Name})
	default:
		http.Error(w, "method", 405)
	}
}

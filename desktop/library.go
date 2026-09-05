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

// defaultTest2Lua is the built-in seed for test2.lua (no TickCount).
const defaultTest2Lua = `while true do
  intX, intY = FindMultiColor(990, 2100, 1050, 2385, "CC7B00", "20|31|CC7B00", 0, 1)
  if intX > -1 then
    intX1, intY1 = intX, intY
    TracePrint("检测到继续")

    repeat
      TracePrint("点继续")
      Tap(978, 2240)
      Delay(300)
    until not CmpColor(intX1, intY1, "CC7B00", 1) and not CmpColor(intX1 + 20, intY1 + 31, "CC7B00", 1)

    repeat
      intX, intY = FindMultiColor(90, 2122, 300, 2147, "CC7B00", "60|20|CC7B00", 0, 1)
      Delay(100)
    until intX > -1

    intX1, intY1 = intX, intY
    repeat
      TracePrint("点开始")
      Tap(845, 2150)
      Delay(300)
    until not CmpColor(intX1, intY1, "CC7B00", 1) and not CmpColor(intX1 + 60, intY1 + 20, "CC7B00", 1)
    Delay(1000)
  end
  Delay(100)
end
`

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
	if !seen["test2.lua"] {
		names = append(names, "test2.lua")
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
	if name == "test2.lua" {
		return defaultTest2Lua, nil
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

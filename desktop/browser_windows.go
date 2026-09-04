//go:build windows

package main

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

// openAppWindow launches Edge/Chrome in --app= mode (aligned with official 1.1.62 shell).
// Does NOT open the system default browser tab.
func openAppWindow(url string) {
	time.Sleep(400 * time.Millisecond)
	exe, args := findChromiumApp(url)
	if exe == "" {
		msg := fmt.Sprintf("未找到 Edge/Chrome，请安装后手动打开 %s（独立窗 = Edge/Chrome 应用模式）", url)
		log.Print(msg)
		addLog(msg)
		return
	}
	cmd := exec.Command(exe, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	if err := cmd.Start(); err != nil {
		log.Printf("启动应用窗失败: %v", err)
		addLog("启动应用窗失败: " + err.Error())
		return
	}
	addLog("已用应用模式打开 " + filepath.Base(exe))
}

func findChromiumApp(url string) (string, []string) {
	edgeArgs := []string{
		"--app=" + url,
		"--window-size=1280,800",
		"--disable-features=msEdgeSync,msSignedInAccount",
	}
	chromeArgs := []string{
		"--app=" + url,
		"--window-size=1280,800",
	}

	local := os.Getenv("LOCALAPPDATA")
	pf := os.Getenv("ProgramFiles")
	pf86 := os.Getenv("ProgramFiles(x86)")
	if pf == "" {
		pf = `C:\Program Files`
	}
	if pf86 == "" {
		pf86 = `C:\Program Files (x86)`
	}

	type cand struct {
		path string
		args []string
	}
	var list []cand
	for _, base := range []string{pf, pf86, local} {
		if base == "" {
			continue
		}
		list = append(list,
			cand{filepath.Join(base, `Microsoft\Edge\Application\msedge.exe`), edgeArgs},
			cand{filepath.Join(base, `Google\Chrome\Application\chrome.exe`), chromeArgs},
		)
	}
	if local != "" {
		list = append(list, cand{filepath.Join(local, `Google\Chrome\Application\chrome.exe`), chromeArgs})
	}

	for _, c := range list {
		if st, err := os.Stat(c.path); err == nil && !st.IsDir() {
			return c.path, c.args
		}
	}

	if p, err := exec.LookPath("msedge.exe"); err == nil {
		return p, edgeArgs
	}
	if p, err := exec.LookPath("msedge"); err == nil {
		return p, edgeArgs
	}
	if p, err := exec.LookPath("chrome.exe"); err == nil {
		return p, chromeArgs
	}
	if p, err := exec.LookPath("chrome"); err == nil {
		return p, chromeArgs
	}
	return "", nil
}

// allowFirewall adds inbound allow rules for TCP/UDP 18766 (best-effort).
func allowFirewall() {
	nameTCP := "TapSprite HTTP 18766"
	nameUDP := "TapSprite UDP 18766"
	_ = exec.Command("netsh", "advfirewall", "firewall", "delete", "rule", "name="+nameTCP).Run()
	_ = exec.Command("netsh", "advfirewall", "firewall", "delete", "rule", "name="+nameUDP).Run()
	out, err := exec.Command("netsh", "advfirewall", "firewall", "add", "rule",
		"name="+nameTCP, "dir=in", "action=allow", "protocol=TCP", "localport=18766").CombinedOutput()
	if err != nil {
		log.Printf("firewall TCP rule: %v (%s)", err, strings.TrimSpace(string(out)))
		addLog("防火墙 TCP 放行失败（可手动放行 18766）")
	} else {
		addLog("已尝试放行防火墙 TCP 18766")
	}
	out, err = exec.Command("netsh", "advfirewall", "firewall", "add", "rule",
		"name="+nameUDP, "dir=in", "action=allow", "protocol=UDP", "localport=18766").CombinedOutput()
	if err != nil {
		log.Printf("firewall UDP rule: %v (%s)", err, strings.TrimSpace(string(out)))
		addLog("防火墙 UDP 放行失败（可手动放行 18766）")
	} else {
		addLog("已尝试放行防火墙 UDP 18766")
	}
}

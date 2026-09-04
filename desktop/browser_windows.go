//go:build windows

package main

import (
	"log"
	"os/exec"
	"strings"
)

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

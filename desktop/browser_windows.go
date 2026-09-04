//go:build windows

package main

// allowFirewall used to shell out to netsh and flashed black console windows.
// Auto-rules removed for zero-flash startup; user can allow 18766 manually if needed.
func allowFirewall() {
	addLog("未自动改防火墙；若手机连不上，请手动放行入站 TCP/UDP 18766")
}

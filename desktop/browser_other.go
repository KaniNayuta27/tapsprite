//go:build !windows

package main

func allowFirewall() {
	addLog("非 Windows：跳过防火墙提示")
}

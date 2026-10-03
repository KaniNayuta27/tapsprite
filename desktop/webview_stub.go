//go:build !windows

package main

import "log"

// runWebView is a no-op shell on non-Windows (Linux CI / go test).
// PC product builds use webview_windows.go (WebView2).
func runWebView(url string) {
	log.Printf("webview stub (non-windows): UI would open at %s — use Windows build for WebView2", url)
}

func quitWebView() {}

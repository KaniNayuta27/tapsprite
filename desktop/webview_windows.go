//go:build windows

package main

import (
	"log"
	"os"

	"github.com/jchv/go-webview2"
)

// runWebView creates an in-process WebView2 window and blocks the main thread
// until the window is closed (or Terminate). Requires WebView2 Runtime.
func runWebView(url string) {
	setProcessDPIAware()
	title := "触控精灵 v" + version
	w := webview2.NewWithOptions(webview2.WebViewOptions{
		Debug:     false,
		AutoFocus: true,
		WindowOptions: webview2.WindowOptions{
			Title:  title,
			Width:  1280,
			Height: 800,
			Center: true,
		},
	})
	if w == nil {
		msg := "WebView2 创建失败：请安装 Microsoft Edge WebView2 Runtime 后重试（https://go.microsoft.com/fwlink/p/?LinkId=2124703）"
		log.Print(msg)
		addLog(msg)
		writeStartupLog(msg)
		// Keep HTTP/UDP so LAN can still work; no MessageBox / no console flash.
		select {}
	}
	webviewInst = w
	defer func() {
		w.Destroy()
		os.Exit(0)
	}()
	applyWindowChrome(uintptr(w.Window()))
	w.Navigate(url)
	addLog("已打开内嵌 WebView2 窗口")
	log.Printf("WebView2 ready → %s", url)
	w.Run()
}

// quitWebView posts WM_CLOSE to the WebView2 HWND (same path as Alt+F4).
// Terminate()/PostQuitMessage on an HTTP worker thread never reaches the UI
// message loop, which is why the custom title-bar X previously did nothing.
func quitWebView() {
	hwnd := winHWND()
	if hwnd != 0 {
		winPostClose(hwnd)
	}
	if webviewInst != nil {
		webviewInst.Destroy()
	}
}

var webviewInst webview2.WebView

//go:build windows

package main

import (
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	user32                            = windows.NewLazySystemDLL("user32.dll")
	dwmapi                            = windows.NewLazySystemDLL("dwmapi.dll")
	procGetWindowLongPtrW             = user32.NewProc("GetWindowLongPtrW")
	procSetWindowLongPtrW             = user32.NewProc("SetWindowLongPtrW")
	procSetWindowPos                  = user32.NewProc("SetWindowPos")
	procShowWindow                    = user32.NewProc("ShowWindow")
	procIsZoomed                      = user32.NewProc("IsZoomed")
	procReleaseCapture                = user32.NewProc("ReleaseCapture")
	procSendMessageW                  = user32.NewProc("SendMessageW")
	procPostMessageW                  = user32.NewProc("PostMessageW")
	procSetProcessDpiAwarenessContext = user32.NewProc("SetProcessDpiAwarenessContext")
	procDwmSetWindowAttribute         = dwmapi.NewProc("DwmSetWindowAttribute")
)

const (
	gwlStyle                             = ^uintptr(15) // -16
	wsCaption                            = 0x00C00000
	swpNoSize                            = 0x0001
	swpNoMove                            = 0x0002
	swpNoZOrder                          = 0x0004
	swpNoActivate                        = 0x0010
	swpFrameChanged                      = 0x0020
	swMinimize                           = 6
	swMaximize                           = 3
	swRestore                            = 9
	wmNCLButtonDown                      = 0x00A1
	wmClose                              = 0x0010
	htCaption                            = 2
	dwmwaWindowCornerPreference          = 33
	dwmwcpRound                          = 2
	dpiAwarenessContextPerMonitorAwareV2 = ^uintptr(3) // -4
)

func setProcessDPIAware() {
	_, _, _ = procSetProcessDpiAwarenessContext.Call(dpiAwarenessContextPerMonitorAwareV2)
}

func applyWindowChrome(hwnd uintptr) {
	if hwnd == 0 {
		return
	}
	pref := uint32(dwmwcpRound)
	_, _, _ = procDwmSetWindowAttribute.Call(
		hwnd,
		uintptr(dwmwaWindowCornerPreference),
		uintptr(unsafe.Pointer(&pref)),
		4,
	)
	style, _, _ := procGetWindowLongPtrW.Call(hwnd, gwlStyle)
	// Keep thick-frame / min / max / sysmenu so snap, resize, and taskbar still work.
	style &^= wsCaption
	_, _, _ = procSetWindowLongPtrW.Call(hwnd, gwlStyle, style)
	_, _, _ = procSetWindowPos.Call(
		hwnd, 0, 0, 0, 0, 0,
		swpNoSize|swpNoMove|swpNoZOrder|swpNoActivate|swpFrameChanged,
	)
}

func winHWND() uintptr {
	if webviewInst == nil {
		return 0
	}
	return uintptr(webviewInst.Window())
}

func onUI(f func()) {
	if webviewInst != nil {
		webviewInst.Dispatch(f)
		return
	}
	f()
}

func winIsMaximized() bool {
	hwnd := winHWND()
	if hwnd == 0 {
		return false
	}
	r, _, _ := procIsZoomed.Call(hwnd)
	return r != 0
}

func winMinimize() {
	onUI(func() {
		hwnd := winHWND()
		if hwnd == 0 {
			return
		}
		_, _, _ = procShowWindow.Call(hwnd, swMinimize)
	})
}

func winToggleMax() {
	onUI(func() {
		hwnd := winHWND()
		if hwnd == 0 {
			return
		}
		cmd := uintptr(swMaximize)
		if r, _, _ := procIsZoomed.Call(hwnd); r != 0 {
			cmd = swRestore
		}
		_, _, _ = procShowWindow.Call(hwnd, cmd)
	})
}

func winDrag() {
	onUI(func() {
		hwnd := winHWND()
		if hwnd == 0 {
			return
		}
		_, _, _ = procReleaseCapture.Call()
		_, _, _ = procSendMessageW.Call(hwnd, wmNCLButtonDown, htCaption, 0)
	})
}

func winPostClose(hwnd uintptr) {
	if hwnd == 0 {
		return
	}
	_, _, _ = procPostMessageW.Call(hwnd, wmClose, 0, 0)
}

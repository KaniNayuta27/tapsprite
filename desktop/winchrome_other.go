//go:build !windows

package main

func setProcessDPIAware() {}

func applyWindowChrome(hwnd uintptr) {}

func winIsMaximized() bool { return false }

func winMinimize() {}

func winToggleMax() {}

func winDrag() {}

func winPostClose(hwnd uintptr) {}

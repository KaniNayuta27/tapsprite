//go:build !windows

package main

import (
	"log"
	"os/exec"
	"runtime"
	"time"
)

// openAppWindow on non-Windows: open default browser for Linux smoke tests only.
func openAppWindow(url string) {
	time.Sleep(400 * time.Millisecond)
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	if err := cmd.Start(); err != nil {
		log.Printf("open browser (smoke): %v — UI at %s", err, url)
	}
}

func allowFirewall() {
	// no-op outside Windows
}

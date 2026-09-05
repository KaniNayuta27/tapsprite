//go:build windows

package main

import (
	"fmt"
	"os"
	"os/exec"
	"syscall"
)

// launchDetached starts path via cmd /C start with CREATE_NO_WINDOW (no flash).
// extraArgs (e.g. --rejoin=ip) are passed only at self-update relaunch.
func launchDetached(path string, extraArgs ...string) error {
	if path == "" {
		return fmt.Errorf("empty path")
	}
	if _, err := os.Stat(path); err != nil {
		return err
	}
	args := []string{"/C", "start", "", path}
	args = append(args, extraArgs...)
	cmd := exec.Command("cmd", args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: 0x08000000, // CREATE_NO_WINDOW
	}
	return cmd.Start()
}

//go:build windows

package main

import (
	"fmt"
	"os"
	"os/exec"
	"syscall"
)

// launchDetached starts path via cmd /C start with CREATE_NO_WINDOW (no flash).
func launchDetached(path string) error {
	if path == "" {
		return fmt.Errorf("empty path")
	}
	if _, err := os.Stat(path); err != nil {
		return err
	}
	cmd := exec.Command("cmd", "/C", "start", "", path)
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: 0x08000000, // CREATE_NO_WINDOW
	}
	return cmd.Start()
}

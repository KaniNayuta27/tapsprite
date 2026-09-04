//go:build !windows

package main

import (
	"fmt"
	"os"
	"os/exec"
)

func launchDetached(path string) error {
	if path == "" {
		return fmt.Errorf("empty path")
	}
	if _, err := os.Stat(path); err != nil {
		return err
	}
	cmd := exec.Command(path)
	return cmd.Start()
}

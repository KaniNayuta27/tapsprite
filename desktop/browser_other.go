//go:build !windows

package main

func allowFirewall() {
	// no-op outside Windows
}

//go:build !windows

package main

import (
	"net/http"
	"net/url"
)

// resolveProxy: non-Windows uses environment proxy only.
func resolveProxy(req *http.Request) (*url.URL, error) {
	return http.ProxyFromEnvironment(req)
}

//go:build windows

package main

import (
	"net"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"golang.org/x/sys/windows/registry"
)

// Common local Clash / V2Ray / Clash Meta ports (TCP probe fallback).
var commonLocalProxyPorts = []string{"7890", "7897", "10809", "10808"}

var (
	localProxyOnce sync.Once
	localProxyURL  *url.URL
)

// resolveProxy: Windows system proxy → env → local common-port probe.
func resolveProxy(req *http.Request) (*url.URL, error) {
	if u := windowsSystemProxy(req); u != nil {
		return u, nil
	}
	if u, err := http.ProxyFromEnvironment(req); err == nil && u != nil {
		return u, nil
	}
	if u := probeLocalProxy(); u != nil {
		return u, nil
	}
	return nil, nil
}

func windowsSystemProxy(req *http.Request) *url.URL {
	k, err := registry.OpenKey(registry.CURRENT_USER,
		`Software\Microsoft\Windows\CurrentVersion\Internet Settings`, registry.QUERY_VALUE)
	if err != nil {
		return nil
	}
	defer k.Close()

	enable, _, err := k.GetIntegerValue("ProxyEnable")
	if err == nil && enable == 1 {
		server, _, err := k.GetStringValue("ProxyServer")
		if err == nil && strings.TrimSpace(server) != "" {
			if bypassHost(req, k) {
				return nil // explicit direct for this host
			}
			if u := parseProxyServer(server, req); u != nil {
				return u
			}
		}
	}

	// PAC AutoConfigURL alone: full WinHttpGetProxyForUrl is costly; static ProxyServer
	// already covers Clash/V2 "system proxy". Leave PAC unresolved here (documented gap).
	return nil
}

func bypassHost(req *http.Request, k registry.Key) bool {
	if req == nil || req.URL == nil {
		return false
	}
	override, _, err := k.GetStringValue("ProxyOverride")
	if err != nil || override == "" {
		return false
	}
	host := req.URL.Hostname()
	if host == "" {
		return false
	}
	for _, p := range strings.Split(override, ";") {
		p = strings.TrimSpace(strings.ToLower(p))
		if p == "" {
			continue
		}
		if p == "<local>" {
			if isLocalHost(host) {
				return true
			}
			continue
		}
		if matchProxyOverride(host, p) {
			return true
		}
	}
	return false
}

func isLocalHost(host string) bool {
	h := strings.ToLower(host)
	return h == "localhost" || h == "127.0.0.1" || h == "::1" || !strings.Contains(h, ".")
}

func matchProxyOverride(host, pattern string) bool {
	host = strings.ToLower(host)
	pattern = strings.ToLower(pattern)
	if pattern == host {
		return true
	}
	if strings.HasPrefix(pattern, "*.") {
		suf := pattern[1:] // ".example.com"
		return strings.HasSuffix(host, suf)
	}
	if strings.HasPrefix(pattern, "*") {
		return strings.HasSuffix(host, strings.TrimPrefix(pattern, "*"))
	}
	return false
}

// parseProxyServer handles "host:port" or "http=h:p;https=h:p;socks=h:p".
func parseProxyServer(server string, req *http.Request) *url.URL {
	server = strings.TrimSpace(server)
	if server == "" {
		return nil
	}
	scheme := "http"
	if req != nil && req.URL != nil && req.URL.Scheme != "" {
		scheme = strings.ToLower(req.URL.Scheme)
	}

	if !strings.Contains(server, "=") {
		return proxyURLFromHostPort(server)
	}

	var fallback, httpEntry string
	for _, part := range strings.Split(server, ";") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		kv := strings.SplitN(part, "=", 2)
		if len(kv) != 2 {
			if fallback == "" {
				fallback = part
			}
			continue
		}
		key := strings.ToLower(strings.TrimSpace(kv[0]))
		val := strings.TrimSpace(kv[1])
		if key == scheme {
			return proxyURLFromHostPort(val)
		}
		if key == "http" && httpEntry == "" {
			httpEntry = val
		}
		if fallback == "" {
			fallback = val
		}
	}
	if httpEntry != "" {
		return proxyURLFromHostPort(httpEntry)
	}
	if fallback != "" {
		return proxyURLFromHostPort(fallback)
	}
	return nil
}

func proxyURLFromHostPort(s string) *url.URL {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil
	}
	if strings.Contains(s, "://") {
		u, err := url.Parse(s)
		if err != nil || u.Host == "" {
			return nil
		}
		if u.Scheme == "" {
			u.Scheme = "http"
		}
		if u.Scheme == "socks" || u.Scheme == "socks5" {
			u.Scheme = "socks5"
		}
		return u
	}
	s = strings.TrimPrefix(s, "//")
	return &url.URL{Scheme: "http", Host: s}
}

func probeLocalProxy() *url.URL {
	localProxyOnce.Do(func() {
		for _, port := range commonLocalProxyPorts {
			addr := "127.0.0.1:" + port
			c, err := net.DialTimeout("tcp", addr, 200*time.Millisecond)
			if err != nil {
				continue
			}
			_ = c.Close()
			localProxyURL = &url.URL{Scheme: "http", Host: addr}
			return
		}
	})
	return localProxyURL
}

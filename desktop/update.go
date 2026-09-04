package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// channelURLs are tried in order when fetching dist-channel.json.
var channelURLs = []string{
	"https://raw.githubusercontent.com/KaniNayuta27/tapsprite/rebuild/source-from-binaries/dist-channel.json",
	"https://github.com/KaniNayuta27/tapsprite/raw/rebuild/source-from-binaries/dist-channel.json",
	"https://ghproxy.net/https://raw.githubusercontent.com/KaniNayuta27/tapsprite/rebuild/source-from-binaries/dist-channel.json",
	"https://mirror.ghproxy.com/https://raw.githubusercontent.com/KaniNayuta27/tapsprite/rebuild/source-from-binaries/dist-channel.json",
}

const netTimeoutMsg = "网络超时，请开代理或稍后重试"

type updateStatus struct {
	Phase   string `json:"phase"`
	Percent int    `json:"percent"`
	Got     int64  `json:"got"`
	Total   int64  `json:"total"`
	Msg     string `json:"msg"`
}

type apkJob struct {
	Busy  bool   `json:"busy"`
	Ready bool   `json:"ready"`
	Got   int64  `json:"got"`
	Total int64  `json:"total"`
	Err   string `json:"err"`
	Path  string `json:"-"`
	Name  string `json:"name,omitempty"`
	Msg   string `json:"msg,omitempty"`
}

var (
	updMu    sync.Mutex
	updState = updateStatus{Phase: "idle", Msg: ""}
	updBusy  bool

	apkMu    sync.Mutex
	apkState = apkJob{}
)

func setUpdate(phase string, percent int, got, total int64, msg string) {
	updMu.Lock()
	defer updMu.Unlock()
	updState = updateStatus{Phase: phase, Percent: percent, Got: got, Total: total, Msg: msg}
}

func getUpdate() updateStatus {
	updMu.Lock()
	defer updMu.Unlock()
	return updState
}

func downloadsDir() string {
	home, err := os.UserHomeDir()
	if err != nil || home == "" {
		home = "."
	}
	dir := filepath.Join(home, "Downloads")
	_ = os.MkdirAll(dir, 0o755)
	return dir
}

func httpClient() *http.Client {
	return &http.Client{
		Timeout: 0, // long downloads
		Transport: &http.Transport{
			Proxy:                 resolveProxy,
			TLSHandshakeTimeout:   15 * time.Second,
			ResponseHeaderTimeout: 30 * time.Second,
			IdleConnTimeout:       90 * time.Second,
		},
	}
}

// proxyLabel returns a short proxy=… tag for logs / update bar.
func proxyLabel(req *http.Request) string {
	if req == nil {
		return "proxy=direct"
	}
	u, err := resolveProxy(req)
	if err != nil || u == nil {
		return "proxy=direct"
	}
	host := u.Host
	if host == "" {
		host = u.String()
	}
	scheme := u.Scheme
	if scheme == "" {
		scheme = "http"
	}
	return "proxy=" + scheme + "://" + host
}

// shortURLDisp shows host + last path segment (for update bar; full URL goes to desktop log).
func shortURLDisp(raw string) string {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return ""
	}
	u, err := url.Parse(raw)
	if err != nil || u.Host == "" {
		if len(raw) > 48 {
			return raw[:20] + "…" + raw[len(raw)-20:]
		}
		return raw
	}
	path := u.EscapedPath()
	base := path
	if i := strings.LastIndex(path, "/"); i >= 0 && i+1 < len(path) {
		base = path[i+1:]
	}
	if base == "" {
		base = "/"
	}
	if len(base) > 28 {
		base = "…" + base[len(base)-24:]
	}
	return u.Host + "/" + base
}

type channelInfo struct {
	VersionCode int    `json:"versionCode"`
	VersionName string `json:"versionName"`
	ApkVer      string `json:"apk_ver"`
	ExeVer      string `json:"exe_ver"`
	Apk         string `json:"apk"`
	Exe         string `json:"exe"`
	Notes       string `json:"notes"`
}

// mirrorCandidates returns [original, raw-equivalent, ghproxy wraps…] for GitHub asset URLs.
func mirrorCandidates(u string) []string {
	u = strings.TrimSpace(u)
	if u == "" {
		return nil
	}
	out := []string{u}
	add := func(s string) {
		if s == "" || s == u {
			return
		}
		for _, x := range out {
			if x == s {
				return
			}
		}
		out = append(out, s)
	}

	rawEq := ""
	switch {
	case strings.HasPrefix(u, "https://raw.githubusercontent.com/"):
		// https://raw.githubusercontent.com/owner/repo/branch/path
		rest := strings.TrimPrefix(u, "https://raw.githubusercontent.com/")
		parts := strings.SplitN(rest, "/", 4)
		if len(parts) == 4 {
			rawEq = fmt.Sprintf("https://github.com/%s/%s/raw/%s/%s", parts[0], parts[1], parts[2], parts[3])
		}
	case strings.Contains(u, "github.com/") && strings.Contains(u, "/raw/"):
		// https://github.com/owner/repo/raw/branch/path
		rest := strings.TrimPrefix(u, "https://github.com/")
		rest = strings.TrimPrefix(rest, "http://github.com/")
		parts := strings.SplitN(rest, "/", 5)
		if len(parts) == 5 && parts[2] == "raw" {
			rawEq = fmt.Sprintf("https://raw.githubusercontent.com/%s/%s/%s/%s", parts[0], parts[1], parts[3], parts[4])
		}
	}

	if rawEq != "" {
		add(rawEq)
	}

	// Prefer fetching the raw.githubusercontent form via mirrors (more CDN-friendly).
	mirrorBase := u
	if strings.HasPrefix(rawEq, "https://raw.githubusercontent.com/") {
		mirrorBase = rawEq
	} else if strings.HasPrefix(u, "https://raw.githubusercontent.com/") {
		mirrorBase = u
	} else if rawEq != "" {
		mirrorBase = rawEq
	}

	if strings.Contains(mirrorBase, "github.com") || strings.Contains(mirrorBase, "githubusercontent.com") {
		add("https://ghproxy.net/" + mirrorBase)
		add("https://mirror.ghproxy.com/" + mirrorBase)
	}
	return out
}

func shortNetErr(err error) string {
	if err == nil {
		return netTimeoutMsg
	}
	s := err.Error()
	low := strings.ToLower(s)
	if strings.Contains(low, "timeout") ||
		strings.Contains(low, "deadline") ||
		strings.Contains(low, "timed out") ||
		strings.Contains(low, "i/o timeout") ||
		strings.Contains(low, "connection reset") ||
		strings.Contains(low, "connection refused") ||
		strings.Contains(low, "no such host") ||
		strings.Contains(low, "temporary failure") ||
		strings.Contains(low, "network is unreachable") {
		return netTimeoutMsg
	}
	// Never dump full URLs into UI.
	if strings.Contains(s, "http://") || strings.Contains(s, "https://") {
		return netTimeoutMsg
	}
	if len(s) > 80 {
		return netTimeoutMsg
	}
	return s
}

func fetchChannel() (*channelInfo, error) {
	var last error
	for _, u := range channelURLs {
		cli := httpClient()
		cli.Timeout = 28 * time.Second
		req, err := http.NewRequest(http.MethodGet, u, nil)
		if err != nil {
			last = err
			continue
		}
		req.Header.Set("User-Agent", "TapSprite-PC/"+version)
		pl := proxyLabel(req)
		writeDesktopLog(fmt.Sprintf("check channel url=%s %s", u, pl))
		setUpdate("checking", 0, 0, 0, fmt.Sprintf("检新 %s %s", shortURLDisp(u), pl))
		resp, err := cli.Do(req)
		if err != nil {
			last = err
			writeDesktopLog(fmt.Sprintf("check channel fail %s %s: %v", shortURLDisp(u), pl, err))
			continue
		}
		if resp.StatusCode >= 400 {
			b, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
			resp.Body.Close()
			last = fmt.Errorf("HTTP %d", resp.StatusCode)
			_ = b
			continue
		}
		var c channelInfo
		err = json.NewDecoder(resp.Body).Decode(&c)
		resp.Body.Close()
		if err != nil {
			last = err
			continue
		}
		return &c, nil
	}
	if last != nil {
		return nil, fmt.Errorf("%s", shortNetErr(last))
	}
	return nil, fmt.Errorf("%s", netTimeoutMsg)
}

func handleChannel(w http.ResponseWriter, r *http.Request) {
	ch, err := fetchChannel()
	if err != nil {
		writeJSON(w, map[string]any{"ok": false, "err": shortNetErr(err)})
		return
	}
	// Return channel JSON as-is (apk/exe/version fields) plus ok.
	writeJSON(w, map[string]any{
		"ok":          true,
		"versionCode": ch.VersionCode,
		"versionName": ch.VersionName,
		"apk_ver":     ch.ApkVer,
		"exe_ver":     ch.ExeVer,
		"apk":         ch.Apk,
		"exe":         ch.Exe,
		"notes":       ch.Notes,
	})
}

// compareVer returns 1 if a>b, -1 if a<b, 0 if equal (dot-separated numeric).
func compareVer(a, b string) int {
	a = strings.TrimSpace(strings.TrimPrefix(strings.ToLower(a), "v"))
	b = strings.TrimSpace(strings.TrimPrefix(strings.ToLower(b), "v"))
	// strip suffixes like -rebuild
	if i := strings.IndexAny(a, "-+_"); i >= 0 {
		a = a[:i]
	}
	if i := strings.IndexAny(b, "-+_"); i >= 0 {
		b = b[:i]
	}
	as := strings.Split(a, ".")
	bs := strings.Split(b, ".")
	n := len(as)
	if len(bs) > n {
		n = len(bs)
	}
	for i := 0; i < n; i++ {
		var ai, bi int
		if i < len(as) {
			fmt.Sscanf(as[i], "%d", &ai)
		}
		if i < len(bs) {
			fmt.Sscanf(bs[i], "%d", &bi)
		}
		if ai > bi {
			return 1
		}
		if ai < bi {
			return -1
		}
	}
	return 0
}

func verToFileStem(ver, kind string) string {
	v := strings.ReplaceAll(ver, ".", "-")
	if kind == "exe" {
		return "tapsprite" + v + ".exe"
	}
	return "tapsprite" + v + ".apk"
}

func handleUpdateStatus(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, getUpdate())
}

func handleSelfUpdate(w http.ResponseWriter, r *http.Request) {
	updMu.Lock()
	if updBusy {
		updMu.Unlock()
		writeJSON(w, map[string]any{"ok": true, "msg": "已在更新中", "busy": true})
		return
	}
	updBusy = true
	updMu.Unlock()

	setUpdate("checking", 0, 0, 0, "正在检测版本")
	writeJSON(w, map[string]any{"ok": true, "msg": "正在检测版本"})

	go func() {
		defer func() {
			updMu.Lock()
			updBusy = false
			updMu.Unlock()
		}()
		ch, err := fetchChannel()
		if err != nil {
			msg := "检测失败：" + shortNetErr(err)
			setUpdate("error", 0, 0, 0, msg)
			writeDesktopLog(msg)
			return
		}
		remote := ch.ExeVer
		if remote == "" {
			remote = ch.VersionName
		}
		if compareVer(remote, version) <= 0 {
			setUpdate("idle", 100, 0, 0, "已是最新 "+version)
			return
		}
		url := ch.Exe
		if url == "" {
			msg := "清单缺少 exe 下载地址"
			setUpdate("error", 0, 0, 0, msg)
			writeDesktopLog(msg)
			return
		}
		name := verToFileStem(remote, "exe")
		dest := filepath.Join(downloadsDir(), name)
		setUpdate("downloading", 0, 0, 0, "发现 "+remote+"，开始下载")
		if err := downloadFile(url, dest, func(got, total int64) {
			pct := 0
			if total > 0 {
				pct = int(got * 100 / total)
			}
			setUpdate("downloading", pct, got, total, fmt.Sprintf("下载中 %d%%", pct))
		}); err != nil {
			msg := "下载失败：" + shortNetErr(err)
			setUpdate("error", 0, 0, 0, msg)
			writeDesktopLog(msg)
			return
		}
		setUpdate("launching", 100, 0, 0, "下载完成，正在启动新版本…")
		if err := launchDetached(dest); err != nil {
			msg := "启动新版本失败：" + err.Error()
			setUpdate("error", 100, 0, 0, msg)
			writeDesktopLog(msg)
			return
		}
		time.Sleep(400 * time.Millisecond)
		quitWebView()
		os.Exit(0)
	}()
}

func downloadFile(url, dest string, progress func(got, total int64)) error {
	cands := mirrorCandidates(url)
	if len(cands) == 0 {
		cands = []string{url}
	}
	var last error
	for _, cand := range cands {
		err := downloadFileOnce(cand, dest, progress)
		if err == nil {
			return nil
		}
		last = err
		_ = os.Remove(dest + ".part")
	}
	if last != nil {
		return fmt.Errorf("%s", shortNetErr(last))
	}
	return fmt.Errorf("%s", netTimeoutMsg)
}

func downloadFileOnce(url, dest string, progress func(got, total int64)) error {
	cli := httpClient()
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "TapSprite-PC/"+version)
	pl := proxyLabel(req)
	writeDesktopLog(fmt.Sprintf("download url=%s %s", url, pl))
	setUpdate("downloading", getUpdate().Percent, getUpdate().Got, getUpdate().Total,
		fmt.Sprintf("下载 %s %s", shortURLDisp(url), pl))
	resp, err := cli.Do(req)
	if err != nil {
		writeDesktopLog(fmt.Sprintf("download fail %s %s: %v", shortURLDisp(url), pl, err))
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		_ = b
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	total := resp.ContentLength
	part := dest + ".part"
	f, err := os.Create(part)
	if err != nil {
		return err
	}
	buf := make([]byte, 64*1024)
	var got int64
	lastReport := time.Time{}
	for {
		n, rerr := resp.Body.Read(buf)
		if n > 0 {
			if _, werr := f.Write(buf[:n]); werr != nil {
				_ = f.Close()
				_ = os.Remove(part)
				return werr
			}
			got += int64(n)
			if progress != nil && (time.Since(lastReport) > 150*time.Millisecond || got == total) {
				progress(got, total)
				lastReport = time.Now()
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			_ = f.Close()
			_ = os.Remove(part)
			return rerr
		}
	}
	_ = f.Close()
	if got < 10000 {
		_ = os.Remove(part)
		return fmt.Errorf("文件过小（%d 字节）", got)
	}
	_ = os.Remove(dest)
	if err := os.Rename(part, dest); err != nil {
		return err
	}
	if progress != nil {
		progress(got, got)
	}
	return nil
}

func handleFetchApk(w http.ResponseWriter, r *http.Request) {
	var body struct {
		URL  string `json:"url"`
		Name string `json:"name"`
	}
	_ = readJSON(r, &body)
	if body.URL == "" {
		writeJSON(w, map[string]any{"ok": false, "err": "缺少 url"})
		return
	}
	apkMu.Lock()
	if apkState.Busy {
		apkMu.Unlock()
		writeJSON(w, map[string]any{"ok": true, "busy": true})
		return
	}
	apkState = apkJob{Busy: true, Ready: false, Name: body.Name, Msg: "开始下载"}
	apkMu.Unlock()

	writeJSON(w, map[string]any{"ok": true})

	go func() {
		defer func() {
			apkMu.Lock()
			apkState.Busy = false
			apkMu.Unlock()
		}()
		name := body.Name
		if name == "" {
			name = "update"
		}
		// prefer tapspriteX-Y-Z.apk naming when name looks like a version
		fname := name
		if !strings.HasSuffix(strings.ToLower(fname), ".apk") {
			if strings.Contains(name, ".") && !strings.Contains(name, "/") {
				fname = verToFileStem(name, "apk")
			} else {
				fname = "tapsprite-update.apk"
			}
		}
		if !strings.HasPrefix(strings.ToLower(fname), "tapsprite") {
			fname = "tapsprite-" + fname
		}
		dest := filepath.Join(downloadsDir(), fname)
		setApkProgress(0, 0, false, "", "电脑下载中")
		err := downloadFile(body.URL, dest, func(got, total int64) {
			setApkProgress(got, total, false, "", "电脑下载中")
		})
		if err != nil {
			setApkProgress(0, 0, false, shortNetErr(err), "下载失败")
			writeDesktopLog("fetchapk: " + shortNetErr(err))
			return
		}
		var got, total int64
		apkMu.Lock()
		apkState.Path = dest
		apkState.Ready = true
		if apkState.Total == 0 {
			if fi, e := os.Stat(dest); e == nil {
				apkState.Got = fi.Size()
				apkState.Total = fi.Size()
			}
		} else {
			apkState.Got = apkState.Total
		}
		apkState.Msg = "下载完成"
		got, total = apkState.Got, apkState.Total
		apkMu.Unlock()
		setUpdate("idle", 100, got, total, "APK 下载完成")
		go cleanupOldDownloads("apk", dest)
	}()
}

func setApkProgress(got, total int64, ready bool, errMsg, msg string) {
	apkMu.Lock()
	apkState.Got = got
	apkState.Total = total
	apkState.Ready = ready
	apkState.Err = errMsg
	apkState.Msg = msg
	if errMsg != "" {
		apkState.Busy = false
	}
	apkMu.Unlock()
	pct := 0
	if total > 0 {
		pct = int(got * 100 / total)
	}
	phase := "downloading"
	if errMsg != "" {
		phase = "error"
		msg = "APK " + errMsg
	} else if ready {
		phase = "idle"
		msg = "APK 下载完成"
		pct = 100
	} else if msg == "" {
		msg = "电脑下载 APK…"
	}
	setUpdate(phase, pct, got, total, msg)
}

func handleApkStatus(w http.ResponseWriter, r *http.Request) {
	apkMu.Lock()
	defer apkMu.Unlock()
	pct := 0
	if apkState.Total > 0 {
		pct = int(apkState.Got * 100 / apkState.Total)
	}
	writeJSON(w, map[string]any{
		"busy":    apkState.Busy,
		"ready":   apkState.Ready,
		"got":     apkState.Got,
		"total":   apkState.Total,
		"err":     apkState.Err,
		"percent": pct,
		"msg":     apkState.Msg,
		"name":    apkState.Name,
	})
}

func handleApkFile(w http.ResponseWriter, r *http.Request) {
	apkMu.Lock()
	path := apkState.Path
	ready := apkState.Ready
	apkMu.Unlock()
	if !ready || path == "" {
		http.Error(w, "not ready", http.StatusNotFound)
		return
	}
	f, err := os.Open(path)
	if err != nil {
		http.Error(w, "missing", http.StatusNotFound)
		return
	}
	defer f.Close()
	fi, _ := f.Stat()
	w.Header().Set("Content-Type", "application/vnd.android.package-archive")
	w.Header().Set("Content-Disposition", "attachment; filename=\""+filepath.Base(path)+"\"")
	if fi != nil {
		w.Header().Set("Content-Length", fmt.Sprintf("%d", fi.Size()))
	}
	_, _ = io.Copy(w, f)
}

// cleanupOldDownloads keeps only the newest tapsprite*.{exe|apk} (or keepPath), deletes older matches.
func cleanupOldDownloads(kind, keepPath string) {
	dir := downloadsDir()
	pat := "tapsprite*.exe"
	if kind == "apk" {
		pat = "tapsprite*.apk"
	}
	matches, err := filepath.Glob(filepath.Join(dir, pat))
	if err != nil {
		return
	}
	selfPath := ""
	if exe, e := os.Executable(); e == nil {
		selfPath, _ = filepath.Abs(exe)
	}
	keepAbs, _ := filepath.Abs(keepPath)
	type item struct {
		path string
		mod  time.Time
	}
	var list []item
	for _, m := range matches {
		abs, _ := filepath.Abs(m)
		// never delete currently running exe
		if selfPath != "" && strings.EqualFold(abs, selfPath) {
			continue
		}
		fi, err := os.Stat(m)
		if err != nil || fi.IsDir() {
			continue
		}
		list = append(list, item{path: m, mod: fi.ModTime()})
	}
	if len(list) == 0 {
		return
	}
	// pick newest by ModTime; prefer keepPath if set
	best := list[0]
	for _, it := range list[1:] {
		if it.mod.After(best.mod) {
			best = it
		}
	}
	if keepAbs != "" {
		for _, it := range list {
			abs, _ := filepath.Abs(it.path)
			if strings.EqualFold(abs, keepAbs) {
				best = it
				break
			}
		}
	}
	bestAbs, _ := filepath.Abs(best.path)
	for _, it := range list {
		abs, _ := filepath.Abs(it.path)
		if strings.EqualFold(abs, bestAbs) {
			continue
		}
		_ = os.Remove(it.path)
	}
}

func scheduleStartupCleanup() {
	go func() {
		time.Sleep(4 * time.Second)
		self := ""
		if exe, err := os.Executable(); err == nil {
			self, _ = filepath.Abs(exe)
		}
		cleanupOldDownloads("exe", self)
	}()
}

func writeDesktopLog(msg string) {
	writeStartupLog(msg)
}

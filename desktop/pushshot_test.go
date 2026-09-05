package main

import (
	"bytes"
	"compress/zlib"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"image"
	"net/http"
	"net/http/httptest"
	"testing"
)

func rgbRawz(w, h int, fill func(x, y int) (r, g, b byte)) []byte {
	raw := make([]byte, w*h*3)
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			r, g, b := fill(x, y)
			i := (y*w + x) * 3
			raw[i], raw[i+1], raw[i+2] = r, g, b
		}
	}
	var buf bytes.Buffer
	zw, err := zlib.NewWriterLevel(&buf, 1)
	if err != nil {
		panic(err)
	}
	if _, err := zw.Write(raw); err != nil {
		panic(err)
	}
	if err := zw.Close(); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

func encodeTSB1(payload []byte, w, h int, mime string) []byte {
	out := make([]byte, 16+len(payload))
	copy(out[0:4], []byte(tsb1Magic))
	binary.BigEndian.PutUint32(out[4:8], uint32(w))
	binary.BigEndian.PutUint32(out[8:12], uint32(h))
	m := []byte("png\x00")
	if mime == "rawz" {
		m = []byte("rawz")
	}
	copy(out[12:16], m)
	copy(out[16:], payload)
	return out
}

func pixelFill(x, y int) (r, g, b byte) {
	return byte(x * 17), byte(y * 31), byte((x + y) * 9)
}

func postPushShot(t *testing.T, body []byte, w, h int, mime string, extra map[string]string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost, "/api/pushshot", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/octet-stream")
	if w > 0 {
		req.Header.Set("X-Ts-W", fmt.Sprintf("%d", w))
	}
	if h > 0 {
		req.Header.Set("X-Ts-H", fmt.Sprintf("%d", h))
	}
	if mime != "" {
		req.Header.Set("X-Ts-Mime", mime)
	}
	for k, v := range extra {
		req.Header.Set(k, v)
	}
	rr := httptest.NewRecorder()
	handlePushShot(rr, req)
	return rr
}

func mustPixel(t *testing.T, x, y int, wantR, wantG, wantB byte) {
	t.Helper()
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/pixel?x=%d&y=%d", x, y), nil)
	handlePixel(rr, req)
	var out map[string]any
	if err := json.Unmarshal(rr.Body.Bytes(), &out); err != nil {
		t.Fatal(err)
	}
	if out["ok"] != true {
		t.Fatalf("pixel %d,%d not ok: %v", x, y, out)
	}
	gotR := byte(out["r"].(float64))
	gotG := byte(out["g"].(float64))
	gotB := byte(out["b"].(float64))
	if gotR != wantR || gotG != wantG || gotB != wantB {
		t.Fatalf("pixel %d,%d want %02X%02X%02X got %02X%02X%02X", x, y, wantR, wantG, wantB, gotR, gotG, gotB)
	}
}

func TestPushShotTSB1RawzLossless(t *testing.T) {
	resetSrv()
	const w, h = 8, 6
	payload := rgbRawz(w, h, pixelFill)
	framed := encodeTSB1(payload, w, h, "rawz")
	rr := postPushShot(t, framed, w, h, "rawz", map[string]string{"X-Ts-Bin": "1"})
	if rr.Code != 200 {
		t.Fatalf("status %d body %s", rr.Code, rr.Body.Bytes())
	}
	var out map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &out)
	if out["via"] != "tsb1" || out["mime"] != "rawz" {
		t.Fatalf("want tsb1/rawz got %v", out)
	}
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			r, g, b := pixelFill(x, y)
			mustPixel(t, x, y, r, g, b)
		}
	}
}

func TestPushShotUnframedRawzCompat(t *testing.T) {
	resetSrv()
	const w, h = 4, 3
	payload := rgbRawz(w, h, pixelFill)
	rr := postPushShot(t, payload, w, h, "rawz", nil)
	if rr.Code != 200 {
		t.Fatalf("status %d body %s", rr.Code, rr.Body.Bytes())
	}
	var out map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &out)
	if out["via"] != "raw" {
		t.Fatalf("want via=raw got %v", out)
	}
	r, g, b := pixelFill(2, 1)
	mustPixel(t, 2, 1, r, g, b)
}

func TestPushShotBase64Compat(t *testing.T) {
	resetSrv()
	const w, h = 4, 3
	payload := rgbRawz(w, h, pixelFill)
	b64 := []byte(base64.StdEncoding.EncodeToString(payload))
	rr := postPushShot(t, b64, w, h, "rawz", nil)
	if rr.Code != 200 {
		t.Fatalf("status %d body %s", rr.Code, rr.Body.Bytes())
	}
	var out map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &out)
	if out["via"] != "b64" {
		t.Fatalf("want via=b64 got %v", out)
	}
	r, g, b := pixelFill(1, 2)
	mustPixel(t, 1, 2, r, g, b)
}

func TestShotHandlerAcceptsBinary(t *testing.T) {
	resetSrv()
	const w, h = 5, 5
	payload := rgbRawz(w, h, pixelFill)
	framed := encodeTSB1(payload, w, h, "rawz")
	req := httptest.NewRequest(http.MethodPost, "/api/shot", bytes.NewReader(framed))
	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("X-Ts-Bin", "1")
	req.Header.Set("X-Ts-Mime", "rawz")
	req.Header.Set("X-Ts-W", "5")
	req.Header.Set("X-Ts-H", "5")
	rr := httptest.NewRecorder()
	handleShot(rr, req)
	if rr.Code != 200 {
		t.Fatalf("status %d body %s", rr.Code, rr.Body.Bytes())
	}
	var out map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &out)
	if out["via"] != "tsb1" {
		t.Fatalf("shot binary want tsb1 got %v", out)
	}
	r, g, b := pixelFill(4, 4)
	mustPixel(t, 4, 4, r, g, b)
}

func TestParseTSB1(t *testing.T) {
	p := []byte{0x78, 0x01, 1, 2, 3}
	framed := encodeTSB1(p, 1080, 1920, "rawz")
	got, w, h, mime, ok := parseTSB1(framed)
	if !ok || w != 1080 || h != 1920 || mime != "rawz" || !bytes.Equal(got, p) {
		t.Fatalf("parse tsb1 got ok=%v %dx%d %s %v", ok, w, h, mime, got)
	}
	if _, _, _, _, ok := parseTSB1(p); ok {
		t.Fatal("raw zlib must not parse as tsb1")
	}
}

func TestDecodeIncomingShotJSON(t *testing.T) {
	payload := rgbRawz(2, 2, pixelFill)
	body, _ := json.Marshal(map[string]any{
		"w": 2, "h": 2, "mime": "rawz",
		"b64": base64.StdEncoding.EncodeToString(payload),
	})
	got, w, h, mime, via, err := decodeIncomingShot(body, 0, 0, "")
	if err != nil || via != "json-b64" || w != 2 || h != 2 || mime != "rawz" || !bytes.Equal(got, payload) {
		t.Fatalf("json unwrap via=%s %dx%d %s err=%v", via, w, h, mime, err)
	}
}

func TestCachedShotImageBounds(t *testing.T) {
	resetSrv()
	const w, h = 3, 2
	payload := rgbRawz(w, h, pixelFill)
	framed := encodeTSB1(payload, w, h, "rawz")
	if rr := postPushShot(t, framed, w, h, "rawz", map[string]string{"X-Ts-Bin": "1"}); rr.Code != 200 {
		t.Fatal(rr.Body.String())
	}
	srv.mu.Lock()
	img := cachedShotLocked()
	srv.mu.Unlock()
	if img == nil {
		t.Fatal("nil shot")
	}
	b := img.Bounds()
	if b != image.Rect(0, 0, w, h) {
		t.Fatalf("bounds %v", b)
	}
}

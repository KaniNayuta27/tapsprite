package main

import (
	"bytes"
	"encoding/json"
	"image"
	"image/color"
	"image/png"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func setTestShot(t *testing.T, w, h int) {
	t.Helper()
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			img.SetRGBA(x, y, color.RGBA{R: uint8(x), G: uint8(y), B: 40, A: 255})
		}
	}
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		t.Fatal(err)
	}
	srv.mu.Lock()
	srv.shotPNG = buf.Bytes()
	srv.shotImg = nil
	srv.shotW, srv.shotH = w, h
	srv.undoStack = nil
	srv.shotRev = 1
	srv.mu.Unlock()
}

func TestCropAndRotate(t *testing.T) {
	setTestShot(t, 20, 10)

	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/crop", strings.NewReader(`{"x1":2,"y1":1,"x2":8,"y2":5}`))
	handleCrop(rr, req)
	var crop map[string]any
	if err := json.Unmarshal(rr.Body.Bytes(), &crop); err != nil {
		t.Fatal(err)
	}
	if crop["ok"] != true {
		t.Fatalf("crop: %v", crop)
	}
	if int(crop["w"].(float64)) != 6 || int(crop["h"].(float64)) != 4 {
		t.Fatalf("crop size got %v", crop)
	}
	srv.mu.Lock()
	if srv.shotW != 6 || srv.shotH != 4 || len(srv.undoStack) != 1 {
		t.Fatalf("after crop w/h/undo=%d/%d/%d", srv.shotW, srv.shotH, len(srv.undoStack))
	}
	srv.mu.Unlock()

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/rotate", strings.NewReader(`{"dir":"cw"}`))
	handleRotate(rr, req)
	var rot map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &rot)
	if rot["ok"] != true {
		t.Fatalf("rotate: %v", rot)
	}
	if int(rot["w"].(float64)) != 4 || int(rot["h"].(float64)) != 6 {
		t.Fatalf("rotate size got %v", rot)
	}

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/rotate", strings.NewReader(`{"dir":"cw"}`))
	// no shot
	srv.mu.Lock()
	srv.shotPNG = nil
	srv.shotImg = nil
	srv.mu.Unlock()
	handleRotate(rr, req)
	var fail map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &fail)
	if fail["ok"] != false {
		t.Fatalf("want fail without shot, got %v", fail)
	}
}

func TestSlotExportTen(t *testing.T) {
	srv.mu.Lock()
	srv.slots = [10]Slot{}
	srv.slots[0] = Slot{X: 1, Y: 2, Hex: "AABBCC"}
	out := exportSlotsLocked()
	srv.mu.Unlock()
	if len(out) != 10 {
		t.Fatalf("want 10 slots, got %d", len(out))
	}
	if out[0]["on"] != true || out[0]["hex"] != "AABBCC" {
		t.Fatalf("slot0: %v", out[0])
	}
	if out[1]["on"] != false {
		t.Fatalf("slot1 should be empty: %v", out[1])
	}
}

func TestPixelUsesDecodedCache(t *testing.T) {
	setTestShot(t, 8, 6)
	rr := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/pixel?x=2&y=3", nil)
	handlePixel(rr, req)
	var first map[string]any
	if err := json.Unmarshal(rr.Body.Bytes(), &first); err != nil {
		t.Fatal(err)
	}
	if first["ok"] != true {
		t.Fatalf("pixel: %v", first)
	}
	if int(first["r"].(float64)) != 2 || int(first["g"].(float64)) != 3 || int(first["b"].(float64)) != 40 {
		t.Fatalf("color got %v", first)
	}
	if first["hex"] != "020328" {
		t.Fatalf("hex got %v", first["hex"])
	}
	srv.mu.Lock()
	if srv.shotImg == nil {
		srv.mu.Unlock()
		t.Fatal("expected decoded shot cache after first pixel")
	}
	srv.mu.Unlock()

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pixel?x=2&y=3", nil)
	handlePixel(rr, req)
	var second map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &second)
	if second["ok"] != true || second["hex"] != "020328" {
		t.Fatalf("cached pixel: %v", second)
	}

	rr = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/pixel?x=99&y=0", nil)
	handlePixel(rr, req)
	var miss map[string]any
	_ = json.Unmarshal(rr.Body.Bytes(), &miss)
	if miss["ok"] != false {
		t.Fatalf("want miss, got %v", miss)
	}
}

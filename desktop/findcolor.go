package main

import (
	"image"
	"image/color"
	"math"
	"strconv"
	"strings"
)

// colorSpec mirrors Android ColorUtil.Spec (hex string is BGR like 按键精灵).
type colorSpec struct {
	r, g, b    int
	tr, tg, tb int
}

type colorOffset struct {
	dx, dy int
	spec   colorSpec
}

func deltaFromSim(sim float64) int {
	if sim <= 0 {
		return 0
	}
	if sim > 1 {
		return int(math.Round(sim))
	}
	if sim >= 0.999 {
		return 0
	}
	return int(math.Round((1.0 - sim) * 255.0))
}

func parseHexColor(s string) int {
	if s == "" {
		return 0
	}
	s = strings.TrimSpace(s)
	if strings.HasPrefix(s, "#") {
		s = s[1:]
	}
	if len(s) > 6 {
		s = s[len(s)-6:]
	}
	n, err := strconv.ParseInt(s, 16, 64)
	if err != nil {
		return 0
	}
	return int(n)
}

// parseColorSpec: "BBGGRR" or "BBGGRR-DRDGDB" (按键偏色).
func parseColorSpec(str string) colorSpec {
	if str == "" {
		return colorSpec{}
	}
	trim := strings.TrimSpace(str)
	if strings.HasPrefix(trim, "#") {
		trim = trim[1:]
	} else if strings.HasPrefix(strings.ToLower(trim), "0x") {
		trim = trim[2:]
	}
	var tol string
	if i := strings.IndexByte(trim, '-'); i > 0 {
		tol = trim[i+1:]
		trim = trim[:i]
	}
	n := parseHexColor(trim)
	t := parseHexColor(tol)
	// ColorUtil: r=low, g=mid, b=high of BGR hex number
	return colorSpec{
		r: n & 255, g: (n >> 8) & 255, b: (n >> 16) & 255,
		tr: t & 255, tg: (t >> 8) & 255, tb: (t >> 16) & 255,
	}
}

func matchColor(c color.Color, spec colorSpec, delta int) bool {
	rr, gg, bb, _ := c.RGBA()
	r8, g8, b8 := int(rr>>8), int(gg>>8), int(bb>>8)
	return absInt(r8-spec.r) <= spec.tr+delta &&
		absInt(g8-spec.g) <= spec.tg+delta &&
		absInt(b8-spec.b) <= spec.tb+delta
}

func absInt(v int) int {
	if v < 0 {
		return -v
	}
	return v
}

func parseOffsets(str string) []colorOffset {
	str = strings.TrimSpace(str)
	if str == "" {
		return nil
	}
	parts := strings.Split(str, ",")
	out := make([]colorOffset, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		bits := strings.Split(p, "|")
		if len(bits) < 3 {
			continue
		}
		dx, _ := strconv.Atoi(strings.TrimSpace(bits[0]))
		dy, _ := strconv.Atoi(strings.TrimSpace(bits[1]))
		out = append(out, colorOffset{dx: dx, dy: dy, spec: parseColorSpec(bits[2])})
	}
	return out
}

func parseColorList(str string) []colorSpec {
	str = strings.TrimSpace(str)
	if str == "" {
		return []colorSpec{parseColorSpec("000000")}
	}
	parts := strings.Split(str, "|")
	out := make([]colorSpec, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		out = append(out, parseColorSpec(p))
	}
	if len(out) == 0 {
		return []colorSpec{parseColorSpec("000000")}
	}
	return out
}

func pixAt(img image.Image, w, h, x, y int) color.Color {
	if x < 0 || y < 0 || x >= w || y >= h {
		return color.RGBA{}
	}
	return img.At(x, y)
}

func hitMulti(img image.Image, w, h, x, y int, first colorSpec, offs []colorOffset, delta int) bool {
	if !matchColor(pixAt(img, w, h, x, y), first, delta) {
		return false
	}
	for _, o := range offs {
		ox, oy := x+o.dx, y+o.dy
		if ox < 0 || oy < 0 || ox >= w || oy >= h {
			return false
		}
		if !matchColor(img.At(ox, oy), o.spec, delta) {
			return false
		}
	}
	return true
}

func hitAny(img image.Image, w, h, x, y int, specs []colorSpec, delta int) bool {
	c := pixAt(img, w, h, x, y)
	for _, s := range specs {
		if matchColor(c, s, delta) {
			return true
		}
	}
	return false
}

// findColorInImage: dir 0 TL→BR, 1 TR→BL, 2 BL→TR, 3 BR→TL (same as ScreenApi.scan).
func findColorInImage(img image.Image, l, t, r, b int, colorStr string, sim float64, dir int) (int, int) {
	bd := img.Bounds()
	w, h := bd.Dx(), bd.Dy()
	left := maxInt(0, minInt(l, r))
	top := maxInt(0, minInt(t, b))
	right := minInt(w-1, maxInt(l, r))
	bottom := minInt(h-1, maxInt(t, b))
	if right < left || bottom < top {
		return -1, -1
	}
	specs := parseColorList(colorStr)
	delta := deltaFromSim(sim)
	revX := dir == 1 || dir == 3
	revY := dir == 2 || dir == 3
	if revY {
		for y := bottom; y >= top; y-- {
			if revX {
				for x := right; x >= left; x-- {
					if hitAny(img, w, h, x, y, specs, delta) {
						return x, y
					}
				}
			} else {
				for x := left; x <= right; x++ {
					if hitAny(img, w, h, x, y, specs, delta) {
						return x, y
					}
				}
			}
		}
	} else {
		for y := top; y <= bottom; y++ {
			if revX {
				for x := right; x >= left; x-- {
					if hitAny(img, w, h, x, y, specs, delta) {
						return x, y
					}
				}
			} else {
				for x := left; x <= right; x++ {
					if hitAny(img, w, h, x, y, specs, delta) {
						return x, y
					}
				}
			}
		}
	}
	return -1, -1
}

func findMultiColorInImage(img image.Image, l, t, r, b int, first, offset string, sim float64, dir int) (int, int) {
	bd := img.Bounds()
	w, h := bd.Dx(), bd.Dy()
	left := maxInt(0, minInt(l, r))
	top := maxInt(0, minInt(t, b))
	right := minInt(w-1, maxInt(l, r))
	bottom := minInt(h-1, maxInt(t, b))
	if right < left || bottom < top {
		return -1, -1
	}
	spec := parseColorSpec(first)
	offs := parseOffsets(offset)
	delta := deltaFromSim(sim)
	revX := dir == 1 || dir == 3
	revY := dir == 2 || dir == 3
	if revY {
		for y := bottom; y >= top; y-- {
			if revX {
				for x := right; x >= left; x-- {
					if hitMulti(img, w, h, x, y, spec, offs, delta) {
						return x, y
					}
				}
			} else {
				for x := left; x <= right; x++ {
					if hitMulti(img, w, h, x, y, spec, offs, delta) {
						return x, y
					}
				}
			}
		}
	} else {
		for y := top; y <= bottom; y++ {
			if revX {
				for x := right; x >= left; x-- {
					if hitMulti(img, w, h, x, y, spec, offs, delta) {
						return x, y
					}
				}
			} else {
				for x := left; x <= right; x++ {
					if hitMulti(img, w, h, x, y, spec, offs, delta) {
						return x, y
					}
				}
			}
		}
	}
	return -1, -1
}

func cmpColorExInImage(img image.Image, desc string, sim float64) bool {
	desc = strings.TrimSpace(desc)
	if desc == "" {
		return false
	}
	bd := img.Bounds()
	w, h := bd.Dx(), bd.Dy()
	delta := deltaFromSim(sim)
	for _, part := range strings.Split(desc, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		bits := strings.Split(part, "|")
		if len(bits) < 3 {
			return false
		}
		x, err1 := strconv.Atoi(strings.TrimSpace(bits[0]))
		y, err2 := strconv.Atoi(strings.TrimSpace(bits[1]))
		if err1 != nil || err2 != nil || x < 0 || y < 0 || x >= w || y >= h {
			return false
		}
		if !matchColor(img.At(x, y), parseColorSpec(bits[2]), delta) {
			return false
		}
	}
	return true
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}
func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}

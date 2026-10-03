package main

import (
	"image"
	"os"
	"strings"

	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
)

// findPicInImage: sample MAD similarity like ScreenApi.matchTemplate (simplified, dir ignored for speed).
func findPicInImage(img image.Image, l, t, r, b int, path string, sim float64) (int, int, string) {
	path = strings.TrimSpace(path)
	if path == "" {
		return -1, -1, "请先填图片路径"
	}
	// strip Attachment: prefix
	if strings.HasPrefix(strings.ToLower(path), "attachment:") {
		path = path[len("Attachment:"):]
	}
	f, err := os.Open(path)
	if err != nil {
		return -1, -1, "读不到模板：" + path
	}
	defer f.Close()
	tpl, _, err := image.Decode(f)
	if err != nil {
		return -1, -1, "模板解码失败"
	}
	bd := img.Bounds()
	sw, sh := bd.Dx(), bd.Dy()
	tb := tpl.Bounds()
	tw, th := tb.Dx(), tb.Dy()
	if tw <= 0 || th <= 0 || tw > sw || th > sh {
		return -1, -1, "模板尺寸无效"
	}
	left, top, right, bottom := l, t, r, b
	if l == 0 && t == 0 && r == 0 && b == 0 {
		left, top, right, bottom = 0, 0, sw-1, sh-1
	} else {
		left = maxInt(0, minInt(l, r))
		top = maxInt(0, minInt(t, b))
		right = minInt(sw-1, maxInt(l, r))
		bottom = minInt(sh-1, maxInt(t, b))
	}
	maxX := minInt(right-tw+1, sw-tw)
	maxY := minInt(bottom-th+1, sh-th)
	if maxX < left || maxY < top {
		return -1, -1, ""
	}
	need := sim
	if need <= 0 {
		need = 0.75
	} else if need > 1 {
		need = maxFloat(0.5, 1-need/255)
	}
	step := maxInt(1, minInt(tw, th)/24)
	best := float64(-1)
	bestX, bestY := -1, -1
	for yy := top; yy <= maxY; yy += step {
		for xx := left; xx <= maxX; xx += step {
			sc := samplePicScore(img, tpl, xx, yy, tw, th, step)
			if sc > best {
				best, bestX, bestY = sc, xx, yy
			}
		}
	}
	if bestX >= 0 && step > 1 {
		x0 := maxInt(left, bestX-step)
		y0 := maxInt(top, bestY-step)
		x1 := minInt(maxX, bestX+step)
		y1 := minInt(maxY, bestY+step)
		for yy := y0; yy <= y1; yy++ {
			for xx := x0; xx <= x1; xx++ {
				sc := samplePicScore(img, tpl, xx, yy, tw, th, 1)
				if sc > best {
					best, bestX, bestY = sc, xx, yy
				}
			}
		}
	}
	if bestX >= 0 && best >= need {
		return bestX, bestY, ""
	}
	return -1, -1, ""
}

func samplePicScore(screen, tpl image.Image, ox, oy, tw, th, step int) float64 {
	var errSum int64
	n := 0
	s := maxInt(1, step)
	for ty := 0; ty < th; ty += s {
		for tx := 0; tx < tw; tx += s {
			tr, tg, tb, ta := tpl.At(tpl.Bounds().Min.X+tx, tpl.Bounds().Min.Y+ty).RGBA()
			if (ta >> 8) < 16 {
				continue
			}
			sr, sg, sb, _ := screen.At(ox+tx, oy+ty).RGBA()
			errSum += int64(absInt(int(sr>>8)-int(tr>>8)) + absInt(int(sg>>8)-int(tg>>8)) + absInt(int(sb>>8)-int(tb>>8)))
			n++
		}
	}
	if n == 0 {
		return 0
	}
	avg := float64(errSum) / float64(n*3*255)
	if avg > 1 {
		avg = 1
	}
	return 1 - avg
}

func maxFloat(a, b float64) float64 {
	if a > b {
		return a
	}
	return b
}

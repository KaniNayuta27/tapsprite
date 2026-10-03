package main

import (
	"image"
	"image/color"
	"testing"
)

func TestFindColorBGR(t *testing.T) {
	img := image.NewRGBA(image.Rect(0, 0, 10, 10))
	// paint (5,5) red
	img.Set(5, 5, color.RGBA{R: 255, G: 0, B: 0, A: 255})
	// 按键 BGR for red is 0000FF
	x, y := findColorInImage(img, 0, 0, 9, 9, "0000FF", 0.9, 0)
	if x != 5 || y != 5 {
		t.Fatalf("want 5,5 got %d,%d", x, y)
	}
	x, y = findMultiColorInImage(img, 0, 0, 9, 9, "0000FF", "0|0|0000FF", 0.9, 0)
	if x != 5 || y != 5 {
		t.Fatalf("multi want 5,5 got %d,%d", x, y)
	}
	ok := cmpColorExInImage(img, "5|5|0000FF", 0.9)
	if !ok {
		t.Fatal("cmpex should match")
	}
}

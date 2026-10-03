"use strict";
const assert = require("assert");
const C = require("./web/live-coord.js");

function near(px, size) {
  const center = (size - 1) / 2;
  assert.ok(Math.abs(px - center) <= 1, px + " not within 1px of " + center);
}

// Horizontal letterbox (bars left/right): tall video in a wide panel.
// video 576x1280, canvas CSS 800x600 at offset (100, 40).
{
  const videoW = 576, videoH = 1280, cssW = 800, cssH = 600, left = 100, top = 40;
  const box = C.contentRect(cssW, cssH, videoW, videoH);
  assert.strictEqual(box.h, 600);
  assert.strictEqual(box.w, 270);
  assert.strictEqual(box.x, 265);
  assert.strictEqual(box.y, 0);

  const tl = C.clientToNormalized(left + box.x, top + box.y, left, top, cssW, cssH, videoW, videoH);
  assert.deepStrictEqual(tl, { nx: 0, ny: 0 });
  const br = C.clientToNormalized(left + box.x + box.w, top + box.y + box.h, left, top, cssW, cssH, videoW, videoH);
  assert.deepStrictEqual(br, { nx: 1, ny: 1 });
  const mid = C.clientToNormalized(left + box.x + box.w / 2, top + box.y + box.h / 2, left, top, cssW, cssH, videoW, videoH);
  assert.ok(Math.abs(mid.nx - 0.5) < 1e-9 && Math.abs(mid.ny - 0.5) < 1e-9);

  // Letterbox click (left bar) is ignored.
  assert.strictEqual(C.clientToNormalized(left + 10, top + 10, left, top, cssW, cssH, videoW, videoH), null);
  // Right bar.
  assert.strictEqual(C.clientToNormalized(left + cssW - 5, top + 300, left, top, cssW, cssH, videoW, videoH), null);
}

// Vertical letterbox (bars top/bottom): wide video in a tall panel.
{
  const videoW = 1280, videoH = 576, cssW = 400, cssH = 600;
  const box = C.contentRect(cssW, cssH, videoW, videoH);
  assert.strictEqual(box.w, 400);
  assert.strictEqual(box.h, 180);
  assert.strictEqual(box.y, 210);
  const tl = C.clientToNormalized(0, 210, 0, 0, cssW, cssH, videoW, videoH);
  assert.deepStrictEqual(tl, { nx: 0, ny: 0 });
  const br = C.clientToNormalized(400, 390, 0, 0, cssW, cssH, videoW, videoH);
  assert.deepStrictEqual(br, { nx: 1, ny: 1 });
  const mid = C.clientToNormalized(200, 300, 0, 0, cssW, cssH, videoW, videoH);
  assert.ok(Math.abs(mid.nx - 0.5) < 1e-9 && Math.abs(mid.ny - 0.5) < 1e-9);
  assert.strictEqual(C.clientToNormalized(200, 10, 0, 0, cssW, cssH, videoW, videoH), null);
  assert.strictEqual(C.clientToNormalized(200, 590, 0, 0, cssW, cssH, videoW, videoH), null);
}

// devicePixelRatio: bitmap pixels are CSS × dpr. dpr=2, same tall video.
{
  const dpr = 2, cssW = 800, cssH = 600, videoW = 576, videoH = 1280;
  const box = C.contentRect(cssW, cssH, videoW, videoH);
  const tl = C.bitmapToNormalized(box.x * dpr, box.y * dpr, cssW * dpr, cssH * dpr, videoW, videoH, dpr);
  assert.deepStrictEqual(tl, { nx: 0, ny: 0 });
  const mid = C.bitmapToNormalized((box.x + box.w / 2) * dpr, (box.y + box.h / 2) * dpr, cssW * dpr, cssH * dpr, videoW, videoH, dpr);
  assert.ok(Math.abs(mid.nx - 0.5) < 1e-9 && Math.abs(mid.ny - 0.5) < 1e-9);
  const bar = C.bitmapToNormalized(10 * dpr, 10 * dpr, cssW * dpr, cssH * dpr, videoW, videoH, dpr);
  assert.strictEqual(bar, null);
}

// Phone pixels: 1080x2400 logical, encoder scale does not change the mapping.
{
  const br = C.normalizedToPhysical(1, 1, 1080, 2400, 0, false);
  assert.deepStrictEqual(br, { x: 1079, y: 2399 });
  const tl = C.normalizedToPhysical(0, 0, 1080, 2400, 0, false);
  assert.deepStrictEqual(tl, { x: 0, y: 0 });
  const c = C.normalizedToPhysical(0.5, 0.5, 1080, 2400, 0, false);
  near(c.x, 1080);
  near(c.y, 2400);
  assert.strictEqual(c.x, 540);
  assert.strictEqual(c.y, 1200);
}

// Landscape logical, frame already rotated with the display.
{
  const br = C.normalizedToPhysical(1, 1, 2400, 1080, 90, false);
  assert.deepStrictEqual(br, { x: 2399, y: 1079 });
  const c = C.normalizedToPhysical(0.5, 0.5, 2400, 1080, 90, false);
  near(c.x, 2400);
  near(c.y, 1080);
}

// Natural buffer rotated into gesture space.
{
  const tl = C.normalizedToPhysical(0, 0, 2400, 1080, 90, true);
  assert.deepStrictEqual(tl, { x: 0, y: 1079 });
  const br = C.normalizedToPhysical(1, 1, 2400, 1080, 90, true);
  assert.deepStrictEqual(br, { x: 2399, y: 0 });
  const c = C.normalizedToPhysical(0.5, 0.5, 2400, 1080, 90, true);
  near(c.x, 2400);
  near(c.y, 1080);

  const a = C.normalizedToPhysical(0, 0, 2400, 1080, 270, true);
  assert.deepStrictEqual(a, { x: 2399, y: 0 });
  const b = C.normalizedToPhysical(0, 0, 1080, 2400, 180, true);
  assert.deepStrictEqual(b, { x: 1079, y: 2399 });
}

// Annex-B split + codec string used by WebCodecs.
{
  const sps = new Uint8Array([0x67, 0x42, 0xc0, 0x1e, 0x11]);
  const pps = new Uint8Array([0x68, 0xce, 0x06, 0xe2]);
  const idr = new Uint8Array([0x65, 0x88, 0x84]);
  const bytes = new Uint8Array([
    0, 0, 0, 1, ...sps,
    0, 0, 1, ...pps,
    0, 0, 0, 1, ...idr
  ]);
  const nals = C.splitAnnexB(bytes);
  assert.strictEqual(nals.length, 3);
  assert.strictEqual(C.nalType(nals[0]), 7);
  assert.strictEqual(C.nalType(nals[1]), 8);
  assert.strictEqual(C.nalType(nals[2]), 5);
  assert.strictEqual(nals[0].length, sps.length);
  assert.strictEqual(C.avcCodecString(nals[0]), "avc1.42C01E");
  const avcc = C.avcCDescription(nals[0], nals[1]);
  assert.strictEqual(avcc[0], 1);
  assert.strictEqual(avcc[1], 0x42);
  const sample = C.avccFromNals([nals[2]]);
  assert.strictEqual(sample[3], idr.length);
  assert.strictEqual(sample[4], 0x65);
  const u = new Uint8Array(8);
  u[6] = 0x03; u[7] = 0xe8; // 1000
  assert.strictEqual(C.readU64BE(u, 0), 1000);
}

console.log("live_coord_test: ok");

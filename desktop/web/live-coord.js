/* Pure 实时操控 mapping. Browser global LiveCoord, or module.exports under node. */
(function (root, factory) {
  var api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.LiveCoord = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  function clamp01(n) {
    if (typeof n !== "number" || isNaN(n) || n < 0) return 0;
    if (n > 1) return 1;
    return n;
  }

  /** Pixel on [0, size-1]. n=0 → 0, n=1 → size-1. Matches Java LiveCoords.axisToPx. */
  function axisToPx(n, size) {
    if (size <= 1) return 0;
    n = clamp01(n);
    var px = Math.round(n * (size - 1));
    if (px < 0) return 0;
    if (px > size - 1) return size - 1;
    return px;
  }

  function normRotation(rotationDeg) {
    var r = rotationDeg % 360;
    if (r < 0) r += 360;
    if (r !== 0 && r !== 90 && r !== 180 && r !== 270) {
      throw new Error("rotation " + rotationDeg);
    }
    return r;
  }

  function naturalNormToLogical(nx, ny, rotationDeg) {
    nx = clamp01(nx);
    ny = clamp01(ny);
    switch (normRotation(rotationDeg)) {
      case 90: return { nx: ny, ny: 1 - nx };
      case 180: return { nx: 1 - nx, ny: 1 - ny };
      case 270: return { nx: 1 - ny, ny: nx };
      default: return { nx: nx, ny: ny };
    }
  }

  /**
   * Normalized → physical gesture pixels. frameIsNatural false is the live path
   * (encoder frame already matches the current logical display).
   */
  function normalizedToPhysical(nx, ny, physW, physH, rotationDeg, frameIsNatural) {
    if (physW < 1 || physH < 1) throw new Error("display size");
    var rot = normRotation(rotationDeg);
    var p = (!frameIsNatural || rot === 0) ? { nx: nx, ny: ny } : naturalNormToLogical(nx, ny, rot);
    return { x: axisToPx(p.nx, physW), y: axisToPx(p.ny, physH) };
  }

  /** Letterboxed content rect inside a cssW×cssH box. Origin top-left of the box. */
  function contentRect(cssW, cssH, videoW, videoH) {
    if (!(cssW > 0) || !(cssH > 0) || !(videoW > 0) || !(videoH > 0)) {
      return { x: 0, y: 0, w: 0, h: 0 };
    }
    var va = videoW / videoH;
    var ca = cssW / cssH;
    var w, h, x, y;
    if (va > ca) {
      w = cssW;
      h = cssW / va;
      x = 0;
      y = (cssH - h) / 2;
    } else {
      h = cssH;
      w = cssH * va;
      y = 0;
      x = (cssW - w) / 2;
    }
    return { x: x, y: y, w: w, h: h };
  }

  /**
   * CSS-pixel pointer → normalized 0..1 on the video, or null if the point is in
   * the letterbox. clientX/clientY and the canvas rect are the same space
   * (event.clientX + getBoundingClientRect). devicePixelRatio is not applied
   * here; use bitmapToNormalized when the point is in canvas bitmap pixels.
   */
  function clientToNormalized(clientX, clientY, cssLeft, cssTop, cssW, cssH, videoW, videoH) {
    var box = contentRect(cssW, cssH, videoW, videoH);
    if (!(box.w > 0) || !(box.h > 0)) return null;
    var x0 = cssLeft + box.x;
    var y0 = cssTop + box.y;
    var x1 = x0 + box.w;
    var y1 = y0 + box.h;
    var eps = 0.51;
    if (clientX < x0 - eps || clientY < y0 - eps || clientX > x1 + eps || clientY > y1 + eps) {
      return null;
    }
    var nx = (clientX - x0) / box.w;
    var ny = (clientY - y0) / box.h;
    return { nx: clamp01(nx), ny: clamp01(ny) };
  }

  /**
   * Bitmap-pixel pointer (canvas.width/height, which are CSS × devicePixelRatio)
   * → normalized. dpr converts bitmap space back to CSS before letterbox math.
   */
  function bitmapToNormalized(bitmapX, bitmapY, bitmapW, bitmapH, videoW, videoH, dpr) {
    var r = dpr > 0 ? dpr : 1;
    return clientToNormalized(bitmapX / r, bitmapY / r, 0, 0, bitmapW / r, bitmapH / r, videoW, videoH);
  }

  function splitAnnexB(u8) {
    var starts = [];
    var i = 0;
    while (i + 3 < u8.length) {
      if (u8[i] === 0 && u8[i + 1] === 0 && u8[i + 2] === 1) {
        starts.push(i + 3);
        i += 3;
        continue;
      }
      if (i + 4 < u8.length && u8[i] === 0 && u8[i + 1] === 0 && u8[i + 2] === 0 && u8[i + 3] === 1) {
        starts.push(i + 4);
        i += 4;
        continue;
      }
      i++;
    }
    var nals = [];
    for (var s = 0; s < starts.length; s++) {
      var from = starts[s];
      var to = s + 1 < starts.length ? starts[s + 1] : u8.length;
      // Walk back over the next start code's leading zeros already excluded.
      // `to` points at the next NAL header, but starts[] is the header index,
      // so the previous NAL runs until the start code that precedes `to`.
      if (s + 1 < starts.length) {
        var sc = starts[s + 1];
        if (sc >= 4 && u8[sc - 4] === 0 && u8[sc - 3] === 0 && u8[sc - 2] === 0 && u8[sc - 1] === 1) to = sc - 4;
        else if (sc >= 3 && u8[sc - 3] === 0 && u8[sc - 2] === 0 && u8[sc - 1] === 1) to = sc - 3;
      }
      if (to > from) nals.push(u8.subarray(from, to));
    }
    return nals;
  }

  function nalType(nal) {
    if (!nal || !nal.length) return -1;
    return nal[0] & 0x1f;
  }

  function avcCodecString(sps) {
    function hex(b) {
      var h = (b & 255).toString(16).toUpperCase();
      return h.length < 2 ? "0" + h : h;
    }
    return "avc1." + hex(sps[1]) + hex(sps[2]) + hex(sps[3]);
  }

  function avcCDescription(sps, pps) {
    var buf = new Uint8Array(11 + sps.length + pps.length);
    var i = 0;
    buf[i++] = 1;
    buf[i++] = sps[1];
    buf[i++] = sps[2];
    buf[i++] = sps[3];
    buf[i++] = 0xff;
    buf[i++] = 0xe1;
    buf[i++] = (sps.length >> 8) & 255;
    buf[i++] = sps.length & 255;
    buf.set(sps, i); i += sps.length;
    buf[i++] = 1;
    buf[i++] = (pps.length >> 8) & 255;
    buf[i++] = pps.length & 255;
    buf.set(pps, i);
    return buf;
  }

  function avccFromNals(nals) {
    var n = 0;
    for (var i = 0; i < nals.length; i++) n += 4 + nals[i].length;
    var out = new Uint8Array(n);
    var o = 0;
    for (var j = 0; j < nals.length; j++) {
      var nal = nals[j];
      var len = nal.length;
      out[o++] = (len >>> 24) & 255;
      out[o++] = (len >>> 16) & 255;
      out[o++] = (len >>> 8) & 255;
      out[o++] = len & 255;
      out.set(nal, o);
      o += len;
    }
    return out;
  }

  function readU64BE(u8, offset) {
    var n = 0;
    for (var i = 0; i < 8; i++) n = n * 256 + u8[offset + i];
    return n;
  }

  return {
    axisToPx: axisToPx,
    contentRect: contentRect,
    clientToNormalized: clientToNormalized,
    bitmapToNormalized: bitmapToNormalized,
    normalizedToPhysical: normalizedToPhysical,
    naturalNormToLogical: naturalNormToLogical,
    splitAnnexB: splitAnnexB,
    nalType: nalType,
    avcCodecString: avcCodecString,
    avcCDescription: avcCDescription,
    avccFromNals: avccFromNals,
    readU64BE: readU64BE
  };
});

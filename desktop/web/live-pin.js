/* PIN pad layout for 实时操控. Coordinates of a press still go through LiveCoord.
   This file only stores the overlay rectangle (fractions of the picture) and the
   log line. It never records which key was pressed. */
(function (root, factory) {
  var api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.LivePin = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  // 3x3 occupies the top 3/4 of the frame, so y 0.58 + 0.36*0.75 = 0.85.
  // The 0 key is the bottom quarter, centered. x 0.15 + width 0.60 ends at 0.75.
  var DEFAULT = { x: 0.15, y: 0.58, w: 0.6, h: 0.36 };
  var KEY = "tapsprite.livePinpad";
  var LOCK_KEY = "tapsprite.livePinLock";
  var LOG = "密码键盘: 按下";
  var MIN_W = 0.18;
  var MIN_H = 0.24;

  function num(v, d) {
    return typeof v === "number" && isFinite(v) ? v : d;
  }

  function clamp(r) {
    var x = num(r && r.x, DEFAULT.x);
    var y = num(r && r.y, DEFAULT.y);
    var w = num(r && r.w, DEFAULT.w);
    var h = num(r && r.h, DEFAULT.h);
    if (w < MIN_W) w = MIN_W;
    if (h < MIN_H) h = MIN_H;
    if (w > 1) w = 1;
    if (h > 1) h = 1;
    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (x > 1 - w) x = 1 - w;
    if (y > 1 - h) y = 1 - h;
    return { x: x, y: y, w: w, h: h };
  }

  /** 3x3 box plus the centered 0 cell beneath it, in picture fractions. */
  function gridSpan(rect) {
    var r = clamp(rect);
    var gh = r.h * 0.75;
    return {
      x: r.x,
      y: r.y,
      w: r.w,
      h: gh,
      zero: {
        x: r.x + r.w / 3,
        y: r.y + gh,
        w: r.w / 3,
        h: r.h - gh
      }
    };
  }

  function load(storage) {
    if (!storage || typeof storage.getItem !== "function") return null;
    var raw;
    try { raw = storage.getItem(KEY); } catch (e) { return null; }
    if (!raw) return null;
    try {
      var o = JSON.parse(raw);
      if (!o || typeof o !== "object") return null;
      if (typeof o.x !== "number" || typeof o.y !== "number" || typeof o.w !== "number" || typeof o.h !== "number") return null;
      if (!isFinite(o.x) || !isFinite(o.y) || !isFinite(o.w) || !isFinite(o.h)) return null;
      return clamp(o);
    } catch (e2) {
      return null;
    }
  }

  function save(storage, rect) {
    if (!storage || typeof storage.setItem !== "function") return;
    try { storage.setItem(KEY, JSON.stringify(clamp(rect))); } catch (e) {}
  }

  function loadLock(storage) {
    if (!storage || typeof storage.getItem !== "function") return false;
    try { return storage.getItem(LOCK_KEY) === "1"; } catch (e) { return false; }
  }

  function saveLock(storage, on) {
    if (!storage || typeof storage.setItem !== "function") return;
    try { storage.setItem(LOCK_KEY, on ? "1" : "0"); } catch (e) {}
  }

  /** True while the user is typing, so live hotkeys must not fire. */
  function typingTarget(el) {
    if (!el || !el.tagName) return false;
    var tag = String(el.tagName).toLowerCase();
    if (tag === "input" || tag === "textarea" || tag === "select") return true;
    return !!el.isContentEditable;
  }

  /** 0-9, or -1. Numpad keys report the same "0"-"9" as the digit row. */
  function digitIndex(key) {
    if (key == null) return -1;
    var s = String(key);
    if (s.length !== 1) return -1;
    var c = s.charCodeAt(0);
    if (c < 48 || c > 57) return -1;
    return c - 48;
  }

  /** Same nx/ny the canvas pointer path already sends. No key id. */
  function tapMessages(nx, ny) {
    return [
      { op: "down", nx: nx, ny: ny },
      { op: "up", nx: nx, ny: ny }
    ];
  }

  /** PC log request. Must not carry a digit or a coordinate. */
  function logMessage() {
    return { op: "pinlog" };
  }

  return {
    DEFAULT: DEFAULT,
    KEY: KEY,
    LOCK_KEY: LOCK_KEY,
    LOG: LOG,
    clamp: clamp,
    gridSpan: gridSpan,
    load: load,
    save: save,
    loadLock: loadLock,
    saveLock: saveLock,
    typingTarget: typingTarget,
    digitIndex: digitIndex,
    tapMessages: tapMessages,
    logMessage: logMessage
  };
});

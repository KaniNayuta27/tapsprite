"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");

const Pin = require("./web/live-pin.js");
const Coord = require("./web/live-coord.js");
const html = fs.readFileSync(path.join(__dirname, "web/ui.html"), "utf8");
const page = fs.readFileSync(path.join(__dirname, "web/live-page.js"), "utf8");
const cssStart = html.indexOf(".live-pin {");
const css = cssStart >= 0 ? html.slice(cssStart, html.indexOf(".lib-head")) : "";

assert.strictEqual(Pin.LOG, "密码键盘: 按下");
assert.strictEqual(Pin.KEY, "tapsprite.livePinpad");
assert.strictEqual(Pin.DEFAULT.x, 0.15);
assert.strictEqual(Pin.DEFAULT.y, 0.58);
assert.strictEqual(Pin.DEFAULT.w, 0.6);
assert.strictEqual(Pin.DEFAULT.h, 0.36);

const grid = Pin.gridSpan(Pin.DEFAULT);
assert.ok(Math.abs(grid.x - 0.15) < 1e-9, "grid x");
assert.ok(Math.abs(grid.y - 0.58) < 1e-9, "grid y");
assert.ok(Math.abs(grid.x + grid.w - 0.75) < 1e-9, "grid right");
assert.ok(Math.abs(grid.y + grid.h - 0.85) < 1e-9, "grid bottom");
assert.ok(grid.zero.y >= grid.y + grid.h - 1e-9, "0 is below the 3x3");
assert.ok(Math.abs((grid.zero.x + grid.zero.w / 2) - (grid.x + grid.w / 2)) < 1e-9, "0 is centered");

const tiny = Pin.clamp({ x: -1, y: 2, w: 0.01, h: 5 });
assert.ok(tiny.w >= 0.18 && tiny.h >= 0.24 && tiny.h <= 1 && tiny.w <= 1);
assert.ok(tiny.x >= 0 && tiny.y >= 0 && tiny.x + tiny.w <= 1 + 1e-9 && tiny.y + tiny.h <= 1 + 1e-9);

const mem = {
  m: {},
  getItem(k) { return Object.prototype.hasOwnProperty.call(this.m, k) ? this.m[k] : null; },
  setItem(k, v) { this.m[k] = String(v); }
};
assert.strictEqual(Pin.load(mem), null);
Pin.save(mem, { x: 0.2, y: 0.3, w: 0.4, h: 0.4 });
assert.deepStrictEqual(Pin.load(mem), { x: 0.2, y: 0.3, w: 0.4, h: 0.4 });
assert.strictEqual(Pin.load({ getItem() { return "{"; } }), null);
assert.strictEqual(Pin.load({ getItem() { return "null"; } }), null);
assert.strictEqual(Pin.load({ getItem() { throw new Error("blocked"); } }), null);

const taps = Pin.tapMessages(0.42, 0.73);
assert.deepStrictEqual(taps, [
  { op: "down", nx: 0.42, ny: 0.73 },
  { op: "up", nx: 0.42, ny: 0.73 }
]);
const logMsg = Pin.logMessage();
assert.deepStrictEqual(logMsg, { op: "pinlog" });
assert.deepStrictEqual(Object.keys(logMsg), ["op"]);
assert.ok(!/[0-9]/.test(JSON.stringify(logMsg)), "log message must not contain a digit");
assert.ok(!JSON.stringify(logMsg).includes("0.42"));

for (const s of [
  'data-act="pinpad"',
  'aria-label="密码键盘"',
  "密码<br>键盘",
  'id="livePin"',
  'class="live-pin"',
  'title="拖动空白处移动，右下角调整大小"',
  'aria-label="关闭"',
  'aria-label="调整大小"',
  'class="live-pin-key live-pin-zero"',
  'src="/live-pin.js"'
]) {
  assert.ok(html.includes(s), "ui.html missing " + s);
}
for (let d = 0; d <= 9; d++) {
  assert.ok(html.includes(">" + d + "</button>"), "missing key " + d);
}
assert.ok(css.includes(".live-pin[hidden]"), "hidden pin must be unstyled out of the hit path");
assert.ok(css.includes("display: none !important"), "hidden pin must not paint");
assert.ok(css.includes("pointer-events: none !important"), "hidden pin must not block touch");
assert.ok(page.includes("normFromEvent({ clientX: box.left + box.width / 2, clientY: box.top + box.height / 2 })"),
  "pin press must use the existing pointer mapping at the cell center");
assert.ok(page.includes("LivePin.tapMessages"), "pin press must send a normal down/up tap");
assert.ok(page.includes("LivePin.logMessage"), "connected press logs through pinlog");
assert.ok(page.includes("addLog(LivePin.LOG)"), "offline press still logs the fixed line");
assert.ok(page.includes('setPinVisible(true)'), "unlock shows the pad");
assert.ok(page.includes('act === "pinpad"'), "button toggles the pad");
assert.ok(page.includes("localStorage"), "position is persisted");
assert.ok(!page.includes('op: "tap"'), "do not revive the old tap opcode in the page");
assert.ok(!page.includes("密码键盘: 按下"), "page must not build its own log line");
const pressFn = page.slice(page.indexOf("function onPinKey"), page.indexOf("function beginPinDrag"));
assert.ok(!pressFn.includes("textContent"), "press handler must not read the key label");
assert.ok(!pressFn.includes("innerText"), "press handler must not read the key label");

// Picture fraction → the same clientToNormalized path a canvas click uses.
{
  const cssW = 200, cssH = 400, left = 100, top = 40, videoW = 576, videoH = 1280;
  const box = Coord.contentRect(cssW, cssH, videoW, videoH);
  const frac = { nx: grid.x + grid.w / 2, ny: grid.y + grid.h / 2 };
  const clientX = left + box.x + frac.nx * box.w;
  const clientY = top + box.y + frac.ny * box.h;
  const n = Coord.clientToNormalized(clientX, clientY, left, top, cssW, cssH, videoW, videoH);
  assert.ok(n, "center of the 3x3 must land on the picture");
  assert.ok(Math.abs(n.nx - frac.nx) < 1e-9 && Math.abs(n.ny - frac.ny) < 1e-9);
}

function El(id) {
  this.id = id || "";
  this.hidden = false;
  this.disabled = false;
  this.attrs = {};
  this.style = {};
  this.children = [];
  this.parentElement = null;
  this.listeners = {};
  this.className = "";
  this.textContent = "";
  this.rect = { left: 0, top: 0, width: 10, height: 10 };
}
El.prototype.addEventListener = function (type, fn) {
  (this.listeners[type] = this.listeners[type] || []).push(fn);
};
El.prototype.setAttribute = function (k, v) { this.attrs[k] = String(v); };
El.prototype.getAttribute = function (k) { return this.attrs[k] == null ? null : this.attrs[k]; };
El.prototype.setPointerCapture = function () {};
El.prototype.getBoundingClientRect = function () { return this.rect; };
El.prototype.getContext = function () {
  return { setTransform() {}, fillRect() {}, beginPath() {}, moveTo() {}, lineTo() {}, stroke() {}, strokeRect() {}, save() {}, restore() {}, drawImage() {} };
};
El.prototype.classList = null;
El.prototype.append = function (child) {
  child.parentElement = this;
  this.children.push(child);
};
El.prototype.matches = function (sel) {
  if (sel === ".live-pin-key") return this.className.split(/\s+/).indexOf("live-pin-key") >= 0;
  if (sel === ".live-pin-x") return this.className.split(/\s+/).indexOf("live-pin-x") >= 0;
  if (sel === ".live-pin-resize") return this.className.split(/\s+/).indexOf("live-pin-resize") >= 0;
  if (sel === "button[data-act]") return this.getAttribute("data-act") != null;
  return false;
};
El.prototype.closest = function (sel) {
  let n = this;
  while (n) {
    if (n.matches && n.matches(sel)) return n;
    n = n.parentElement;
  }
  return null;
};
El.prototype.querySelectorAll = function (sel) {
  const out = [];
  const walk = (node) => {
    for (const c of node.children) {
      if (c.matches && c.matches(sel)) out.push(c);
      walk(c);
    }
  };
  walk(this);
  return out;
};
El.prototype.querySelector = function (sel) {
  return this.querySelectorAll(sel)[0] || null;
};
El.prototype.dispatch = function (type, ev) {
  ev = ev || {};
  ev.target = ev.target || this;
  ev.currentTarget = this;
  ev.preventDefault = ev.preventDefault || function () {};
  if (!ev.stopPropagation) {
    ev.stopPropagation = function () { ev._stop = true; };
  }
  const list = (this.listeners[type] || []).slice();
  for (const fn of list) {
    fn.call(this, ev);
    if (ev._stop) return;
  }
  if (!ev._stop && this.parentElement && this.parentElement.dispatch) {
    const next = Object.assign({}, ev, { currentTarget: this.parentElement });
    next.stopPropagation = ev.stopPropagation;
    this.parentElement.dispatch(type, next);
  }
};
function installClassList(el) {
  el.classList = {
    add(c) {
      const parts = el.className.split(/\s+/).filter(Boolean);
      if (parts.indexOf(c) < 0) parts.push(c);
      el.className = parts.join(" ");
    },
    remove(c) {
      el.className = el.className.split(/\s+/).filter((x) => x && x !== c).join(" ");
    },
    contains(c) { return el.className.split(/\s+/).indexOf(c) >= 0; }
  };
}

const byId = {};
function make(id, className) {
  const el = new El(id);
  installClassList(el);
  if (className) el.className = className;
  if (id) byId[id] = el;
  return el;
}

const pic = make("pic", "live-pic");
pic.rect = { left: 0, top: 0, width: 200, height: 400 };
const canvas = make("liveCanvas");
canvas.rect = { left: 100, top: 40, width: 200, height: 400 };
const map = make("liveMap");
map.hidden = true;
const pin = make("livePin", "live-pin");
pin.hidden = true;
const close = make("", "live-pin-x");
close.textContent = "×";
const resize = make("", "live-pin-resize");
const keys = [];
for (let d = 1; d <= 9; d++) {
  const k = make("", "live-pin-key");
  k.textContent = String(d);
  keys.push(k);
}
const zero = make("", "live-pin-key live-pin-zero");
zero.textContent = "0";
keys.push(zero);
pin.append(close);
for (const k of keys) pin.append(k);
pin.append(resize);
pic.append(canvas);
pic.append(map);
pic.append(pin);
canvas.parentElement = pic;

const keysBar = make("liveKeys");
function barButton(act, label) {
  const b = make("", "live-key");
  b.setAttribute("data-act", act);
  b.textContent = label;
  b.disabled = act !== "pinpad";
  keysBar.append(b);
  return b;
}
const unlock = barButton("unlock", "解锁");
const pinpad = barButton("pinpad", "密码键盘");
const toggle = make("liveToggle");
const dbg = make("liveDbg");
const ind = make("liveInd");
const fps = make("liveFps30");

const store = {};
global.localStorage = {
  getItem(k) { return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null; },
  setItem(k, v) { store[k] = String(v); }
};
store[Pin.KEY] = JSON.stringify({ x: 0.2, y: 0.3, w: 0.4, h: 0.4 });
global.LiveCoord = Coord;
global.LivePin = Pin;
global.location = { protocol: "http:", host: "127.0.0.1:18766" };
global.window = global;
global.devicePixelRatio = 1;
global.document = {
  getElementById(id) { return byId[id] || null; },
  querySelector(sel) {
    if (sel === "#page-live .live-pic") return pic;
    if (sel === '#liveKeys button[data-act="pinpad"]') return pinpad;
    return null;
  },
  querySelectorAll(sel) {
    if (sel === "#liveKeys button[data-act]") return keysBar.children.slice();
    return [];
  }
};
if (!global.addEventListener) global.addEventListener = function () {};
const logs = [];
global.addLog = function (s) { logs.push(s); };

require("./web/live-page.js");

assert.strictEqual(pin.style.left, "20%");
assert.strictEqual(pin.style.top, "30%");
assert.strictEqual(pin.style.width, "40%");
assert.strictEqual(pin.style.height, "40%");
assert.strictEqual(pin.hidden, true, "pad stays hidden until unlock or the toggle");

keysBar.dispatch("click", { target: pinpad, button: 0 });
assert.strictEqual(pin.hidden, false);
assert.strictEqual(pinpad.getAttribute("aria-pressed"), "true");
keysBar.dispatch("click", { target: { nodeType: 3, parentNode: pinpad }, button: 0 });
assert.strictEqual(pin.hidden, true, "text-node click still toggles");
assert.strictEqual(pinpad.getAttribute("aria-pressed"), "false");

const sent = [];
global.WebSocket = function () {
  return {
    readyState: 1,
    send(s) { sent.push(s); },
    close() {},
    addEventListener() {}
  };
};
toggle.dispatch("click", { target: toggle, button: 0 });
unlock.disabled = false;
keysBar.dispatch("click", { target: unlock, button: 0 });
assert.strictEqual(pin.hidden, false, "unlock shows the pad");
assert.ok(sent.some((s) => s.indexOf('"op":"unlock"') >= 0), "unlock still sends op unlock");

logs.length = 0;
sent.length = 0;
const box = Coord.contentRect(200, 400, 576, 1280);
const wantNx = 0.33;
const wantNy = 0.62;
const cx = 100 + box.x + wantNx * box.w;
const cy = 40 + box.y + wantNy * box.h;
keys[4].rect = { left: cx - 8, top: cy - 8, width: 16, height: 16 };
keys[4].dispatch("pointerdown", { target: keys[4], button: 0 });
assert.ok(keys[4].classList.contains("lit"), "press highlight");
assert.strictEqual(logs.length, 0, "connected press must not also client-log");
assert.strictEqual(sent.length, 3, "down, up, pinlog: " + sent.join(" | "));
const down = JSON.parse(sent[0]);
const up = JSON.parse(sent[1]);
const logged = JSON.parse(sent[2]);
assert.strictEqual(down.op, "down");
assert.strictEqual(up.op, "up");
assert.ok(Math.abs(down.nx - wantNx) < 1e-9 && Math.abs(down.ny - wantNy) < 1e-9, JSON.stringify(down));
assert.strictEqual(up.nx, down.nx);
assert.strictEqual(up.ny, down.ny);
assert.deepStrictEqual(logged, { op: "pinlog" });
assert.ok(!/[0-9]/.test(sent[2]), "pinlog payload has no digit");
assert.ok(!sent[2].includes(String(keys[4].textContent)), "pinlog must not echo the key label");

close.dispatch("click", { target: close, button: 0 });
assert.strictEqual(pin.hidden, true, "close hides the pad");
assert.strictEqual(pinpad.getAttribute("aria-pressed"), "false");

pin.hidden = false;
const beforeDrag = store[Pin.KEY];
pin.dispatch("pointerdown", { target: pin, button: 0, pointerId: 7, clientX: 10, clientY: 20 });
pin.dispatch("pointermove", { target: pin, button: 0, pointerId: 7, clientX: 30, clientY: 60 });
pin.dispatch("pointerup", { target: pin, button: 0, pointerId: 7, clientX: 30, clientY: 60 });
const moved = JSON.parse(store[Pin.KEY]);
assert.notStrictEqual(store[Pin.KEY], beforeDrag);
assert.ok(Math.abs(moved.x - 0.3) < 1e-9, moved.x);
assert.ok(Math.abs(moved.y - 0.4) < 1e-9, moved.y);
assert.ok(Math.abs(moved.w - 0.4) < 1e-9 && Math.abs(moved.h - 0.4) < 1e-9);

resize.dispatch("pointerdown", { target: resize, button: 0, pointerId: 8, clientX: 0, clientY: 0 });
pin.dispatch("pointermove", { target: pin, pointerId: 8, clientX: 20, clientY: 40 });
pin.dispatch("pointerup", { target: pin, pointerId: 8, clientX: 20, clientY: 40 });
const sized = JSON.parse(store[Pin.KEY]);
assert.ok(Math.abs(sized.w - 0.5) < 1e-9, sized.w);
assert.ok(Math.abs(sized.h - 0.5) < 1e-9, sized.h);
assert.ok(Math.abs(sized.x - moved.x) < 1e-9 && Math.abs(sized.y - moved.y) < 1e-9, "resize keeps the origin");

console.log("live_pin_test: ok");

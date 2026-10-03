"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");

const html = fs.readFileSync(path.join(__dirname, "web/ui.html"), "utf8");
const page = fs.readFileSync(path.join(__dirname, "web/live-page.js"), "utf8");

assert.ok(!fs.existsSync(path.join(__dirname, "web/live-gesture.js")), "live-gesture.js must be removed");

for (const s of [
  'id="liveKeys"',
  'data-act="back"',
  'data-act="home"',
  'data-act="recents"',
  'data-act="notifications"',
  'data-act="quicksettings"',
  'data-act="lock"',
  'data-act="screenshot"',
  'data-act="wake"',
  'data-act="unlock"',
  'title="返回"',
  'title="主页"',
  'title="多任务"',
  'title="通知栏"',
  'title="快捷设置"',
  'title="锁屏"',
  'title="截图"',
  'title="亮屏。部分机型需开启「后台弹出界面」和「锁屏显示」权限"',
  'title="解锁（弹出密码或 PIN）。部分机型需开启「后台弹出界面」和「锁屏显示」权限"',
  'id="liveFps30" checked',
  "锁定30帧",
  'id="liveLog"',
  'class="log"',
  'src="/live-coord.js"',
  'src="/live-page.js"',
]) {
  assert.ok(html.includes(s), "ui.html missing " + s);
}

for (const s of [
  'data-act="power"',
  'title="电源菜单"',
  'src="/live-gesture.js"',
  "live-gesture.js",
]) {
  assert.ok(!html.includes(s), "ui.html still has " + s);
}

for (const s of [
  'op: "down"',
  'op: "move"',
  'op: "up"',
  "held: held",
  'op: "fps"',
  'op: "wake"',
  'op: "unlock"',
  'op: "global"',
  "setPointerCapture",
  "pointercancel",
  "lostpointercapture",
  "pagehide",
  "contextmenu",
  "MOVE_MS = 16",
  "MOVE_EPS = 2",
  "GAP_MS = 120",
  "contentRect",
  "rebuildDecoder",
  "decodeQueueSize",
]) {
  if (s === "decodeQueueSize") {
    assert.ok(!page.includes(s), "live-page.js still drops on " + s);
  } else {
    assert.ok(page.includes(s), "live-page.js missing " + s);
  }
}

for (const s of ["LiveGesture", 'op: "tap"', 'op: "swipe"', "power: 1"]) {
  assert.ok(!page.includes(s), "live-page.js still has " + s);
}

console.log("live_gesture_test: ok");

"use strict";
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const G = require("./web/live-gesture.js");

function expect(name, pts, ms, kind, action) {
  const got = G.classify(pts, ms);
  assert.strictEqual(got.kind, kind, name + " kind " + JSON.stringify(got));
  if (kind === "global") {
    assert.strictEqual(got.action, action, name + " action " + JSON.stringify(got));
  } else {
    assert.strictEqual(got.action, undefined, name + " unexpected action " + JSON.stringify(got));
  }
}

// bottom short -> HOME
expect("bottom short", [[0.50, 0.98], [0.50, 0.82]], 180, "global", "home");
// bottom long -> RECENTS (past ~40% of height, even when the finger is quick)
expect("bottom long", [[0.50, 0.99], [0.50, 0.45]], 200, "global", "recents");
// bottom hold -> RECENTS (short rise, finger stays down)
expect("bottom hold", [[0.50, 0.97], [0.52, 0.84]], 700, "global", "recents");

// left/right edge inward -> BACK
expect("left inward", [[0.02, 0.50], [0.28, 0.51]], 160, "global", "back");
expect("right inward", [[0.98, 0.46], [0.70, 0.48]], 160, "global", "back");

// middle drag -> swipe
expect("middle drag", [[0.40, 0.40], [0.62, 0.72]], 220, "swipe");
// a long middle drag is still a swipe (hold only applies to the bottom band)
expect("middle hold", [[0.40, 0.70], [0.42, 0.40]], 900, "swipe");

// tap -> tap
expect("tap point", [[0.50, 0.50]], 40, "tap");
expect("tap micro", [[0.50, 0.50], [0.503, 0.504]], 80, "tap");
expect("bottom tap", [[0.50, 0.99]], 60, "tap");
expect("edge tap", [[0.01, 0.50], [0.012, 0.501]], 50, "tap");

// edge drag outward/vertical -> swipe
expect("left outward", [[0.03, 0.50], [0.00, 0.50]], 140, "swipe");
expect("right outward", [[0.97, 0.50], [1.00, 0.51]], 140, "swipe");
expect("left vertical", [[0.02, 0.30], [0.03, 0.72]], 200, "swipe");
expect("right vertical", [[0.98, 0.28], [0.97, 0.70]], 200, "swipe");
expect("left inward but vertical", [[0.02, 0.30], [0.12, 0.80]], 200, "swipe");

// Not a system gesture: bottom band, but the finger goes down or sideways.
expect("bottom down", [[0.50, 0.97], [0.50, 0.99]], 120, "swipe");
expect("bottom horizontal", [[0.30, 0.98], [0.70, 0.97]], 180, "swipe");

// Band edges. 4% is inside; just outside stays a swipe.
expect("bottom band edge short", [[0.50, 0.96], [0.50, 0.80]], 160, "global", "home");
expect("above bottom band", [[0.50, 0.95], [0.50, 0.70]], 160, "swipe");
expect("left band edge", [[0.04, 0.50], [0.30, 0.50]], 160, "global", "back");
expect("inside of left band", [[0.05, 0.50], [0.30, 0.50]], 160, "swipe");
expect("right band edge", [[0.96, 0.50], [0.70, 0.50]], 160, "global", "back");

// Rise thresholds: 40% is recents; just under is home unless the finger is held.
expect("rise just under 40", [[0.50, 0.99], [0.50, 0.60]], 200, "global", "home");
expect("rise at 40", [[0.50, 0.99], [0.50, 0.59]], 120, "global", "recents");
expect("hold boundary below", [[0.50, 0.98], [0.50, 0.85]], G.HOLD_MS - 1, "global", "home");
expect("hold boundary at", [[0.50, 0.98], [0.50, 0.85]], G.HOLD_MS, "global", "recents");

// A long bottom swipe that wanders back still counts once it has passed 40%.
expect("bottom peak then settle", [[0.50, 0.98], [0.50, 0.40], [0.50, 0.70]], 240, "global", "recents");
// Returning to the start cancels the nav gesture.
expect("bottom cancel", [[0.50, 0.98], [0.50, 0.40], [0.50, 0.98]], 300, "swipe");

// Corner: up from the bottom-left is Home, not Back. Inward along the bottom is Back.
expect("corner up", [[0.02, 0.98], [0.04, 0.78]], 160, "global", "home");
expect("corner inward", [[0.02, 0.98], [0.30, 0.97]], 160, "global", "back");

const html = fs.readFileSync(path.join(__dirname, "web/ui.html"), "utf8");
const page = fs.readFileSync(path.join(__dirname, "web/live-page.js"), "utf8");
for (const s of [
  'id="liveKeys"',
  'data-act="back"',
  'data-act="home"',
  'data-act="recents"',
  'data-act="notifications"',
  'data-act="quicksettings"',
  'data-act="lock"',
  'data-act="power"',
  'data-act="screenshot"',
  'title="返回"',
  'title="主页"',
  'title="多任务"',
  'title="通知栏"',
  'title="快捷设置"',
  'title="锁屏"',
  'title="电源菜单"',
  'title="截图"',
  'src="/live-gesture.js"',
]) {
  assert.ok(html.includes(s), "ui.html missing " + s);
}
assert.ok(page.includes("LiveGesture.classify"), "live-page.js does not classify");
assert.ok(page.includes('op: "global"'), "live-page.js does not send global");
assert.ok(page.includes('op: "tap"'), "live-page.js dropped tap");
assert.ok(page.includes('op: "swipe"'), "live-page.js dropped swipe");

console.log("live_gesture_test: ok");

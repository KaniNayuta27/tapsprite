/* Pure 实时操控 gesture classification. Browser global LiveGesture, or module.exports under node.
   Points are normalized on the phone picture (0..1, origin top-left). This file does not
   map pixels. System gesture navigation often drops injected edge swipes, so a drag that
   matches a system gesture becomes a global action instead of a raw swipe.

   Bottom band (start ny >= 1-EDGE) moving up, vertical axis dominant:
     rise past LONG_FRAC, or finger down for HOLD_MS → recents
     otherwise → home
   Left/right band (start nx <= EDGE or >= 1-EDGE) moving inward, horizontal dominant → back
   Travel under TAP_TRAVEL, or a single point → tap
   Every other drag, including an edge drag that goes outward or stays vertical → swipe
*/
(function (root, factory) {
  var api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.LiveGesture = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  var EDGE = 0.04;
  var LONG_FRAC = 0.40;
  var HOLD_MS = 450;
  var TAP_TRAVEL = 0.01;

  function normPoint(p) {
    var x;
    var y;
    if (Array.isArray(p)) {
      x = p[0];
      y = p[1];
    } else if (p && typeof p === "object") {
      x = p.nx != null ? p.nx : p.x;
      y = p.ny != null ? p.ny : p.y;
    } else {
      return null;
    }
    if (typeof x !== "number" || typeof y !== "number" || x !== x || y !== y) return null;
    return { x: x, y: y };
  }

  function classify(pts, ms) {
    var seq = [];
    if (pts && pts.length) {
      for (var i = 0; i < pts.length; i++) {
        var p = normPoint(pts[i]);
        if (p) seq.push(p);
      }
    }
    if (!seq.length) return { kind: "tap" };
    var start = seq[0];
    var end = seq[seq.length - 1];
    var travel = 0;
    var minY = start.y;
    for (var j = 1; j < seq.length; j++) {
      travel += Math.hypot(seq[j].x - seq[j - 1].x, seq[j].y - seq[j - 1].y);
      if (seq[j].y < minY) minY = seq[j].y;
    }
    if (seq.length < 2 || travel < TAP_TRAVEL) return { kind: "tap" };

    var dx = end.x - start.x;
    var dy = end.y - start.y;
    var up = -dy;
    var peakUp = start.y - minY;
    var held = typeof ms === "number" && ms >= HOLD_MS;
    var fromBottom = start.y >= 1 - EDGE;
    var fromLeft = start.x <= EDGE;
    var fromRight = start.x >= 1 - EDGE;

    if (fromBottom && up > Math.abs(dx)) {
      if (peakUp >= LONG_FRAC || held) return { kind: "global", action: "recents" };
      return { kind: "global", action: "home" };
    }
    if (fromLeft && dx > Math.abs(dy)) return { kind: "global", action: "back" };
    if (fromRight && -dx > Math.abs(dy)) return { kind: "global", action: "back" };
    return { kind: "swipe" };
  }

  return {
    classify: classify,
    EDGE: EDGE,
    LONG_FRAC: LONG_FRAC,
    HOLD_MS: HOLD_MS,
    TAP_TRAVEL: TAP_TRAVEL
  };
});

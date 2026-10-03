/* 实时操控 page: WebSocket → WebCodecs canvas.
   Pointer events are a live finger (down / move / up). System keys are the button bar. */
(function () {
  var canvas = document.getElementById("liveCanvas");
  var toggle = document.getElementById("liveToggle");
  var dbgBtn = document.getElementById("liveDbg");
  var ind = document.getElementById("liveInd");
  var mapEl = document.getElementById("liveMap");
  if (!canvas || !toggle || typeof LiveCoord === "undefined") return;

  var ws = null;
  var streaming = false;
  var debug = false;
  var meta = null;
  var decoder = null;
  var decGen = 0;
  var configured = false;
  var sps = null;
  var pps = null;
  var closing = false;
  var rebuilding = false;
  var needKey = true;
  var lastPhoneTs = 0;
  var GAP_MS = 120;
  var finger = null;
  var MOVE_MS = 16;
  var MOVE_EPS = 2;
  var lastMap = null;
  var recvAt = new Map();
  var fpsN = 0;
  var fpsT = 0;
  var fps = 0;
  var latText = "—";
  var picRatio = "";
  var pinQuiet = 0;

  function videoSize() {
    if (meta && meta.encW > 0 && meta.encH > 0) return { w: meta.encW, h: meta.encH };
    return { w: 576, h: 1280 };
  }

  function fitPicture() {
    var pic = canvas.parentElement;
    if (!pic || !pic.classList || !pic.classList.contains("live-pic")) {
      pic = document.querySelector("#page-live .live-pic");
    }
    if (!pic) return;
    var v = videoSize();
    var ratio = v.w + " / " + v.h;
    if (picRatio === ratio) return;
    picRatio = ratio;
    pic.style.aspectRatio = ratio;
  }

  function prep() {
    var dpr = window.devicePixelRatio || 1;
    var r = canvas.getBoundingClientRect();
    var bw = Math.max(1, Math.round(r.width * dpr));
    var bh = Math.max(1, Math.round(r.height * dpr));
    if (canvas.width !== bw || canvas.height !== bh) {
      canvas.width = bw;
      canvas.height = bh;
    }
    var ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { ctx: ctx, r: r };
  }

  function drawGrid(ctx, box) {
    ctx.save();
    ctx.strokeStyle = "rgba(255,255,255,.28)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (var i = 0; i <= 10; i++) {
      var x = box.x + (box.w * i) / 10;
      var y = box.y + (box.h * i) / 10;
      ctx.moveTo(x, box.y);
      ctx.lineTo(x, box.y + box.h);
      ctx.moveTo(box.x, y);
      ctx.lineTo(box.x + box.w, y);
    }
    ctx.stroke();
    ctx.restore();
  }

  function redrawIdle() {
    if (!debug) return;
    var p = prep();
    if (p.r.width < 2 || p.r.height < 2) return;
    var v = videoSize();
    p.ctx.fillStyle = "#111018";
    p.ctx.fillRect(0, 0, p.r.width, p.r.height);
    var box = LiveCoord.contentRect(p.r.width, p.r.height, v.w, v.h);
    p.ctx.strokeStyle = "rgba(255,255,255,.45)";
    p.ctx.strokeRect(box.x + 0.5, box.y + 0.5, Math.max(0, box.w - 1), Math.max(0, box.h - 1));
    drawGrid(p.ctx, box);
  }

  function renderMap() {
    if (!mapEl) return;
    if (!debug) {
      mapEl.hidden = true;
      return;
    }
    mapEl.hidden = false;
    if (!lastMap) {
      mapEl.textContent = meta ? "网格已开 · 点击画面" : "自测网格 · 假定 1080×2400（未连接）";
      return;
    }
    var px = lastMap.phone || lastMap.px;
    var tag = lastMap.phone ? "手机" : (meta ? "预计" : "自测");
    var rot = meta ? meta.rot : 0;
    var pw = meta ? meta.physW : 1080;
    var ph = meta ? meta.physH : 2400;
    mapEl.textContent = "n " + lastMap.n.nx.toFixed(4) + "," + lastMap.n.ny.toFixed(4)
      + " → " + (px ? px.x + "," + px.y : "—")
      + "  " + tag + "  rot " + rot + "  " + pw + "×" + ph;
  }

  function showLocal(n) {
    var physW = meta && meta.physW ? meta.physW : 1080;
    var physH = meta && meta.physH ? meta.physH : 2400;
    var rot = meta && meta.rot ? meta.rot : 0;
    var px = null;
    try {
      px = LiveCoord.normalizedToPhysical(n.nx, n.ny, physW, physH, rot, false);
    } catch (e) { px = null; }
    lastMap = { n: n, px: px, phone: null };
    renderMap();
  }

  function send(obj) {
    if (!ws || ws.readyState !== 1) return;
    try { ws.send(JSON.stringify(obj)); } catch (e) {}
  }

  function fpsMax() {
    var box = document.getElementById("liveFps30");
    return box && box.checked ? 30 : 60;
  }

  function sendFps() {
    send({ op: "fps", max: fpsMax() });
  }

  var fpsBox = document.getElementById("liveFps30");
  if (fpsBox) fpsBox.addEventListener("change", sendFps);

  var ALWAYS_KEYS = { wake: 1, unlock: 1, pinpad: 1 };
  var BASE_KEYS = { back: 1, home: 1, recents: 1, notifications: 1, quicksettings: 1 };

  function capsFrom(m) {
    if (m && Array.isArray(m.actions)) {
      var listed = {};
      for (var i = 0; i < m.actions.length; i++) listed[String(m.actions[i])] = true;
      return listed;
    }
    if (m && typeof m.api === "number" && isFinite(m.api)) {
      var set = {};
      if (m.api >= 16) set.back = set.home = set.recents = set.notifications = true;
      if (m.api >= 17) set.quicksettings = true;
      if (m.api >= 28) set.lock = set.screenshot = true;
      return set;
    }
    return null;
  }

  function syncKeyEnabled() {
    var on = !!(streaming && ws && ws.readyState === 1);
    var nodes = document.querySelectorAll("#liveKeys button[data-act]");
    for (var i = 0; i < nodes.length; i++) {
      if (nodes[i].getAttribute("data-act") === "pinpad") {
        nodes[i].disabled = false;
        continue;
      }
      nodes[i].disabled = !on;
    }
  }

  function applyCaps(m) {
    var set = capsFrom(m);
    var nodes = document.querySelectorAll("#liveKeys button[data-act]");
    for (var i = 0; i < nodes.length; i++) {
      var act = nodes[i].getAttribute("data-act");
      if (ALWAYS_KEYS[act]) {
        nodes[i].hidden = false;
        continue;
      }
      nodes[i].hidden = !(set ? set[act] : BASE_KEYS[act]);
    }
    syncKeyEnabled();
  }

  var keysEl = document.getElementById("liveKeys");
  if (keysEl) {
    keysEl.addEventListener("click", function (e) {
      var t = e.target;
      if (t && t.nodeType === 3) t = t.parentNode;
      var b = t && t.closest ? t.closest("button[data-act]") : null;
      if (!b || b.hidden) return;
      var act = b.getAttribute("data-act");
      if (!act) return;
      if (act === "pinpad") {
        setPinVisible(!!pinEl && pinEl.hidden);
        return;
      }
      if (b.disabled) return;
      if (!streaming || !ws || ws.readyState !== 1) return;
      if (act === "wake") send({ op: "wake" });
      else if (act === "unlock") {
        send({ op: "unlock" });
        setPinVisible(true);
      }
      else send({ op: "global", action: act });
    });
  }

  function bytesEq(a, b) {
    if (!a || !b || a.length !== b.length) return false;
    for (var i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
    return true;
  }

  function detachDecoder() {
    var d = decoder;
    decoder = null;
    decGen++;
    if (d) {
      try { d.close(); } catch (e) {}
    }
  }

  function stopDecoder() {
    closing = true;
    configured = false;
    sps = null;
    pps = null;
    needKey = true;
    lastPhoneTs = 0;
    detachDecoder();
    recvAt.clear();
    closing = false;
  }

  function ensureDecoder() {
    if (decoder && decoder.state !== "closed") return true;
    if (typeof VideoDecoder === "undefined") {
      ind.textContent = "此 WebView2 没有 WebCodecs";
      return false;
    }
    var gen = ++decGen;
    decoder = new VideoDecoder({
      output: function (frame) { paint(frame); },
      error: function () {
        if (closing || rebuilding || gen !== decGen) return;
        rebuildDecoder();
      }
    });
    return true;
  }

  function rebuildDecoder() {
    if (rebuilding || closing) return;
    rebuilding = true;
    configured = false;
    needKey = true;
    lastPhoneTs = 0;
    detachDecoder();
    recvAt.clear();
    rebuilding = false;
    if (!ensureDecoder()) return;
    if (sps && pps) configure();
    send({ op: "sync" });
  }

  function configure() {
    if (!ensureDecoder() || !sps || !pps) return;
    var codec = LiveCoord.avcCodecString(sps);
    var description = LiveCoord.avcCDescription(sps, pps);
    var w = meta && meta.encW;
    var h = meta && meta.encH;
    var attempts = [
      { codec: codec, description: description, optimizeForLatency: true, hardwareAcceleration: "prefer-hardware", codedWidth: w, codedHeight: h },
      { codec: codec, description: description, optimizeForLatency: true, hardwareAcceleration: "prefer-hardware" },
      { codec: codec, description: description }
    ];
    if (decoder.state === "configured") {
      try { decoder.reset(); } catch (e) {}
    }
    for (var i = 0; i < attempts.length; i++) {
      var cfg = attempts[i];
      if (!cfg.codedWidth) {
        delete cfg.codedWidth;
        delete cfg.codedHeight;
      }
      try {
        decoder.configure(cfg);
        configured = true;
        return;
      } catch (e) {}
    }
    configured = false;
    ind.textContent = "解码配置失败";
  }

  function paint(frame) {
    fitPicture();
    var p = prep();
    var v = videoSize();
    if (p.r.width >= 2 && p.r.height >= 2) {
      p.ctx.fillStyle = "#111018";
      p.ctx.fillRect(0, 0, p.r.width, p.r.height);
      var box = LiveCoord.contentRect(p.r.width, p.r.height, v.w, v.h);
      try { p.ctx.drawImage(frame, box.x, box.y, box.w, box.h); } catch (e) {}
      if (debug) drawGrid(p.ctx, box);
    }
    var info = recvAt.get(frame.timestamp);
    recvAt.delete(frame.timestamp);
    var dec = info ? (performance.now() - info.t) : 0;
    var clock = info ? (Date.now() - info.phone) : -1;
    fpsN++;
    var now = performance.now();
    if (!fpsT) fpsT = now;
    if (now - fpsT >= 500) {
      fps = Math.round(fpsN * 1000 / (now - fpsT));
      fpsN = 0;
      fpsT = now;
      var parts = [fps + " fps", "解码 " + Math.max(0, Math.round(dec)) + "ms"];
      if (clock >= 0 && clock <= 1500) parts.push("估 " + Math.round(clock) + "ms");
      latText = parts.join(" · ");
      if (streaming) ind.textContent = latText;
    }
    frame.close();
  }

  function feed(flags, tsMs, annex) {
    if (typeof VideoDecoder === "undefined") {
      ind.textContent = "此 WebView2 没有 WebCodecs";
      return;
    }
    var nals = LiveCoord.splitAnnexB(annex);
    var spsN = null, ppsN = null, vcl = [], key = (flags & 1) !== 0;
    for (var i = 0; i < nals.length; i++) {
      var t = LiveCoord.nalType(nals[i]);
      if (t === 7) spsN = nals[i];
      else if (t === 8) ppsN = nals[i];
      else if (t === 5) { key = true; vcl.push(nals[i]); }
      else if (t === 1) vcl.push(nals[i]);
    }
    if (spsN && ppsN && (!bytesEq(sps, spsN) || !bytesEq(pps, ppsN) || !configured)) {
      sps = spsN;
      pps = ppsN;
      configure();
    }
    if (!configured || !vcl.length) {
      if (key) send({ op: "sync" });
      return;
    }
    if (!key && needKey) return;
    if (!key && lastPhoneTs > 0 && (tsMs < lastPhoneTs || tsMs - lastPhoneTs > GAP_MS)) {
      rebuildDecoder();
      return;
    }
    var tsUs = tsMs * 1000;
    while (recvAt.has(tsUs)) tsUs++;
    recvAt.set(tsUs, { t: performance.now(), phone: tsMs });
    try {
      decoder.decode(new EncodedVideoChunk({
        type: key ? "key" : "delta",
        timestamp: tsUs,
        data: LiveCoord.avccFromNals(vcl)
      }));
      if (key) needKey = false;
      lastPhoneTs = tsMs;
    } catch (e) {
      rebuildDecoder();
    }
  }

  function onControl(msg) {
    if (!msg || !msg.op) return;
    if (msg.op === "log") return;
    if (msg.op === "state") {
      if (msg.err) {
        releaseFinger(null, null);
        ind.textContent = msg.err;
        streaming = false;
        toggle.textContent = "开始";
        var sock = ws;
        ws = null;
        stopDecoder();
        if (sock) {
          try { sock.close(); } catch (e) {}
        }
        syncKeyEnabled();
      } else if (msg.on) {
        streaming = true;
        toggle.textContent = "结束";
        ind.textContent = "等待画面…";
        syncKeyEnabled();
      } else if (!streaming) {
        ind.textContent = "已结束";
      }
      return;
    }
    if (msg.op === "mapped") {
      if (pinQuiet && Date.now() < pinQuiet) return;
      if (!lastMap) lastMap = { n: { nx: msg.nx, ny: msg.ny }, px: null, phone: null };
      lastMap.n = { nx: msg.nx, ny: msg.ny };
      lastMap.phone = { x: msg.px, y: msg.py };
      if (!meta) meta = {};
      if (msg.physW) meta.physW = msg.physW;
      if (msg.physH) meta.physH = msg.physH;
      if (msg.rot != null) meta.rot = msg.rot;
      renderMap();
    }
  }

  function onBinary(u) {
    if (!u || !u.length) return;
    if (u[0] === 1) {
      try { meta = JSON.parse(new TextDecoder().decode(u.subarray(1))); } catch (e) { return; }
      ind.textContent = (meta.encW || "?") + "×" + (meta.encH || "?") + " · 屏 " + meta.physW + "×" + meta.physH + " rot " + meta.rot;
      fitPicture();
      applyCaps(meta);
      renderMap();
      send({ op: "sync" });
      return;
    }
    if (u[0] === 3) {
      try { onControl(JSON.parse(new TextDecoder().decode(u.subarray(1)))); } catch (e) {}
      return;
    }
    if (u[0] !== 2 || u.length < 10) return;
    feed(u[1], LiveCoord.readU64BE(u, 2), u.subarray(10));
  }

  function normFromEvent(e) {
    var r = canvas.getBoundingClientRect();
    var v = videoSize();
    return LiveCoord.clientToNormalized(e.clientX, e.clientY, r.left, r.top, r.width, r.height, v.w, v.h);
  }

  function pictureMovePx(a, b) {
    var r = canvas.getBoundingClientRect();
    var v = videoSize();
    var box = LiveCoord.contentRect(r.width, r.height, v.w, v.h);
    var dx = (a.nx - b.nx) * (box.w || 0);
    var dy = (a.ny - b.ny) * (box.h || 0);
    return Math.hypot(dx, dy);
  }

  function flushMove() {
    if (!finger) return;
    finger.timer = 0;
    if (!finger.dragged) return;
    send({ op: "move", nx: finger.nx, ny: finger.ny });
    finger.sentNx = finger.nx;
    finger.sentNy = finger.ny;
  }

  function scheduleMove() {
    if (!finger || finger.timer) return;
    finger.timer = setTimeout(flushMove, MOVE_MS);
  }

  function releaseFinger(nx, ny) {
    if (!finger) return;
    var f = finger;
    finger = null;
    if (f.timer) {
      clearTimeout(f.timer);
      f.timer = 0;
    }
    if (nx != null && ny != null) {
      f.nx = nx;
      f.ny = ny;
    }
    if (debug) showLocal({ nx: f.nx, ny: f.ny });
    var held = Date.now() - (f.t0 || Date.now());
    if (held < 0) held = 0;
    send({ op: "up", nx: f.nx, ny: f.ny, held: held });
  }

  canvas.addEventListener("pointerdown", function (e) {
    if (e.button != null && e.button !== 0) return;
    var n = normFromEvent(e);
    if (!n) return;
    if (finger) releaseFinger(finger.nx, finger.ny);
    try { canvas.setPointerCapture(e.pointerId); } catch (err) {}
    finger = {
      id: e.pointerId,
      nx: n.nx,
      ny: n.ny,
      downNx: n.nx,
      downNy: n.ny,
      sentNx: n.nx,
      sentNy: n.ny,
      dragged: false,
      timer: 0,
      t0: Date.now()
    };
    send({ op: "down", nx: n.nx, ny: n.ny });
    if (debug) showLocal(n);
    e.preventDefault();
  });

  canvas.addEventListener("pointermove", function (e) {
    if (!finger || e.pointerId !== finger.id) return;
    var n = normFromEvent(e);
    if (!n) return;
    if (!finger.dragged) {
      if (pictureMovePx(n, { nx: finger.downNx, ny: finger.downNy }) < MOVE_EPS) return;
      finger.dragged = true;
    } else if (pictureMovePx(n, { nx: finger.sentNx, ny: finger.sentNy }) < MOVE_EPS) {
      return;
    }
    finger.nx = n.nx;
    finger.ny = n.ny;
    scheduleMove();
  });

  function endPointer(e) {
    if (!finger) return;
    if (e && e.pointerId != null && e.pointerId !== finger.id) return;
    var n = e ? normFromEvent(e) : null;
    if (n) releaseFinger(n.nx, n.ny);
    else releaseFinger(null, null);
  }

  canvas.addEventListener("pointerup", endPointer);
  canvas.addEventListener("pointercancel", endPointer);
  canvas.addEventListener("lostpointercapture", endPointer);
  canvas.addEventListener("contextmenu", function (e) { e.preventDefault(); });
  // Capture can miss a release outside the picture. A stuck down becomes a long press.
  window.addEventListener("pointerup", endPointer);
  window.addEventListener("pointercancel", endPointer);
  window.addEventListener("blur", function () { releaseFinger(null, null); });
  window.addEventListener("pagehide", function () { releaseFinger(null, null); });

  function shutdown(why) {
    releaseFinger(null, null);
    streaming = false;
    toggle.textContent = "开始";
    if (ws) {
      try { send({ op: "stop" }); } catch (e) {}
      try { ws.close(); } catch (e2) {}
      ws = null;
    }
    stopDecoder();
    ind.textContent = why || "已结束";
    syncKeyEnabled();
  }

  toggle.addEventListener("click", function () {
    if (ws && (ws.readyState === 0 || ws.readyState === 1)) {
      shutdown("已结束");
      return;
    }
    var proto = location.protocol === "https:" ? "wss:" : "ws:";
    ws = new WebSocket(proto + "//" + location.host + "/api/live/view");
    ws.binaryType = "arraybuffer";
    toggle.textContent = "结束";
    ind.textContent = "正在连接…";
    streaming = true;
    ws.onopen = function () {
      send({ op: "start" });
      sendFps();
      syncKeyEnabled();
    };
    ws.onmessage = function (ev) {
      if (typeof ev.data === "string") {
        try { onControl(JSON.parse(ev.data)); } catch (e) {}
        return;
      }
      onBinary(new Uint8Array(ev.data));
    };
    ws.onerror = function () {
      if (streaming) ind.textContent = "连接失败";
    };
    ws.onclose = function () {
      releaseFinger(null, null);
      streaming = false;
      toggle.textContent = "开始";
      stopDecoder();
      if (ind.textContent.indexOf("失败") < 0 && ind.textContent.indexOf("没有") < 0) ind.textContent = "已结束";
      ws = null;
      syncKeyEnabled();
    };
  });

  dbgBtn.addEventListener("click", function () {
    debug = !debug;
    dbgBtn.className = debug ? "btn primary" : "btn ghost";
    renderMap();
    if (debug && !streaming) redrawIdle();
  });

  var pinEl = document.getElementById("livePin");
  var pinState = (typeof LivePin !== "undefined") ? LivePin.DEFAULT : { x: 0.15, y: 0.58, w: 0.6, h: 0.36 };
  var pinDrag = null;

  function setPinVisible(on) {
    if (!pinEl) return;
    pinEl.hidden = !on;
    var b = document.querySelector('#liveKeys button[data-act="pinpad"]');
    if (b) b.setAttribute("aria-pressed", on ? "true" : "false");
  }

  function applyPin(r) {
    if (!pinEl || typeof LivePin === "undefined") return;
    pinState = LivePin.clamp(r);
    pinEl.style.left = (pinState.x * 100) + "%";
    pinEl.style.top = (pinState.y * 100) + "%";
    pinEl.style.width = (pinState.w * 100) + "%";
    pinEl.style.height = (pinState.h * 100) + "%";
  }

  function savePin() {
    if (typeof LivePin === "undefined") return;
    LivePin.save(window.localStorage, pinState);
  }

  function flashPin(btn) {
    btn.classList.add("lit");
    if (btn._lit) clearTimeout(btn._lit);
    btn._lit = setTimeout(function () {
      btn.classList.remove("lit");
      btn._lit = 0;
    }, 140);
  }

  function onPinKey(e) {
    if (e.button != null && e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    var btn = e.currentTarget;
    flashPin(btn);
    var box = btn.getBoundingClientRect();
    if (!(box.width > 0) || !(box.height > 0)) return;
    var n = normFromEvent({ clientX: box.left + box.width / 2, clientY: box.top + box.height / 2 });
    if (!n) return;
    if (finger) releaseFinger(finger.nx, finger.ny);
    var taps = LivePin.tapMessages(n.nx, n.ny);
    for (var ti = 0; ti < taps.length; ti++) send(taps[ti]);
    pinQuiet = Date.now() + 600;
    if (ws && ws.readyState === 1) send(LivePin.logMessage());
    else if (typeof addLog === "function") addLog(LivePin.LOG);
  }

  function beginPinDrag(e, mode) {
    if (!pinEl) return;
    var pic = pinEl.parentElement.getBoundingClientRect();
    if (!(pic.width > 0) || !(pic.height > 0)) return;
    pinDrag = {
      id: e.pointerId,
      mode: mode,
      x: pinState.x,
      y: pinState.y,
      w: pinState.w,
      h: pinState.h,
      px: e.clientX,
      py: e.clientY,
      pw: pic.width,
      ph: pic.height
    };
    try { pinEl.setPointerCapture(e.pointerId); } catch (err) {}
  }

  function movePinDrag(e) {
    if (!pinDrag || e.pointerId !== pinDrag.id) return;
    var dx = (e.clientX - pinDrag.px) / pinDrag.pw;
    var dy = (e.clientY - pinDrag.py) / pinDrag.ph;
    if (pinDrag.mode === "resize") applyPin({ x: pinDrag.x, y: pinDrag.y, w: pinDrag.w + dx, h: pinDrag.h + dy });
    else applyPin({ x: pinDrag.x + dx, y: pinDrag.y + dy, w: pinDrag.w, h: pinDrag.h });
  }

  function endPinDrag(e) {
    if (!pinDrag || e.pointerId !== pinDrag.id) return;
    pinDrag = null;
    savePin();
  }

  if (pinEl && typeof LivePin !== "undefined") {
    applyPin(LivePin.load(window.localStorage) || LivePin.DEFAULT);
    var pinKeys = pinEl.querySelectorAll(".live-pin-key");
    for (var pk = 0; pk < pinKeys.length; pk++) pinKeys[pk].addEventListener("pointerdown", onPinKey);
    var pinClose = pinEl.querySelector(".live-pin-x");
    if (pinClose) {
      pinClose.addEventListener("pointerdown", function (e) { e.stopPropagation(); });
      pinClose.addEventListener("click", function (e) {
        e.preventDefault();
        e.stopPropagation();
        setPinVisible(false);
      });
    }
    var pinResize = pinEl.querySelector(".live-pin-resize");
    if (pinResize) {
      pinResize.addEventListener("pointerdown", function (e) {
        if (e.button != null && e.button !== 0) return;
        e.preventDefault();
        e.stopPropagation();
        beginPinDrag(e, "resize");
      });
    }
    pinEl.addEventListener("pointerdown", function (e) {
      if (e.button != null && e.button !== 0) return;
      var t = e.target;
      if (t && t.closest && (t.closest(".live-pin-key") || t.closest(".live-pin-x") || t.closest(".live-pin-resize"))) return;
      e.preventDefault();
      beginPinDrag(e, "move");
    });
    pinEl.addEventListener("pointermove", movePinDrag);
    pinEl.addEventListener("pointerup", endPinDrag);
    pinEl.addEventListener("pointercancel", endPinDrag);
    pinEl.addEventListener("contextmenu", function (e) { e.preventDefault(); });
  }

  window.liveOnShow = function () {
    fitPicture();
    if (debug) redrawIdle();
    renderMap();
  };
})();

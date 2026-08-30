const DEFAULT_SCRIPT =
  'KeyPress "Home"\nDelay 1000\nTap 167,775\nDelay 2000\nKeyPress "Back"\nDelay 2000\nToast "脚本结束"\nDelay 1000\n';

const mem = globalThis.__tapspriteRooms || (globalThis.__tapspriteRooms = new Map());

function json(data, status) {
  return new Response(JSON.stringify(data), {
    status: status || 200,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "content-type",
      "access-control-allow-methods": "GET,POST,OPTIONS",
    },
  });
}

function emptyRoom() {
  return {
    logs: [],
    seq: 0,
    script: DEFAULT_SCRIPT,
    pending: [],
    running: false,
    loaded: false,
    step: "待命",
    a11y: false,
    phoneSeenAt: 0,
    updatedAt: Date.now(),
  };
}

function cacheKey(code) {
  return new Request("https://tapsprite.rooms/" + code);
}

async function loadRoom(code) {
  if (mem.has(code)) return mem.get(code);
  try {
    const hit = await caches.default.match(cacheKey(code));
    if (hit) {
      const data = await hit.json();
      mem.set(code, data);
      return data;
    }
  } catch (_) {}
  const fresh = emptyRoom();
  mem.set(code, fresh);
  return fresh;
}

async function saveRoom(code, data) {
  data.updatedAt = Date.now();
  mem.set(code, data);
  try {
    await caches.default.put(
      cacheKey(code),
      new Response(JSON.stringify(data), {
        headers: {
          "content-type": "application/json",
          "cache-control": "max-age=86400",
        },
      }),
    );
  } catch (_) {}
}

function linesAfter(room, after) {
  const n = Number(after) || 0;
  return room.logs.filter((l) => l.seq > n).slice(-200);
}

function snapshot(room, after) {
  return {
    ok: true,
    running: !!room.running,
    loaded: !!room.loaded,
    step: room.step || "待命",
    a11y: !!room.a11y,
    script: room.script || DEFAULT_SCRIPT,
    phoneSeenAt: room.phoneSeenAt || 0,
    lines: linesAfter(room, after),
  };
}

async function readBody(request) {
  const t = await request.text();
  if (!t) return {};
  try {
    return JSON.parse(t);
  } catch {
    return { raw: t };
  }
}

export async function onRequest(context) {
  const { request } = context;
  if (request.method === "OPTIONS") {
    return json({ ok: true });
  }

  const url = new URL(request.url);
  const parts = url.pathname.split("/").filter(Boolean);
  // /api/room/:code[/action]
  if (parts[0] !== "api") {
    return json({ error: "not found" }, 404);
  }
  if (parts[1] === "health") {
    return json({ ok: true });
  }
  if (parts[1] !== "room" || !parts[2]) {
    return json({ error: "缺少房间码" }, 400);
  }
  const code = String(parts[2]).toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (code.length < 4 || code.length > 8) {
    return json({ error: "房间码无效" }, 400);
  }
  const action = parts[3] || "";
  const after = url.searchParams.get("after") || "0";
  const room = await loadRoom(code);

  try {
    if (request.method === "GET" && action === "") {
      return json(snapshot(room, after));
    }
    if (request.method === "GET" && action === "logs") {
      return json({ lines: linesAfter(room, after) });
    }

    const body = request.method === "POST" ? await readBody(request) : {};

    if (request.method === "POST" && (action === "phone" || action === "sync")) {
      room.phoneSeenAt = Date.now();
      room.running = !!body.running;
      room.loaded = !!body.loaded;
      room.step = body.step || room.step;
      room.a11y = !!body.a11y;
      const incoming = Array.isArray(body.logs) ? body.logs : [];
      for (const line of incoming) {
        room.seq += 1;
        room.logs.push({
          seq: room.seq,
          t: Number(line.t) || Date.now(),
          msg: String(line.msg || "").slice(0, 500),
        });
      }
      while (room.logs.length > 400) room.logs.shift();
      const pending = room.pending.shift() || null;
      await saveRoom(code, room);
      return json({ ok: true, pending, ...snapshot(room, body.after || 0) });
    }

    if (request.method === "POST" && action === "script") {
      const script = String(body.script || body.raw || "");
      if (!script.trim()) return json({ ok: false, error: "脚本为空" }, 400);
      room.script = script;
      room.pending.push({ type: "script", script, run: !!body.run });
      room.seq += 1;
      room.logs.push({
        seq: room.seq,
        t: Date.now(),
        msg: body.run ? "电脑下发脚本并请求运行" : "电脑下发了新脚本",
      });
      await saveRoom(code, room);
      return json({ ok: true });
    }

    if (request.method === "POST" && action === "control") {
      const act = String(body.action || "").toLowerCase();
      if (act !== "start" && act !== "stop") {
        return json({ ok: false, error: "未知动作" }, 400);
      }
      room.pending.push({ type: "control", action: act });
      room.seq += 1;
      room.logs.push({
        seq: room.seq,
        t: Date.now(),
        msg: act === "start" ? "电脑请求开始" : "电脑请求停止",
      });
      await saveRoom(code, room);
      return json({ ok: true });
    }

    return json({ error: "not found" }, 404);
  } catch (e) {
    return json({ error: String(e && e.message ? e.message : e) }, 500);
  }
}

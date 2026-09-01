const DEFAULT_SCRIPT =
  'KeyPress "Home"\nDelay 1000\nTap 167,775\nDelay 2000\nKeyPress "Back"\nDelay 2000\nToast "脚本结束"\nDelay 1000\n';

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

function linesAfter(room, after) {
  const n = Number(after) || 0;
  return (room.logs || []).filter((l) => l.seq > n).slice(-200);
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

async function handleRoom(request, storage) {
  const url = new URL(request.url);
  const parts = url.pathname.split("/").filter(Boolean);
  const action = parts[3] || "";
  const after = url.searchParams.get("after") || "0";
  let room = (await storage.get("room")) || emptyRoom();

  if (request.method === "GET" && (action === "" || action === "logs")) {
    return json(action === "logs" ? { lines: linesAfter(room, after) } : snapshot(room, after));
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
    const pending = (room.pending || []).shift() || null;
    room.updatedAt = Date.now();
    await storage.put("room", room);
    return json({ ok: true, pending, ...snapshot(room, body.after || 0) });
  }

  if (request.method === "POST" && action === "script") {
    const script = String(body.script || body.raw || "");
    if (!script.trim()) return json({ ok: false, error: "脚本为空" }, 400);
    room.script = script;
    room.pending = room.pending || [];
    room.pending.push({ type: "script", script, run: !!body.run });
    room.seq += 1;
    room.logs.push({
      seq: room.seq,
      t: Date.now(),
      msg: body.run ? "电脑下发脚本并请求运行" : "电脑下发了新脚本",
    });
    room.updatedAt = Date.now();
    await storage.put("room", room);
    return json({ ok: true });
  }

  if (request.method === "POST" && action === "control") {
    const act = String(body.action || "").toLowerCase();
    if (act !== "start" && act !== "stop") {
      return json({ ok: false, error: "未知动作" }, 400);
    }
    room.pending = room.pending || [];
    room.pending.push({ type: "control", action: act });
    room.seq += 1;
    room.logs.push({
      seq: room.seq,
      t: Date.now(),
      msg: act === "start" ? "电脑请求开始" : "电脑请求停止",
    });
    room.updatedAt = Date.now();
    await storage.put("room", room);
    return json({ ok: true });
  }

  return json({ error: "not found" }, 404);
}

const PRESENCE_URL = "https://tapsprite.internal/presence-v1";

async function loadPresence() {
  let mem = globalThis.__tsDevs || {};
  let cached = {};
  try {
    const hit = await caches.default.match(PRESENCE_URL);
    if (hit) {
      const data = await hit.json();
      if (data && data.devices && typeof data.devices === "object") {
        cached = data.devices;
      }
    }
  } catch (e) {}
  const ids = new Set([].concat(Object.keys(mem), Object.keys(cached)));
  const out = {};
  ids.forEach((id) => {
    out[id] = pickDev(mem[id], cached[id]);
  });
  globalThis.__tsDevs = out;
  return out;
}

function pickDev(a, b) {
  if (!a) return b;
  if (!b) return a;
  const ga = Number(a.gen) || 0;
  const gb = Number(b.gen) || 0;
  if (ga !== gb) return ga > gb ? a : b;
  if (a.online === false) return a;
  if (b.online === false) return b;
  return (Number(a.seen) || 0) >= (Number(b.seen) || 0) ? a : b;
}

async function savePresence(devices) {
  globalThis.__tsDevs = devices;
  try {
    await caches.default.delete(PRESENCE_URL);
    await caches.default.put(
      PRESENCE_URL,
      new Response(JSON.stringify({ devices }), {
        headers: {
          "content-type": "application/json",
          "cache-control": "max-age=3",
        },
      })
    );
  } catch (e) {}
}

async function handlePresence(request) {
  if (request.method === "GET") {
    const devices = await loadPresence();
    const now = Date.now();
    const out = [];
    for (const id of Object.keys(devices)) {
      const d = devices[id];
      if (!d || d.online === false) continue;
      if (now - (Number(d.seen) || 0) > 8000) continue;
      out.push({
        id,
        name: d.name || id,
        a11y: !!d.a11y,
        emu: !!d.emu,
        seen: d.seen || 0,
      });
    }
    return json({ ok: true, devices: out });
  }
  if (request.method === "POST") {
    const body = await readBody(request);
    const id = String(body.id || "").trim();
    if (!id || id === "旧版") return json({ ok: false, error: "缺少 id" }, 400);
    const devices = await loadPresence();
    if (body.online === false) {
      const gen = Number(body.gen) || Date.now();
      const prev = devices[id];
      if (prev && Number(prev.gen) > gen) {
        return json({ ok: true, ignored: true });
      }
      devices[id] = {
        id,
        name: prev && prev.name ? prev.name : String(body.name || id).slice(0, 40),
        online: false,
        seen: Date.now(),
        gen,
      };
    } else {
      const gen = Number(body.gen) || Date.now();
      const prev = devices[id];
      if (prev && Number(prev.gen) > gen) {
        return json({ ok: true, ignored: true });
      }
      devices[id] = {
        id,
        name: String(body.name || id).slice(0, 40),
        a11y: !!body.a11y,
        emu: !!body.emu,
        online: true,
        seen: Date.now(),
        gen,
      };
    }
    await savePresence(devices);
    return json({ ok: true });
  }
  return json({ error: "method" }, 405);
}

export class RoomDO {
  constructor(state, env) {
    this.state = state;
  }

  async fetch(request) {
    return handleRoom(request, this.state.storage);
  }
}

const mem = globalThis.__tapspriteRooms || (globalThis.__tapspriteRooms = new Map());

function memStorage(code) {
  return {
    async get() {
      return mem.get(code) || null;
    },
    async put(_k, room) {
      mem.set(code, room);
    },
  };
}

export async function onRequest(context) {
  const { request, env } = context;
  if (request.method === "OPTIONS") {
    return json({ ok: true });
  }

  const url = new URL(request.url);
  const parts = url.pathname.split("/").filter(Boolean);
  if (parts[0] !== "api") {
    return json({ error: "not found" }, 404);
  }
  if (parts[1] === "health") {
    return json({ ok: true, do: !!(env && env.ROOMS) });
  }
  if (parts[1] === "presence") {
    return handlePresence(request);
  }
  if (parts[1] !== "room" || !parts[2]) {
    return json({ error: "缺少房间码" }, 400);
  }
  const code = String(parts[2]).toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (code.length < 4 || code.length > 8) {
    return json({ error: "房间码无效" }, 400);
  }

  try {
    if (env && env.ROOMS) {
      const id = env.ROOMS.idFromName(code);
      return env.ROOMS.get(id).fetch(request);
    }
    return handleRoom(request, memStorage(code));
  } catch (e) {
    return json({ error: String(e && e.message ? e.message : e) }, 500);
  }
}

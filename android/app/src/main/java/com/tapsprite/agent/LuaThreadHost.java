package com.tapsprite.agent;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.UpValue;
import org.luaj.vm2.Varargs;

/**
 * 按键精灵-style Thread.Start / Stop / SetShareVar / GetShareVar.
 *
 * True parallel: each Start gets its own LuaJ {@link Globals} and a Java Thread.
 * There is no global bytecode lock. Delay on one thread does not stall others.
 * Ordinary Lua globals are NOT shared; use ShareVar for number/string/bool/nil.
 * TracePrint/Tip go to the same App+PC log sink. Touch/screenshot serialize in
 * {@link DeviceGate}.
 */
final class LuaThreadHost {
    private static final int MAX_WORKERS = 32;
    private static final ConcurrentHashMap<String, Object> SHARE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();
    private static volatile Session session;

    private LuaThreadHost() {
    }

    static final class Worker {
        final int id;
        final AtomicBoolean stop = new AtomicBoolean(false);
        volatile Thread javaThread;

        Worker(int id) {
            this.id = id;
        }
    }

    static final class Session {
        final AtomicInteger nextId = new AtomicInteger(1);
        final ConcurrentHashMap<Integer, Worker> workers = new ConcurrentHashMap<>();
    }

    /** Snapshot of a Lua closure so a child Globals can rebind it without sharing state. */
    private static final class FnSnap {
        final LuaValue key;
        final Prototype proto;
        final LuaValue[] ups;

        FnSnap(LuaValue key, LuaClosure fn) {
            this.key = key;
            this.proto = fn.p;
            this.ups = snapshotUps(fn);
        }
    }

    static Session begin(Globals globals) {
        if (globals == null) {
            throw new LuaError("Thread: 无运行环境");
        }
        Session prev = session;
        if (prev != null) {
            stopSession(prev);
        }
        SHARE.clear();
        Session s = new Session();
        session = s;
        CURRENT.remove();
        return s;
    }

    static void end() {
        end(session);
    }

    static void end(Session mine) {
        if (mine == null) {
            return;
        }
        stopSession(mine);
        joinWorkers(mine, 2500L);
        if (session == mine) {
            session = null;
        }
        CURRENT.remove();
    }

    static void requestStopAll() {
        Session s = session;
        if (s == null) {
            return;
        }
        stopSession(s);
    }

    private static void stopSession(Session s) {
        if (s == null) {
            return;
        }
        for (Worker w : s.workers.values()) {
            w.stop.set(true);
            Thread t = w.javaThread;
            if (t != null && t != Thread.currentThread()) {
                t.interrupt();
            }
        }
    }

    static boolean isCurrentStopped() {
        Worker w = CURRENT.get();
        return w != null && w.stop.get();
    }

    static int start(Globals caller, Varargs varargs) {
        Session s = session;
        if (s == null) {
            throw new LuaError("Thread.Start: 脚本未在运行");
        }
        LuaValue a1 = varargs.arg(1);
        if (a1.isnil()) {
            throw new LuaError("Thread.Start: 需要函数或函数名");
        }
        LuaValue fn;
        if (a1.isfunction()) {
            fn = a1;
        } else {
            String name = a1.tojstring();
            fn = caller.get(name);
            if (fn == null || !fn.isfunction()) {
                throw new LuaError("Thread.Start: 找不到函数 " + name);
            }
        }
        if (!fn.isclosure()) {
            throw new LuaError("Thread.Start: 需要 Lua 函数");
        }
        if (s.workers.size() >= MAX_WORKERS) {
            throw new LuaError("Thread.Start: 线程过多");
        }
        LuaClosure orig = fn.checkclosure();
        final FnSnap started = new FnSnap(LuaValue.NIL, orig);
        final ArrayList<FnSnap> siblings = snapshotSiblings(caller);
        final LuaValue[] extra = copyArgs(varargs);
        final Worker w = new Worker(s.nextId.getAndIncrement());
        s.workers.put(Integer.valueOf(w.id), w);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                CURRENT.set(w);
                try {
                    if (w.stop.get() || ScriptEngine.isStopRequested()) {
                        return;
                    }
                    Globals child = LuaEngine.newGlobals();
                    for (int i = 0; i < siblings.size(); i++) {
                        FnSnap snap = siblings.get(i);
                        child.set(snap.key, rebind(snap, child));
                    }
                    LuaClosure run = rebind(started, child);
                    Varargs args = extra.length == 0 ? LuaValue.NONE : LuaValue.varargsOf(extra);
                    run.invoke(args);
                } catch (LuaError e) {
                    if (!isQuietStop(e)) {
                        AppState.log("线程 " + w.id + "：" + e.getMessage());
                    }
                } catch (Throwable e) {
                    if (!w.stop.get() && !ScriptEngine.isStopRequested()) {
                        AppState.log("线程 " + w.id + "：" + e.getMessage());
                    }
                } finally {
                    CURRENT.remove();
                    Session cur = session;
                    if (cur != null) {
                        cur.workers.remove(Integer.valueOf(w.id));
                    }
                }
            }
        }, "tapsprite-lua-" + w.id);
        w.javaThread = t;
        t.start();
        return w.id;
    }

    static boolean stop(int id) {
        Session s = session;
        if (s == null) {
            return false;
        }
        Worker w = s.workers.get(Integer.valueOf(id));
        if (w == null) {
            return false;
        }
        w.stop.set(true);
        Thread t = w.javaThread;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
        }
        return true;
    }

    static void setShareVar(LuaValue nameVal, LuaValue value) {
        String name = shareName(nameVal);
        if (value == null || value.isnil()) {
            SHARE.remove(name);
            return;
        }
        int t = value.type();
        if (t == LuaValue.TBOOLEAN) {
            SHARE.put(name, Boolean.valueOf(value.toboolean()));
            return;
        }
        if (t == LuaValue.TNUMBER) {
            if (value.isinttype()) {
                SHARE.put(name, Long.valueOf(value.tolong()));
            } else {
                SHARE.put(name, Double.valueOf(value.todouble()));
            }
            return;
        }
        if (t == LuaValue.TSTRING) {
            SHARE.put(name, value.tojstring());
            return;
        }
        throw new LuaError("Thread.SetShareVar: 仅支持 number/string/bool/nil");
    }

    static LuaValue getShareVar(LuaValue nameVal) {
        if (nameVal == null || nameVal.isnil()) {
            return LuaValue.NIL;
        }
        String name = nameVal.tojstring();
        if (name == null || name.length() == 0) {
            return LuaValue.NIL;
        }
        Object o = SHARE.get(name);
        if (o == null) {
            return LuaValue.NIL;
        }
        if (o instanceof Boolean) {
            return LuaValue.valueOf(((Boolean) o).booleanValue());
        }
        if (o instanceof Integer) {
            return LuaValue.valueOf(((Integer) o).intValue());
        }
        if (o instanceof Long) {
            long n = ((Long) o).longValue();
            if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) {
                return LuaValue.valueOf((int) n);
            }
            return LuaValue.valueOf((double) n);
        }
        if (o instanceof Double) {
            return LuaValue.valueOf(((Double) o).doubleValue());
        }
        if (o instanceof String) {
            return LuaValue.valueOf((String) o);
        }
        if (o instanceof Float) {
            return LuaValue.valueOf(((Float) o).doubleValue());
        }
        return LuaValue.NIL;
    }

    private static String shareName(LuaValue nameVal) {
        if (nameVal == null || nameVal.isnil()) {
            throw new LuaError("Thread.SetShareVar: 需要变量名");
        }
        String name = nameVal.tojstring();
        if (name == null || name.length() == 0) {
            throw new LuaError("Thread.SetShareVar: 需要变量名");
        }
        return name;
    }

    private static ArrayList<FnSnap> snapshotSiblings(Globals caller) {
        ArrayList<FnSnap> out = new ArrayList<>();
        LuaValue k = LuaValue.NIL;
        while (true) {
            Varargs n = caller.next(k);
            k = n.arg1();
            if (k.isnil()) {
                break;
            }
            LuaValue v = n.arg(2);
            if (v.isclosure()) {
                out.add(new FnSnap(k, v.checkclosure()));
            }
        }
        return out;
    }

    private static LuaValue[] snapshotUps(LuaClosure orig) {
        UpValue[] src = orig.upValues;
        if (src == null || src.length == 0) {
            return new LuaValue[0];
        }
        LuaValue[] out = new LuaValue[src.length];
        for (int i = 0; i < src.length; i++) {
            UpValue u = src[i];
            out[i] = u == null ? LuaValue.NIL : copyValue(u.getValue(), false);
        }
        return out;
    }

    private static LuaClosure rebind(FnSnap snap, Globals child) {
        LuaClosure n = new LuaClosure(snap.proto, child);
        if (n.upValues == null || snap.ups.length == 0) {
            return n;
        }
        int len = Math.min(n.upValues.length, snap.ups.length);
        for (int i = 1; i < len; i++) {
            n.upValues[i] = new UpValue(new LuaValue[] { snap.ups[i] }, 0);
        }
        return n;
    }

    private static LuaValue[] copyArgs(Varargs varargs) {
        int n = Math.max(0, varargs.narg() - 1);
        LuaValue[] extra = new LuaValue[n];
        for (int i = 0; i < extra.length; i++) {
            extra[i] = copyValue(varargs.arg(i + 2), true);
        }
        return extra;
    }

    /** Primitive snapshot. Tables/functions become nil (or error if required). */
    private static LuaValue copyValue(LuaValue v, boolean required) {
        if (v == null || v.isnil()) {
            return LuaValue.NIL;
        }
        int t = v.type();
        if (t == LuaValue.TBOOLEAN) {
            return LuaValue.valueOf(v.toboolean());
        }
        if (t == LuaValue.TNUMBER) {
            if (v.isinttype()) {
                return LuaValue.valueOf(v.toint());
            }
            return LuaValue.valueOf(v.todouble());
        }
        if (t == LuaValue.TSTRING) {
            return LuaValue.valueOf(v.tojstring());
        }
        if (required) {
            throw new LuaError("Thread.Start: 参数仅支持 number/string/bool/nil");
        }
        return LuaValue.NIL;
    }

    private static boolean isQuietStop(LuaError e) {
        if (isCurrentStopped() || ScriptEngine.isStopRequested()) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("脚本已停止") || msg.contains("线程已停止"));
    }

    private static void joinWorkers(Session s, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ArrayList<Worker> list = new ArrayList<>(s.workers.values());
        for (Worker w : list) {
            Thread t = w.javaThread;
            if (t == null || t == Thread.currentThread() || !t.isAlive()) {
                continue;
            }
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                t.interrupt();
                continue;
            }
            try {
                t.join(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                t.interrupt();
            }
            if (t.isAlive()) {
                t.interrupt();
            }
        }
    }
}

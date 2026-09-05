package com.tapsprite.agent;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * 按键精灵-style Thread.Start / Thread.Stop.
 *
 * LuaJ 3 Globals is not thread-safe. Pattern: one Java Thread per worker, a
 * per-Java-thread LuaThread as {@code globals.running}, and a fair ReentrantLock
 * around Lua bytecode. Delay/sleep drop the lock so main and children interleave.
 * Java bindings (Tap, TracePrint, …) stay on the shared Globals and log to the
 * same AppState / PC trace as the main script.
 */
final class LuaThreadHost {
    private static final int MAX_WORKERS = 32;
    private static final ReentrantLock VM = new ReentrantLock(true);
    private static final ThreadLocal<LuaThread> LUA = new ThreadLocal<>();
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
        final Globals globals;
        final AtomicInteger nextId = new AtomicInteger(1);
        final ConcurrentHashMap<Integer, Worker> workers = new ConcurrentHashMap<>();

        Session(Globals globals) {
            this.globals = globals;
        }
    }

    static void begin(Globals globals) {
        session = new Session(globals);
        LUA.remove();
        CURRENT.remove();
    }

    static void end() {
        requestStopAll();
        Session s = session;
        if (s != null) {
            joinWorkers(s, 2500L);
        }
        session = null;
        LUA.remove();
        CURRENT.remove();
    }

    static void requestStopAll() {
        Session s = session;
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

    static void enterVm() {
        try {
            VM.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LuaError("脚本已停止");
        }
        Session s = session;
        if (s == null) {
            return;
        }
        LuaThread mine = LUA.get();
        if (mine == null) {
            mine = new LuaThread(s.globals);
            LUA.set(mine);
        }
        s.globals.running = mine;
    }

    static void leaveVm() {
        if (VM.isHeldByCurrentThread()) {
            VM.unlock();
        }
    }

    static boolean isHeldByCurrentThread() {
        return VM.isHeldByCurrentThread();
    }

    static void unlocked(Runnable r) {
        boolean held = isHeldByCurrentThread();
        if (held) {
            leaveVm();
        }
        try {
            r.run();
        } finally {
            if (held) {
                enterVm();
            }
        }
    }

    static int start(Globals globals, Varargs varargs) {
        Session s = session;
        if (s == null || s.globals != globals) {
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
            fn = globals.get(name);
            if (fn == null || !fn.isfunction()) {
                throw new LuaError("Thread.Start: 找不到函数 " + name);
            }
        }
        if (s.workers.size() >= MAX_WORKERS) {
            throw new LuaError("Thread.Start: 线程过多");
        }
        int n = varargs.narg();
        LuaValue[] extra = new LuaValue[Math.max(0, n - 1)];
        for (int i = 0; i < extra.length; i++) {
            extra[i] = varargs.arg(i + 2);
        }
        final Varargs args = extra.length == 0 ? LuaValue.NONE : LuaValue.varargsOf(extra);
        final Worker w = new Worker(s.nextId.getAndIncrement());
        s.workers.put(Integer.valueOf(w.id), w);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                CURRENT.set(w);
                try {
                    enterVm();
                    try {
                        if (w.stop.get() || ScriptEngine.isStopRequested()) {
                            return;
                        }
                        fn.invoke(args);
                    } finally {
                        leaveVm();
                    }
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
                    LUA.remove();
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

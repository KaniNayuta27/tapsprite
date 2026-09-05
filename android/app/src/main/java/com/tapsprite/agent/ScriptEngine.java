package com.tapsprite.agent;

import android.os.Looper;
import android.os.PowerManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.luaj.vm2.LuaError;

/**
 * Two lanes:
 * <ul>
 *   <li>ConsoleSession — single slot, replace-on-start, overlay bubble, AppState.script.</li>
 *   <li>LibrarySessions — map by id, concurrent, isolated Globals; never touch console slot or bubble.</li>
 * </ul>
 */
public final class ScriptEngine {
    static final class Session {
        final String id;
        final boolean library;
        final AtomicBoolean stop = new AtomicBoolean(false);
        volatile Thread worker;
        volatile int runGen;
        volatile LuaThreadHost.Session threadHost;
        volatile boolean running;

        Session(String id, boolean library) {
            this.id = id;
            this.library = library;
        }
    }

    private static final Session console = new Session("console", false);
    private static final ConcurrentHashMap<String, Session> library = new ConcurrentHashMap<>();
    private static final InheritableThreadLocal<Session> CURRENT = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<Integer> CURRENT_GEN = new InheritableThreadLocal<>();

    private ScriptEngine() {
    }

    public static boolean start() {
        return start(AppState.script);
    }

    /** Console lane: stop-old-then-start. Does not touch library sessions. */
    public static synchronized boolean start(final String src) {
        synchronized (ScriptEngine.class) {
            return launch(console, src == null ? "" : src, true);
        }
    }

    /**
     * Library lane: start this id if not already running. Never writes AppState.script
     * and never refreshes the overlay bubble.
     */
    public static synchronized boolean startLibrary(String id, String src) {
        synchronized (ScriptEngine.class) {
            final String name = normalizeLibId(id);
            Session existing = library.get(name);
            if (existing != null && existing.running && !existing.stop.get()
                    && existing.worker != null && existing.worker.isAlive()) {
                AppState.log("脚本库已在运行 " + name);
                return true;
            }
            Session s = new Session(name, true);
            library.put(name, s);
            return launch(s, src == null ? "" : src, false);
        }
    }

    public static synchronized void stopLibrary(String id) {
        synchronized (ScriptEngine.class) {
            if (id == null || id.trim().length() == 0) {
                return;
            }
            Session s = library.get(normalizeLibId(id));
            if (s == null) {
                return;
            }
            requestStopSession(s);
        }
        LanLink.pushHello();
    }

    /** Stop every library session. Does not touch the console lane. */
    public static synchronized void stopAllLibrary() {
        synchronized (ScriptEngine.class) {
            for (Session s : library.values()) {
                requestStopSession(s);
            }
            AppState.log("脚本库全部停止");
        }
        LanLink.pushHello();
    }

    static List<String> libraryRunningIds() {
        ArrayList<String> out = new ArrayList<>();
        for (Session s : library.values()) {
            if (s != null && s.running && s.worker != null && s.worker.isAlive()) {
                out.add(s.id);
            }
        }
        Collections.sort(out);
        return out;
    }

    static String libraryJsonArray() {
        List<String> ids = libraryRunningIds();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ConsoleServer.jsonStr(ids.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    static String normalizeLibId(String id) {
        if (id == null) {
            return "library.lua";
        }
        String name = id.trim();
        if (name.length() == 0) {
            return "library.lua";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.length() == 0 || ".".equals(name) || "..".equals(name)) {
            return "library.lua";
        }
        return name;
    }

    static void attachThreadHost(LuaThreadHost.Session host) {
        Session s = CURRENT.get();
        if (s != null) {
            s.threadHost = host;
        }
    }

    /** Caller must hold ScriptEngine.class. */
    private static boolean launch(final Session s, final String str, boolean consoleLane) {
        Thread prev = s.worker;
        if (consoleLane) {
            if (s.running || (prev != null && prev.isAlive())) {
                stopRunningLocked(s, "停止旧脚本，运行新脚本 " + str.length() + " 字");
                if (s.running) {
                    AppState.log("无法运行：脚本仍在运行");
                    return false;
                }
            }
        } else if (s.running && prev != null && prev.isAlive()) {
            AppState.log("脚本库已在运行 " + s.id);
            return true;
        } else if (prev != null && prev.isAlive()) {
            stopRunningLocked(s, "停止旧脚本库 " + s.id);
        }
        if (AppState.auto == null) {
            AppState.log("无障碍未连接，本次用 input。点「需重开」只尝试打开，不会再关掉服务。");
        } else {
            AppState.log("无障碍已连接");
        }
        if (consoleLane) {
            AppState.log("准备运行 " + str.length() + " 字  开头：" + str.replace("\n", " ").trim().substring(0, Math.min(40, str.trim().length())));
        } else {
            AppState.log("脚本库运行 " + s.id + " " + str.length() + " 字");
        }
        s.stop.set(false);
        s.running = true;
        if (consoleLane) {
            AppState.running = true;
        }
        final int gen = ++s.runGen;
        final Session session = s;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                CURRENT.set(session);
                CURRENT_GEN.set(Integer.valueOf(gen));
                try {
                    ScriptEngine.runLua(session, LuaPrep.toLua(str), gen);
                } finally {
                    CURRENT.remove();
                    CURRENT_GEN.remove();
                }
            }
        }, consoleLane ? "tapsprite-script" : ("tapsprite-lib-" + s.id));
        s.worker = t;
        t.start();
        if (consoleLane) {
            OverlayService overlayService = AppState.overlay;
            if (overlayService != null) {
                overlayService.refreshBubble();
            }
        } else {
            LanLink.pushHello();
        }
        return true;
    }

    /** Caller must hold ScriptEngine.class. */
    private static void stopRunningLocked(Session s, String why) {
        if (why != null && why.length() > 0) {
            AppState.log(why);
        }
        requestStopSession(s);
        Thread thread = s.worker;
        if (thread != null && thread != Thread.currentThread() && thread.isAlive()) {
            long waitMs = 800L;
            try {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    waitMs = 200L;
                }
            } catch (Throwable t) {
                waitMs = 400L;
            }
            try {
                thread.join(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (thread != null && thread.isAlive()) {
            AppState.log(s.library ? ("旧脚本库线程未结束 " + s.id) : "旧脚本线程未结束，仍启动新脚本");
        }
        s.runGen++;
        s.running = false;
        s.worker = null;
        if (!s.library) {
            AppState.running = false;
            AppState.currentStep = "已停止";
        }
    }

    private static void requestStopSession(Session s) {
        if (s == null) {
            return;
        }
        s.stop.set(true);
        Thread thread = s.worker;
        if (thread != null) {
            thread.interrupt();
        }
        LuaThreadHost.stopSession(s.threadHost);
    }

    /** Console lane only. Overlay bubble / App stop / sendRun replace use this. */
    public static synchronized void requestStop() {
        synchronized (ScriptEngine.class) {
            requestStopSession(console);
        }
    }

    /** Stop the session that owns the calling Lua thread (ExitScript). */
    public static void requestStopCurrent() {
        Session s = CURRENT.get();
        if (s == null || !s.library) {
            requestStop();
            return;
        }
        stopLibrary(s.id);
    }

    public static boolean isStopRequested() {
        Session s = CURRENT.get();
        if (s != null) {
            Integer g = CURRENT_GEN.get();
            if (g != null && g.intValue() != s.runGen) {
                return true;
            }
            if (s.stop.get()) {
                return true;
            }
        }
        return LuaThreadHost.isCurrentStopped();
    }

    private static void runSteps(List<ScriptParser.Step> list) {
        OverlayService overlayService;
        String str;
        PowerManager.WakeLock wakeLock = null;
        try {
            try {
                PowerManager powerManager = (PowerManager) App.ctx.getSystemService("power");
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(1, "tapsprite:script");
                    wakeLock.setReferenceCounted(false);
                    wakeLock.acquire(300000L);
                }
                AppState.log("脚本开始，共 " + list.size() + " 步");
                AutoService autoService = AppState.auto;
                int i = 0;
                while (i < list.size()) {
                    if (console.stop.get()) {
                        AppState.currentStep = "已停止";
                        AppState.log("脚本被停止");
                        AppState.running = false;
                        if (wakeLock != null && wakeLock.isHeld()) {
                            try {
                                wakeLock.release();
                            } catch (Exception e) {
                            }
                        }
                        OverlayService overlayService2 = AppState.overlay;
                        if (overlayService2 != null) {
                            overlayService2.refreshBubble();
                            return;
                        }
                        return;
                    }
                    ScriptParser.Step step = list.get(i);
                    String describe = describe(step);
                    AppState.currentStep = describe;
                    i++;
                    AppState.log("第 " + i + " 步 · " + describe);
                    OverlayService overlayService3 = AppState.overlay;
                    if (overlayService3 != null) {
                        overlayService3.refreshBubble();
                    }
                    switch (AnonymousClass2.$SwitchMap$com$tapsprite$agent$ScriptParser$Kind[step.kind.ordinal()]) {
                        case 1:
                            Sprite.keyPress(step.key);
                            sleep(180L);
                            break;
                        case 2:
                            sleep(step.delayMs);
                            break;
                        case 3:
                            if (!Sprite.tap(step.x, step.y)) {
                                AppState.log("点击失败 (" + ((int) step.x) + "," + ((int) step.y) + ")");
                            }
                            sleep(80L);
                            break;
                        case 4:
                            if (step.text != null && step.text.length() != 0) {
                                str = step.text;
                                Sprite.tip(str);
                                break;
                            }
                            str = "提示";
                            Sprite.tip(str);
                            break;
                        case 5:
                            AppState.log(step.text);
                            break;
                        case 6:
                            try {
                                Sprite.run(step.cmd, step.args);
                                break;
                            } catch (IllegalArgumentException e2) {
                                AppState.log("第 " + step.lineNo + " 行：" + e2.getMessage());
                                AppState.running = false;
                                if (wakeLock != null && wakeLock.isHeld()) {
                                    try {
                                        wakeLock.release();
                                    } catch (Exception e3) {
                                    }
                                }
                                OverlayService overlayService4 = AppState.overlay;
                                if (overlayService4 != null) {
                                    overlayService4.refreshBubble();
                                    return;
                                }
                                return;
                            }
                    }
                }
                if (!console.stop.get()) {
                    AppState.currentStep = "已完成";
                    AppState.log("脚本结束");
                }
                AppState.running = false;
                if (wakeLock != null && wakeLock.isHeld()) {
                    try {
                        wakeLock.release();
                    } catch (Exception e4) {
                    }
                }
                overlayService = AppState.overlay;
                if (overlayService == null) {
                    return;
                }
            } finally {
            }
        } catch (InterruptedException e5) {
            AppState.currentStep = "已停止";
            AppState.log("脚本被停止");
            Thread.currentThread().interrupt();
            AppState.running = false;
            if (0 != 0 && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception e6) {
                }
            }
            overlayService = AppState.overlay;
            if (overlayService == null) {
                return;
            }
        } catch (Exception e7) {
            AppState.currentStep = "出错";
            AppState.log("运行出错：" + e7.getMessage());
            AppState.running = false;
            if (0 != 0 && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception e8) {
                }
            }
            overlayService = AppState.overlay;
            if (overlayService == null) {
                return;
            }
        }
        overlayService.refreshBubble();
    }

    /* renamed from: com.tapsprite.agent.ScriptEngine$2, reason: invalid class name */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$tapsprite$agent$ScriptParser$Kind;

        static {
            int[] iArr = new int[ScriptParser.Kind.values().length];
            $SwitchMap$com$tapsprite$agent$ScriptParser$Kind = iArr;
            try {
                iArr[ScriptParser.Kind.KEY.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[ScriptParser.Kind.DELAY.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[ScriptParser.Kind.TAP.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                iArr[ScriptParser.Kind.TOAST.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                iArr[ScriptParser.Kind.PRINT.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                iArr[ScriptParser.Kind.CALL.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    private static boolean isCurrent(Session s, int gen) {
        return s != null && s.runGen == gen;
    }

    private static void finishRun(Session s, int gen) {
        if (!isCurrent(s, gen)) {
            return;
        }
        s.running = false;
        if (s.library) {
            library.remove(s.id, s);
            LanLink.pushHello();
            return;
        }
        AppState.running = false;
        OverlayService overlayService = AppState.overlay;
        if (overlayService != null) {
            overlayService.refreshBubble();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void runLua(Session s, String str, int gen) {
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager powerManager = (PowerManager) App.ctx.getSystemService("power");
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(1, s.library ? "tapsprite:lib" : "tapsprite:lua");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(600000L);
            }
            if (isCurrent(s, gen)) {
                if (s.library) {
                    AppState.log("脚本库 " + s.id + " 开始");
                } else {
                    AppState.currentStep = "Lua 运行中";
                    AppState.log("Lua 脚本开始");
                }
            }
            LuaEngine.run(str);
            if (isCurrent(s, gen) && !s.stop.get()) {
                if (s.library) {
                    AppState.log("脚本库 " + s.id + " 结束");
                } else {
                    AppState.currentStep = "已完成";
                    AppState.log("脚本结束");
                }
            }
        } catch (LuaError e2) {
            if (isCurrent(s, gen)) {
                if (s.stop.get()) {
                    if (s.library) {
                        AppState.log("脚本库 " + s.id + " 已停止");
                    } else {
                        AppState.currentStep = "已停止";
                        AppState.log("脚本被停止");
                    }
                } else if (s.library) {
                    AppState.log("脚本库 " + s.id + " Lua 错误：" + e2.getMessage());
                } else {
                    AppState.currentStep = "出错";
                    AppState.log("Lua 错误：" + e2.getMessage());
                }
            }
        } catch (Exception e4) {
            if (isCurrent(s, gen)) {
                if (s.library) {
                    AppState.log("脚本库 " + s.id + " 运行出错：" + e4.getMessage());
                } else {
                    AppState.currentStep = "出错";
                    AppState.log("运行出错：" + e4.getMessage());
                }
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception e) {
                }
            }
            finishRun(s, gen);
        }
    }

    static void sleepMs(long j) throws InterruptedException {
        sleep(j);
    }

    private static void sleep(long j) throws InterruptedException {
        while (j > 0) {
            if (isStopRequested()) {
                throw new InterruptedException();
            }
            long min = Math.min(j, 120L);
            Thread.sleep(min);
            j -= min;
        }
    }

    private static String describe(ScriptParser.Step step) {
        switch (AnonymousClass2.$SwitchMap$com$tapsprite$agent$ScriptParser$Kind[step.kind.ordinal()]) {
            case 1:
                if ("home".equals(step.key)) {
                    return "按下 Home 键";
                }
                if ("back".equals(step.key)) {
                    return "按下后退键";
                }
                if ("recents".equals(step.key) || "recent".equals(step.key)) {
                    return "按下最近任务键";
                }
                return "按键 " + step.key;
            case 2:
                return "等待 " + step.delayMs + "ms";
            case 3:
                return "点击 (" + ((int) step.x) + ", " + ((int) step.y) + ")";
            case 4:
                return "弹窗显示「" + step.text + "」";
            case 5:
                return step.text;
            case 6:
                return step.cmd;
            default:
                return step.raw;
        }
    }
}

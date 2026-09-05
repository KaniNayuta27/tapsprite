package com.tapsprite.agent;

import android.os.Looper;
import android.os.PowerManager;
import com.tapsprite.agent.ScriptParser;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.luaj.vm2.LuaError;

/* loaded from: classes.dex */
public final class ScriptEngine {
    private static final AtomicBoolean stop = new AtomicBoolean(false);
    private static final ThreadLocal<Integer> CURRENT_GEN = new ThreadLocal<>();
    private static volatile Thread worker;
    private static volatile int runGen;

    private ScriptEngine() {
    }

    public static boolean start() {
        return start(AppState.script);
    }

    public static synchronized boolean start(final String src) {
        synchronized (ScriptEngine.class) {
            final String str = src == null ? "" : src;
            Thread prev = worker;
            if (AppState.running || (prev != null && prev.isAlive())) {
                stopRunningLocked("停止旧脚本，运行新脚本 " + str.length() + " 字");
                if (AppState.running) {
                    AppState.log("无法运行：脚本仍在运行");
                    return false;
                }
            }
            if (AppState.auto == null) {
                AppState.log("无障碍未连接，本次用 input。点「需重开」只尝试打开，不会再关掉服务。");
            } else {
                AppState.log("无障碍已连接");
            }
            AppState.log("准备运行 " + str.length() + " 字  开头：" + str.replace("\n", " ").trim().substring(0, Math.min(40, str.trim().length())));
            stop.set(false);
            AppState.running = true;
            final int gen = ++runGen;
            worker = new Thread(new Runnable() { // from class: com.tapsprite.agent.ScriptEngine.1
                @Override // java.lang.Runnable
                public void run() {
                    CURRENT_GEN.set(Integer.valueOf(gen));
                    try {
                        ScriptEngine.runLua(LuaPrep.toLua(str), gen);
                    } finally {
                        CURRENT_GEN.remove();
                    }
                }
            }, "tapsprite-script");
            worker.start();
            OverlayService overlayService = AppState.overlay;
            if (overlayService != null) {
                overlayService.refreshBubble();
            }
            return true;
        }
    }

    /** Caller must hold ScriptEngine.class. */
    private static void stopRunningLocked(String why) {
        if (why != null && why.length() > 0) {
            AppState.log(why);
        }
        stop.set(true);
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
        LuaThreadHost.requestStopAll();
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
            AppState.log("旧脚本线程未结束，仍启动新脚本");
        }
        runGen++;
        AppState.running = false;
        worker = null;
        AppState.currentStep = "已停止";
    }

    public static synchronized void requestStop() {
        synchronized (ScriptEngine.class) {
            stop.set(true);
            Thread thread = worker;
            if (thread != null) {
                thread.interrupt();
            }
            LuaThreadHost.requestStopAll();
        }
    }

    public static boolean isStopRequested() {
        Integer g = CURRENT_GEN.get();
        if (g != null && g.intValue() != runGen) {
            return true;
        }
        return stop.get() || LuaThreadHost.isCurrentStopped();
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
                    if (stop.get()) {
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
                if (!stop.get()) {
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
                $SwitchMap$com$tapsprite$agent$ScriptParser$Kind[ScriptParser.Kind.DELAY.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$tapsprite$agent$ScriptParser$Kind[ScriptParser.Kind.TAP.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$tapsprite$agent$ScriptParser$Kind[ScriptParser.Kind.TOAST.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$tapsprite$agent$ScriptParser$Kind[ScriptParser.Kind.PRINT.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$tapsprite$agent$ScriptParser$Kind[ScriptParser.Kind.CALL.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    private static boolean isCurrent(int gen) {
        return runGen == gen;
    }

    private static void finishRun(int gen) {
        if (!isCurrent(gen)) {
            return;
        }
        AppState.running = false;
        OverlayService overlayService = AppState.overlay;
        if (overlayService != null) {
            overlayService.refreshBubble();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void runLua(String str, int gen) {
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager powerManager = (PowerManager) App.ctx.getSystemService("power");
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(1, "tapsprite:lua");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(600000L);
            }
            if (isCurrent(gen)) {
                AppState.currentStep = "Lua 运行中";
                AppState.log("Lua 脚本开始");
            }
            LuaEngine.run(str);
            if (isCurrent(gen) && !stop.get()) {
                AppState.currentStep = "已完成";
                AppState.log("脚本结束");
            }
        } catch (LuaError e2) {
            if (isCurrent(gen)) {
                if (stop.get()) {
                    AppState.currentStep = "已停止";
                    AppState.log("脚本被停止");
                } else {
                    AppState.currentStep = "出错";
                    AppState.log("Lua 错误：" + e2.getMessage());
                }
            }
        } catch (Exception e4) {
            if (isCurrent(gen)) {
                AppState.currentStep = "出错";
                AppState.log("运行出错：" + e4.getMessage());
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception e) {
                }
            }
            finishRun(gen);
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

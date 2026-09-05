package com.tapsprite.agent;

import com.tapsprite.agent.ElementApi;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/* loaded from: classes.dex */
public final class LuaEngine {
    private LuaEngine() {
    }

    public static boolean looksLikeLua(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase();
        return lowerCase.contains("function ") || lowerCase.contains("\nfunction") || lowerCase.contains("local ") || lowerCase.contains(" then") || lowerCase.contains("elseif") || lowerCase.contains("end\n") || lowerCase.contains("\nend") || lowerCase.contains(" for ") || lowerCase.contains(" while ") || lowerCase.contains("~=") || lowerCase.contains("..") || lowerCase.contains("fw.") || lowerCase.contains("--");
    }

    public static void run(String str) {
        Globals standardGlobals = newGlobals();
        LuaThreadHost.Session host = LuaThreadHost.begin(standardGlobals);
        try {
            standardGlobals.load(str, "script").call();
        } catch (LuaError e) {
            AppState.log("Lua 错误：" + e.getMessage());
            throw e;
        } finally {
            LuaThreadHost.end(host);
        }
    }

    /** Fresh LuaJ Globals with TapSprite APIs. Each Thread.Start uses its own copy. */
    static Globals newGlobals() {
        Globals g = JsePlatform.standardGlobals();
        bind(g);
        g.set("print", g.get("TracePrint"));
        installStopHook(g);
        return g;
    }

    /** Count hook so tight loops still honor stop / generation change. */
    private static void installStopHook(Globals g) {
        try {
            g.load(new org.luaj.vm2.lib.DebugLib());
            LuaValue dbg = g.get("debug");
            if (dbg == null || dbg.isnil()) {
                return;
            }
            dbg.get("sethook").invoke(LuaValue.varargsOf(new LuaValue[] {
                new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        LuaEngine.checkStop();
                        return NIL;
                    }
                },
                LuaValue.valueOf(""),
                LuaValue.valueOf(500)
            }));
        } catch (Throwable ignored) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void checkStop() {
        if (ScriptEngine.isStopRequested()) {
            throw new LuaError("脚本已停止");
        }
    }

    private static void bind(Globals globals) {
        globals.set("Tap", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.1
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                Sprite.tap(luaValue.tofloat(), luaValue2.tofloat());
                return TRUE;
            }
        });
        globals.set("Click", globals.get("Tap"));
        globals.set("Tap2", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.2
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                Sprite.tap2(luaValue.tofloat(), luaValue2.tofloat());
                return TRUE;
            }
        });
        globals.set("LongClick", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.3
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                Sprite.longClick(luaValue.tofloat(), luaValue2.tofloat());
                return TRUE;
            }
        });
        globals.set("Touch", new ThreeArgFunction() { // from class: com.tapsprite.agent.LuaEngine.4
            @Override // org.luaj.vm2.lib.ThreeArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2, LuaValue luaValue3) {
                LuaEngine.checkStop();
                Sprite.touch(luaValue.tofloat(), luaValue2.tofloat(), luaValue3.isnil() ? 1000 : luaValue3.toint());
                return TRUE;
            }
        });
        globals.set("Swipe", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.5
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                Sprite.swipe(varargs.arg(1).tofloat(), varargs.arg(2).tofloat(), varargs.arg(3).tofloat(), varargs.arg(4).tofloat(), varargs.optint(5, 300));
                return TRUE;
            }
        });
        globals.set("Delay", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.6
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                try {
                    Sprite.delay(luaValue.tolong());
                    LuaEngine.checkStop();
                    return TRUE;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LuaError("脚本已停止");
                }
            }
        });
        globals.set("Sleep", globals.get("Delay"));
        globals.set("KeyPress", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.7
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                Sprite.keyPress(luaValue.tojstring());
                return TRUE;
            }
        });
        globals.set("TouchDown", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.8
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                Sprite.touchDown(luaValue.tofloat(), luaValue2.tofloat());
                return TRUE;
            }
        });
        globals.set("TouchMove", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.9
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                Sprite.touchMove(varargs.arg(1).tofloat(), varargs.arg(2).tofloat(), varargs.narg() >= 4 ? varargs.optint(4, 50) : varargs.optint(3, 50));
                return TRUE;
            }
        });
        globals.set("TouchUp", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.10
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                Sprite.touchUp(luaValue.optint(1));
                return TRUE;
            }
        });
        globals.set("RandomTap", new ThreeArgFunction() { // from class: com.tapsprite.agent.LuaEngine.11
            @Override // org.luaj.vm2.lib.ThreeArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2, LuaValue luaValue3) {
                LuaEngine.checkStop();
                ExtraApi.randomTap(luaValue.toint(), luaValue2.toint(), luaValue3.optint(12));
                return TRUE;
            }
        });
        globals.set("RandomsTap", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.RandomsTap
            @Override
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                ExtraApi.randomsTap(varargs.arg(1).toint(), varargs.arg(2).toint(),
                        varargs.optint(3, 5), varargs.optint(4, 1));
                return TRUE;
            }
        });
        globals.set("MoveZoomOut", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.MoveZoomOut
            @Override
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                return ExtraApi.moveZoomOut(varargs.arg(1).tofloat(), varargs.arg(2).tofloat(),
                        varargs.arg(3).tofloat(), varargs.arg(4).tofloat(), varargs.optint(5, 300))
                        ? TRUE : FALSE;
            }
        });
        globals.set("MoveZoomIn", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.MoveZoomIn
            @Override
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                return ExtraApi.moveZoomIn(varargs.arg(1).tofloat(), varargs.arg(2).tofloat(),
                        varargs.arg(3).tofloat(), varargs.arg(4).tofloat(), varargs.optint(5, 300))
                        ? TRUE : FALSE;
            }
        });
        globals.set("OpenUrl", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.OpenUrl
            @Override
            public LuaValue call(LuaValue luaValue) {
                return ExtraApi.openUrl(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("GetColorNum", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.GetColorNum
            @Override
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                return valueOf(ScreenApi.getColorNum(
                        varargs.arg(1).toint(), varargs.arg(2).toint(),
                        varargs.arg(3).toint(), varargs.arg(4).toint(),
                        varargs.optjstring(5, "000000"),
                        (float) varargs.optdouble(6, 0.9d)));
            }
        });
        globals.set("GetPixelColor", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.12
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                return valueOf(Sprite.getPixelColor(luaValue.toint(), luaValue2.toint()));
            }
        });
        TwoArgFunction getPixelColorA11yFn = new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.GetPixelColorA11y
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                return valueOf(Sprite.getPixelColorA11y(luaValue.toint(), luaValue2.toint()));
            }
        };
        globals.set("GetPixelColorA11y", getPixelColorA11yFn);
        globals.set("GetColorA11y", getPixelColorA11yFn);
        globals.set("FindColor", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.13
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                float[] dirSim = LuaEngine.dirSim(varargs.optdouble(6, 0.0d), varargs.optdouble(7, 0.0d));
                synchronized (DeviceGate.LOCK) {
                    if (!Sprite.findColor(varargs.arg(1).toint(), varargs.arg(2).toint(), varargs.arg(3).toint(), varargs.arg(4).toint(), varargs.optjstring(5, "000000"), dirSim[0], (int) dirSim[1])) {
                        return varargsOf(valueOf(-1), valueOf(-1));
                    }
                    return varargsOf(valueOf(Sprite.intXY.x), valueOf(Sprite.intXY.y));
                }
            }
        });
        globals.set("FindMultiColor", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.14
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                float[] dirSim = LuaEngine.dirSim(varargs.optdouble(7, 0.0d), varargs.optdouble(8, 0.0d));
                synchronized (DeviceGate.LOCK) {
                    if (!Sprite.findMultiColor(varargs.arg(1).toint(), varargs.arg(2).toint(), varargs.arg(3).toint(), varargs.arg(4).toint(), varargs.optjstring(5, "000000"), varargs.optjstring(6, ""), dirSim[0], (int) dirSim[1])) {
                        return varargsOf(valueOf(-1), valueOf(-1));
                    }
                    return varargsOf(valueOf(Sprite.intXY.x), valueOf(Sprite.intXY.y));
                }
            }
        });
        globals.set("CmpColor", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.15
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                synchronized (DeviceGate.LOCK) {
                    return ScreenApi.cmpColor(varargs.arg(1).toint(), varargs.arg(2).toint(), varargs.optjstring(3, "000000"), (float) varargs.optdouble(4, 0.0d)) ? TRUE : FALSE;
                }
            }
        });
        globals.set("CmpColorEx", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.16
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                return valueOf(Sprite.cmpColorEx(luaValue.tojstring(), luaValue2.tofloat()) ? 1 : 0);
            }
        });
        globals.set("KeepScreen", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.17
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                Sprite.keepScreen(luaValue.toboolean());
                return TRUE;
            }
        });
        globals.set("KeepCapture", globals.get("KeepScreen"));
        globals.set("SnapShot", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.18
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                synchronized (DeviceGate.LOCK) {
                    String optjstring = varargs.optjstring(1, "");
                    if (optjstring.length() == 0) {
                        return valueOf(ScreenApi.snapShot());
                    }
                    return valueOf(ScreenApi.snapShotTo(optjstring));
                }
            }
        });
        globals.set("A11yShot", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.19
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                LuaEngine.checkStop();
                return Sprite.a11yShot() ? TRUE : FALSE;
            }
        });
        globals.set("FindPic", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.20
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                // 兼容：FindPic(x1,y1,x2,y2,pic[,sim])
                // 或按键精灵风格：FindPic(..., pic, delta, dir, sim) —— 取最后的 0~1 小数为相似度
                float sim = 0.75f;
                if (varargs.narg() >= 8) {
                    sim = (float) varargs.optdouble(8, 0.75d);
                } else if (varargs.narg() >= 6) {
                    double a6 = varargs.optdouble(6, 0.75d);
                    if (a6 > 0.0d && a6 <= 1.0001d) {
                        sim = (float) a6;
                    } else if (varargs.narg() >= 7) {
                        double a7 = varargs.optdouble(7, 0.75d);
                        if (a7 > 0.0d && a7 <= 1.0001d) {
                            sim = (float) a7;
                        }
                    }
                }
                synchronized (DeviceGate.LOCK) {
                    if (!ScreenApi.findPic(varargs.arg(1).toint(), varargs.arg(2).toint(), varargs.arg(3).toint(), varargs.arg(4).toint(), varargs.optjstring(5, ""), sim)) {
                        return varargsOf(valueOf(-1), valueOf(-1));
                    }
                    return varargsOf(valueOf(Sprite.intXY.x), valueOf(Sprite.intXY.y));
                }
            }
        });
        globals.set("WaitColor", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.21
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                if (!ExtraApi.waitColor(varargs.arg(1).toint(), varargs.arg(2).toint(), varargs.arg(3).toint(), varargs.arg(4).toint(), varargs.optjstring(5, "000000"), varargs.optint(6, 5000), (float) varargs.optdouble(7, 0.0d))) {
                    return varargsOf(valueOf(-1), valueOf(-1));
                }
                synchronized (DeviceGate.LOCK) {
                    return varargsOf(valueOf(Sprite.intXY.x), valueOf(Sprite.intXY.y));
                }
            }
        });
        globals.set("TracePrint", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.22
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                Sprite.tracePrint(luaValue.isnil() ? "" : luaValue.tojstring());
                return NIL;
            }
        });
        globals.set("Tip", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.23
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                Sprite.tip(luaValue.isnil() ? "" : luaValue.tojstring());
                return TRUE;
            }
        });
        globals.set("Toast", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.24
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                Sprite.tip(luaValue.isnil() ? "" : luaValue.tojstring());
                return TRUE;
            }
        });
        globals.set("ExitScript", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.25
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                Sprite.exitScript();
                throw new LuaError("脚本已停止");
            }
        });
        globals.set("IsRoot", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.26
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(Sprite.isRoot() ? 1 : 0);
            }
        });
        globals.set("DelayS", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.27
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                try {
                    Sprite.delay((long) (luaValue.todouble() * 1000.0d));
                    LuaEngine.checkStop();
                    return TRUE;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LuaError("脚本已停止");
                }
            }
        });
        globals.set("GetScreenX", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.28
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(Sprite.getScreenX());
            }
        });
        globals.set("GetScreenY", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.29
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(Sprite.getScreenY());
            }
        });
        globals.set("GetColorDep", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.30
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(ExtraApi.colorDep());
            }
        });
        globals.set("GetDeviceID", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.31
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(ExtraApi.deviceId());
            }
        });
        globals.set("Play", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.32
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return ExtraApi.play(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("KillApp", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.33
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return ExtraApi.killApp(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("PutAttachment", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.34
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(ExtraApi.putAttachment(luaValue.tojstring()));
            }
        });
        globals.set("TickCount", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.35
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(Sprite.tickCount());
            }
        });
        globals.set("KeepAwake", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.36
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                DeviceApi.keepAwake(luaValue.toboolean());
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.applyKeepScreenFlag();
                }
                return TRUE;
            }
        });
        globals.set("SetScreenAlwaysOn", globals.get("KeepAwake"));
        globals.set("Vibrate", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.37
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                DeviceApi.vibrate(luaValue.optint(40));
                return TRUE;
            }
        });
        globals.set("Rnd", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.38
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(DeviceApi.rnd());
            }
        });
        globals.set("Randomize", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.39
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                DeviceApi.randomize();
                return TRUE;
            }
        });
        globals.set("ReadUIConfig", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.40
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                String str = luaValue.tojstring();
                if (str.startsWith("多选框")) {
                    return ConfigApi.readBool(str, luaValue2.toboolean()) ? TRUE : FALSE;
                }
                if (!str.startsWith("下拉框")) {
                    return valueOf(ConfigApi.read(str, luaValue2.isnil() ? "" : luaValue2.tojstring()));
                }
                int i = (luaValue2.isnumber() || luaValue2.isint()) ? luaValue2.toint() : 0;
                try {
                    if (luaValue2.isstring() && !luaValue2.isnil()) {
                        i = Integer.parseInt(luaValue2.tojstring().trim());
                    }
                } catch (Exception e) {
                }
                return valueOf(ConfigApi.readInt(str, i));
            }
        });
        globals.set("WriteUIConfig", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.41
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                ConfigApi.write(luaValue.tojstring(), luaValue2.tojstring());
                return TRUE;
            }
        });
        globals.set("InputText", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.42
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                return Sprite.inputText(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("ClickText", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.43
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                return ElementApi.clickText(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("GetClip", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.44
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(ExtraApi.getClip());
            }
        });
        globals.set("SetClip", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.45
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                ExtraApi.setClip(luaValue.tojstring());
                return TRUE;
            }
        });
        globals.set("RunApp", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.46
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return ExtraApi.runApp(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("GetBattery", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.47
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(ExtraApi.battery());
            }
        });
        globals.set("DrawCircle", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.48
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                Sprite.drawCircle(varargs.arg(1).toint(), varargs.arg(2).toint(), varargs.arg(3).toint(), varargs.arg(4).toint(), varargs.optint(5, 400), varargs.optint(6, 1));
                return TRUE;
            }
        });
        globals.set("OcrText", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.49
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                return valueOf(Sprite.ocrText(varargs.arg(1).toint(), varargs.arg(2).toint(), varargs.arg(3).toint(), varargs.arg(4).toint()));
            }
        });
        globals.set("FileRead", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.50
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(FileApi.read(luaValue.tojstring()));
            }
        });
        globals.set("FileWrite", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.51
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                FileApi.write(luaValue.tojstring(), luaValue2.tojstring());
                return TRUE;
            }
        });
        globals.set("RGB", new ThreeArgFunction() { // from class: com.tapsprite.agent.LuaEngine.52
            @Override // org.luaj.vm2.lib.ThreeArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2, LuaValue luaValue3) {
                return valueOf(String.format("%02X%02X%02X", Integer.valueOf(luaValue.toint()), Integer.valueOf(luaValue2.toint()), Integer.valueOf(luaValue3.toint())));
            }
        });
        globals.set("ColorDiff", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.53
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                return valueOf(ExtraApi.colorDiff(luaValue.tojstring(), luaValue2.tojstring()));
            }
        });
        globals.set("Left", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.54
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                String str = luaValue.tojstring();
                return valueOf(str.substring(0, Math.max(0, Math.min(luaValue2.toint(), str.length()))));
            }
        });
        globals.set("Right", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.55
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                String str = luaValue.tojstring();
                return valueOf(str.substring(str.length() - Math.max(0, Math.min(luaValue2.toint(), str.length()))));
            }
        });
        globals.set("Mid", new ThreeArgFunction() { // from class: com.tapsprite.agent.LuaEngine.56
            @Override // org.luaj.vm2.lib.ThreeArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2, LuaValue luaValue3) {
                String str = luaValue.tojstring();
                int max = Math.max(1, luaValue2.toint()) - 1;
                if (max >= str.length()) {
                    return valueOf("");
                }
                int i = luaValue3.toint();
                return valueOf(str.substring(max, i < 0 ? str.length() : Math.min(str.length(), i + max)));
            }
        });
        globals.set("Len", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.57
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(luaValue.tojstring().length());
            }
        });
        globals.set("InStr", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.58
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                return valueOf(luaValue.tojstring().indexOf(luaValue2.tojstring()) + 1);
            }
        });
        globals.set("Replace", new ThreeArgFunction() { // from class: com.tapsprite.agent.LuaEngine.59
            @Override // org.luaj.vm2.lib.ThreeArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2, LuaValue luaValue3) {
                return valueOf(luaValue.tojstring().replace(luaValue2.tojstring(), luaValue3.tojstring()));
            }
        });
        globals.set("Trim", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.60
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(luaValue.tojstring().trim());
            }
        });
        globals.set("UCase", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.61
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(luaValue.tojstring().toUpperCase());
            }
        });
        globals.set("LCase", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.62
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(luaValue.tojstring().toLowerCase());
            }
        });
        globals.set("CInt", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.63
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                try {
                    return valueOf(Integer.parseInt(luaValue.tojstring().trim()));
                } catch (Exception e) {
                    return valueOf(0);
                }
            }
        });
        globals.set("CDbl", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.64
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                try {
                    return valueOf(Double.parseDouble(luaValue.tojstring().trim()));
                } catch (Exception e) {
                    return valueOf(0);
                }
            }
        });
        globals.set("Int", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.65
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf((int) Math.floor(luaValue.todouble()));
            }
        });
        globals.set("Now", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.66
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                return valueOf(new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US).format(new Date()));
            }
        });
        globals.set("IsNull", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.67
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return (luaValue.isnil() || luaValue.tojstring().length() == 0) ? TRUE : FALSE;
            }
        });
        globals.set("UBound", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.68
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(luaValue.length());
            }
        });
        globals.set("Cos", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.69
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(Math.cos(luaValue.todouble()));
            }
        });
        globals.set("Sin", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.70
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return valueOf(Math.sin(luaValue.todouble()));
            }
        });
        globals.set("tip", globals.get("Tip"));
        bindFw(globals);
        bindDialog(globals);
        bindSys(globals);
        bindElement(globals);
        bindImage(globals);
        bindUtf8(globals);
        bindUrl(globals);
        bindDir(globals);
        bindThread(globals);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float[] dirSim(double d, double d2) {
        boolean z = d >= 0.0d && d <= 4.0d && Math.abs(d - ((double) Math.round(d))) < 1.0E-6d;
        boolean z2 = d2 > 0.0d && d2 <= 1.0001d;
        if (z && z2) {
            return new float[]{(float) d2, Math.round(d)};
        }
        return new float[]{(float) d, Math.round(d2)};
    }

    private static void bindFw(Globals globals) {
        LuaTable luaTable = new LuaTable();
        luaTable.set("NewFWindow", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.71
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                FwApi.newFWindow(varargs.optjstring(1, "win"), varargs.optint(2, 0), varargs.optint(3, 0), varargs.optint(4, 200), varargs.optint(5, 80));
                return TRUE;
            }
        });
        luaTable.set("SetBackColor", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.72
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                FwApi.setBackColor(luaValue.tojstring(), luaValue2.tojstring());
                return TRUE;
            }
        });
        luaTable.set("Opacity", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.73
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                FwApi.opacity(luaValue.tojstring(), luaValue2.toint());
                return TRUE;
            }
        });
        luaTable.set("AddTextView", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.74
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                FwApi.addTextView(varargs.optjstring(1, ""), varargs.optjstring(2, ""), varargs.optjstring(3, ""), varargs.optint(4, 0), varargs.optint(5, 0), varargs.optint(6, 200), varargs.optint(7, 40));
                return TRUE;
            }
        });
        luaTable.set("SetTextColor", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.75
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                FwApi.setTextColor(luaValue.tojstring(), luaValue2.tojstring());
                return TRUE;
            }
        });
        luaTable.set("SetTextSize", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.76
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                FwApi.setTextSize(luaValue.tojstring(), luaValue2.toint());
                return TRUE;
            }
        });
        luaTable.set("SetText", new TwoArgFunction() { // from class: com.tapsprite.agent.LuaEngine.77
            @Override // org.luaj.vm2.lib.TwoArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                FwApi.setText(luaValue.tojstring(), luaValue2.tojstring());
                return TRUE;
            }
        });
        luaTable.set("Show", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.78
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                FwApi.show(luaValue.tojstring());
                return TRUE;
            }
        });
        luaTable.set("Hide", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.79
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                FwApi.hide(luaValue.tojstring());
                return TRUE;
            }
        });
        luaTable.set("Close", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.80
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                FwApi.close(luaValue.tojstring());
                return TRUE;
            }
        });
        globals.set("FW", luaTable);
        globals.set("Fw", luaTable);
    }

    private static void bindDialog(Globals globals) {
        LuaTable luaTable = new LuaTable();
        luaTable.set("InputBox", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.81
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                OverlayService overlayService = AppState.overlay;
                String optjstring = overlayService == null ? varargs.optjstring(2, "") : overlayService.prompt(varargs.optjstring(1, "输入"), varargs.optjstring(2, ""));
                return valueOf(optjstring != null ? optjstring : "");
            }
        });
        globals.set("Dialog", luaTable);
    }

    private static void bindSys(Globals globals) {
        LuaTable luaTable = new LuaTable();
        luaTable.set("AppIsFront", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.82
            @Override // org.luaj.vm2.lib.OneArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call(LuaValue luaValue) {
                return ExtraApi.appIsFront(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("Sys", luaTable);
    }

    private static void bindElement(Globals globals) {
        LuaTable luaTable = new LuaTable();
        luaTable.set("GetAll", new ZeroArgFunction() { // from class: com.tapsprite.agent.LuaEngine.83
            @Override // org.luaj.vm2.lib.ZeroArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public LuaValue call() {
                List<ElementApi.Node> all = ElementApi.getAll();
                LuaTable luaTable2 = new LuaTable();
                int i = 0;
                while (i < all.size()) {
                    ElementApi.Node node = all.get(i);
                    LuaTable luaTable3 = new LuaTable();
                    luaTable3.set("text", valueOf(node.text == null ? "" : node.text));
                    luaTable3.set("desc", valueOf(node.desc == null ? "" : node.desc));
                    luaTable3.set("id", valueOf(node.id == null ? "" : node.id));
                    luaTable3.set("cls", valueOf(node.cls == null ? "" : node.cls));
                    luaTable3.set("clickable", valueOf(node.clickable));
                    LuaTable bounds = new LuaTable();
                    bounds.set("left", valueOf(node.left));
                    bounds.set("top", valueOf(node.top));
                    bounds.set("right", valueOf(node.right));
                    bounds.set("bottom", valueOf(node.bottom));
                    luaTable3.set("bounds", bounds);
                    // 摘要字符串，便于 TracePrint
                    luaTable3.set("boundsStr", valueOf(node.left + "," + node.top + "," + node.right + "," + node.bottom));
                    i++;
                    luaTable2.set(i, luaTable3);
                }
                return luaTable2;
            }
        });
        luaTable.set("Click", new OneArgFunction() { // from class: com.tapsprite.agent.LuaEngine.ElementClick
            @Override
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                return ElementApi.clickText(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("Element", luaTable);
    }

    private static void bindImage(Globals globals) {
        LuaTable luaTable = new LuaTable();
        luaTable.set("OcrText", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.84
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                return valueOf(Sprite.ocrText(varargs.arg(1).toint(), varargs.arg(2).toint(), varargs.arg(3).toint(), varargs.arg(4).toint()));
            }
        });
        globals.set("Image", luaTable);
    }

    private static void bindUtf8(Globals globals) {
        LuaTable luaTable = new LuaTable();
        luaTable.set("InStr", new VarArgFunction() { // from class: com.tapsprite.agent.LuaEngine.85
            @Override // org.luaj.vm2.lib.VarArgFunction, org.luaj.vm2.lib.LibFunction, org.luaj.vm2.LuaValue
            public Varargs invoke(Varargs varargs) {
                int indexOf = varargs.optjstring(2, "").indexOf(varargs.optjstring(3, ""), Math.max(1, varargs.optint(1, 1)) - 1);
                return valueOf(indexOf < 0 ? 0 : indexOf + 1);
            }
        });
        globals.set("UTF8", luaTable);
    }

    private static void bindUrl(Globals globals) {
        LuaTable t = new LuaTable();
        t.set("HttpGet", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                return valueOf(UrlApi.httpGet(luaValue.tojstring()));
            }
        });
        t.set("HttpPost", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                return valueOf(UrlApi.httpPost(luaValue.tojstring(), luaValue2.isnil() ? "" : luaValue2.tojstring()));
            }
        });
        t.set("Download", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                return UrlApi.download(luaValue.tojstring(), luaValue2.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("Url", t);
        globals.set("url", t);
    }

    private static void bindDir(Globals globals) {
        LuaTable t = new LuaTable();
        t.set("Exist", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue) {
                return DirApi.exist(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        t.set("Create", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue) {
                return DirApi.create(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        t.set("Delete", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue) {
                return DirApi.delete(luaValue.tojstring()) ? TRUE : FALSE;
            }
        });
        globals.set("dir", t);
        globals.set("Dir", t);
    }

    private static void bindThread(final Globals globals) {
        LuaTable t = new LuaTable();
        t.set("Start", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs varargs) {
                LuaEngine.checkStop();
                return valueOf(LuaThreadHost.start(globals, varargs));
            }
        });
        t.set("Stop", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue) {
                if (luaValue.isnil()) {
                    return FALSE;
                }
                return LuaThreadHost.stop(luaValue.toint()) ? TRUE : FALSE;
            }
        });
        t.set("SetShareVar", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue, LuaValue luaValue2) {
                LuaEngine.checkStop();
                LuaThreadHost.setShareVar(luaValue, luaValue2);
                return TRUE;
            }
        });
        t.set("GetShareVar", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue luaValue) {
                LuaEngine.checkStop();
                return LuaThreadHost.getShareVar(luaValue);
            }
        });
        globals.set("Thread", t);
        globals.set("thread", t);
    }
}

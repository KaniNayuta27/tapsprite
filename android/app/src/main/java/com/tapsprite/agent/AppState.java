package com.tapsprite.agent;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* loaded from: classes.dex */
public final class AppState {
    public static final String CLOUD = "https://tapsprite.pages.dev";
    public static final int PORT = 18765;
    public static volatile AutoService auto;
    public static volatile OverlayService overlay;
    public static volatile ConsoleServer server;
    public static String PET_SCRIPT = "";
    public static String BASIC_SCRIPT = "";
    public static volatile int scriptTab = 0;
    public static final String DEFAULT_SCRIPT = "KeyPress(\"Home\")\nDelay(1000)\nTap(167, 775)\nDelay(2000)\nKeyPress(\"Back\")\nDelay(2000)\n";
    public static volatile String script = DEFAULT_SCRIPT;
    public static volatile String pcScript = "";
    public static volatile String deviceId = "";
    public static volatile String deviceName = "";
    public static volatile boolean debugToPc = true;
    public static volatile boolean keepAwake = true;
    public static volatile boolean loaded = false;
    public static volatile boolean running = false;
    public static volatile String currentStep = "待命";
    static final Object logLock = new Object();
    static final ArrayList<LogLine> logs = new ArrayList<>();
    static int seq = 0;

    public static synchronized void ensureServer() {
        synchronized (AppState.class) {
            if (server == null) {
                ConsoleServer consoleServer = new ConsoleServer();
                server = consoleServer;
                consoleServer.start();
            }
            if (debugToPc) {
                LanLink.hello();
            }
        }
    }

    public static synchronized void stopServer() {
        synchronized (AppState.class) {
            LanLink.bye();
        }
    }

    private AppState() {
    }

    static void ensureDevice() {
        try {
            SharedPreferences sharedPreferences = App.ctx.getSharedPreferences("tapsprite", 0);
            String string = sharedPreferences.getString("deviceId", "");
            if (string == null || string.length() < 6) {
                string = newCode();
                sharedPreferences.edit().putString("deviceId", string).apply();
            }
            deviceId = string;
            deviceName = isEmulator() ? "模拟器" : Build.MODEL;
            if (deviceName == null || deviceName.length() == 0) {
                deviceName = "手机";
            }
            if (string.length() >= 4) {
                deviceName += " " + string.substring(string.length() - 4);
            }
            Log.i("tapsprite", "device " + deviceId + " " + deviceName);
            log("本机设备 " + deviceName);
        } catch (Exception e) {
            deviceId = "DEV";
            deviceName = "手机";
        }
    }

    static boolean isEmulator() {
        String lowerCase = (Build.FINGERPRINT + " " + Build.MODEL + " " + Build.PRODUCT + " " + Build.HARDWARE + " " + Build.MANUFACTURER + " " + Build.BRAND + " " + Build.CPU_ABI).toLowerCase();
        if (lowerCase.contains("generic") || lowerCase.contains("emulator") || lowerCase.contains("sdk") || lowerCase.contains("ranchu") || lowerCase.contains("goldfish") || lowerCase.contains("vbox") || lowerCase.contains("ttvm") || lowerCase.contains("nox") || lowerCase.contains("mumu") || lowerCase.contains("leidian") || lowerCase.contains("ldplayer") || lowerCase.contains("changwan") || lowerCase.contains("virtual") || lowerCase.contains("x86")) {
            return true;
        }
        try {
            return new File("/system/bin/ldinit").exists();
        } catch (Exception e) {
            return false;
        }
    }

    static void init() {
        PET_SCRIPT = FileApi.readAsset("scripts/pet.lua");
        String readAsset = FileApi.readAsset("scripts/basic.lua");
        BASIC_SCRIPT = readAsset;
        int length = readAsset.length();
        String str = DEFAULT_SCRIPT;
        if (length == 0) {
            BASIC_SCRIPT = DEFAULT_SCRIPT;
        }
        try {
            scriptTab = App.ctx.getSharedPreferences("tapsprite", 0).getInt("scriptTab", 0);
        } catch (Exception e) {
        }
        if (scriptTab == 1) {
            str = BASIC_SCRIPT;
        } else if (PET_SCRIPT.length() > 0) {
            str = PET_SCRIPT;
        }
        script = str;
        ensureDevice();
        try {
            debugToPc = App.ctx.getSharedPreferences("tapsprite", 0).getBoolean("debugToPc", true);
        } catch (Exception e2) {
        }
        ConfigApi.initDefaults();
        try {
            keepAwake = App.ctx.getSharedPreferences("tapsprite", 0).getBoolean("keepAwake", true);
        } catch (Exception e3) {
        }
    }

    public static void selectTab(int i) {
        scriptTab = i;
        if (i == 1) {
            script = BASIC_SCRIPT;
        } else {
            String str = DEFAULT_SCRIPT;
            if (i == 2) {
                if (pcScript.length() > 0) {
                    str = pcScript;
                }
                script = str;
            } else {
                if (PET_SCRIPT.length() > 0) {
                    str = PET_SCRIPT;
                }
                script = str;
                scriptTab = 0;
            }
        }
        try {
            App.ctx.getSharedPreferences("tapsprite", 0).edit().putInt("scriptTab", scriptTab).apply();
        } catch (Exception e) {
        }
        currentStep = scriptTab == 1 ? "基础测试" : scriptTab == 2 ? "电脑下发" : "宠物脚本";
        log("当前脚本：" + currentStep);
    }

    public static String withLineNumbers(String str) {
        if (str == null) {
            str = "";
        }
        String[] split = str.split("\n", -1);
        int length = String.valueOf(Math.max(1, split.length)).length();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < split.length) {
            if (i > 0) {
                sb.append('\n');
            }
            int i2 = i + 1;
            String valueOf = String.valueOf(i2);
            while (valueOf.length() < length) {
                valueOf = " " + valueOf;
            }
            sb.append(valueOf).append("  ").append(split[i]);
            i = i2;
        }
        return sb.toString();
    }

    public static void log(String str) {
        synchronized (logLock) {
            int i = seq + 1;
            seq = i;
            logs.add(new LogLine(i, System.currentTimeMillis(), str));
            while (true) {
                ArrayList<LogLine> arrayList = logs;
                if (arrayList.size() <= 400) {
                    break;
                } else {
                    arrayList.remove(0);
                }
            }
        }
        Log.i("TapSprite", str);
        OverlayService overlayService = overlay;
        if (overlayService != null) {
            overlayService.onStatus(currentStep, str);
        }
    }

    public static List<LogLine> logsAfter(int i) {
        ArrayList arrayList = new ArrayList();
        synchronized (logLock) {
            int i2 = 0;
            while (true) {
                ArrayList<LogLine> arrayList2 = logs;
                if (i2 < arrayList2.size()) {
                    LogLine logLine = arrayList2.get(i2);
                    if (logLine.seq > i) {
                        arrayList.add(logLine);
                    }
                    i2++;
                } else {
                    break;
                }
            }
        }
        return arrayList;
    }

    public static void clearLogs() {
        synchronized (logLock) {
            logs.clear();
        }
    }

    static String newCode() {
        Random random = new Random();
        char[] cArr = new char[6];
        for (int i = 0; i < 6; i++) {
            cArr[i] = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".charAt(random.nextInt("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".length()));
        }
        return new String(cArr);
    }

    public static final class LogLine {
        public final String msg;
        public final int seq;
        public final long t;

        LogLine(int i, long j, String str) {
            this.seq = i;
            this.t = j;
            this.msg = str;
        }
    }
}

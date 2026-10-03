package com.tapsprite.agent;

import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Random;

/* loaded from: classes.dex */
public final class DeviceApi {
    private static Random rnd = new Random();
    private static PowerManager.WakeLock screenLock;

    private DeviceApi() {
    }

    public static void randomize() {
        rnd = new Random();
    }

    public static double rnd() {
        return rnd.nextDouble();
    }

    public static int rndInt(int i) {
        if (i <= 0) {
            return 0;
        }
        return rnd.nextInt(i);
    }

    public static void volume(int i) {
        try {
            AudioManager audioManager = (AudioManager) App.ctx.getSystemService("audio");
            if (audioManager != null) {
                audioManager.adjustVolume(i >= 0 ? 1 : -1, 1);
            }
        } catch (Exception e) {
        }
    }

    public static Point realSize() {
        WindowManager windowManager = null;
        Point point = new Point();
        try {
            windowManager = (WindowManager) App.ctx.getSystemService("window");
        } catch (Exception e) {
            DisplayMetrics metrics = metrics();
            point.x = metrics.widthPixels;
            point.y = metrics.heightPixels;
        }
        if (windowManager == null) {
            DisplayMetrics metrics2 = metrics();
            point.x = metrics2.widthPixels;
            point.y = metrics2.heightPixels;
            return point;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
            point.x = bounds.width();
            point.y = bounds.height();
        } else {
            windowManager.getDefaultDisplay().getRealSize(point);
        }
        DisplayMetrics metrics3 = metrics();
        if (metrics3.widthPixels > point.x) {
            point.x = metrics3.widthPixels;
        }
        if (metrics3.heightPixels > point.y) {
            point.y = metrics3.heightPixels;
        }
        return point;
    }

    public static int[] wmPhysical() {
        try {
            Process exec = Runtime.getRuntime().exec(new String[]{"sh", "-c", "wm size"});
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(exec.getInputStream()));
            int i = 0;
            int i2 = 0;
            while (true) {
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    break;
                }
                String trim = readLine.trim();
                if (trim.startsWith("Physical size:") || trim.startsWith("Override size:")) {
                    String trim2 = trim.substring(trim.indexOf(58) + 1).trim();
                    int indexOf = trim2.indexOf(120);
                    if (indexOf > 0) {
                        int parseInt = Integer.parseInt(trim2.substring(0, indexOf).trim());
                        int parseInt2 = Integer.parseInt(trim2.substring(indexOf + 1).trim());
                        if (parseInt > i) {
                            i2 = parseInt2;
                            i = parseInt;
                        }
                    }
                }
            }
            exec.waitFor();
            if (i > 0 && i2 > 0) {
                return new int[]{i, i2};
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static int getScreenX() {
        return realSize().x;
    }

    public static int getScreenY() {
        return realSize().y;
    }

    public static DisplayMetrics metrics() {
        WindowManager windowManager = (WindowManager) App.ctx.getSystemService("window");
        if (windowManager == null) {
            return App.ctx.getResources().getDisplayMetrics();
        }
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
    }

    public static boolean isRoot() {
        return NetInfo.isEmulator();
    }

    public static long tickCount() {
        return SystemClock.uptimeMillis();
    }

    public static synchronized void keepAwake(boolean z) {
        synchronized (DeviceApi.class) {
            AppState.keepAwake = z;
            try {
                App.ctx.getSharedPreferences("tapsprite", 0).edit().putBoolean("keepAwake", z).apply();
            } catch (Exception e) {
            }
            PowerManager powerManager = (PowerManager) App.ctx.getSystemService("power");
            if (powerManager == null) {
                return;
            }
            if (z) {
                if (screenLock == null) {
                    try {
                        screenLock = powerManager.newWakeLock(536870922, "tapsprite:screen");
                    } catch (Exception e2) {
                        screenLock = powerManager.newWakeLock(1, "tapsprite:cpu");
                    }
                    screenLock.setReferenceCounted(false);
                }
                if (!screenLock.isHeld()) {
                    try {
                        screenLock.acquire();
                        AppState.log("屏幕常亮已开（免 Root）");
                    } catch (Exception e3) {
                        AppState.log("常亮失败：" + e3.getMessage());
                    }
                }
            } else {
                PowerManager.WakeLock wakeLock = screenLock;
                if (wakeLock != null && wakeLock.isHeld()) {
                    try {
                        screenLock.release();
                    } catch (Exception e4) {
                    }
                    AppState.log("屏幕常亮已关");
                }
            }
        }
    }

    public static boolean keepAwakeHeld() {
        PowerManager.WakeLock wakeLock = screenLock;
        return wakeLock != null && wakeLock.isHeld();
    }

    public static void vibrate(int i) {
        try {
            Vibrator vibrator = (Vibrator) App.ctx.getSystemService("vibrator");
            if (vibrator != null) {
                vibrator.vibrate(Math.max(1, i));
            }
        } catch (Exception e) {
        }
    }

    public static String model() {
        return (Build.MANUFACTURER + " " + Build.MODEL).trim();
    }
}

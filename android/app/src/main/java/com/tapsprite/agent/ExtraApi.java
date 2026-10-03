package com.tapsprite.agent;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.media.MediaPlayer;
import android.os.BatteryManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import com.tapsprite.agent.ColorUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/* loaded from: classes.dex */
public final class ExtraApi {
    private ExtraApi() {
    }

    public static void randomTap(int i, int i2, int i3) {
        int max = (Math.max(0, i3) * 2) + 1;
        Sprite.tap((i + DeviceApi.rndInt(max)) - i3, (i2 + DeviceApi.rndInt(max)) - i3);
    }

    public static boolean waitColor(int i, int i2, int i3, int i4, String str, int i5, float f) {
        long tickCount = DeviceApi.tickCount() + Math.max(0, i5);
        while (DeviceApi.tickCount() <= tickCount) {
            if (ScriptEngine.isStopRequested()) {
                return false;
            }
            if (Sprite.findColor(i, i2, i3, i4, str, f, 0)) {
                return true;
            }
            try {
                ScriptEngine.sleepMs(120L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        ScreenApi.last.clear();
        return false;
    }

    public static String getClip() {
        ClipData primaryClip;
        CharSequence coerceToText;
        try {
            ClipboardManager clipboardManager = (ClipboardManager) App.ctx.getSystemService("clipboard");
            if (clipboardManager != null && clipboardManager.hasPrimaryClip() && (primaryClip = clipboardManager.getPrimaryClip()) != null && primaryClip.getItemCount() != 0 && (coerceToText = primaryClip.getItemAt(0).coerceToText(App.ctx)) != null) {
                return coerceToText.toString();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void setClip(String str) {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) App.ctx.getSystemService("clipboard");
            if (clipboardManager != null) {
                if (str == null) {
                    str = "";
                }
                clipboardManager.setPrimaryClip(ClipData.newPlainText("tapsprite", str));
            }
        } catch (Exception e) {
        }
    }

    public static boolean runApp(String str) {
        try {
            Intent launchIntentForPackage = App.ctx.getPackageManager().getLaunchIntentForPackage(str);
            if (launchIntentForPackage == null) {
                AppState.log("RunApp 找不到 " + str);
                return false;
            }
            launchIntentForPackage.addFlags(268435456);
            App.ctx.startActivity(launchIntentForPackage);
            return true;
        } catch (Exception e) {
            AppState.log("RunApp 失败：" + e.getMessage());
            return false;
        }
    }

    public static boolean killApp(String str) {
        try {
            ActivityManager activityManager = (ActivityManager) App.ctx.getSystemService("activity");
            if (activityManager != null) {
                activityManager.killBackgroundProcesses(str);
                AppState.log("KillApp " + str + "（后台进程；强杀其它 App 仍需 Root）");
                return true;
            }
            return false;
        } catch (Exception e) {
            AppState.log("KillApp 失败：" + e.getMessage());
            return false;
        }
    }

    public static int battery() {
        try {
            BatteryManager batteryManager = (BatteryManager) App.ctx.getSystemService("batterymanager");
            if (batteryManager == null) {
                return -1;
            }
            return batteryManager.getIntProperty(4);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int colorDep() {
        return 32;
    }

    public static String deviceId() {
        try {
            String string = Settings.Secure.getString(App.ctx.getContentResolver(), "android_id");
            return string == null ? "" : string;
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean play(String str) {
        if (str == null || str.length() == 0) {
            return false;
        }
        File file = str.startsWith("/") ? new File(str) : new File(FileApi.dir(), str);
        if (!file.exists()) {
            AppState.log("Play 找不到文件 " + file.getAbsolutePath());
            return false;
        }
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { // from class: com.tapsprite.agent.ExtraApi.1
                @Override // android.media.MediaPlayer.OnCompletionListener
                public void onCompletion(MediaPlayer mediaPlayer2) {
                    try {
                        mediaPlayer2.release();
                    } catch (Exception e) {
                    }
                }
            });
            mediaPlayer.prepare();
            mediaPlayer.start();
            AppState.log("Play " + file.getName());
            return true;
        } catch (Exception e) {
            AppState.log("Play 失败：" + e.getMessage());
            return false;
        }
    }

    public static String putAttachment(String str) {
        if (str == null || str.length() == 0) {
            return "";
        }
        File file = new File(FileApi.dir(), new File(str).getName());
        try {
            InputStream open = App.ctx.getAssets().open(str);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            byte[] bArr = new byte[8192];
            while (true) {
                int read = open.read(bArr);
                if (read > 0) {
                    fileOutputStream.write(bArr, 0, read);
                } else {
                    fileOutputStream.close();
                    open.close();
                    AppState.log("PutAttachment → " + file.getAbsolutePath());
                    return file.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            if (file.exists()) {
                return file.getAbsolutePath();
            }
            AppState.log("PutAttachment 无此附件：" + str);
            return "";
        }
    }

    public static boolean appIsFront(String str) {
        AutoService autoService;
        AccessibilityNodeInfo rootInActiveWindow;
        boolean z = false;
        if (str == null || str.length() == 0 || (autoService = AppState.auto) == null || (rootInActiveWindow = autoService.getRootInActiveWindow()) == null) {
            return false;
        }
        try {
            CharSequence packageName = rootInActiveWindow.getPackageName();
            if (packageName != null) {
                if (str.equals(packageName.toString())) {
                    z = true;
                }
            }
            return z;
        } finally {
            try {
                rootInActiveWindow.recycle();
            } catch (Exception e) {
            }
        }
    }

    public static int colorDiff(String str, String str2) {
        ColorUtil.Spec parse = ColorUtil.parse(str);
        ColorUtil.Spec parse2 = ColorUtil.parse(str2);
        return Math.max(Math.abs(parse.r - parse2.r), Math.max(Math.abs(parse.g - parse2.g), Math.abs(parse.b - parse2.b)));
    }

    /**
     * 带抖动的多次随机点击。n 默认 1。
     * 与按键精灵差异：官方第 4 参是展示图片路径；此处第 4 参为点击次数。
     * 每次在半径 r 内取点，并做短按+微移模拟抖动。
     */
    public static void randomsTap(int x, int y, int r, int n) {
        int times = Math.max(1, n);
        int radius = Math.max(0, r);
        for (int i = 0; i < times; i++) {
            if (ScriptEngine.isStopRequested()) {
                return;
            }
            int max = (radius * 2) + 1;
            int tx = (x + DeviceApi.rndInt(max)) - radius;
            int ty = (y + DeviceApi.rndInt(max)) - radius;
            // 抖动：按下 → 轻微偏移 → 抬起
            int jx = tx + DeviceApi.rndInt(5) - 2;
            int jy = ty + DeviceApi.rndInt(5) - 2;
            AutoService auto = AppState.auto;
            if (auto != null) {
                auto.touchDown(tx, ty);
                auto.touchMove(jx, jy, 40 + DeviceApi.rndInt(40));
                auto.touchUp();
            } else {
                Sprite.tap(tx, ty);
            }
            if (i + 1 < times) {
                try {
                    ScriptEngine.sleepMs(60L + DeviceApi.rndInt(80));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * 双指捏合（缩小）：从 (x1,y1)/(x2,y2) 滑向中点。
     * ms 为手势总时长，默认 300（按键精灵默认「速度」50，含义不同，见文档差异）。
     */
    public static boolean moveZoomOut(float x1, float y1, float x2, float y2, int ms) {
        float cx = (x1 + x2) / 2f;
        float cy = (y1 + y2) / 2f;
        // 终点靠近中心，留一点间距避免重合
        float e1x = cx + (x1 - cx) * 0.15f;
        float e1y = cy + (y1 - cy) * 0.15f;
        float e2x = cx + (x2 - cx) * 0.15f;
        float e2y = cy + (y2 - cy) * 0.15f;
        return pinch(x1, y1, x2, y2, e1x, e1y, e2x, e2y, ms);
    }

    /**
     * 双指放大：从靠近中点处滑向 (x1,y1)/(x2,y2)。
     */
    public static boolean moveZoomIn(float x1, float y1, float x2, float y2, int ms) {
        float cx = (x1 + x2) / 2f;
        float cy = (y1 + y2) / 2f;
        float s1x = cx + (x1 - cx) * 0.15f;
        float s1y = cy + (y1 - cy) * 0.15f;
        float s2x = cx + (x2 - cx) * 0.15f;
        float s2y = cy + (y2 - cy) * 0.15f;
        return pinch(s1x, s1y, s2x, s2y, x1, y1, x2, y2, ms);
    }

    private static boolean pinch(float x1, float y1, float x2, float y2,
                                float e1x, float e1y, float e2x, float e2y, int ms) {
        int dur = ms <= 0 ? 300 : Math.max(50, ms);
        AutoService auto = AppState.auto;
        if (auto == null) {
            AppState.log("MoveZoom 需要无障碍双指手势");
            return false;
        }
        return auto.pinch(x1, y1, x2, y2, e1x, e1y, e2x, e2y, dur);
    }

    public static boolean openUrl(String url) {
        if (url == null || url.trim().length() == 0) {
            return false;
        }
        try {
            String u = url.trim();
            if (!u.contains("://")) {
                u = "https://" + u;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            AppState.log("OpenUrl 失败：" + e.getMessage());
            return false;
        }
    }
}

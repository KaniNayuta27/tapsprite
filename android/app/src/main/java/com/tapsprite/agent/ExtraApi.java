package com.tapsprite.agent;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
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
}

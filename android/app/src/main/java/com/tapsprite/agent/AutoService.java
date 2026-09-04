package com.tapsprite.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/* loaded from: classes.dex */
public class AutoService extends AccessibilityService {
    private GestureDescription.StrokeDescription continued;
    private float lastX;
    private float lastY;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
    }

    @Override // android.accessibilityservice.AccessibilityService
    protected void onServiceConnected() {
        super.onServiceConnected();
        AppState.auto = this;
        AppState.log("无障碍服务已连接");
        LanLink.hello();
    }

    public static boolean reconnect() {
        String str = App.ctx.getPackageName() + "/" + AutoService.class.getName();
        try {
            ContentResolver contentResolver = App.ctx.getContentResolver();
            String string = Settings.Secure.getString(contentResolver, "enabled_accessibility_services");
            if (string == null) {
                string = "";
            }
            if (string.indexOf("AutoService") < 0 && string.indexOf("tapsprite") < 0) {
                if (string.length() != 0) {
                    str = string + ":" + str;
                }
                Settings.Secure.putString(contentResolver, "enabled_accessibility_services", str);
                Settings.Secure.putInt(contentResolver, "accessibility_enabled", 1);
                AppState.log("已写入无障碍名单（只开不关）");
            } else {
                AppState.log("系统名单里已有无障碍，不改开关");
            }
            for (int i = 0; i < 15; i++) {
                if (AppState.auto != null) {
                    return true;
                }
                Thread.sleep(80L);
            }
        } catch (Exception e) {
            AppState.log("写入无障碍失败 " + e.getMessage());
        }
        try {
            new ProcessBuilder("sh", "-c", "settings put secure accessibility_enabled 1").redirectErrorStream(true).start().waitFor();
        } catch (Exception e2) {
        }
        return AppState.auto != null;
    }

    private static String stripUs(String str, String str2) {
        String[] split = str.split(":");
        StringBuilder sb = new StringBuilder();
        for (String str3 : split) {
            if (str3 != null && str3.length() != 0 && !str3.contains("tapsprite") && !str3.contains("AutoService")) {
                if (sb.length() > 0) {
                    sb.append(':');
                }
                sb.append(str3);
            }
        }
        return sb.toString();
    }

    @Override // android.app.Service
    public void onDestroy() {
        if (AppState.auto == this) {
            AppState.auto = null;
        }
        super.onDestroy();
    }

    public void pressKey(String str) {
        final int i;
        if ("home".equals(str)) {
            i = 2;
        } else if ("recents".equals(str) || "recent".equals(str)) {
            i = 3;
        } else if ("notifications".equals(str) || "notify".equals(str)) {
            i = 4;
        } else if ("quicksettings".equals(str) || "qs".equals(str)) {
            i = 5;
        } else if ("power".equals(str)) {
            i = 6;
        } else if ("back".equals(str) || "enter".equals(str)) {
            i = 1;
        } else if ("screenshot".equals(str) && Build.VERSION.SDK_INT >= 28) {
            i = 9;
        } else if ("lock".equals(str) && Build.VERSION.SDK_INT >= 28) {
            i = 8;
        } else {
            i = 1;
        }
        this.main.post(new Runnable() { // from class: com.tapsprite.agent.AutoService.1
            @Override // java.lang.Runnable
            public void run() {
                AutoService.this.performGlobalAction(i);
            }
        });
    }

    public boolean tap(final float f, final float f2) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        this.main.post(new Runnable() { // from class: com.tapsprite.agent.AutoService.2
            @Override // java.lang.Runnable
            public void run() {
                Path path = new Path();
                path.moveTo(f, f2);
                if (!AutoService.this.dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, 90L)).build(), new AccessibilityService.GestureResultCallback() { // from class: com.tapsprite.agent.AutoService.2.1
                    @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
                    public void onCompleted(GestureDescription gestureDescription) {
                        atomicBoolean.set(true);
                        countDownLatch.countDown();
                    }

                    @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
                    public void onCancelled(GestureDescription gestureDescription) {
                        countDownLatch.countDown();
                    }
                }, null)) {
                    countDownLatch.countDown();
                }
            }
        });
        try {
            countDownLatch.await(2L, TimeUnit.SECONDS);
            return atomicBoolean.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean longClick(float f, float f2) {
        return stroke(f, f2, f, f2, 700, false);
    }

    public boolean touch(float f, float f2, int i) {
        return stroke(f, f2, f, f2, Math.max(50, i), false);
    }

    public boolean swipe(float f, float f2, float f3, float f4, int i) {
        return stroke(f, f2, f3, f4, i, false);
    }

    /** 双指同时滑动（捏合/放大）。两条 Stroke 并行 dispatchGesture。 */
    public boolean pinch(final float x1, final float y1, final float x2, final float y2,
                         final float e1x, final float e1y, final float e2x, final float e2y, final int ms) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean ok = new AtomicBoolean(false);
        this.main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Path p1 = new Path();
                    p1.moveTo(x1, y1);
                    if (x1 != e1x || y1 != e1y) {
                        p1.lineTo(e1x, e1y);
                    }
                    Path p2 = new Path();
                    p2.moveTo(x2, y2);
                    if (x2 != e2x || y2 != e2y) {
                        p2.lineTo(e2x, e2y);
                    }
                    int dur = Math.max(50, ms);
                    GestureDescription.Builder b = new GestureDescription.Builder();
                    b.addStroke(new GestureDescription.StrokeDescription(p1, 0L, dur));
                    b.addStroke(new GestureDescription.StrokeDescription(p2, 0L, dur));
                    AutoService.this.continued = null;
                    if (!AutoService.this.dispatchGesture(b.build(), AutoService.this.cb(ok, latch), null)) {
                        latch.countDown();
                    }
                } catch (Exception e) {
                    latch.countDown();
                }
            }
        });
        return await(latch, ok);
    }

    public boolean touchDown(float f, float f2) {
        return stroke(f, f2, f, f2, 80, true);
    }

    public boolean touchMove(float f, float f2, int i) {
        return continueTo(f, f2, i, true);
    }

    public boolean touchUp() {
        if (this.continued == null) {
            return true;
        }
        return continueTo(this.lastX, this.lastY, 40, false);
    }

    private boolean continueTo(final float f, final float f2, final int i, final boolean z) {
        if (Build.VERSION.SDK_INT < 26 || this.continued == null) {
            boolean stroke = stroke(this.lastX, this.lastY, f, f2, i, z);
            this.lastX = f;
            this.lastY = f2;
            return stroke;
        }
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        this.main.post(new Runnable() { // from class: com.tapsprite.agent.AutoService.3
            @Override // java.lang.Runnable
            public void run() {
                Path path = new Path();
                path.moveTo(AutoService.this.lastX, AutoService.this.lastY);
                path.lineTo(f, f2);
                try {
                    GestureDescription.StrokeDescription continueStroke = AutoService.this.continued.continueStroke(path, 0L, Math.max(40, i), z);
                    AutoService.this.continued = z ? continueStroke : null;
                    AutoService.this.lastX = f;
                    AutoService.this.lastY = f2;
                    GestureDescription build = new GestureDescription.Builder().addStroke(continueStroke).build();
                    AutoService autoService = AutoService.this;
                    if (!autoService.dispatchGesture(build, autoService.cb(atomicBoolean, countDownLatch), null)) {
                        countDownLatch.countDown();
                    }
                } catch (Exception e) {
                    AutoService.this.continued = null;
                    countDownLatch.countDown();
                }
            }
        });
        return await(countDownLatch, atomicBoolean);
    }

    private boolean stroke(final float f, final float f2, final float f3, final float f4, final int i, final boolean z) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        this.main.post(new Runnable() { // from class: com.tapsprite.agent.AutoService.4
            @Override // java.lang.Runnable
            public void run() {
                GestureDescription.StrokeDescription strokeDescription;
                Path path = new Path();
                path.moveTo(f, f2);
                float f5 = f;
                float f6 = f3;
                if (f5 != f6 || f2 != f4) {
                    path.lineTo(f6, f4);
                }
                int max = Math.max(40, i);
                if (Build.VERSION.SDK_INT >= 26) {
                    strokeDescription = new GestureDescription.StrokeDescription(path, 0L, max, z);
                    AutoService.this.continued = z ? strokeDescription : null;
                } else {
                    strokeDescription = new GestureDescription.StrokeDescription(path, 0L, max);
                    AutoService.this.continued = null;
                }
                AutoService.this.lastX = f3;
                AutoService.this.lastY = f4;
                GestureDescription build = new GestureDescription.Builder().addStroke(strokeDescription).build();
                AutoService autoService = AutoService.this;
                if (!autoService.dispatchGesture(build, autoService.cb(atomicBoolean, countDownLatch), null)) {
                    countDownLatch.countDown();
                }
            }
        });
        return await(countDownLatch, atomicBoolean);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public AccessibilityService.GestureResultCallback cb(final AtomicBoolean atomicBoolean, final CountDownLatch countDownLatch) {
        return new AccessibilityService.GestureResultCallback() { // from class: com.tapsprite.agent.AutoService.5
            @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
            public void onCompleted(GestureDescription gestureDescription) {
                atomicBoolean.set(true);
                countDownLatch.countDown();
            }

            @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
            public void onCancelled(GestureDescription gestureDescription) {
                countDownLatch.countDown();
            }
        };
    }

    private boolean await(CountDownLatch countDownLatch, AtomicBoolean atomicBoolean) {
        try {
            countDownLatch.await(3L, TimeUnit.SECONDS);
            return atomicBoolean.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }


    public String getPixelColorA11y(int x, int y) {
        if (Build.VERSION.SDK_INT < 30) {
            AppState.log("GetPixelColorA11y：需要安卓 11+");
            return "";
        }
        if (AppState.auto == null) {
            AppState.log("GetPixelColorA11y：未开启无障碍");
            return "";
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> colorRef = new AtomicReference<>("");
        final int reqX = x;
        final int reqY = y;
        try {
            takeScreenshot(0, new Executor() { // from class: com.tapsprite.agent.AutoService.A11yColorExec
                @Override // java.util.concurrent.Executor
                public void execute(Runnable runnable) {
                    runnable.run();
                }
            }, new AccessibilityService.TakeScreenshotCallback() { // from class: com.tapsprite.agent.AutoService.A11yColorCb
                @Override // android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
                public void onSuccess(AccessibilityService.ScreenshotResult screenshotResult) {
                    HardwareBuffer hardwareBuffer = null;
                    Bitmap hardwareBitmap = null;
                    Bitmap softBitmap = null;
                    try {
                        if (screenshotResult == null) {
                            AppState.log("GetPixelColorA11y：截图结果为空");
                            return;
                        }
                        hardwareBuffer = screenshotResult.getHardwareBuffer();
                        if (hardwareBuffer == null) {
                            AppState.log("GetPixelColorA11y：HardwareBuffer 为空");
                            return;
                        }
                        hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.getColorSpace());
                        if (hardwareBitmap == null) {
                            AppState.log("GetPixelColorA11y：无法包装 Bitmap");
                            return;
                        }
                        softBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
                        if (softBitmap == null) {
                            AppState.log("GetPixelColorA11y：无法复制为软件 Bitmap");
                            return;
                        }
                        int width = softBitmap.getWidth();
                        int height = softBitmap.getHeight();
                        if (width <= 0 || height <= 0) {
                            AppState.log("GetPixelColorA11y：截图像素尺寸无效");
                            return;
                        }
                        int cx = reqX;
                        int cy = reqY;
                        if (cx < 0 || cy < 0 || cx >= width || cy >= height) {
                            AppState.log("GetPixelColorA11y：坐标越界 (" + reqX + "," + reqY + ") 图 " + width + "x" + height + "，已夹紧");
                            cx = Math.max(0, Math.min(cx, width - 1));
                            cy = Math.max(0, Math.min(cy, height - 1));
                        }
                        colorRef.set(ColorUtil.hex(softBitmap.getPixel(cx, cy)));
                    } catch (Exception e) {
                        AppState.log("GetPixelColorA11y：取色失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                    } finally {
                        try {
                            if (softBitmap != null) {
                                softBitmap.recycle();
                            }
                        } catch (Exception e) {
                        }
                        try {
                            if (hardwareBitmap != null) {
                                hardwareBitmap.recycle();
                            }
                        } catch (Exception e) {
                        }
                        try {
                            if (hardwareBuffer != null) {
                                hardwareBuffer.close();
                            }
                        } catch (Exception e) {
                        }
                        latch.countDown();
                    }
                }

                @Override // android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
                public void onFailure(int errorCode) {
                    String reason;
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        reason = "截图间隔太短/冷却中";
                    } else if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) {
                        reason = "无障碍无截图权限";
                    } else if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR) {
                        reason = "内部错误";
                    } else if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY) {
                        reason = "无效显示器";
                    } else {
                        reason = "失败码 " + errorCode;
                    }
                    AppState.log("GetPixelColorA11y：截图失败——" + reason);
                    latch.countDown();
                }
            });
            if (!latch.await(3L, TimeUnit.SECONDS)) {
                AppState.log("GetPixelColorA11y：等待截图超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AppState.log("GetPixelColorA11y：被中断");
        } catch (Throwable th) {
            AppState.log("GetPixelColorA11y：异常：" + (th.getMessage() != null ? th.getMessage() : th.getClass().getSimpleName()));
        }
        return colorRef.get() != null ? colorRef.get() : "";
    }

    public boolean takeA11yShot() {
        if (Build.VERSION.SDK_INT < 30) {
            return false;
        }
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        try {
            takeScreenshot(0, new Executor() { // from class: com.tapsprite.agent.AutoService.6
                @Override // java.util.concurrent.Executor
                public void execute(Runnable runnable) {
                    runnable.run();
                }
            }, new AccessibilityService.TakeScreenshotCallback() { // from class: com.tapsprite.agent.AutoService.7
                @Override // android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
                public void onSuccess(AccessibilityService.ScreenshotResult screenshotResult) {
                    if (screenshotResult != null) {
                        try {
                            if (screenshotResult.getHardwareBuffer() != null) {
                                screenshotResult.getHardwareBuffer().close();
                            }
                        } catch (Exception e) {
                        }
                    }
                    atomicInteger.set(1);
                    countDownLatch.countDown();
                }

                @Override // android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
                public void onFailure(int i) {
                    atomicInteger.set(i == 0 ? -1 : -i);
                    countDownLatch.countDown();
                }
            });
            try {
                return countDownLatch.await(2L, TimeUnit.SECONDS) && atomicInteger.get() == 1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } catch (Throwable th) {
            return false;
        }
    }
}

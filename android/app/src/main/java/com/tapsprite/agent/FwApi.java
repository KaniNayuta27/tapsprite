package com.tapsprite.agent;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/* loaded from: classes.dex */
public final class FwApi {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private FwApi() {
    }

    public static void newFWindow(final String str, final int i, final int i2, final int i3, final int i4) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.1
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwNew(str, i, i2, i3, i4);
                }
            }
        });
    }

    public static void setBackColor(final String str, final String str2) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.2
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwBackColor(str, str2);
                }
            }
        });
    }

    public static void opacity(final String str, final int i) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.3
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwOpacity(str, i);
                }
            }
        });
    }

    public static void addTextView(final String str, final String str2, final String str3, final int i, final int i2, final int i3, final int i4) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.4
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwAddText(str, str2, str3, i, i2, i3, i4);
                }
            }
        });
    }

    public static void setTextColor(final String str, final String str2) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.5
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwTextColor(str, str2);
                }
            }
        });
    }

    public static void setTextSize(final String str, final int i) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.6
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwTextSize(str, i);
                }
            }
        });
    }

    public static void setText(final String str, final String str2) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.7
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwSetText(str, str2);
                }
            }
        });
    }

    public static void show(final String str) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.8
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwShow(str, true);
                }
            }
        });
    }

    public static void hide(final String str) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.9
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwShow(str, false);
                }
            }
        });
    }

    public static void close(final String str) {
        run(new Runnable() { // from class: com.tapsprite.agent.FwApi.10
            @Override // java.lang.Runnable
            public void run() {
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.fwClose(str);
                }
            }
        });
    }

    private static void run(final Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
            return;
        }
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        MAIN.post(new Runnable() { // from class: com.tapsprite.agent.FwApi.11
            @Override // java.lang.Runnable
            public void run() {
                try {
                    runnable.run();
                } finally {
                    countDownLatch.countDown();
                }
            }
        });
        try {
            countDownLatch.await(2L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

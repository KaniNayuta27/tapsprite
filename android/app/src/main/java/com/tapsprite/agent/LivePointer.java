package com.tapsprite.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Real-time finger for 实时操控. The network thread only publishes the latest
 * target. The main thread dispatches the next accessibility segment from
 * {@code onCompleted}, never before, and does not hold {@link DeviceGate}
 * across the drag.
 *
 * <p>API 26+ chains short {@code continueStroke} segments so a held mouse is a
 * held finger (long-press runs while the button is down). Older APIs, or no
 * accessibility service, record the path and play one swipe on release.
 */
final class LivePointer {
    private static final int DOWN = 1;
    private static final int SAMPLE_CAP = 64;

    private static final Object PUB = new Object();
    private static int pubPhase;
    private static int pubX;
    private static int pubY;
    private static int pubDownX;
    private static int pubDownY;
    private static boolean needPress;

    private static final Object SAMPLES = new Object();
    private static final int[] SX = new int[SAMPLE_CAP];
    private static final int[] SY = new int[SAMPLE_CAP];
    private static int sn;
    private static boolean sampleOpen;
    private static long sampleT0;
    private static boolean cancelFallback;

    private static Handler main;
    private static final Runnable PUMP = new Runnable() {
        @Override
        public void run() {
            pump();
        }
    };
    private static final Runnable FAIL = new Runnable() {
        @Override
        public void run() {
            afterGesture(true);
        }
    };

    private static GestureDescription.StrokeDescription stroke;
    private static boolean dispatching;
    private static int strokeGen;
    private static boolean suppressed;
    private static int mode;
    private static boolean dragged;
    private static int anchorX;
    private static int anchorY;
    private static int lastX;
    private static int lastY;
    private static long lastDispatchAt;
    private static int burst;
    private static boolean abortIfIdle;

    private LivePointer() {
    }

    /** Network thread. {@code op} is down, move, or up. Coordinates are physical pixels. */
    static void input(String op, int x, int y) {
        synchronized (PUB) {
            if ("down".equals(op)) {
                pubDownX = x;
                pubDownY = y;
                pubX = x;
                pubY = y;
                pubPhase = DOWN;
                needPress = true;
                abortIfIdle = false;
            } else if ("move".equals(op)) {
                if (pubPhase != DOWN && !needPress) {
                    return;
                }
                pubX = x;
                pubY = y;
            } else if ("up".equals(op)) {
                pubX = x;
                pubY = y;
                pubPhase = 0;
            } else {
                return;
            }
        }
        synchronized (SAMPLES) {
            if ("down".equals(op)) {
                sn = 0;
                sampleOpen = true;
                cancelFallback = false;
                sampleT0 = SystemClock.uptimeMillis();
                pushSample(x, y);
            } else if ("move".equals(op) && sampleOpen) {
                pushSample(x, y);
            } else if ("up".equals(op) && sampleOpen) {
                pushSample(x, y);
                sampleOpen = false;
            }
        }
        kick();
    }

    /** Socket drop, stop, or a new session: lift an open finger and do not replay a fallback swipe. */
    static void lift() {
        synchronized (PUB) {
            pubPhase = 0;
        }
        synchronized (SAMPLES) {
            cancelFallback = true;
            sampleOpen = false;
        }
        abortIfIdle = true;
        kick();
    }

    /** A script gesture is about to dispatch. Drop our stroke id so we do not continue it. */
    static void preempt() {
        suppressed = true;
        strokeGen++;
        dispatching = false;
        stroke = null;
    }

    static void resumeAfterScript() {
        suppressed = false;
        kick();
    }

    private static void kick() {
        Handler h = handler();
        if (h != null) {
            h.post(PUMP);
        }
    }

    private static Handler handler() {
        if (main != null) {
            return main;
        }
        Looper looper = Looper.getMainLooper();
        if (looper == null) {
            return null;
        }
        main = new Handler(looper);
        return main;
    }

    private static void pushSample(int x, int y) {
        if (sn > 0 && SX[sn - 1] == x && SY[sn - 1] == y) {
            return;
        }
        if (sn >= SAMPLE_CAP) {
            SX[SAMPLE_CAP - 1] = x;
            SY[SAMPLE_CAP - 1] = y;
            return;
        }
        SX[sn] = x;
        SY[sn] = y;
        sn++;
    }

    private static void pump() {
        if (dispatching) {
            return;
        }
        if (suppressed || AutoService.scriptGesture) {
            return;
        }
        int phase;
        int x;
        int y;
        int dx;
        int dy;
        boolean press;
        synchronized (PUB) {
            phase = pubPhase;
            x = pubX;
            y = pubY;
            dx = pubDownX;
            dy = pubDownY;
            press = needPress;
        }
        if (abortIfIdle && mode == 0) {
            abortIfIdle = false;
            synchronized (PUB) {
                needPress = false;
            }
            discardSamples();
            return;
        }
        if (mode == 0 && (phase == DOWN || press)) {
            mode = canStream() ? 1 : 2;
            dragged = false;
            anchorX = dx;
            anchorY = dy;
            lastX = dx;
            lastY = dy;
        }
        if (mode == 1) {
            if (!canStream()) {
                stroke = null;
                mode = 2;
            } else {
                streamStep(phase, x, y);
                return;
            }
        }
        if (mode == 2 && phase != DOWN) {
            boolean cancel;
            synchronized (SAMPLES) {
                cancel = cancelFallback;
            }
            if (cancel || abortIfIdle) {
                discardSamples();
                synchronized (PUB) {
                    needPress = false;
                }
            } else {
                playFallback();
            }
            mode = 0;
            abortIfIdle = false;
            synchronized (SAMPLES) {
                cancelFallback = false;
            }
        }
    }

    private static boolean canStream() {
        return Build.VERSION.SDK_INT >= 26 && AppState.auto != null;
    }

    private static void streamStep(int phase, int x, int y) {
        if (stroke == null) {
            boolean press;
            synchronized (PUB) {
                press = needPress;
                if (press || phase == DOWN) {
                    needPress = false;
                }
            }
            if (!press && phase != DOWN) {
                mode = 0;
                return;
            }
            Path path = new Path();
            path.moveTo(anchorX, anchorY);
            lastX = anchorX;
            lastY = anchorY;
            dragged = false;
            dispatch(path, 20L, true, true);
            return;
        }
        if (phase != DOWN) {
            Path path = new Path();
            path.moveTo(lastX, lastY);
            if (x != lastX || y != lastY) {
                path.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
            dispatch(path, 20L, false, false);
            return;
        }
        if (!dragged && !LiveTouch.leftSlop(anchorX, anchorY, x, y)) {
            Path path = new Path();
            path.moveTo(lastX, lastY);
            dispatch(path, 50L, true, false);
            return;
        }
        dragged = true;
        if (x == lastX && y == lastY) {
            Path path = new Path();
            path.moveTo(lastX, lastY);
            dispatch(path, 50L, true, false);
            return;
        }
        int dur = LiveTouch.moveDurationMs(SystemClock.uptimeMillis() - lastDispatchAt);
        Path path = new Path();
        path.moveTo(lastX, lastY);
        path.lineTo(x, y);
        lastX = x;
        lastY = y;
        dispatch(path, dur, true, false);
    }

    private static void dispatch(Path path, long dur, final boolean cont, boolean fresh) {
        AutoService auto = AppState.auto;
        if (auto == null || Build.VERSION.SDK_INT < 26) {
            stroke = null;
            dispatching = false;
            mode = 2;
            return;
        }
        final int my = ++strokeGen;
        dispatching = true;
        lastDispatchAt = SystemClock.uptimeMillis();
        try {
            GestureDescription.StrokeDescription next;
            if (fresh || stroke == null) {
                next = new GestureDescription.StrokeDescription(path, 0L, dur, cont);
            } else {
                next = stroke.continueStroke(path, 0L, dur, cont);
            }
            stroke = cont ? next : null;
            GestureDescription gesture = new GestureDescription.Builder().addStroke(next).build();
            boolean ok = auto.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    if (my != strokeGen) {
                        return;
                    }
                    dispatching = false;
                    if (!cont) {
                        stroke = null;
                    }
                    afterGesture(false);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    if (my != strokeGen) {
                        return;
                    }
                    dispatching = false;
                    stroke = null;
                    afterGesture(true);
                }
            }, handler());
            if (!ok) {
                dispatching = false;
                stroke = null;
                strokeGen++;
                Handler h = handler();
                if (h != null) {
                    h.post(FAIL);
                }
            }
        } catch (RuntimeException ex) {
            dispatching = false;
            stroke = null;
            strokeGen++;
            Handler h = handler();
            if (h != null) {
                h.post(FAIL);
            }
        }
    }

    private static void afterGesture(boolean cancelled) {
        if (suppressed || AutoService.scriptGesture) {
            return;
        }
        int phase;
        synchronized (PUB) {
            phase = pubPhase;
        }
        if (cancelled) {
            if (phase == DOWN) {
                repressAnchor();
                burst++;
                if (burst > 4) {
                    burst = 0;
                    Handler h = handler();
                    if (h != null) {
                        h.postDelayed(PUMP, 60);
                    }
                    return;
                }
                pump();
                return;
            }
            mode = 0;
            return;
        }
        burst = 0;
        if (phase != DOWN && stroke == null) {
            mode = 0;
        }
        pump();
    }

    private static void repressAnchor() {
        synchronized (PUB) {
            anchorX = pubX;
            anchorY = pubY;
            pubDownX = pubX;
            pubDownY = pubY;
        }
        lastX = anchorX;
        lastY = anchorY;
        dragged = false;
        stroke = null;
    }

    private static void discardSamples() {
        synchronized (SAMPLES) {
            sn = 0;
            sampleOpen = false;
        }
    }

    private static void playFallback() {
        final int n;
        final int[] xs;
        final int[] ys;
        final long t0;
        synchronized (SAMPLES) {
            n = sn;
            xs = new int[n];
            ys = new int[n];
            System.arraycopy(SX, 0, xs, 0, n);
            System.arraycopy(SY, 0, ys, 0, n);
            t0 = sampleT0;
            sn = 0;
            sampleOpen = false;
        }
        synchronized (PUB) {
            needPress = false;
        }
        if (n <= 0) {
            return;
        }
        long ms = SystemClock.uptimeMillis() - t0;
        if (ms < 40) {
            ms = 40;
        }
        if (ms > 10000) {
            ms = 10000;
        }
        final int dur = (int) ms;
        final float[] fx = new float[n];
        final float[] fy = new float[n];
        for (int i = 0; i < n; i++) {
            fx[i] = xs[i];
            fy[i] = ys[i];
        }
        LiveStream.playFallback(fx, fy, dur);
    }
}

package com.tapsprite.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayDeque;

/**
 * Real-time finger for 实时操控. The network thread only enqueues the latest
 * samples. The main thread asks {@link LiveGesture} what to dispatch, and
 * never starts the next segment before {@code onCompleted}.
 *
 * <p>A click (button up before the hold threshold, tiny movement) is one
 * 60ms tap with {@code willContinue=false}. A hold of {@link LiveGesture#HOLD_MS}
 * becomes a long press. A drag streams short {@code continueStroke} segments.
 * Keep-alives run only after that choice, so they cannot turn a click into a
 * long press by adding segment time while {@code up} waits.
 */
final class LivePointer {
    private static final int DOWN = 1;
    private static final int MOVE = 2;
    private static final int UP = 3;
    private static final int LIFT = 4;
    private static final int TICK = 5;
    private static final int QCAP = 48;
    private static final int SAMPLE_CAP = 64;

    private static final Object Q = new Object();
    private static final ArrayDeque<Ev> QUEUE = new ArrayDeque<Ev>();
    private static final LiveGesture GESTURE = new LiveGesture();

    private static final class Ev {
        int k;
        int x;
        int y;
        long t;
        long held;
    }

    private static Handler main;
    private static final Runnable PUMP = new Runnable() {
        @Override
        public void run() {
            pump();
        }
    };
    private static final Runnable HOLD = new Runnable() {
        @Override
        public void run() {
            enqueue(TICK, 0, 0, SystemClock.uptimeMillis(), -1);
            pump();
        }
    };

    private static GestureDescription.StrokeDescription stroke;
    private static int strokeGen;
    private static boolean suppressed;

    private static final int[] SX = new int[SAMPLE_CAP];
    private static final int[] SY = new int[SAMPLE_CAP];
    private static int sn;

    private LivePointer() {
    }

    /** Network thread. {@code op} is down, move, or up. Coordinates are physical pixels. */
    static void input(String op, int x, int y) {
        input(op, x, y, -1);
    }

    /**
     * @param clientHeldMs for {@code up}: how long the PC button was down, or -1.
     */
    static void input(String op, int x, int y, long clientHeldMs) {
        int k = "down".equals(op) ? DOWN : "move".equals(op) ? MOVE : "up".equals(op) ? UP : 0;
        if (k == 0) {
            return;
        }
        enqueue(k, x, y, SystemClock.uptimeMillis(), clientHeldMs);
        kick();
    }

    /** Socket drop, stop, or a new session: lift an open finger and do not replay a fallback swipe. */
    static void lift() {
        enqueue(LIFT, 0, 0, SystemClock.uptimeMillis(), -1);
        kick();
    }

    /** A script gesture is about to dispatch. Drop our stroke id so we do not continue it. */
    static void preempt() {
        suppressed = true;
        strokeGen++;
        stroke = null;
        GESTURE.lostStroke();
    }

    static void resumeAfterScript() {
        suppressed = false;
        kick();
    }

    private static void enqueue(int k, int x, int y, long t, long held) {
        synchronized (Q) {
            if (k == MOVE) {
                Ev last = QUEUE.peekLast();
                if (last != null && last.k == MOVE) {
                    last.x = x;
                    last.y = y;
                    last.t = t;
                    return;
                }
            }
            if (QUEUE.size() >= QCAP) {
                boolean dropped = false;
                java.util.Iterator<Ev> it = QUEUE.iterator();
                while (it.hasNext()) {
                    if (it.next().k == MOVE) {
                        it.remove();
                        dropped = true;
                        break;
                    }
                }
                if (!dropped) {
                    QUEUE.pollFirst();
                }
            }
            Ev e = new Ev();
            e.k = k;
            e.x = x;
            e.y = y;
            e.t = t;
            e.held = held;
            QUEUE.addLast(e);
        }
    }

    private static Ev poll() {
        synchronized (Q) {
            return QUEUE.pollFirst();
        }
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

    private static void arm(LiveGesture.Step step) {
        if (step == null || step.armAt < 0) {
            return;
        }
        Handler h = handler();
        if (h == null) {
            return;
        }
        h.removeCallbacks(HOLD);
        if (step.armAt == 0) {
            return;
        }
        long delay = step.armAt - SystemClock.uptimeMillis();
        if (delay < 1) {
            delay = 1;
        }
        h.postDelayed(HOLD, delay);
    }

    private static void log(LiveGesture.Step step) {
        if (step != null && step.reason != null && step.reason.length() > 0) {
            LiveStream.reportLive("实时操控手势 " + step.reason);
        }
    }

    private static void pump() {
        if (suppressed || AutoService.scriptGesture) {
            return;
        }
        if (GESTURE.busy()) {
            return;
        }
        GESTURE.setInstant(!canStream());
        for (int guard = 0; guard < 8; guard++) {
            if (GESTURE.busy()) {
                return;
            }
            Ev ev = poll();
            LiveGesture.Step step = ev != null ? apply(ev) : GESTURE.follow(SystemClock.uptimeMillis());
            arm(step);
            log(step);
            if (step.op == LiveGesture.OP_NONE) {
                if (ev == null) {
                    return;
                }
                continue;
            }
            if (!canStream()) {
                if (GESTURE.aborted()) {
                    sn = 0;
                } else {
                    recordAndMaybePlay(step);
                }
                GESTURE.noteDone(false);
                continue;
            }
            if (!launch(step)) {
                GESTURE.noteDone(true);
                continue;
            }
            return;
        }
    }

    private static LiveGesture.Step apply(Ev e) {
        switch (e.k) {
            case DOWN:
                sn = 0;
                return GESTURE.down(e.t, e.x, e.y);
            case MOVE:
                return GESTURE.move(e.t, e.x, e.y);
            case UP:
                return GESTURE.up(e.t, e.x, e.y, e.held);
            case LIFT:
                sn = 0;
                return GESTURE.lift(e.t);
            case TICK:
                return GESTURE.tick(e.t);
            default:
                return LiveGesture.Step.KEEP;
        }
    }

    private static boolean canStream() {
        return Build.VERSION.SDK_INT >= 26 && AppState.auto != null;
    }

    private static void remember(int x, int y) {
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

    private static void recordAndMaybePlay(LiveGesture.Step s) {
        if (s.op == LiveGesture.OP_TAP || s.op == LiveGesture.OP_HOLD
                || s.op == LiveGesture.OP_SWIPE || s.op == LiveGesture.OP_START) {
            sn = 0;
        }
        remember(s.x0, s.y0);
        remember(s.x1, s.y1);
        if (s.cont) {
            return;
        }
        int n = sn;
        sn = 0;
        if (n <= 0) {
            return;
        }
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = SX[i];
            ys[i] = SY[i];
        }
        LiveStream.playFallback(xs, ys, s.durMs);
    }

    private static boolean launch(LiveGesture.Step s) {
        AutoService auto = AppState.auto;
        if (auto == null || Build.VERSION.SDK_INT < 26) {
            return false;
        }
        Path path = new Path();
        path.moveTo(s.x0, s.y0);
        if (s.x0 != s.x1 || s.y0 != s.y1) {
            path.lineTo(s.x1, s.y1);
        }
        final int my = ++strokeGen;
        boolean fresh = s.op == LiveGesture.OP_TAP || s.op == LiveGesture.OP_HOLD
                || s.op == LiveGesture.OP_SWIPE || s.op == LiveGesture.OP_START || stroke == null;
        try {
            GestureDescription.StrokeDescription next;
            if (fresh || stroke == null) {
                next = new GestureDescription.StrokeDescription(path, 0L, s.durMs, s.cont);
            } else {
                next = stroke.continueStroke(path, 0L, s.durMs, s.cont);
            }
            stroke = s.cont ? next : null;
            GestureDescription gesture = new GestureDescription.Builder().addStroke(next).build();
            boolean ok = auto.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    if (my != strokeGen) {
                        return;
                    }
                    GESTURE.noteDone(false);
                    pump();
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    if (my != strokeGen) {
                        return;
                    }
                    stroke = null;
                    GESTURE.noteDone(true);
                    pump();
                }
            }, handler());
            if (!ok) {
                stroke = null;
                strokeGen++;
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            stroke = null;
            strokeGen++;
            return false;
        }
    }
}

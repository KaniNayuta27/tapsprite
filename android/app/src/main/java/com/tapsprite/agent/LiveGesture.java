package com.tapsprite.agent;

/**
 * Tap / long-press / drag decisions for one live finger. Time is the caller's
 * clock (uptime millis). No Android types, so unit tests can run the timing.
 *
 * <p>A click must not start a continued stroke. Chaining {@code willContinue}
 * keep-alives on pointer-down holds the finger for the sum of every segment
 * (plus the gap while {@code up} waits for {@code onCompleted}). That sum
 * crosses the system long-press timeout (~400–500ms) on an ordinary click.
 * Nothing is dispatched until the gesture is known:
 * <ul>
 *   <li>up before {@link #HOLD_MS} with tiny movement → one 60ms tap</li>
 *   <li>still down at {@link #HOLD_MS} → a stationary press long enough to
 *       fire long-press</li>
 *   <li>movement past the slop → a short real-time stroke</li>
 * </ul>
 */
final class LiveGesture {
    static final int HOLD_MS = 450;
    /** One-shot click. Inside the 50–80ms window so the phone treats it as a tap. */
    static final int TAP_MS = 60;
    /** Longer than OEM long-press timeouts (400ms, some 500ms). */
    static final int LONG_MS = 560;
    static final int END_MS = 20;
    static final int KEEPALIVE_MS = 50;

    static final int OP_NONE = 0;
    static final int OP_TAP = 1;
    static final int OP_HOLD = 2;
    static final int OP_START = 3;
    static final int OP_CONT = 4;
    static final int OP_END = 5;
    static final int OP_SWIPE = 6;

    static final int KIND_TAP = 1;
    static final int KIND_LONG = 2;
    static final int KIND_DRAG = 3;

    private static final int IDLE = 0;
    private static final int PENDING = 1;
    private static final int STREAM = 2;
    private static final int ONESHOT = 3;

    private int phase;
    private int kind;
    private int ax;
    private int ay;
    private int lx;
    private int ly;
    private int cx;
    private int cy;
    private long t0;
    private long lastEmit;
    private long heldHint = -1;
    private boolean upSeen;
    private boolean abort;
    private boolean dispatching;
    private boolean open;
    private boolean fresh = true;
    private boolean cancelNext;
    private boolean waitingRetry;
    private boolean instant;
    private boolean queued;
    private int contact;
    private int burst;
    private int shotTries;
    private int pendingDur;
    private boolean pendingCont;
    private int qx;
    private int qy;
    private long qt;

    static final class Step {
        final int op;
        final int x0;
        final int y0;
        final int x1;
        final int y1;
        final int durMs;
        final boolean cont;
        final int kind;
        final String reason;
        /** -1 keep the hold timer, 0 cancel it, &gt;0 fire a tick at this uptime. */
        final long armAt;

        static final Step KEEP = new Step(OP_NONE, 0, 0, 0, 0, 0, false, 0, null, -1);
        static final Step DISARM = new Step(OP_NONE, 0, 0, 0, 0, 0, false, 0, null, 0);

        private Step(int op, int x0, int y0, int x1, int y1, int durMs, boolean cont,
                     int kind, String reason, long armAt) {
            this.op = op;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.durMs = durMs;
            this.cont = cont;
            this.kind = kind;
            this.reason = reason;
            this.armAt = armAt;
        }

        static Step armAt(long when) {
            return new Step(OP_NONE, 0, 0, 0, 0, 0, false, 0, null, when);
        }

        static Step go(int op, int x0, int y0, int x1, int y1, int dur, boolean cont, int kind, String reason) {
            return new Step(op, x0, y0, x1, y1, dur, cont, kind, reason, 0);
        }
    }

    /** API &lt; 26 has no continueStroke. Stationary keep-alives are skipped. */
    void setInstant(boolean on) {
        instant = on;
    }

    boolean busy() {
        return dispatching;
    }

    int contactMs() {
        return contact;
    }

    boolean aborted() {
        return abort;
    }

    Step down(long now, int x, int y) {
        if (phase == IDLE || (phase == PENDING && !dispatching)) {
            return beginPending(now, x, y);
        }
        queued = true;
        qx = x;
        qy = y;
        qt = now;
        upSeen = true;
        if (dispatching) {
            return Step.KEEP;
        }
        return follow(now);
    }

    Step move(long now, int x, int y) {
        if (phase == IDLE || phase == ONESHOT) {
            return Step.KEEP;
        }
        cx = x;
        cy = y;
        if (dispatching) {
            return Step.KEEP;
        }
        if (phase == PENDING) {
            if (!LiveTouch.leftSlop(ax, ay, x, y)) {
                return Step.KEEP;
            }
            return beginDrag(now);
        }
        if (waitingRetry) {
            waitingRetry = false;
        }
        return follow(now);
    }

    /**
     * @param clientHeldMs button-down time measured by the PC page, or -1 if absent.
     *                     Arrival delay must not turn that into a long press.
     */
    Step up(long now, int x, int y, long clientHeldMs) {
        if (phase == IDLE) {
            return Step.KEEP;
        }
        cx = x;
        cy = y;
        upSeen = true;
        if (clientHeldMs >= 0) {
            heldHint = clientHeldMs;
        }
        if (dispatching) {
            return Step.KEEP;
        }
        if (phase == PENDING) {
            return classifyPending(now);
        }
        if (waitingRetry) {
            waitingRetry = false;
        }
        return follow(now);
    }

    Step tick(long now) {
        if (waitingRetry) {
            waitingRetry = false;
            if (!dispatching) {
                return onCancel(now);
            }
        }
        if (dispatching) {
            return Step.KEEP;
        }
        if (phase == PENDING && !upSeen && now - t0 >= HOLD_MS) {
            if (LiveTouch.leftSlop(ax, ay, cx, cy)) {
                return beginDrag(now);
            }
            return beginLong(now);
        }
        if (phase == STREAM) {
            return follow(now);
        }
        return Step.KEEP;
    }

    Step lift(long now) {
        abort = true;
        upSeen = true;
        queued = false;
        if (phase == IDLE) {
            return Step.DISARM;
        }
        if (phase == PENDING && !dispatching) {
            return goIdle();
        }
        if (dispatching) {
            return Step.KEEP;
        }
        return follow(now);
    }

    /** The in-flight segment finished. Does not choose the next one. */
    void noteDone(boolean cancelled) {
        dispatching = false;
        if (cancelled) {
            open = false;
            fresh = true;
            pendingCont = false;
            cancelNext = true;
            return;
        }
        if (pendingCont) {
            contact += pendingDur;
            open = true;
        } else {
            open = false;
        }
        pendingCont = false;
        burst = 0;
        cancelNext = false;
    }

    /** Accessibility took the stroke away (script gesture). */
    void lostStroke() {
        dispatching = false;
        open = false;
        fresh = true;
        pendingCont = false;
        cancelNext = false;
        if (phase == ONESHOT) {
            phase = IDLE;
            upSeen = false;
            kind = 0;
        }
    }

    Step follow(long now) {
        if (dispatching) {
            return Step.KEEP;
        }
        if (cancelNext) {
            cancelNext = false;
            return onCancel(now);
        }
        if (waitingRetry) {
            return Step.KEEP;
        }
        if (phase == ONESHOT) {
            return finishOrRestart(now);
        }
        if (phase != STREAM) {
            return Step.KEEP;
        }
        if (!open) {
            if (!upSeen && !abort) {
                return onCancel(now);
            }
            return finishOrRestart(now);
        }
        if (upSeen || abort) {
            if (!abort && kind == KIND_LONG && contact < LONG_MS) {
                int dur = Math.min(80, LONG_MS - contact);
                return emit(now, fresh ? OP_START : OP_CONT, lx, ly, cx, cy, dur, true, 0, null);
            }
            return emitEnd(now);
        }
        if (cx != lx || cy != ly) {
            return emit(now, fresh ? OP_START : OP_CONT, lx, ly, cx, cy, segDur(now), true, 0, null);
        }
        if (instant) {
            return Step.KEEP;
        }
        return emit(now, fresh ? OP_START : OP_CONT, lx, ly, lx, ly, KEEPALIVE_MS, true, 0, null);
    }

    private Step beginPending(long now, int x, int y) {
        phase = PENDING;
        kind = 0;
        ax = lx = cx = x;
        ay = ly = cy = y;
        t0 = now;
        lastEmit = 0;
        heldHint = -1;
        upSeen = false;
        abort = false;
        dispatching = false;
        open = false;
        fresh = true;
        cancelNext = false;
        waitingRetry = false;
        queued = false;
        contact = 0;
        burst = 0;
        shotTries = 0;
        pendingCont = false;
        return Step.armAt(now + HOLD_MS);
    }

    private Step classifyPending(long now) {
        long held = heldHint >= 0 ? heldHint : Math.max(0, now - t0);
        if (held < 0) {
            held = 0;
        }
        if (LiveTouch.leftSlop(ax, ay, cx, cy)) {
            return beginSwipe(now, held);
        }
        if (held >= HOLD_MS) {
            return beginHold(now, held);
        }
        return beginTap(now, held);
    }

    private Step beginTap(long now, long held) {
        phase = ONESHOT;
        kind = KIND_TAP;
        shotTries = 1;
        open = false;
        fresh = true;
        return emit(now, OP_TAP, ax, ay, ax, ay, TAP_MS, false, KIND_TAP,
                "tap " + TAP_MS + "ms 抬起" + held + "ms");
    }

    private Step beginHold(long now, long held) {
        phase = ONESHOT;
        kind = KIND_LONG;
        shotTries = 1;
        open = false;
        fresh = true;
        return emit(now, OP_HOLD, ax, ay, ax, ay, LONG_MS, false, KIND_LONG,
                "long 按住" + held + "ms");
    }

    private Step beginLong(long now) {
        phase = STREAM;
        kind = KIND_LONG;
        open = false;
        fresh = true;
        long held = Math.max(0, now - t0);
        lx = ax;
        ly = ay;
        return emit(now, OP_START, ax, ay, ax, ay, LONG_MS, true, KIND_LONG,
                "long 按住" + held + "ms");
    }

    private Step beginDrag(long now) {
        phase = STREAM;
        kind = KIND_DRAG;
        open = false;
        fresh = true;
        return emit(now, OP_START, ax, ay, cx, cy, segDur(now), true, KIND_DRAG, "drag 移动出容差");
    }

    private Step beginSwipe(long now, long held) {
        phase = ONESHOT;
        kind = KIND_DRAG;
        shotTries = 1;
        open = false;
        fresh = true;
        int dur = (int) held;
        if (dur < 40) {
            dur = 40;
        }
        if (dur > 180) {
            dur = 180;
        }
        return emit(now, OP_SWIPE, ax, ay, cx, cy, dur, false, KIND_DRAG, "drag 移动出容差");
    }

    private Step emitEnd(long now) {
        int dur = END_MS;
        if (instant) {
            long held = Math.max(0, now - t0);
            dur = (int) Math.max(40, Math.min(10000, held));
            if (kind == KIND_LONG && dur < LONG_MS) {
                dur = LONG_MS;
            }
        }
        int op = fresh ? OP_START : OP_END;
        return emit(now, op, lx, ly, cx, cy, dur, false, 0, null);
    }

    private Step emit(long now, int op, int x0, int y0, int x1, int y1, int dur, boolean cont, int logKind, String reason) {
        if (dur < 1) {
            dur = 1;
        }
        dispatching = true;
        pendingDur = dur;
        pendingCont = cont;
        lastEmit = now;
        fresh = false;
        lx = x1;
        ly = y1;
        return Step.go(op, x0, y0, x1, y1, dur, cont, logKind, reason);
    }

    private int segDur(long now) {
        long since = lastEmit > 0 ? now - lastEmit : now - t0;
        if (since < 0) {
            since = 0;
        }
        return LiveTouch.moveDurationMs(since);
    }

    private Step onCancel(long now) {
        if (abort) {
            return finishOrRestart(now);
        }
        if (phase == ONESHOT) {
            if (queued) {
                return finishOrRestart(now);
            }
            if (kind == KIND_TAP && shotTries < 2) {
                shotTries++;
                fresh = true;
                return emit(now, OP_TAP, ax, ay, ax, ay, TAP_MS, false, 0, null);
            }
            if (kind == KIND_LONG && shotTries < 2) {
                shotTries++;
                fresh = true;
                return emit(now, OP_HOLD, ax, ay, ax, ay, LONG_MS, false, 0, null);
            }
            return finishOrRestart(now);
        }
        if (phase == STREAM && upSeen && kind == KIND_LONG && contact < LONG_MS) {
            phase = ONESHOT;
            shotTries = 1;
            fresh = true;
            return emit(now, OP_HOLD, cx, cy, cx, cy, LONG_MS, false, 0, null);
        }
        if (phase == STREAM && !upSeen) {
            burst++;
            if (burst > 4) {
                burst = 0;
                waitingRetry = true;
                return Step.armAt(now + 60);
            }
            fresh = true;
            if (kind == KIND_LONG && contact < LONG_MS) {
                return emit(now, OP_START, cx, cy, cx, cy, LONG_MS, true, 0, null);
            }
            int dur = segDur(now);
            return emit(now, OP_START, cx, cy, cx, cy, dur, true, 0, null);
        }
        return finishOrRestart(now);
    }

    private Step finishOrRestart(long now) {
        phase = IDLE;
        open = false;
        dispatching = false;
        upSeen = false;
        kind = 0;
        fresh = true;
        if (queued && !abort) {
            queued = false;
            int x = qx;
            int y = qy;
            long t = qt;
            return beginPending(t, x, y);
        }
        return goIdle();
    }

    private Step goIdle() {
        phase = IDLE;
        open = false;
        dispatching = false;
        upSeen = false;
        abort = false;
        kind = 0;
        fresh = true;
        queued = false;
        waitingRetry = false;
        cancelNext = false;
        return Step.DISARM;
    }
}

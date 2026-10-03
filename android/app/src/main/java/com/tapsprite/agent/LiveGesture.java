package com.tapsprite.agent;

/**
 * One live finger. Time is the caller's clock (uptime millis). No Android types.
 *
 * <p>On API 26+ the finger goes down on the down event. Short
 * {@code willContinue=true} segments keep it down while the button is held, so
 * a held mouse is already a press on the phone. A move is applied on the next
 * segment (tens of milliseconds, not a 560ms first stroke). Up ends the stroke.
 *
 * <p>API &lt; 26 has no {@code continueStroke}. That path waits and sends one
 * shot: a short tap, a long hold, or a swipe.
 */
final class LiveGesture {
    static final int HOLD_MS = 450;
    static final int TAP_MS = 60;
    static final int LONG_MS = 560;
    static final int END_MS = 20;
    /** Live segment. Short so a drag is not stuck behind a long first stroke. */
    static final int SEG_MS = 32;
    static final int KEEPALIVE_MS = SEG_MS;

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
    private long heldHint = -1;
    private boolean upSeen;
    private boolean abort;
    private boolean dispatching;
    private boolean fresh = true;
    private boolean cancelNext;
    private boolean waitingRetry;
    private boolean instant;
    private boolean queued;
    private boolean ending;
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

    /** API &lt; 26 has no continueStroke. */
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
        if (instant) {
            return downInstant(now, x, y);
        }
        if (phase == STREAM || phase == ONESHOT || phase == PENDING) {
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
        return beginStream(now, x, y);
    }

    Step move(long now, int x, int y) {
        if (instant) {
            return moveInstant(now, x, y);
        }
        if (phase != STREAM || ending) {
            return Step.KEEP;
        }
        cx = x;
        cy = y;
        if (dispatching || upSeen) {
            return Step.KEEP;
        }
        return followStream(now);
    }

    Step up(long now, int x, int y, long clientHeldMs) {
        if (instant) {
            return upInstant(now, x, y, clientHeldMs);
        }
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
        return follow(now);
    }

    Step tick(long now) {
        if (instant) {
            return tickInstant(now);
        }
        if (waitingRetry) {
            waitingRetry = false;
        }
        if (dispatching) {
            return Step.KEEP;
        }
        return follow(now);
    }

    Step lift(long now) {
        abort = true;
        upSeen = true;
        queued = false;
        if (phase == IDLE) {
            return Step.DISARM;
        }
        if (instant && phase == PENDING && !dispatching) {
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
            fresh = true;
            pendingCont = false;
            cancelNext = true;
            return;
        }
        if (pendingCont) {
            contact += pendingDur;
        }
        pendingCont = false;
        burst = 0;
        cancelNext = false;
    }

    /** Accessibility took the stroke away (script gesture). */
    void lostStroke() {
        dispatching = false;
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
        if (phase == ONESHOT) {
            return finishOrRestart(now);
        }
        if (instant || phase == PENDING) {
            return followInstant(now);
        }
        return followStream(now);
    }

    private Step followStream(long now) {
        if (waitingRetry) {
            return Step.KEEP;
        }
        if (cancelNext) {
            cancelNext = false;
            return onCancelStream(now);
        }
        if (phase != STREAM) {
            return Step.KEEP;
        }
        if (upSeen || abort) {
            if (ending) {
                return finishOrRestart(now);
            }
            ending = true;
            int dur = END_MS;
            if (cx != lx || cy != ly) {
                dur = Math.min(120, Math.max(END_MS, SEG_MS));
            }
            int op = fresh ? OP_START : OP_END;
            return emit(now, op, lx, ly, cx, cy, dur, false, 0, "up");
        }
        boolean drag = (cx != lx || cy != ly) && LiveTouch.leftSlop(ax, ay, cx, cy);
        if (drag) {
            String reason = kind == KIND_DRAG ? null : "drag";
            kind = KIND_DRAG;
            int logKind = reason != null ? KIND_DRAG : 0;
            return emit(now, fresh ? OP_START : OP_CONT, lx, ly, cx, cy, SEG_MS, true, logKind, reason);
        }
        return emit(now, fresh ? OP_START : OP_CONT, lx, ly, lx, ly, SEG_MS, true, 0, null);
    }

    private Step onCancelStream(long now) {
        if (abort) {
            return finishOrRestart(now);
        }
        if (ending || upSeen) {
            if (!queued && kind != KIND_DRAG && contact < HOLD_MS && shotTries < 1) {
                shotTries++;
                phase = ONESHOT;
                kind = KIND_TAP;
                fresh = true;
                return emit(now, OP_TAP, ax, ay, ax, ay, TAP_MS, false, 0, null);
            }
            return finishOrRestart(now);
        }
        burst++;
        if (burst > 4) {
            burst = 0;
            waitingRetry = true;
            return Step.armAt(now + 60);
        }
        fresh = true;
        return emit(now, OP_START, cx, cy, cx, cy, SEG_MS, true, 0, null);
    }

    private Step beginStream(long now, int x, int y) {
        phase = STREAM;
        kind = 0;
        ax = lx = cx = x;
        ay = ly = cy = y;
        t0 = now;
        heldHint = -1;
        upSeen = false;
        abort = false;
        dispatching = false;
        fresh = true;
        cancelNext = false;
        waitingRetry = false;
        queued = false;
        ending = false;
        contact = 0;
        burst = 0;
        shotTries = 0;
        pendingCont = false;
        return emit(now, OP_START, x, y, x, y, SEG_MS, true, 0, "down");
    }

    private Step downInstant(long now, int x, int y) {
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
        return followInstant(now);
    }

    private Step moveInstant(long now, int x, int y) {
        if (phase == IDLE || phase == ONESHOT) {
            return Step.KEEP;
        }
        cx = x;
        cy = y;
        return Step.KEEP;
    }

    private Step upInstant(long now, int x, int y, long clientHeldMs) {
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
        return followInstant(now);
    }

    private Step tickInstant(long now) {
        if (waitingRetry) {
            waitingRetry = false;
        }
        if (dispatching) {
            return Step.KEEP;
        }
        if (phase == PENDING && !upSeen && now - t0 >= HOLD_MS) {
            if (LiveTouch.leftSlop(ax, ay, cx, cy)) {
                return beginSwipe(now, Math.max(0, now - t0));
            }
            return beginHold(now, Math.max(0, now - t0));
        }
        return Step.KEEP;
    }

    private Step followInstant(long now) {
        if (cancelNext) {
            cancelNext = false;
            if (phase == ONESHOT && kind == KIND_TAP && shotTries < 2 && !abort && !queued) {
                shotTries++;
                fresh = true;
                return emit(now, OP_TAP, ax, ay, ax, ay, TAP_MS, false, 0, null);
            }
            return finishOrRestart(now);
        }
        if (phase == ONESHOT || phase == PENDING) {
            return finishOrRestart(now);
        }
        return Step.KEEP;
    }

    private Step beginPending(long now, int x, int y) {
        phase = PENDING;
        kind = 0;
        ax = lx = cx = x;
        ay = ly = cy = y;
        t0 = now;
        heldHint = -1;
        upSeen = false;
        abort = false;
        dispatching = false;
        fresh = true;
        cancelNext = false;
        waitingRetry = false;
        queued = false;
        ending = false;
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
        fresh = true;
        return emit(now, OP_TAP, ax, ay, ax, ay, TAP_MS, false, KIND_TAP,
                "tap " + TAP_MS + "ms 抬起" + held + "ms");
    }

    private Step beginHold(long now, long held) {
        phase = ONESHOT;
        kind = KIND_LONG;
        shotTries = 1;
        fresh = true;
        return emit(now, OP_HOLD, ax, ay, ax, ay, LONG_MS, false, KIND_LONG,
                "long 按住" + held + "ms");
    }

    private Step beginSwipe(long now, long held) {
        phase = ONESHOT;
        kind = KIND_DRAG;
        shotTries = 1;
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

    private Step emit(long now, int op, int x0, int y0, int x1, int y1, int dur, boolean cont, int logKind, String reason) {
        if (dur < 1) {
            dur = 1;
        }
        dispatching = true;
        pendingDur = dur;
        pendingCont = cont;
        fresh = false;
        lx = x1;
        ly = y1;
        return Step.go(op, x0, y0, x1, y1, dur, cont, logKind, reason);
    }

    private Step finishOrRestart(long now) {
        phase = IDLE;
        dispatching = false;
        upSeen = false;
        kind = 0;
        fresh = true;
        ending = false;
        if (queued && !abort) {
            queued = false;
            int x = qx;
            int y = qy;
            long t = qt;
            if (instant) {
                return beginPending(t, x, y);
            }
            return beginStream(t, x, y);
        }
        return goIdle();
    }

    private Step goIdle() {
        phase = IDLE;
        dispatching = false;
        upSeen = false;
        abort = false;
        kind = 0;
        fresh = true;
        queued = false;
        waitingRetry = false;
        cancelNext = false;
        ending = false;
        return Step.DISARM;
    }
}

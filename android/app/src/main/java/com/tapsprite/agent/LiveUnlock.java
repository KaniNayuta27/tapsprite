package com.tapsprite.agent;

/**
 * Unlock geometry for 实时操控. requestDismissKeyguard cancels on some phones,
 * so the working path is: wake, wait until the keyguard is ready, then swipe
 * up from near the bottom edge to well above the middle so the PIN pad appears.
 * The swipe is repeated once. Pure math so unit tests can pin the fractions.
 */
final class LiveUnlock {
    /** After the screen turns on, before the first swipe. */
    static final int WAKE_WAIT_MS = 800;
    /** Let the translucent wake activity leave before the finger hits the keyguard. */
    static final int AFTER_FINISH_MS = 250;
    /** Accessibility swipe duration. Slow enough for the keyguard to track it. */
    static final int SWIPE_MS = 500;
    /** How many times to swipe. The first one is often eaten by the wake animation. */
    static final int PASSES = 2;
    /** Pause between the two swipes. */
    static final int REPEAT_GAP_MS = 400;

    static final double X_FRAC = 0.50;
    /** Start almost at the bottom edge (navigation / gesture zone). */
    static final double Y_FROM = 0.98;
    /** End high enough that a short flick cannot bounce back to the clock. */
    static final double Y_TO = 0.20;

    private LiveUnlock() {
    }

    /**
     * {@code [x, yFrom, yTo, durationMs]}. x is 50% of the width. The finger
     * starts at {@link #Y_FROM} of the height and moves to {@link #Y_TO}.
     * Pixels are the same space as {@code getRealMetrics} / window metrics
     * ({@code [0, size-1]}), which is what the logged 540,2279 pair was.
     */
    static int[] swipePx(int w, int h) {
        if (w < 2) {
            w = 2;
        }
        if (h < 2) {
            h = 2;
        }
        int x = LiveCoords.axisToPx(X_FRAC, w);
        int y0 = LiveCoords.axisToPx(Y_FROM, h);
        int y1 = LiveCoords.axisToPx(Y_TO, h);
        if (y1 >= y0) {
            y1 = Math.max(0, y0 - 1);
        }
        return new int[]{x, y0, y1, SWIPE_MS};
    }
}

package com.tapsprite.agent;

/**
 * Unlock geometry for 实时操控. requestDismissKeyguard cancels on some phones,
 * so the working path is: wake, wait, swipe up from the bottom center to reveal
 * the PIN pad. Pure math so unit tests can pin the fractions.
 */
final class LiveUnlock {
    /** After the screen turns on, before the swipe. Inside 400–600ms. */
    static final int WAKE_WAIT_MS = 500;
    /** Accessibility swipe duration. Inside 250–350ms. */
    static final int SWIPE_MS = 300;

    private LiveUnlock() {
    }

    /**
     * {@code [x, yFrom, yTo, durationMs]}. x is 50% of the width. The finger
     * starts at 95% of the height and moves to 40% (upward).
     */
    static int[] swipePx(int w, int h) {
        if (w < 2) {
            w = 2;
        }
        if (h < 2) {
            h = 2;
        }
        int x = LiveCoords.axisToPx(0.50, w);
        int y0 = LiveCoords.axisToPx(0.95, h);
        int y1 = LiveCoords.axisToPx(0.40, h);
        if (y1 >= y0) {
            y1 = Math.max(0, y0 - 1);
        }
        return new int[]{x, y0, y1, SWIPE_MS};
    }
}

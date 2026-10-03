package com.tapsprite.agent;

/** Pure helpers for the live finger. Pixels are physical gesture pixels. */
final class LiveTouch {
    static final int SLOP_PX = 16;

    private LiveTouch() {
    }

    /** True once the finger has left the long-press deadzone. */
    static boolean leftSlop(int x0, int y0, int x1, int y1) {
        long dx = (long) x1 - x0;
        long dy = (long) y1 - y0;
        return dx * dx + dy * dy >= (long) SLOP_PX * SLOP_PX;
    }

    /** Move-segment duration. Each segment is its own gesture, so keep it short. */
    static int moveDurationMs(long sinceDispatchMs) {
        if (sinceDispatchMs < 20) {
            return 20;
        }
        if (sinceDispatchMs > 40) {
            return 40;
        }
        return (int) sinceDispatchMs;
    }
}

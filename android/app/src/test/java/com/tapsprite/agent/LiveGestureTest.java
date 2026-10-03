package com.tapsprite.agent;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Timing of the live finger. A fast click must be one short tap. Segment
 * keep-alives must not run before that decision, or their durations add up
 * past the long-press timeout.
 */
public class LiveGestureTest {
    private static boolean fired(LiveGesture.Step s) {
        return s.op != LiveGesture.OP_NONE;
    }

    private static int continuedMs(List<LiveGesture.Step> steps) {
        int ms = 0;
        for (int i = 0; i < steps.size(); i++) {
            LiveGesture.Step s = steps.get(i);
            if (s.cont) {
                ms += s.durMs;
            }
        }
        return ms;
    }

    @Test
    public void fastClickIsOneShortTap() {
        LiveGesture g = new LiveGesture();
        List<LiveGesture.Step> fired = new ArrayList<LiveGesture.Step>();
        assertFalse(fired(g.down(1000, 100, 200)));
        // The old path dispatched a 20ms continued stroke here, then a 50ms
        // keep-alive on every completion while up was still queued.
        for (int t = 1020; t <= 1240; t += 20) {
            LiveGesture.Step tick = g.tick(t);
            assertEquals(LiveGesture.OP_NONE, tick.op);
            if (fired(tick)) {
                fired.add(tick);
            }
        }
        LiveGesture.Step up = g.up(1250, 100, 204, 180);
        assertTrue(fired(up));
        fired.add(up);

        assertEquals(1, fired.size());
        assertEquals(LiveGesture.OP_TAP, up.op);
        assertEquals(LiveGesture.KIND_TAP, up.kind);
        assertFalse(up.cont);
        assertTrue(up.durMs >= 50 && up.durMs <= 80);
        assertEquals(60, up.durMs);
        assertEquals(100, up.x0);
        assertEquals(200, up.y0);
        assertTrue(up.reason.contains("tap"));
        assertEquals(0, continuedMs(fired));
        assertEquals(0, g.contactMs());

        g.noteDone(false);
        assertEquals(LiveGesture.OP_NONE, g.follow(1320).op);
    }

    @Test
    public void clickWithin250msIsTapNotLong() {
        LiveGesture g = new LiveGesture();
        g.down(0, 10, 10);
        g.tick(100);
        g.tick(200);
        LiveGesture.Step up = g.up(240, 12, 14, 250);
        assertEquals(LiveGesture.OP_TAP, up.op);
        assertFalse(up.cont);
        assertTrue(up.durMs <= 80);
        assertTrue(up.reason.startsWith("tap "));
    }

    @Test
    public void releaseJustUnderHoldThresholdIsStillTap() {
        LiveGesture g = new LiveGesture();
        g.down(0, 4, 8);
        assertEquals(LiveGesture.OP_NONE, g.tick(449).op);
        LiveGesture.Step up = g.up(449, 4, 8, 449);
        assertEquals(LiveGesture.OP_TAP, up.op);
        assertFalse(up.cont);
    }

    @Test
    public void holdAtThresholdBecomesLongPress() {
        LiveGesture g = new LiveGesture();
        g.down(0, 30, 40);
        assertEquals(LiveGesture.OP_NONE, g.tick(449).op);
        LiveGesture.Step s = g.tick(450);
        assertEquals(LiveGesture.OP_START, s.op);
        assertEquals(LiveGesture.KIND_LONG, s.kind);
        assertTrue(s.cont);
        assertTrue(s.durMs >= 500);
        assertEquals(LiveGesture.LONG_MS, s.durMs);
        assertTrue(s.reason.startsWith("long "));
        assertEquals(30, s.x0);
        assertEquals(40, s.y0);
        assertEquals(s.x0, s.x1);
        assertEquals(s.y0, s.y1);

        // Released a moment later, while the long segment is still in flight.
        // up must not replace it with a 60ms tap, and must not wait behind
        // another keep-alive once the segment completes.
        assertEquals(LiveGesture.OP_NONE, g.up(460, 30, 40, 460).op);
        g.noteDone(false);
        LiveGesture.Step end = g.follow(450 + s.durMs);
        assertEquals(LiveGesture.OP_END, end.op);
        assertFalse(end.cont);
        assertTrue(end.durMs <= 40);
        assertNotEquals(LiveGesture.OP_TAP, end.op);
        assertTrue(g.contactMs() >= 500);
    }

    @Test
    public void upAtThresholdBeforeTimerIsOneLongHold() {
        LiveGesture g = new LiveGesture();
        g.down(0, 1, 2);
        LiveGesture.Step s = g.up(450, 1, 2, 450);
        assertEquals(LiveGesture.OP_HOLD, s.op);
        assertEquals(LiveGesture.KIND_LONG, s.kind);
        assertFalse(s.cont);
        assertTrue(s.durMs >= 500);
        assertTrue(s.reason.contains("long"));
    }

    @Test
    public void clientHeldBeatsLateArrival() {
        // up packet shows up late on the phone clock, but the page measured 90ms.
        LiveGesture g = new LiveGesture();
        g.down(0, 8, 8);
        LiveGesture.Step s = g.up(800, 8, 9, 90);
        assertEquals(LiveGesture.OP_TAP, s.op);
        assertFalse(s.cont);
        assertTrue(s.durMs <= 80);
    }

    @Test
    public void dragIsRealtimeAndNotATap() {
        LiveGesture g = new LiveGesture();
        g.down(0, 0, 0);
        LiveGesture.Step start = g.move(30, 40, 0);
        assertEquals(LiveGesture.OP_START, start.op);
        assertEquals(LiveGesture.KIND_DRAG, start.kind);
        assertTrue(start.cont);
        assertTrue(start.durMs >= 20 && start.durMs <= 40);
        assertEquals(0, start.x0);
        assertEquals(40, start.x1);
        assertTrue(start.reason.contains("drag"));

        g.noteDone(false);
        LiveGesture.Step keep = g.follow(40);
        assertEquals(LiveGesture.OP_CONT, keep.op);
        assertEquals(LiveGesture.KEEPALIVE_MS, keep.durMs);
        assertTrue(keep.cont);
        assertEquals(0, keep.kind);

        assertEquals(LiveGesture.OP_NONE, g.move(55, 80, 5).op);
        g.noteDone(false);
        LiveGesture.Step cont = g.follow(70);
        assertEquals(LiveGesture.OP_CONT, cont.op);
        assertEquals(80, cont.x1);
        assertEquals(5, cont.y1);
        assertTrue(cont.durMs <= 40);
        assertTrue(cont.cont);

        assertEquals(LiveGesture.OP_NONE, g.up(100, 80, 5, 100).op);
        g.noteDone(false);
        LiveGesture.Step end = g.follow(110);
        assertEquals(LiveGesture.OP_END, end.op);
        assertFalse(end.cont);
        assertTrue(end.durMs <= 40);
        assertTrue(g.contactMs() < LiveGesture.HOLD_MS);
    }

    @Test
    public void slopBoundary() {
        LiveGesture g = new LiveGesture();
        g.down(0, 0, 0);
        assertEquals(LiveGesture.OP_NONE, g.move(20, 15, 0).op);
        assertEquals(LiveGesture.OP_TAP, g.up(80, 15, 0, 80).op);

        LiveGesture d = new LiveGesture();
        d.down(0, 0, 0);
        LiveGesture.Step drag = d.move(20, 16, 0);
        assertEquals(LiveGesture.OP_START, drag.op);
        assertEquals(LiveGesture.KIND_DRAG, drag.kind);
    }

    @Test
    public void upPastSlopWithoutMovesIsAShortSwipe() {
        LiveGesture g = new LiveGesture();
        g.down(0, 0, 0);
        LiveGesture.Step s = g.up(90, 30, 0, 90);
        assertEquals(LiveGesture.OP_SWIPE, s.op);
        assertEquals(LiveGesture.KIND_DRAG, s.kind);
        assertFalse(s.cont);
        assertTrue(s.durMs <= 180);
        assertEquals(0, s.x0);
        assertEquals(30, s.x1);
    }

    @Test
    public void cancelledTapRetriesOnceAndDoesNotHold() {
        LiveGesture g = new LiveGesture();
        g.down(0, 3, 3);
        LiveGesture.Step tap = g.up(70, 3, 3, 70);
        assertEquals(LiveGesture.OP_TAP, tap.op);
        g.noteDone(true);
        LiveGesture.Step retry = g.follow(120);
        assertEquals(LiveGesture.OP_TAP, retry.op);
        assertFalse(retry.cont);
        assertEquals(0, retry.kind);
        assertTrue(retry.durMs <= 80);
        g.noteDone(true);
        LiveGesture.Step done = g.follow(180);
        assertEquals(LiveGesture.OP_NONE, done.op);
        assertEquals(0, g.contactMs());
    }

    @Test
    public void cancelledLongBeforeContactStillHoldsWhenUpArrives() {
        LiveGesture g = new LiveGesture();
        g.down(0, 30, 40);
        assertEquals(LiveGesture.OP_START, g.tick(450).op);
        g.noteDone(true);
        LiveGesture.Step again = g.up(500, 30, 40, 500);
        assertEquals(LiveGesture.OP_HOLD, again.op);
        assertFalse(again.cont);
        assertTrue(again.durMs >= 500);
        assertNotEquals(LiveGesture.OP_TAP, again.op);
    }

    @Test
    public void cancelledDragDoesNotBecomeLongPress() {
        LiveGesture g = new LiveGesture();
        g.down(0, 0, 0);
        assertEquals(LiveGesture.OP_START, g.move(25, 40, 0).op);
        g.noteDone(true);
        LiveGesture.Step again = g.follow(50);
        assertEquals(LiveGesture.OP_START, again.op);
        assertTrue(again.cont);
        assertTrue(again.durMs <= 80);
        assertEquals(0, again.kind);
        assertTrue(again.durMs < LiveGesture.HOLD_MS);
    }

    @Test
    public void unlockSwipeRunsBottomCenterUpward() {
        assertTrue(LiveUnlock.WAKE_WAIT_MS >= 400 && LiveUnlock.WAKE_WAIT_MS <= 600);
        assertTrue(LiveUnlock.SWIPE_MS >= 250 && LiveUnlock.SWIPE_MS <= 350);
        int[] s = LiveUnlock.swipePx(1080, 2400);
        assertEquals(LiveCoords.axisToPx(0.50, 1080), s[0]);
        assertEquals(LiveCoords.axisToPx(0.95, 2400), s[1]);
        assertEquals(LiveCoords.axisToPx(0.40, 2400), s[2]);
        assertEquals(LiveUnlock.SWIPE_MS, s[3]);
        assertTrue(s[1] > s[2]);
        assertEquals(540, s[0]);
    }
}

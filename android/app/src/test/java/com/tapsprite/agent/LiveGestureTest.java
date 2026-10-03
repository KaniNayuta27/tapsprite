package com.tapsprite.agent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The live finger goes down immediately and stays down in short continued
 * segments. A click is that stroke ended quickly. A hold is the same stroke
 * still down. A drag updates on the next short segment, not after 560ms.
 */
public class LiveGestureTest {
    private static boolean fired(LiveGesture.Step s) {
        return s.op != LiveGesture.OP_NONE;
    }

    @Test
    public void downDispatchesImmediately() {
        LiveGesture g = new LiveGesture();
        LiveGesture.Step s = g.down(1000, 100, 200);
        assertEquals(LiveGesture.OP_START, s.op);
        assertTrue(s.cont);
        assertTrue(s.durMs <= 40);
        assertEquals(LiveGesture.SEG_MS, s.durMs);
        assertEquals(100, s.x0);
        assertEquals(200, s.y0);
        assertEquals(s.x0, s.x1);
        assertEquals(s.y0, s.y1);
        assertEquals("down", s.reason);
        assertNotEquals(LiveGesture.LONG_MS, s.durMs);
    }

    @Test
    public void fastClickEndsWhileHeldTimeIsStillATap() {
        LiveGesture g = new LiveGesture();
        LiveGesture.Step down = g.down(1000, 100, 200);
        assertTrue(down.cont);
        // Released while the first segment is still in flight. Nothing else
        // may be queued behind a long stroke.
        assertFalse(fired(g.up(1080, 100, 204, 80)));
        g.noteDone(false);
        LiveGesture.Step end = g.follow(1080);
        assertEquals(LiveGesture.OP_END, end.op);
        assertFalse(end.cont);
        assertTrue(end.durMs <= 40);
        assertNotEquals(LiveGesture.OP_TAP, end.op);
        assertNotEquals(LiveGesture.OP_HOLD, end.op);
        assertTrue(g.contactMs() < LiveGesture.HOLD_MS);
        assertTrue(g.contactMs() <= down.durMs);

        g.noteDone(false);
        assertEquals(LiveGesture.OP_NONE, g.follow(1200).op);
    }

    @Test
    public void holdStaysDownBeforeRelease() {
        LiveGesture g = new LiveGesture();
        LiveGesture.Step first = g.down(0, 30, 40);
        assertTrue(first.cont);
        g.noteDone(false);
        int guard = 0;
        while (g.contactMs() < LiveGesture.HOLD_MS && guard++ < 40) {
            LiveGesture.Step s = g.follow(guard * LiveGesture.SEG_MS);
            assertTrue(s.cont);
            assertTrue(s.durMs <= 40);
            assertNotEquals(LiveGesture.OP_TAP, s.op);
            assertNotEquals(LiveGesture.OP_HOLD, s.op);
            g.noteDone(false);
        }
        assertTrue(g.contactMs() >= LiveGesture.HOLD_MS);
        LiveGesture.Step still = g.follow(5000);
        assertTrue(still.cont);
        assertEquals(30, still.x0);
        assertEquals(40, still.y0);
        // up arrives while that keep-alive is in flight, so the phone is
        // already pressing. The completion ends the stroke.
        assertFalse(fired(g.up(5200, 30, 40, 5200)));
        g.noteDone(false);
        LiveGesture.Step end = g.follow(5300);
        assertEquals(LiveGesture.OP_END, end.op);
        assertFalse(end.cont);
    }

    @Test
    public void dragDuringFirstSegmentIsNotStuckFor560ms() {
        LiveGesture g = new LiveGesture();
        LiveGesture.Step first = g.down(0, 0, 0);
        assertTrue(first.durMs <= 40);
        assertNotEquals(LiveGesture.LONG_MS, first.durMs);
        // Move while that first segment is in flight. It must land on the
        // next segment, whose duration is also short.
        assertFalse(fired(g.move(10, 80, 5)));
        g.noteDone(false);
        LiveGesture.Step drag = g.follow(first.durMs);
        assertEquals(LiveGesture.OP_CONT, drag.op);
        assertEquals(LiveGesture.KIND_DRAG, drag.kind);
        assertTrue(drag.cont);
        assertTrue(drag.durMs <= 40);
        assertEquals(0, drag.x0);
        assertEquals(80, drag.x1);
        assertEquals(5, drag.y1);
        assertEquals("drag", drag.reason);
        assertTrue(g.contactMs() <= first.durMs);
    }

    @Test
    public void laterDragSegmentHasNoRepeatReason() {
        LiveGesture g = new LiveGesture();
        g.down(0, 0, 0);
        g.noteDone(false);
        LiveGesture.Step first = g.move(10, 40, 0);
        assertEquals("drag", first.reason);
        assertEquals(LiveGesture.OP_CONT, first.op);
        g.noteDone(false);
        LiveGesture.Step next = g.move(40, 90, 0);
        assertEquals(LiveGesture.OP_CONT, next.op);
        assertEquals(90, next.x1);
        assertEquals(null, next.reason);
        assertTrue(next.durMs <= 40);
    }

    @Test
    public void slopDoesNotDrag() {
        LiveGesture g = new LiveGesture();
        g.down(0, 0, 0);
        g.noteDone(false);
        LiveGesture.Step s = g.move(20, 10, 0);
        assertTrue(s.cont);
        assertEquals(0, s.x1);
        assertEquals(0, s.y1);
        assertEquals(0, s.kind);
    }

    @Test
    public void cancelledDownRestartsAShortSegment() {
        LiveGesture g = new LiveGesture();
        assertEquals(LiveGesture.OP_START, g.down(0, 3, 4).op);
        g.noteDone(true);
        LiveGesture.Step again = g.follow(40);
        assertEquals(LiveGesture.OP_START, again.op);
        assertTrue(again.cont);
        assertTrue(again.durMs <= 40);
        assertTrue(again.durMs < LiveGesture.HOLD_MS);
    }

    @Test
    public void instantFastClickIsOneShortTap() {
        LiveGesture g = new LiveGesture();
        g.setInstant(true);
        assertFalse(fired(g.down(1000, 100, 200)));
        assertEquals(LiveGesture.OP_NONE, g.tick(1200).op);
        LiveGesture.Step up = g.up(1250, 100, 204, 180);
        assertEquals(LiveGesture.OP_TAP, up.op);
        assertEquals(LiveGesture.KIND_TAP, up.kind);
        assertFalse(up.cont);
        assertEquals(LiveGesture.TAP_MS, up.durMs);
        assertTrue(up.reason.contains("tap"));
        g.noteDone(false);
        assertEquals(LiveGesture.OP_NONE, g.follow(1320).op);
    }

    @Test
    public void instantHoldIsOneShot() {
        LiveGesture g = new LiveGesture();
        g.setInstant(true);
        g.down(0, 1, 2);
        LiveGesture.Step s = g.up(450, 1, 2, 450);
        assertEquals(LiveGesture.OP_HOLD, s.op);
        assertEquals(LiveGesture.KIND_LONG, s.kind);
        assertFalse(s.cont);
        assertEquals(LiveGesture.LONG_MS, s.durMs);
    }

    @Test
    public void instantClientHeldBeatsLateArrival() {
        LiveGesture g = new LiveGesture();
        g.setInstant(true);
        g.down(0, 8, 8);
        LiveGesture.Step s = g.up(800, 8, 9, 90);
        assertEquals(LiveGesture.OP_TAP, s.op);
        assertFalse(s.cont);
    }

    @Test
    public void unlockSwipeRunsFromNearTheBottomToHigh() {
        assertTrue(LiveUnlock.WAKE_WAIT_MS >= 800);
        assertTrue(LiveUnlock.SWIPE_MS >= 400 && LiveUnlock.SWIPE_MS <= 600);
        assertTrue(LiveUnlock.PASSES >= 2);
        int[] s = LiveUnlock.swipePx(1080, 2400);
        assertEquals(LiveCoords.axisToPx(0.50, 1080), s[0]);
        assertEquals(LiveCoords.axisToPx(0.98, 2400), s[1]);
        assertEquals(LiveCoords.axisToPx(0.20, 2400), s[2]);
        assertEquals(LiveUnlock.SWIPE_MS, s[3]);
        assertEquals(540, s[0]);
        assertTrue(s[1] > s[2]);
        assertTrue(s[1] > 2300);
        assertTrue(s[2] < 600);
        // The old 95% → 40% stroke on a 2400px panel was 2279 → 960.
        assertTrue(s[1] > 2279);
        assertTrue(s[2] < 960);
    }
}

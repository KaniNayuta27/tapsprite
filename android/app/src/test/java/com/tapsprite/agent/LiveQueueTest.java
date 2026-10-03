package com.tapsprite.agent;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LiveQueueTest {
    private static byte[] key(int mark) {
        return new byte[]{2, 1, (byte) mark};
    }

    private static byte[] delta(int mark) {
        return new byte[]{2, 0, (byte) mark};
    }

    @Test
    public void keepsThreeFramesInOrder() {
        LiveAuQueue q = new LiveAuQueue();
        assertFalse(q.offer(delta(1)));
        assertFalse(q.offer(delta(2)));
        assertFalse(q.offer(key(3)));
        assertEquals(3, q.size());
        assertFalse(q.waitingForKey());
        assertArrayEquals(delta(1), q.poll());
        assertArrayEquals(delta(2), q.poll());
        assertArrayEquals(key(3), q.poll());
        assertTrue(q.isEmpty());
        assertNull(q.poll());
    }

    @Test
    public void overflowDropsUntilKeyframe() {
        LiveAuQueue q = new LiveAuQueue();
        q.offer(delta(1));
        q.offer(delta(2));
        q.offer(delta(3));
        assertTrue(q.offer(delta(4)));
        assertTrue(q.waitingForKey());
        assertTrue(q.isEmpty());
        assertFalse(q.offer(delta(5)));
        assertTrue(q.isEmpty());
        assertFalse(q.offer(key(6)));
        assertFalse(q.waitingForKey());
        assertArrayEquals(key(6), q.poll());
    }

    @Test
    public void overflowKeepsIncomingKeyframeAndStillAsksForSync() {
        LiveAuQueue q = new LiveAuQueue();
        q.offer(delta(1));
        q.offer(delta(2));
        q.offer(delta(3));
        assertTrue(q.offer(key(9)));
        assertFalse(q.waitingForKey());
        assertArrayEquals(key(9), q.poll());
        assertTrue(q.isEmpty());
    }

    @Test
    public void slopIsSixteenPhonePixels() {
        assertFalse(LiveTouch.leftSlop(0, 0, 15, 0));
        assertTrue(LiveTouch.leftSlop(0, 0, 16, 0));
        assertFalse(LiveTouch.leftSlop(10, 10, 10, 10));
        assertTrue(LiveTouch.leftSlop(0, 0, 12, 12));
        assertEquals(20, LiveTouch.moveDurationMs(0));
        assertEquals(20, LiveTouch.moveDurationMs(20));
        assertEquals(33, LiveTouch.moveDurationMs(33));
        assertEquals(40, LiveTouch.moveDurationMs(40));
        assertEquals(40, LiveTouch.moveDurationMs(400));
    }

    @Test
    public void annexBKeepsStartCodes() {
        byte[] annex = new byte[]{0, 0, 0, 1, 0x65, 0x11};
        assertArrayEquals(annex, LiveAnnex.toAnnexB(annex));
    }

    @Test
    public void annexBConvertsEveryAvccNal() {
        byte[] avcc = new byte[]{
                0, 0, 0, 2, 0x67, 0x42,
                0, 0, 0, 2, 0x65, (byte) 0x88
        };
        byte[] got = LiveAnnex.toAnnexB(avcc);
        assertArrayEquals(new byte[]{
                0, 0, 0, 1, 0x67, 0x42,
                0, 0, 0, 1, 0x65, (byte) 0x88
        }, got);
    }

    @Test
    public void annexBPrefixesASingleRawNal() {
        byte[] raw = new byte[]{0x65, 0x00, 0x11};
        assertArrayEquals(new byte[]{0, 0, 0, 1, 0x65, 0x00, 0x11}, LiveAnnex.toAnnexB(raw));
    }
}

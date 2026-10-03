package com.tapsprite.agent;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiveCoordsTest {
    private static void assertNearCenter(int x, int y, int physW, int physH) {
        double cx = (physW - 1) / 2.0;
        double cy = (physH - 1) / 2.0;
        assertTrue("cx " + x + " vs " + cx, Math.abs(x - cx) <= 1.0);
        assertTrue("cy " + y + " vs " + cy, Math.abs(y - cy) <= 1.0);
    }

    @Test
    public void encodeSize_1080x2400_fits1280_keepsAspect() {
        int[] enc = LiveCoords.encodeSize(1080, 2400, 1280);
        assertArrayEquals(new int[]{576, 1280}, enc);
        assertEquals(0, enc[0] % 2);
        assertEquals(0, enc[1] % 2);
        assertTrue(Math.max(enc[0], enc[1]) <= 1280);
        double src = 1080 / 2400.0;
        double out = enc[0] / (double) enc[1];
        assertTrue(Math.abs(src - out) < 0.01);
    }

    @Test
    public void encodeSize_landscape() {
        int[] enc = LiveCoords.encodeSize(2400, 1080, 1280);
        assertArrayEquals(new int[]{1280, 576}, enc);
    }

    @Test
    public void portraitCornersAndCenter_ignoreEncoderScale() {
        // 1080x2400 logical, encoder 576x1280. Mapping uses physical pixels only.
        int[] enc = LiveCoords.encodeSize(1080, 2400, 1280);
        assertArrayEquals(new int[]{576, 1280}, enc);

        int[] tl = LiveCoords.toPhysical(0, 0, 1080, 2400, 0, false);
        assertArrayEquals(new int[]{0, 0}, tl);

        int[] br = LiveCoords.toPhysical(1, 1, 1080, 2400, 0, false);
        assertArrayEquals(new int[]{1079, 2399}, br);

        int[] c = LiveCoords.toPhysical(0.5, 0.5, 1080, 2400, 0, false);
        assertNearCenter(c[0], c[1], 1080, 2400);

        // Same normalized point at a different encoder size (full res) is identical.
        int[] a = LiveCoords.toPhysical(0.25, 0.75, 1080, 2400, 0, false);
        int[] b = LiveCoords.toPhysical(0.25, 0.75, 1080, 2400, 0, false);
        assertArrayEquals(a, b);
        assertEquals(LiveCoords.axisToPx(0.25, 1080), a[0]);
        assertEquals(LiveCoords.axisToPx(0.75, 2400), a[1]);
    }

    @Test
    public void landscapeLogical_rotation90_frameAlreadyLogical() {
        int[] tl = LiveCoords.toPhysical(0, 0, 2400, 1080, 90, false);
        assertArrayEquals(new int[]{0, 0}, tl);
        int[] br = LiveCoords.toPhysical(1, 1, 2400, 1080, 90, false);
        assertArrayEquals(new int[]{2399, 1079}, br);
        int[] c = LiveCoords.toPhysical(0.5, 0.5, 2400, 1080, 90, false);
        assertNearCenter(c[0], c[1], 2400, 1080);
        int[] enc = LiveCoords.encodeSize(2400, 1080, 1280);
        assertArrayEquals(new int[]{1280, 576}, enc);
    }

    @Test
    public void rotation180_upsideDownLogicalFrame_staysDirect() {
        int[] br = LiveCoords.toPhysical(1, 0, 1080, 2400, 180, false);
        assertArrayEquals(new int[]{1079, 0}, br);
    }

    @Test
    public void naturalBuffer_rotation90_mapsCornersWithin1px() {
        // Logical gesture space is landscape 2400x1080 (rotation 90).
        // Point is on the natural portrait buffer.
        int[] tl = LiveCoords.toPhysical(0, 0, 2400, 1080, 90, true);
        assertArrayEquals(new int[]{0, 1079}, tl);
        int[] br = LiveCoords.toPhysical(1, 1, 2400, 1080, 90, true);
        assertArrayEquals(new int[]{2399, 0}, br);
        int[] c = LiveCoords.toPhysical(0.5, 0.5, 2400, 1080, 90, true);
        assertNearCenter(c[0], c[1], 2400, 1080);
    }

    @Test
    public void naturalBuffer_rotation270_and180() {
        int[] tl = LiveCoords.toPhysical(0, 0, 2400, 1080, 270, true);
        assertArrayEquals(new int[]{2399, 0}, tl);
        int[] br = LiveCoords.toPhysical(1, 1, 2400, 1080, 270, true);
        assertArrayEquals(new int[]{0, 1079}, br);

        int[] p = LiveCoords.toPhysical(0, 0, 1080, 2400, 180, true);
        assertArrayEquals(new int[]{1079, 2399}, p);
        int[] q = LiveCoords.toPhysical(1, 1, 1080, 2400, 180, true);
        assertArrayEquals(new int[]{0, 0}, q);
    }

    @Test
    public void clampsAndRejectsBadRotation() {
        int[] p = LiveCoords.toPhysical(-1, 2, 100, 200, 0, false);
        assertArrayEquals(new int[]{0, 199}, p);
        try {
            LiveCoords.toPhysical(0, 0, 100, 200, 45, false);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected bad rotation");
    }
}

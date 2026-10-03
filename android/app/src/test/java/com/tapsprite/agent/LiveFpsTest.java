package com.tapsprite.agent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveFpsTest {
    @Test
    public void missingAndLowStayAt30() {
        assertEquals(30, LiveFps.normalize(0));
        assertEquals(30, LiveFps.normalize(1));
        assertEquals(30, LiveFps.normalize(30));
        assertEquals(30, LiveFps.normalize(59));
    }

    @Test
    public void sixtyAndAboveIsTheHighMode() {
        assertEquals(60, LiveFps.normalize(60));
        assertEquals(60, LiveFps.normalize(120));
    }
}

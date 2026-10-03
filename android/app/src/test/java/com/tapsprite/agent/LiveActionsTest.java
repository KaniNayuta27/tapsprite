package com.tapsprite.agent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LiveActionsTest {
    @Test
    public void api24_baseKeys_noLockOrScreenshot() {
        assertEquals(LiveActions.BACK, LiveActions.codeFor("back", 24));
        assertEquals(LiveActions.HOME, LiveActions.codeFor("home", 24));
        assertEquals(LiveActions.RECENTS, LiveActions.codeFor("recents", 24));
        assertEquals(LiveActions.NOTIFICATIONS, LiveActions.codeFor("notifications", 24));
        assertEquals(LiveActions.QUICK_SETTINGS, LiveActions.codeFor("quicksettings", 24));
        assertEquals(LiveActions.QUICK_SETTINGS, LiveActions.codeFor("qs", 24));
        assertEquals(LiveActions.POWER_DIALOG, LiveActions.codeFor("power", 24));
        assertEquals(-1, LiveActions.codeFor("lock", 24));
        assertEquals(-1, LiveActions.codeFor("screenshot", 24));
        assertEquals(-1, LiveActions.codeFor("enter", 24));
        assertEquals(-1, LiveActions.codeFor("nope", 24));
        assertEquals(-1, LiveActions.codeFor(null, 24));
        assertEquals(
                "[\"back\",\"home\",\"recents\",\"notifications\",\"quicksettings\",\"power\"]",
                LiveActions.actionsJson(24));
    }

    @Test
    public void api27_stillHidesLockAndScreenshot() {
        assertEquals(-1, LiveActions.codeFor("lock", 27));
        assertEquals(-1, LiveActions.codeFor("screenshot", 27));
        assertEquals(LiveActions.POWER_DIALOG, LiveActions.codeFor("power", 27));
        assertFalse(LiveActions.actionsJson(27).contains("lock"));
        assertFalse(LiveActions.actionsJson(27).contains("screenshot"));
    }

    @Test
    public void api28_lockAndScreenshot() {
        assertEquals(LiveActions.LOCK_SCREEN, LiveActions.codeFor("lock", 28));
        assertEquals(LiveActions.TAKE_SCREENSHOT, LiveActions.codeFor("screenshot", 28));
        assertEquals(
                "[\"back\",\"home\",\"recents\",\"notifications\",\"quicksettings\",\"power\",\"lock\",\"screenshot\"]",
                LiveActions.actionsJson(28));
    }

    @Test
    public void olderApis_dropWhatTheyCannotRun() {
        assertEquals(LiveActions.BACK, LiveActions.codeFor("back", 16));
        assertEquals(-1, LiveActions.codeFor("quicksettings", 16));
        assertEquals(-1, LiveActions.codeFor("power", 16));
        assertEquals(LiveActions.QUICK_SETTINGS, LiveActions.codeFor("quicksettings", 17));
        assertEquals(-1, LiveActions.codeFor("power", 20));
        assertEquals(LiveActions.POWER_DIALOG, LiveActions.codeFor("power", 21));
        assertEquals(-1, LiveActions.codeFor("back", 15));
        assertEquals("[]", LiveActions.actionsJson(15));
    }
}

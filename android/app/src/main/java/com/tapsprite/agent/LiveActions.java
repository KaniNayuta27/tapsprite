package com.tapsprite.agent;

/**
 * Global actions for 实时操控. Integer values match
 * {@code AccessibilityService.GLOBAL_ACTION_*}. No Android APIs, so JVM tests
 * can check the API-level guard without a device.
 *
 * <ul>
 *   <li>back, home, recents, notifications — API 16</li>
 *   <li>quick settings — API 17</li>
 *   <li>power dialog — API 21</li>
 *   <li>lock screen, screenshot — API 28</li>
 * </ul>
 */
public final class LiveActions {
    public static final int BACK = 1;
    public static final int HOME = 2;
    public static final int RECENTS = 3;
    public static final int NOTIFICATIONS = 4;
    public static final int QUICK_SETTINGS = 5;
    public static final int POWER_DIALOG = 6;
    public static final int LOCK_SCREEN = 8;
    public static final int TAKE_SCREENSHOT = 9;

    private static final String[] ORDER = {
            "back", "home", "recents", "notifications", "quicksettings", "power", "lock", "screenshot"
    };

    private LiveActions() {
    }

    /** @return the GLOBAL_ACTION id, or -1 when the name is unknown or the API is too old. */
    public static int codeFor(String action, int api) {
        if (action == null) {
            return -1;
        }
        if ("back".equals(action)) {
            return api >= 16 ? BACK : -1;
        }
        if ("home".equals(action)) {
            return api >= 16 ? HOME : -1;
        }
        if ("recents".equals(action)) {
            return api >= 16 ? RECENTS : -1;
        }
        if ("notifications".equals(action)) {
            return api >= 16 ? NOTIFICATIONS : -1;
        }
        if ("quicksettings".equals(action) || "qs".equals(action)) {
            return api >= 17 ? QUICK_SETTINGS : -1;
        }
        if ("power".equals(action)) {
            return api >= 21 ? POWER_DIALOG : -1;
        }
        if ("lock".equals(action)) {
            return api >= 28 ? LOCK_SCREEN : -1;
        }
        if ("screenshot".equals(action)) {
            return api >= 28 ? TAKE_SCREENSHOT : -1;
        }
        return -1;
    }

    /** Canonical names this API level can run, in strip order. */
    public static String[] supported(int api) {
        int n = 0;
        for (int i = 0; i < ORDER.length; i++) {
            if (codeFor(ORDER[i], api) >= 0) {
                n++;
            }
        }
        String[] out = new String[n];
        int w = 0;
        for (int i = 0; i < ORDER.length; i++) {
            if (codeFor(ORDER[i], api) >= 0) {
                out[w++] = ORDER[i];
            }
        }
        return out;
    }

    public static String actionsJson(int api) {
        String[] names = supported(api);
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append('"').append(names[i]).append('"');
        }
        b.append(']');
        return b.toString();
    }
}

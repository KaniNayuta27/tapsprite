package com.tapsprite.agent;

import java.util.Locale;

/* loaded from: classes.dex */
public final class ShellInput {
    private ShellInput() {
    }

    public static boolean available() {
        return run(new String[]{"sh", "-c", "command -v input"}) || run(new String[]{"input", "keyevent", "0"});
    }

    public static boolean tap(float f, float f2) {
        boolean run = run(new String[]{"input", "tap", String.valueOf(Math.round(f)), String.valueOf(Math.round(f2))});
        AppState.log("input tap " + Math.round(f) + "," + Math.round(f2) + (run ? " ok" : " fail"));
        return run;
    }

    public static boolean swipe(float f, float f2, float f3, float f4, int i) {
        if (i <= 0) {
            i = 300;
        }
        return run(new String[]{"input", "swipe", String.valueOf(Math.round(f)), String.valueOf(Math.round(f2)), String.valueOf(Math.round(f3)), String.valueOf(Math.round(f4)), String.valueOf(i)});
    }

    public static boolean key(String str) {
        int i;
        String lowerCase = str == null ? "back" : str.toLowerCase(Locale.US);
        if ("home".equals(lowerCase)) {
            i = 3;
        } else if ("back".equals(lowerCase)) {
            i = 4;
        } else if ("recents".equals(lowerCase) || "recent".equals(lowerCase)) {
            i = 187;
        } else if ("enter".equals(lowerCase)) {
            i = 66;
        } else if ("menu".equals(lowerCase)) {
            i = 82;
        } else if (!"power".equals(lowerCase)) {
            i = 4;
        } else {
            i = 26;
        }
        boolean run = run(new String[]{"input", "keyevent", String.valueOf(i)});
        AppState.log("input keyevent " + lowerCase + "=" + i + (run ? " ok" : " fail"));
        return run;
    }

    static boolean run(String[] strArr) {
        try {
            return new ProcessBuilder(strArr).redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            AppState.log("input 执行失败 " + e.getMessage());
            return false;
        }
    }
}

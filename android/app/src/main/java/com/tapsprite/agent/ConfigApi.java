package com.tapsprite.agent;

import android.content.SharedPreferences;

/* loaded from: classes.dex */
public final class ConfigApi {
    private static final String PREF = "tapsprite_ui";

    private ConfigApi() {
    }

    static void initDefaults() {
        SharedPreferences prefs = prefs();
        SharedPreferences.Editor edit = prefs.edit();
        putDefault(edit, prefs, "多选框1", "true");
        putDefault(edit, prefs, "多选框2", "false");
        putDefault(edit, prefs, "多选框3", "false");
        putDefault(edit, prefs, "多选框4", "false");
        putDefault(edit, prefs, "多选框5", "false");
        putDefault(edit, prefs, "多选框6", "false");
        putDefault(edit, prefs, "多选框7", "false");
        putDefault(edit, prefs, "多选框8", "false");
        putDefault(edit, prefs, "多选框9", "false");
        putDefault(edit, prefs, "下拉框1", "0");
        putDefault(edit, prefs, "下拉框2", "0");
        putDefault(edit, prefs, "下拉框3", "7");
        putDefault(edit, prefs, "下拉框5", "0");
        edit.apply();
    }

    private static void putDefault(SharedPreferences.Editor editor, SharedPreferences sharedPreferences, String str, String str2) {
        if (!sharedPreferences.contains(str)) {
            editor.putString(str, str2);
        }
    }

    private static SharedPreferences prefs() {
        return App.ctx.getSharedPreferences(PREF, 0);
    }

    public static boolean readBool(String str, boolean z) {
        String string = prefs().getString(str, z ? "true" : "false");
        return "true".equalsIgnoreCase(string) || "1".equals(string) || "yes".equalsIgnoreCase(string);
    }

    public static int readInt(String str, int i) {
        try {
            return Integer.parseInt(prefs().getString(str, String.valueOf(i)).trim());
        } catch (Exception e) {
            return i;
        }
    }

    public static String readStr(String str, String str2) {
        SharedPreferences prefs = prefs();
        if (str2 == null) {
            str2 = "";
        }
        return prefs.getString(str, str2);
    }

    public static String read(String str, String str2) {
        if (str != null && str.startsWith("多选框")) {
            return readBool(str, parseBool(str2, false)) ? "true" : "false";
        }
        if (str != null && str.startsWith("下拉框")) {
            return String.valueOf(readInt(str, parseInt(str2, 0)));
        }
        return readStr(str, str2);
    }

    public static void write(String str, String str2) {
        SharedPreferences.Editor edit = prefs().edit();
        if (str2 == null) {
            str2 = "";
        }
        edit.putString(str, str2).apply();
    }

    public static void writeBool(String str, boolean z) {
        write(str, z ? "true" : "false");
    }

    public static void writeInt(String str, int i) {
        write(str, String.valueOf(i));
    }

    public static String dump() {
        String[][] strArr = {new String[]{"多选框1", "上课/打工/冒险"}, new String[]{"多选框2", "雇佣+投喂"}, new String[]{"多选框3", "小号循环"}, new String[]{"多选框6", "PK"}, new String[]{"多选框5", "只洗澡"}, new String[]{"多选框7", "给大号投喂"}, new String[]{"多选框8", "雇佣附带洗澡"}, new String[]{"多选框9", "大号洗澡"}};
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < 8) {
            sb.append(readBool(strArr[i][0], i == 0) ? "●  " : "○  ").append(strArr[i][1]).append('\n');
            i++;
        }
        sb.append("小号数量  ").append(readInt("下拉框3", 7) + 1);
        return sb.toString();
    }

    private static boolean parseBool(String str, boolean z) {
        if (str == null) {
            return z;
        }
        String trim = str.trim();
        if (trim.equalsIgnoreCase("true") || trim.equals("1")) {
            return true;
        }
        if (trim.equalsIgnoreCase("false") || trim.equals("0")) {
            return false;
        }
        return z;
    }

    private static int parseInt(String str, int i) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return i;
        }
    }
}

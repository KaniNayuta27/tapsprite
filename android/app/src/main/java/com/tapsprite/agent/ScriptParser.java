package com.tapsprite.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: classes.dex */
public final class ScriptParser {
    private static final Pattern KEY = Pattern.compile("(?i)^(?:keypress|key)\\s*\\(?\\s*[\"']?([A-Za-z]+)[\"']?\\s*\\)?$");
    private static final Pattern DELAY = Pattern.compile("(?i)^(?:delay|sleep)\\s*\\(?\\s*(\\d+)\\s*(?:ms)?\\s*\\)?$");
    private static final Pattern TAP = Pattern.compile("(?i)^(?:tap|click)\\s*\\(?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\)?$");
    private static final Pattern TOAST = Pattern.compile("(?i)^(?:toast|popup|alert)\\s*\\(?\\s*[\"']?(.*?)[\"']?\\s*\\)?$");
    private static final Pattern PRINT = Pattern.compile("(?i)^(?:print|log|echo|traceprint)\\s*\\(?\\s*[\"']?(.*?)[\"']?\\s*\\)?$");
    private static final Pattern CALL = Pattern.compile("(?i)^([A-Za-z][A-Za-z0-9_.]*)\\s*(?:\\((.*)\\)|(.*))$");

    public enum Kind {
        KEY,
        DELAY,
        TAP,
        TOAST,
        PRINT,
        CALL
    }

    public static final class Step {
        public final String[] args;
        public final String cmd;
        public final long delayMs;
        public final String key;
        public final Kind kind;
        public final int lineNo;
        public final String raw;
        public final String text;
        public final float x;
        public final float y;

        Step(Kind kind, int i, String str, String str2, long j, float f, float f2, String str3) {
            this(kind, i, str, str2, j, f, f2, str3, null, null);
        }

        Step(Kind kind, int i, String str, String str2, String[] strArr) {
            this(kind, i, str, null, 0L, 0.0f, 0.0f, null, str2, strArr);
        }

        Step(Kind kind, int i, String str, String str2, long j, float f, float f2, String str3, String str4, String[] strArr) {
            this.kind = kind;
            this.lineNo = i;
            this.raw = str;
            this.key = str2;
            this.delayMs = j;
            this.x = f;
            this.y = f2;
            this.text = str3;
            this.cmd = str4;
            this.args = strArr;
        }
    }

    public static final class Result {
        public final String error;
        public final List<Step> steps;

        Result(List<Step> list, String str) {
            this.steps = list;
            this.error = str;
        }
    }

    private ScriptParser() {
    }

    public static Result parse(String str) {
        ArrayList arrayList = new ArrayList();
        if (str == null) {
            return new Result(arrayList, "脚本为空");
        }
        String[] split = str.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < split.length; i++) {
            String str2 = split[i];
            String trim = stripComment(str2).trim();
            if (trim.length() != 0) {
                int i2 = i + 1;
                Matcher matcher = KEY.matcher(trim);
                if (matcher.matches()) {
                    String lowerCase = matcher.group(1).toLowerCase(Locale.US);
                    if (!lowerCase.equals("home") && !lowerCase.equals("back") && !lowerCase.equals("recents") && !lowerCase.equals("recent") && !lowerCase.equals("enter") && !lowerCase.equals("notifications") && !lowerCase.equals("notify") && !lowerCase.equals("quicksettings") && !lowerCase.equals("qs") && !lowerCase.equals("power") && !lowerCase.equals("screenshot") && !lowerCase.equals("lock") && !lowerCase.equals("volumeup") && !lowerCase.equals("volumedown") && !lowerCase.equals("volup") && !lowerCase.equals("voldown")) {
                        return new Result(arrayList, "第 " + i2 + " 行：不支持的按键 " + matcher.group(1));
                    }
                    arrayList.add(new Step(Kind.KEY, i2, str2, lowerCase, 0L, 0.0f, 0.0f, null));
                } else {
                    Matcher matcher2 = DELAY.matcher(trim);
                    if (matcher2.matches()) {
                        arrayList.add(new Step(Kind.DELAY, i2, str2, null, Long.parseLong(matcher2.group(1)), 0.0f, 0.0f, null));
                    } else {
                        Matcher matcher3 = TAP.matcher(trim);
                        if (matcher3.matches()) {
                            arrayList.add(new Step(Kind.TAP, i2, str2, null, 0L, Float.parseFloat(matcher3.group(1)), Float.parseFloat(matcher3.group(2)), null));
                        } else {
                            Matcher matcher4 = TOAST.matcher(trim);
                            if (matcher4.matches()) {
                                arrayList.add(new Step(Kind.TOAST, i2, str2, null, 0L, 0.0f, 0.0f, trimQuotes(matcher4.group(1))));
                            } else {
                                Matcher matcher5 = PRINT.matcher(trim);
                                if (matcher5.matches()) {
                                    arrayList.add(new Step(Kind.PRINT, i2, str2, null, 0L, 0.0f, 0.0f, trimQuotes(matcher5.group(1))));
                                } else {
                                    String lowerCase2 = trim.toLowerCase(Locale.US);
                                    if (lowerCase2.equals("end") || lowerCase2.equals("exit") || lowerCase2.equals("stop") || lowerCase2.equals("exitscript")) {
                                        arrayList.add(new Step(Kind.CALL, i2, str2, "ExitScript", new String[0]));
                                        break;
                                    }
                                    Matcher matcher6 = CALL.matcher(trim);
                                    if (matcher6.matches()) {
                                        String group = matcher6.group(1);
                                        String group2 = matcher6.group(2) != null ? matcher6.group(2) : matcher6.group(3);
                                        arrayList.add(new Step(Kind.CALL, i2, str2, group, parseArgs(group2 == null ? "" : group2.trim())));
                                    } else {
                                        AppState.log("跳过第 " + i2 + " 行：" + trim);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (arrayList.isEmpty()) {
            return new Result(arrayList, "脚本里没有可执行的步骤");
        }
        return new Result(arrayList, null);
    }

    static String[] parseArgs(String str) {
        ArrayList arrayList = new ArrayList();
        if (str == null) {
            return new String[0];
        }
        String trim = str.trim();
        if (trim.startsWith("(") && trim.endsWith(")") && trim.length() >= 2) {
            trim = trim.substring(1, trim.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        boolean z = false;
        char c = 0;
        for (int i = 0; i < trim.length(); i++) {
            char charAt = trim.charAt(i);
            if (z) {
                if (charAt == c) {
                    z = false;
                } else {
                    sb.append(charAt);
                }
            } else if (charAt == '\"' || charAt == '\'') {
                z = true;
                c = charAt;
            } else if (charAt == ',') {
                arrayList.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(charAt);
            }
        }
        if (sb.length() > 0 || trim.endsWith(",")) {
            arrayList.add(sb.toString().trim());
        }
        return (String[]) arrayList.toArray(new String[0]);
    }

    private static String stripComment(String str) {
        int indexOfComment = indexOfComment(str, '#');
        int indexOf = str.indexOf("//");
        int length = str.length();
        if (indexOfComment >= 0) {
            length = Math.min(length, indexOfComment);
        }
        if (indexOf >= 0) {
            length = Math.min(length, indexOf);
        }
        return str.substring(0, length);
    }

    private static int indexOfComment(String str, char c) {
        boolean z = false;
        char c2 = 0;
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (z) {
                if (charAt == c2) {
                    z = false;
                }
            } else if (charAt == '\"' || charAt == '\'') {
                z = true;
                c2 = charAt;
            } else if (charAt == c) {
                return i;
            }
        }
        return -1;
    }

    private static String trimQuotes(String str) {
        if (str == null) {
            return "";
        }
        String trim = str.trim();
        if (trim.length() >= 2) {
            char charAt = trim.charAt(0);
            char charAt2 = trim.charAt(trim.length() - 1);
            if ((charAt == '\"' && charAt2 == '\"') || (charAt == '\'' && charAt2 == '\'')) {
                return trim.substring(1, trim.length() - 1);
            }
        }
        return trim;
    }
}

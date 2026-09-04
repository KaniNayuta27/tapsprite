package com.tapsprite.agent;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: classes.dex */
public final class LuaPrep {
    private static final HashSet<String> KW = new HashSet<>();
    private static final Pattern CALL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_.]*)(\\s+)(.+)$");

    static {
        String[] strArr = {"and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"};
        for (int i = 0; i < 22; i++) {
            KW.add(strArr[i]);
        }
    }

    private LuaPrep() {
    }

    public static String toLua(String str) {
        if (str == null || str.length() == 0) {
            return "";
        }
        String[] split = str.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder(str.length() + 32);
        for (int i = 0; i < split.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(convertLine(split[i]));
        }
        return sb.toString();
    }

    private static String convertLine(String str) {
        String slashToDash = slashToDash(str);
        String trim = slashToDash.trim();
        if (trim.length() == 0 || trim.startsWith("--")) {
            return slashToDash;
        }
        int i = 0;
        while (i < slashToDash.length() && (slashToDash.charAt(i) == ' ' || slashToDash.charAt(i) == '\t')) {
            i++;
        }
        String substring = slashToDash.substring(0, i);
        String substring2 = slashToDash.substring(i);
        if (substring2.startsWith("local ") || (substring2.indexOf(61) > 0 && looksAssign(substring2))) {
            return slashToDash;
        }
        Matcher matcher = CALL.matcher(substring2);
        if (!matcher.matches()) {
            return slashToDash;
        }
        String group = matcher.group(1);
        if (KW.contains(group)) {
            return slashToDash;
        }
        String trim2 = matcher.group(3).trim();
        if (trim2.length() == 0 || trim2.charAt(0) == '(' || trim2.charAt(0) == '{' || trim2.charAt(0) == ':') {
            return slashToDash;
        }
        return substring + group + "(" + trim2 + ")";
    }

    private static boolean looksAssign(String str) {
        char charAt;
        int indexOf = str.indexOf(61);
        if (indexOf <= 0) {
            return false;
        }
        int i = indexOf + 1;
        if (i < str.length() && str.charAt(i) == '=') {
            return false;
        }
        if (indexOf > 0 && ((charAt = str.charAt(indexOf - 1)) == '~' || charAt == '<' || charAt == '>')) {
            return false;
        }
        return str.substring(0, indexOf).trim().matches("^(local\\s+)?[A-Za-z_][A-Za-z0-9_.]*$");
    }

    /* JADX WARN: Code restructure failed: missing block: B:41:0x007d, code lost:
    
        r0.append("--").append(r11.substring(r2 + 1));
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private static String slashToDash(String str) {
        int i;
        int i2;
        StringBuilder sb = new StringBuilder(str.length() + 2);
        int i3 = 0;
        boolean z = false;
        char c = 0;
        while (true) {
            if (i3 >= str.length()) {
                break;
            }
            char charAt = str.charAt(i3);
            if (z) {
                sb.append(charAt);
                if (charAt == c) {
                    z = false;
                }
            } else if (charAt == '\"' || charAt == '\'') {
                sb.append(charAt);
                c = charAt;
                z = true;
            } else if (charAt != '-' || (i2 = i3 + 1) >= str.length() || str.charAt(i2) != '-') {
                if (charAt == '/' && (i = i3 + 1) < str.length() && str.charAt(i) == '/') {
                    sb.append("--").append(str.substring(i3 + 2));
                    break;
                }
                if (charAt != '#' || (i3 != 0 && !Character.isWhitespace(str.charAt(i3 - 1)))) {
                    sb.append(charAt);
                }
            } else {
                sb.append(str.substring(i3));
                break;
            }
            i3++;
        }
        return sb.toString();
    }
}

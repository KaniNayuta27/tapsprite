package com.tapsprite.agent;


/* loaded from: classes.dex */
public final class ColorUtil {

    public static final class Spec {
        public final int b;
        public final int g;
        public final int r;
        public final int tb;
        public final int tg;
        public final int tr;

        Spec(int i, int i2, int i3, int i4, int i5, int i6) {
            this.r = i;
            this.g = i2;
            this.b = i3;
            this.tr = i4;
            this.tg = i5;
            this.tb = i6;
        }
    }

    private ColorUtil() {
    }

    public static int deltaFromSim(float f) {
        if (f <= 0.0f) {
            return 0;
        }
        if (f > 1.0f) {
            return Math.round(f);
        }
        if (f >= 0.999f) {
            return 0;
        }
        return Math.round((1.0f - f) * 255.0f);
    }

    public static Spec parse(String str) {
        String str2;
        if (str == null) {
            return new Spec(0, 0, 0, 0, 0, 0);
        }
        String trim = str.trim();
        if (trim.startsWith("#") || trim.startsWith("0x") || trim.startsWith("0X")) {
            trim = trim.startsWith("#") ? trim.substring(1) : trim.substring(2);
        }
        int indexOf = trim.indexOf(45);
        if (indexOf <= 0) {
            str2 = null;
        } else {
            String substring = trim.substring(0, indexOf);
            str2 = trim.substring(indexOf + 1);
            trim = substring;
        }
        int parseHex = parseHex(trim);
        int parseHex2 = parseHex(str2);
        return new Spec(parseHex & 255, (parseHex >> 8) & 255, (parseHex >> 16) & 255, parseHex2 & 255, (parseHex2 >> 8) & 255, (parseHex2 >> 16) & 255);
    }

    public static int parseHex(String str) {
        if (str == null || str.length() == 0) {
            return 0;
        }
        String trim = str.trim();
        if (trim.startsWith("#")) {
            trim = trim.substring(1);
        }
        if (trim.length() > 6) {
            trim = trim.substring(trim.length() - 6);
        }
        try {
            return (int) Long.parseLong(trim, 16);
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean match(int i, Spec spec, int i2) {
        return Math.abs(((i >> 16) & 255) - spec.r) <= spec.tr + i2 && Math.abs(((i >> 8) & 255) - spec.g) <= spec.tg + i2 && Math.abs((i & 255) - spec.b) <= spec.tb + i2;
    }

    public static String hex(int i) {
        return String.format("%02X%02X%02X", Integer.valueOf(i & 255), Integer.valueOf((i >> 8) & 255), Integer.valueOf((i >> 16) & 255));
    }
}

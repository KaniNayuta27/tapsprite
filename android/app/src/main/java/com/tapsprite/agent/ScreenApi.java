package com.tapsprite.agent;

import android.graphics.Bitmap;
import com.tapsprite.agent.ColorUtil;
import java.io.File;
import java.io.FileOutputStream;

/* loaded from: classes.dex */
public final class ScreenApi {
    static volatile Bitmap frozen;
    static volatile boolean keep;
    public static final PointXY last = new PointXY();
    private static boolean warned;

    private ScreenApi() {
    }

    public static boolean ready() {
        return CaptureService.ready;
    }

    public static void keepScreen(boolean z) {
        keep = z;
        if (z) {
            Bitmap grabLive = grabLive();
            if (grabLive != null) {
                frozen = grabLive;
            }
            AppState.log("KeepScreen " + (frozen != null ? "已冻结画面" : "失败：还没有截屏"));
            return;
        }
        frozen = null;
        AppState.log("KeepScreen 关闭");
    }

    public static Bitmap grab() {
        if (keep && frozen != null && !frozen.isRecycled()) {
            return frozen;
        }
        return waitLive();
    }

    public static Bitmap grabLive() {
        return waitLive();
    }

    static Bitmap waitLive() {
        Bitmap copyLatest;
        for (int i = 0; i < 30; i++) {
            CaptureService captureService = CaptureService.instance;
            if (captureService != null && (copyLatest = captureService.copyLatest()) != null) {
                return copyLatest;
            }
            try {
                Thread.sleep(40L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        noteNoCapture();
        return null;
    }

    static void recycleIfTemp(Bitmap bitmap) {
        if (bitmap != null && bitmap != frozen && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public static String snapShot() {
        return snapShotTo("snapshot.png");
    }

    public static String snapShotTo(String str) {
        Bitmap grabLive = grabLive();
        if (grabLive == null) {
            return "";
        }
        if (str == null || str.length() == 0) {
            str = "snapshot.png";
        }
        if (!str.toLowerCase().endsWith(".png") && !str.contains("/")) {
            str = str + ".png";
        }
        try {
            File file = str.startsWith("/") ? new File(str) : new File(FileApi.dir(), str);
            File parentFile = file.getParentFile();
            if (parentFile != null) {
                parentFile.mkdirs();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            grabLive.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            fileOutputStream.close();
            AppState.log("SnapShot " + grabLive.getWidth() + "x" + grabLive.getHeight() + " → " + file.getAbsolutePath());
            return file.getAbsolutePath();
        } catch (Exception e) {
            AppState.log("SnapShot 失败：" + e.getMessage());
            return "";
        } finally {
            recycleIfTemp(grabLive);
        }
    }

    public static String getPixelColor(int i, int i2) {
        Bitmap grab = grab();
        if (grab == null) {
            last.clear();
            return "000000";
        }
        if (i >= 0 && i2 >= 0) {
            try {
                if (i < grab.getWidth() && i2 < grab.getHeight()) {
                    PointXY pointXY = last;
                    pointXY.x = i;
                    pointXY.y = i2;
                    return ColorUtil.hex(grab.getPixel(i, i2));
                }
            } finally {
                recycleIfTemp(grab);
            }
        }
        last.clear();
        return "000000";
    }

    public static boolean cmpColor(int i, int i2, String str, float f) {
        Bitmap grab = grab();
        if (grab == null) {
            return false;
        }
        if (i >= 0 && i2 >= 0) {
            try {
                if (i < grab.getWidth() && i2 < grab.getHeight()) {
                    return ColorUtil.match(grab.getPixel(i, i2), ColorUtil.parse(str), ColorUtil.deltaFromSim(f));
                }
            } finally {
                recycleIfTemp(grab);
            }
        }
        return false;
    }

    public static boolean cmpColorEx(String str, float f) {
        Bitmap grab;
        if (str == null || str.length() == 0 || (grab = grab()) == null) {
            return false;
        }
        try {
            int deltaFromSim = ColorUtil.deltaFromSim(f);
            for (String str2 : str.split(",")) {
                String trim = str2.trim();
                if (trim.length() != 0) {
                    String[] split = trim.split("\\|");
                    if (split.length < 3) {
                        return false;
                    }
                    int parseInt = parseInt(split[0], -1);
                    int parseInt2 = parseInt(split[1], -1);
                    if (parseInt >= 0 && parseInt2 >= 0 && parseInt < grab.getWidth() && parseInt2 < grab.getHeight()) {
                        if (!ColorUtil.match(grab.getPixel(parseInt, parseInt2), ColorUtil.parse(split[2]), deltaFromSim)) {
                            return false;
                        }
                    }
                    return false;
                }
            }
            return true;
        } finally {
            recycleIfTemp(grab);
        }
    }

    public static boolean findColor(int i, int i2, int i3, int i4, String str, float f, int i5) {
        last.clear();
        Bitmap grab = grab();
        if (grab == null) {
            return false;
        }
        try {
            return scan(grab, i, i2, i3, i4, i5, ColorUtil.parse(str), null, ColorUtil.deltaFromSim(f));
        } finally {
            recycleIfTemp(grab);
        }
    }

    public static boolean findMultiColor(int i, int i2, int i3, int i4, String str, String str2, float f, int i5) {
        last.clear();
        Bitmap grab = grab();
        if (grab == null) {
            return false;
        }
        try {
            return scan(grab, i, i2, i3, i4, i5, ColorUtil.parse(str), parseOffset(str2), ColorUtil.deltaFromSim(f));
        } finally {
            recycleIfTemp(grab);
        }
    }

    public static int findColorEx(int i, int i2, int i3, int i4, String str, float f, int i5, int i6) {
        last.clear();
        Bitmap grab = grab();
        int i7 = 0;
        if (grab == null) {
            return 0;
        }
        try {
            ColorUtil.Spec parse = ColorUtil.parse(str);
            int deltaFromSim = ColorUtil.deltaFromSim(f);
            int i8 = i6 <= 0 ? 20 : i6;
            int width = grab.getWidth();
            int height = grab.getHeight();
            int[] iArr = new int[width * height];
            int i9 = i8;
            grab.getPixels(iArr, 0, width, 0, 0, width, height);
            int max = Math.max(0, Math.min(i, i3));
            int max2 = Math.max(0, Math.min(i2, i4));
            int min = Math.min(width - 1, Math.max(i, i3));
            int min2 = Math.min(height - 1, Math.max(i2, i4));
            while (max2 <= min2) {
                int i10 = i9;
                if (i7 >= i10) {
                    break;
                }
                int i11 = max2 * width;
                for (int i12 = max; i12 <= min && i7 < i10; i12++) {
                    if (ColorUtil.match(iArr[i11 + i12], parse, deltaFromSim)) {
                        if (i7 == 0) {
                            PointXY pointXY = last;
                            pointXY.x = i12;
                            pointXY.y = max2;
                        }
                        i7++;
                    }
                }
                max2++;
                i9 = i10;
            }
            AppState.log("FindColorEx 命中 " + i7 + (i7 > 0 ? " 首个 " + last : ""));
            return i7;
        } finally {
            recycleIfTemp(grab);
        }
    }

    public static String ocrText(int i, int i2, int i3, int i4) {
        Bitmap grabLive = grabLive();
        if (grabLive == null) {
            AppState.log("OCR 失败：没有截屏");
            return "";
        }
        try {
            return OcrEngine.recognize(grabLive, i, i2, i3, i4);
        } finally {
            recycleIfTemp(grabLive);
        }
    }

    public static boolean findPic(int i, int i2, int i3, int i4, String str) {
        last.clear();
        AppState.log("FindPic 尚未接入：" + str);
        return false;
    }

    private static boolean scan(Bitmap bitmap, int i, int i2, int i3, int i4, int i5, ColorUtil.Spec spec, Offset[] offsetArr, int i6) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] iArr = new int[width * height];
        bitmap.getPixels(iArr, 0, width, 0, 0, width, height);
        int max = Math.max(0, Math.min(i, i3));
        int max2 = Math.max(0, Math.min(i2, i4));
        int min = Math.min(width - 1, Math.max(i, i3));
        int min2 = Math.min(height - 1, Math.max(i2, i4));
        boolean z = true;
        boolean z2 = i5 == 1 || i5 == 3;
        if (i5 == 2 || i5 == 3) {
            while (min2 >= max2) {
                boolean z3 = z;
                int i7 = min2;
                if (!row(iArr, width, height, min2, max, min, z2, spec, offsetArr, i6)) {
                    min2 = i7 - 1;
                    z = z3;
                } else {
                    return z3;
                }
            }
        } else {
            while (max2 <= min2) {
                int i8 = min2;
                if (row(iArr, width, height, max2, max, min, z2, spec, offsetArr, i6)) {
                    return true;
                }
                max2++;
                min2 = i8;
            }
        }
        return false;
    }

    private static boolean row(int[] iArr, int i, int i2, int i3, int i4, int i5, boolean z, ColorUtil.Spec spec, Offset[] offsetArr, int i6) {
        if (z) {
            for (int i7 = i5; i7 >= i4; i7--) {
                if (hit(iArr, i, i2, i7, i3, spec, offsetArr, i6)) {
                    return true;
                }
            }
            return false;
        }
        for (int i8 = i4; i8 <= i5; i8++) {
            if (hit(iArr, i, i2, i8, i3, spec, offsetArr, i6)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hit(int[] iArr, int i, int i2, int i3, int i4, ColorUtil.Spec spec, Offset[] offsetArr, int i5) {
        if (!ColorUtil.match(iArr[(i4 * i) + i3], spec, i5)) {
            return false;
        }
        if (offsetArr != null) {
            for (int i6 = 0; i6 < offsetArr.length; i6++) {
                int i7 = offsetArr[i6].dx + i3;
                int i8 = offsetArr[i6].dy + i4;
                if (i7 < 0 || i8 < 0 || i7 >= i || i8 >= i2 || !ColorUtil.match(iArr[(i8 * i) + i7], offsetArr[i6].spec, i5)) {
                    return false;
                }
            }
        }
        PointXY pointXY = last;
        pointXY.x = i3;
        pointXY.y = i4;
        return true;
    }

    private static Offset[] parseOffset(String str) {
        if (str == null || str.trim().length() == 0) {
            return new Offset[0];
        }
        String[] split = str.split(",");
        int length = split.length;
        Offset[] offsetArr = new Offset[length];
        int i = 0;
        for (String str2 : split) {
            String trim = str2.trim();
            if (trim.length() != 0) {
                String[] split2 = trim.split("\\|");
                if (split2.length >= 3) {
                    offsetArr[i] = new Offset(parseInt(split2[0], 0), parseInt(split2[1], 0), ColorUtil.parse(split2[2]));
                    i++;
                }
            }
        }
        if (i == length) {
            return offsetArr;
        }
        Offset[] offsetArr2 = new Offset[i];
        System.arraycopy(offsetArr, 0, offsetArr2, 0, i);
        return offsetArr2;
    }

    private static int parseInt(String str, int i) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return i;
        }
    }

    private static void noteNoCapture() {
        if (!warned) {
            warned = true;
            AppState.log("还没有截屏权限，找色/取色会失败。打开 App 点「截屏（找色）」允许。");
        }
    }

    private static final class Offset {
        final int dx;
        final int dy;
        final ColorUtil.Spec spec;

        Offset(int i, int i2, ColorUtil.Spec spec) {
            this.dx = i;
            this.dy = i2;
            this.spec = spec;
        }
    }
}

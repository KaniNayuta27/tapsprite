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

    /**
     * 区域模板找图（自研抽样 MAD，无 OpenCV）。
     * 成功写入 last/Sprite.intXY，返回 true。
     * sim：0~1，默认 0.75；也可传 >1 当作通道容差像素。
     */
    public static boolean findPic(int x1, int y1, int x2, int y2, String picPath) {
        return findPic(x1, y1, x2, y2, picPath, 0.75f);
    }

    public static boolean findPic(int x1, int y1, int x2, int y2, String picPath, float sim) {
        last.clear();
        if (picPath == null || picPath.trim().length() == 0) {
            AppState.log("FindPic 路径为空");
            return false;
        }
        // 多图用 | 分隔，命中第一张即返回
        String[] parts = picPath.split("\\|");
        for (String part : parts) {
            String one = part.trim();
            if (one.length() == 0) {
                continue;
            }
            Bitmap tpl = loadTemplate(one);
            if (tpl == null) {
                AppState.log("FindPic 读不到模板：" + one);
                continue;
            }
            try {
                if (matchTemplate(x1, y1, x2, y2, tpl, sim)) {
                    AppState.log("FindPic 命中 " + one + " @ " + last);
                    return true;
                }
            } finally {
                if (!tpl.isRecycled()) {
                    tpl.recycle();
                }
            }
        }
        AppState.log("FindPic 未找到：" + picPath);
        return false;
    }

    /** 区域内匹配颜色的像素计数。color 可用 | 多色，支持偏色。区域全 0 表示全屏。 */
    public static int getColorNum(int x1, int y1, int x2, int y2, String color, float sim) {
        Bitmap grab = grab();
        if (grab == null) {
            return 0;
        }
        try {
            int w = grab.getWidth();
            int h = grab.getHeight();
            int left;
            int top;
            int right;
            int bottom;
            if (x1 == 0 && y1 == 0 && x2 == 0 && y2 == 0) {
                left = 0;
                top = 0;
                right = w - 1;
                bottom = h - 1;
            } else {
                left = Math.max(0, Math.min(x1, x2));
                top = Math.max(0, Math.min(y1, y2));
                right = Math.min(w - 1, Math.max(x1, x2));
                bottom = Math.min(h - 1, Math.max(y1, y2));
            }
            ColorUtil.Spec[] specs = parseColors(color);
            int delta = ColorUtil.deltaFromSim(sim);
            int[] pixels = new int[w * h];
            grab.getPixels(pixels, 0, w, 0, 0, w, h);
            int count = 0;
            for (int yy = top; yy <= bottom; yy++) {
                int row = yy * w;
                for (int xx = left; xx <= right; xx++) {
                    int px = pixels[row + xx];
                    for (int i = 0; i < specs.length; i++) {
                        if (ColorUtil.match(px, specs[i], delta)) {
                            count++;
                            break;
                        }
                    }
                }
            }
            return count;
        } finally {
            recycleIfTemp(grab);
        }
    }

    private static ColorUtil.Spec[] parseColors(String color) {
        if (color == null || color.trim().length() == 0) {
            return new ColorUtil.Spec[]{ColorUtil.parse("000000")};
        }
        String[] parts = color.split("\\|");
        ColorUtil.Spec[] out = new ColorUtil.Spec[parts.length];
        int n = 0;
        for (String p : parts) {
            String t = p.trim();
            if (t.length() > 0) {
                out[n++] = ColorUtil.parse(t);
            }
        }
        if (n == 0) {
            return new ColorUtil.Spec[]{ColorUtil.parse("000000")};
        }
        if (n == out.length) {
            return out;
        }
        ColorUtil.Spec[] trimmed = new ColorUtil.Spec[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private static Bitmap loadTemplate(String path) {
        String p = path.trim();
        if (p.regionMatches(true, 0, "Attachment:", 0, 11)) {
            p = p.substring(11);
        }
        try {
            // 绝对路径
            if (p.startsWith("/")) {
                File f = new File(p);
                if (f.exists()) {
                    return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                }
            }
            // 私有 data 目录
            File local = new File(FileApi.dir(), p);
            if (local.exists()) {
                return android.graphics.BitmapFactory.decodeFile(local.getAbsolutePath());
            }
            // assets
            try {
                java.io.InputStream in = App.ctx.getAssets().open(p);
                Bitmap b = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                if (b != null) {
                    return b;
                }
            } catch (Exception ignored) {
            }
            // assets/ocr 等同级常见附件名
            try {
                java.io.InputStream in = App.ctx.getAssets().open("attach/" + p);
                Bitmap b = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                return b;
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            AppState.log("FindPic 加载异常：" + e.getMessage());
        }
        return null;
    }

    private static boolean matchTemplate(int x1, int y1, int x2, int y2, Bitmap tpl, float sim) {
        Bitmap screen = grab();
        if (screen == null) {
            return false;
        }
        try {
            int sw = screen.getWidth();
            int sh = screen.getHeight();
            int tw = tpl.getWidth();
            int th = tpl.getHeight();
            if (tw <= 0 || th <= 0 || tw > sw || th > sh) {
                return false;
            }
            int left, top, right, bottom;
            if (x1 == 0 && y1 == 0 && x2 == 0 && y2 == 0) {
                left = 0;
                top = 0;
                right = sw - 1;
                bottom = sh - 1;
            } else {
                left = Math.max(0, Math.min(x1, x2));
                top = Math.max(0, Math.min(y1, y2));
                right = Math.min(sw - 1, Math.max(x1, x2));
                bottom = Math.min(sh - 1, Math.max(y1, y2));
            }
            // 可放置模板的右下角
            int maxX = Math.min(right - tw + 1, sw - tw);
            int maxY = Math.min(bottom - th + 1, sh - th);
            if (maxX < left || maxY < top) {
                return false;
            }
            int[] sPix = new int[sw * sh];
            screen.getPixels(sPix, 0, sw, 0, 0, sw, sh);
            int[] tPix = new int[tw * th];
            tpl.getPixels(tPix, 0, tw, 0, 0, tw, th);

            // 抽样步长：大图加快
            int step = Math.max(1, Math.min(tw, th) / 24);
            float best = -1f;
            int bestX = -1;
            int bestY = -1;
            float need = sim <= 0f ? 0.75f : (sim > 1f ? Math.max(0.5f, 1f - sim / 255f) : sim);

            for (int yy = top; yy <= maxY; yy += step) {
                for (int xx = left; xx <= maxX; xx += step) {
                    float score = sampleScore(sPix, sw, tPix, tw, th, xx, yy, step);
                    if (score > best) {
                        best = score;
                        bestX = xx;
                        bestY = yy;
                    }
                }
            }
            // 在粗匹配附近精修
            if (bestX >= 0 && step > 1) {
                int x0 = Math.max(left, bestX - step);
                int y0 = Math.max(top, bestY - step);
                int x1b = Math.min(maxX, bestX + step);
                int y1b = Math.min(maxY, bestY + step);
                for (int yy = y0; yy <= y1b; yy++) {
                    for (int xx = x0; xx <= x1b; xx++) {
                        float score = sampleScore(sPix, sw, tPix, tw, th, xx, yy, 1);
                        if (score > best) {
                            best = score;
                            bestX = xx;
                            bestY = yy;
                        }
                    }
                }
            }
            if (bestX >= 0 && best >= need) {
                last.x = bestX;
                last.y = bestY;
                return true;
            }
            return false;
        } finally {
            recycleIfTemp(screen);
        }
    }

    /** 抽样相似度：忽略接近透明像素（alpha<16）；返回 0~1。 */
    private static float sampleScore(int[] screen, int sw, int[] tpl, int tw, int th,
                                     int ox, int oy, int step) {
        long err = 0;
        int n = 0;
        int s = Math.max(1, step);
        for (int ty = 0; ty < th; ty += s) {
            int sRow = (oy + ty) * sw + ox;
            int tRow = ty * tw;
            for (int tx = 0; tx < tw; tx += s) {
                int tp = tpl[tRow + tx];
                int a = (tp >>> 24) & 255;
                if (a < 16) {
                    continue;
                }
                int sp = screen[sRow + tx];
                int dr = Math.abs(((sp >> 16) & 255) - ((tp >> 16) & 255));
                int dg = Math.abs(((sp >> 8) & 255) - ((tp >> 8) & 255));
                int db = Math.abs((sp & 255) - (tp & 255));
                err += dr + dg + db;
                n++;
            }
        }
        if (n == 0) {
            return 0f;
        }
        // 每通道最大差 255，三通道
        float avg = err / (float) (n * 3 * 255);
        return 1f - Math.min(1f, avg);
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

package com.tapsprite.agent;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* loaded from: classes.dex */
public final class OcrEngine {
    private static final int REC_H = 48;
    private static final int REC_MAX_W = 480;
    private static OcrEngine inst;
    private OrtSession det;
    private String detIn;
    private OrtEnvironment env;
    private String[] keys;
    private OrtSession rec;
    private String recIn;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Object LOCK = new Object();

    private OcrEngine() {
    }

    public static String recognize(Bitmap bitmap, int i, int i2, int i3, int i4) {
        int i5;
        if (bitmap == null) {
            return "";
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(0, Math.min(i, i3));
        int max2 = Math.max(0, Math.min(i2, i4));
        int min = Math.min(width, Math.max(i, i3) + 1);
        int min2 = Math.min(height, Math.max(i2, i4) + 1);
        int i6 = min - max;
        if (i6 < 4 || (i5 = min2 - max2) < 4) {
            return "";
        }
        Bitmap createBitmap = Bitmap.createBitmap(bitmap, max, max2, i6, i5);
        try {
            return get().run(createBitmap);
        } catch (Throwable th) {
            try {
                AppState.log("OCR 失败：" + th.getMessage());
                if (createBitmap != bitmap) {
                    createBitmap.recycle();
                }
                return "";
            } finally {
                if (createBitmap != bitmap) {
                    createBitmap.recycle();
                }
            }
        }
    }

    private static OcrEngine get() throws Exception {
        OcrEngine ocrEngine;
        synchronized (LOCK) {
            if (inst == null) {
                OcrEngine ocrEngine2 = new OcrEngine();
                inst = ocrEngine2;
                ocrEngine2.init();
            }
            ocrEngine = inst;
        }
        return ocrEngine;
    }

    private void init() throws Exception {
        System.loadLibrary("onnxruntime");
        System.loadLibrary("onnxruntime4j_jni");
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        sessionOptions.setIntraOpNumThreads(2);
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        OrtSession createSession = this.env.createSession(loadModel("rec.onnx"), sessionOptions);
        this.rec = createSession;
        this.recIn = createSession.getInputNames().iterator().next();
        try {
            OrtSession createSession2 = this.env.createSession(loadModel("det.onnx"), sessionOptions);
            this.det = createSession2;
            this.detIn = createSession2.getInputNames().iterator().next();
        } catch (Exception e) {
            this.det = null;
            AppState.log("OCR 检测模型未加载，用切行：" + e.getMessage());
        }
        this.keys = loadKeys();
        AppState.log("OCR 就绪 PP-OCRv4  字表 " + this.keys.length + "  输入 " + this.recIn + (this.det != null ? " +det" : ""));
    }

    private String run(Bitmap bitmap) throws Exception {
        String str;
        long uptimeMillis = SystemClock.uptimeMillis();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (height <= 80 || width >= height * 2) {
            str = recLine(bitmap);
        } else {
            String recLines = recLines(bitmap);
            if (recLines.length() == 0 && this.det != null) {
                str = recDet(bitmap);
            } else {
                str = recLines;
            }
        }
        long uptimeMillis2 = SystemClock.uptimeMillis() - uptimeMillis;
        if (str.length() > 0) {
            AppState.log("OCR " + width + "x" + height + " " + uptimeMillis2 + "ms → " + trimLog(str));
        } else {
            AppState.log("OCR " + width + "x" + height + " " + uptimeMillis2 + "ms 空");
        }
        return str;
    }

    private String recLines(Bitmap bitmap) throws Exception {
        List<Rect> splitLines = splitLines(bitmap);
        if (splitLines.isEmpty()) {
            return recLine(bitmap);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < splitLines.size(); i++) {
            Rect rect = splitLines.get(i);
            int max = Math.max(1, rect.height());
            int max2 = Math.max(0, rect.top);
            if (max2 + max > bitmap.getHeight()) {
                max = bitmap.getHeight() - max2;
            }
            if (max >= 6) {
                Bitmap createBitmap = Bitmap.createBitmap(bitmap, 0, max2, bitmap.getWidth(), max);
                try {
                    String recLine = recLine(createBitmap);
                    if (recLine.length() > 0) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(recLine);
                    }
                } finally {
                    createBitmap.recycle();
                }
            }
        }
        return sb.toString();
    }

    private String recDet(Bitmap bitmap) throws Exception {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float min = Math.min(1.0f, 640 / Math.max(width, height));
        int max = Math.max(32, ((((int) (width * min)) + 31) / 32) * 32);
        int max2 = Math.max(32, ((((int) (height * min)) + 31) / 32) * 32);
        Bitmap createScaledBitmap = Bitmap.createScaledBitmap(bitmap, max, max2, true);
        float[] bitmapToNchw = bitmapToNchw(createScaledBitmap, true);
        if (createScaledBitmap != bitmap) {
            createScaledBitmap.recycle();
        }
        OnnxTensor createTensor = OnnxTensor.createTensor(this.env, FloatBuffer.wrap(bitmapToNchw), new long[]{1, 3, max2, max});
        try {
            OrtSession.Result run = this.det.run(Collections.singletonMap(this.detIn, createTensor));
            try {
                OnnxTensor onnxTensor = (OnnxTensor) run.get(0);
                long[] shape = onnxTensor.getInfo().getShape();
                int i = (int) shape[shape.length - 2];
                int i2 = (int) shape[shape.length - 1];
                float[] fArr = new float[i * i2];
                onnxTensor.getFloatBuffer().get(fArr);
                createTensor.close();
                List<Rect> boxesFromMap = boxesFromMap(fArr, i2, i, width, height);
                if (boxesFromMap.isEmpty()) {
                    return recLines(bitmap);
                }
                StringBuilder sb = new StringBuilder();
                for (int i3 = 0; i3 < boxesFromMap.size(); i3++) {
                    Rect rect = boxesFromMap.get(i3);
                    if (rect.width() >= 4 && rect.height() >= 4) {
                        Bitmap createBitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height());
                        try {
                            String recLine = recLine(createBitmap);
                            if (recLine.length() > 0) {
                                sb.append(recLine);
                            }
                        } finally {
                            createBitmap.recycle();
                        }
                    }
                }
                return sb.toString();
            } finally {
                run.close();
            }
        } catch (Throwable th) {
            createTensor.close();
            throw th;
        }
    }

    private String recLine(Bitmap bitmap) throws Exception {
        int max = Math.max(8, Math.min(REC_MAX_W, ((Math.round((bitmap.getWidth() / Math.max(1, bitmap.getHeight())) * 48.0f) + 7) / 8) * 8));
        Bitmap createScaledBitmap = Bitmap.createScaledBitmap(bitmap, max, 48, true);
        float[] bitmapToNchw = bitmapToNchw(createScaledBitmap, false);
        if (createScaledBitmap != bitmap) {
            createScaledBitmap.recycle();
        }
        OnnxTensor createTensor = OnnxTensor.createTensor(this.env, FloatBuffer.wrap(bitmapToNchw), new long[]{1, 3, 48, max});
        try {
            OrtSession.Result run = this.rec.run(Collections.singletonMap(this.recIn, createTensor));
            try {
                OnnxTensor onnxTensor = (OnnxTensor) run.get(0);
                return ctc(onnxTensor.getFloatBuffer(), onnxTensor.getInfo().getShape());
            } finally {
                run.close();
            }
        } finally {
            createTensor.close();
        }
    }

    private String ctc(FloatBuffer floatBuffer, long[] jArr) {
        int length;
        int remaining;
        int i;
        if (jArr.length == 3) {
            remaining = (int) jArr[1];
            length = (int) jArr[2];
        } else if (jArr.length == 2) {
            remaining = (int) jArr[0];
            length = (int) jArr[1];
        } else {
            length = this.keys.length + 1;
            remaining = floatBuffer.remaining() / length;
        }
        StringBuilder sb = new StringBuilder();
        int i2 = -1;
        int i3 = 0;
        int i4 = 0;
        while (i3 < remaining) {
            float f = -3.4028235E38f;
            int i5 = 0;
            for (int i6 = 0; i6 < length; i6++) {
                float f2 = floatBuffer.get(i4 + i6);
                if (f2 > f) {
                    i5 = i6;
                    f = f2;
                }
            }
            i4 += length;
            if (i5 != 0 && i5 != i2 && i5 - 1 >= 0) {
                String[] strArr = this.keys;
                int keyIdx = i5 - 1;
                if (keyIdx < strArr.length) {
                    sb.append(strArr[keyIdx]);
                }
            }
            i3++;
            i2 = i5;
        }
        return sb.toString();
    }

    private static float[] bitmapToNchw(Bitmap bitmap, boolean z) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int i = width * height;
        int[] iArr = new int[i];
        bitmap.getPixels(iArr, 0, width, 0, 0, width, height);
        float[] fArr = new float[width * 3 * height];
        for (int i2 = 0; i2 < i; i2++) {
            int i3 = iArr[i2];
            float f = ((i3 >> 16) & 255) / 255.0f;
            float f2 = ((i3 >> 8) & 255) / 255.0f;
            float f3 = (i3 & 255) / 255.0f;
            if (z) {
                fArr[i2] = (f - 0.485f) / 0.229f;
                fArr[i + i2] = (f2 - 0.456f) / 0.224f;
                fArr[(i * 2) + i2] = (f3 - 0.406f) / 0.225f;
            } else {
                fArr[i2] = (f - 0.5f) / 0.5f;
                fArr[i + i2] = (f2 - 0.5f) / 0.5f;
                fArr[(i * 2) + i2] = (f3 - 0.5f) / 0.5f;
            }
        }
        return fArr;
    }

    private static List<Rect> splitLines(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] iArr = new int[width * height];
        bitmap.getPixels(iArr, 0, width, 0, 0, width, height);
        int[] iArr2 = new int[height];
        int i = 1;
        for (int i2 = 0; i2 < height; i2++) {
            int i3 = i2 * width;
            int i4 = 0;
            int i5 = 0;
            for (int i6 = 0; i6 < width; i6++) {
                int i7 = iArr[i3 + i6];
                int i8 = (((((i7 >> 16) & 255) * 30) + (((i7 >> 8) & 255) * 59)) + ((i7 & 255) * 11)) / 100;
                if (i8 < 110) {
                    i4++;
                }
                if (i8 > 160) {
                    i5++;
                }
            }
            int max = Math.max(i4, i5);
            iArr2[i2] = max;
            if (max > i) {
                i = max;
            }
        }
        int max2 = Math.max(3, i / 12);
        ArrayList arrayList = new ArrayList();
        int i9 = 0;
        while (i9 < height) {
            while (i9 < height && iArr2[i9] < max2) {
                i9++;
            }
            if (i9 >= height) {
                break;
            }
            int i10 = i9;
            while (i10 < height && iArr2[i10] >= max2) {
                i10++;
            }
            int i11 = i10 - i9;
            if (i11 >= 8) {
                int max3 = Math.max(1, i11 / 8);
                arrayList.add(new Rect(0, Math.max(0, i9 - max3), width, Math.min(height, max3 + i10)));
            }
            i9 = i10;
        }
        return arrayList;
    }

    private static List<Rect> boxesFromMap(float[] fArr, int i, int i2, int i3, int i4) {
        int i5;
        ArrayList arrayList;
        int i6;
        int i7;
        int i8;
        int i9;
        int i10;
        int i11 = i4;
        int i12 = i * i2;
        boolean[] zArr = new boolean[i12];
        ArrayList arrayList2 = new ArrayList();
        float f = i3 / i;
        float f2 = i11 / i2;
        int[] iArr = new int[i12];
        int[] iArr2 = new int[i12];
        int i13 = 0;
        int i14 = 0;
        while (i14 < i2) {
            int i15 = i13;
            while (i15 < i) {
                int i16 = (i14 * i) + i15;
                if (zArr[i16]) {
                    i5 = i11;
                    arrayList = arrayList2;
                    i6 = i13;
                    i7 = i14;
                    i8 = i15;
                } else if (fArr[i16] < 0.3f) {
                    i5 = i11;
                    arrayList = arrayList2;
                    i6 = i13;
                    i7 = i14;
                    i8 = i15;
                } else {
                    iArr[i13] = i15;
                    iArr2[i13] = i14;
                    zArr[i16] = true;
                    int i17 = i13;
                    i7 = i14;
                    int i18 = i15;
                    i8 = i18;
                    int i19 = 1;
                    int i20 = i7;
                    int i21 = i8;
                    int i22 = i20;
                    while (i17 < i19) {
                        ArrayList arrayList3 = arrayList2;
                        int i23 = iArr[i17];
                        int i24 = iArr2[i17];
                        i17++;
                        if (i23 < i18) {
                            i18 = i23;
                        }
                        if (i23 > i21) {
                            i21 = i23;
                        }
                        if (i24 < i20) {
                            i20 = i24;
                        }
                        if (i24 > i22) {
                            i22 = i24;
                        }
                        int i25 = i19;
                        int i26 = -1;
                        while (true) {
                            i9 = i21;
                            int i27 = 1;
                            if (i26 <= 1) {
                                int i28 = i22;
                                int i29 = -1;
                                while (i29 <= i27) {
                                    int i30 = i23 + i29;
                                    int i31 = i23;
                                    int i32 = i24 + i26;
                                    if (i30 < 0 || i32 < 0 || i30 >= i) {
                                        i10 = 1;
                                    } else if (i32 >= i2) {
                                        i10 = 1;
                                    } else {
                                        int i33 = (i32 * i) + i30;
                                        if (zArr[i33]) {
                                            i10 = 1;
                                        } else if (fArr[i33] < 0.3f) {
                                            i10 = 1;
                                        } else {
                                            i10 = 1;
                                            zArr[i33] = true;
                                            iArr[i25] = i30;
                                            iArr2[i25] = i32;
                                            i25++;
                                        }
                                    }
                                    i29++;
                                    i27 = i10;
                                    i23 = i31;
                                }
                                i26++;
                                i21 = i9;
                                i22 = i28;
                            } else {
                                break;
                            }
                        }
                        arrayList2 = arrayList3;
                        i19 = i25;
                        i21 = i9;
                    }
                    ArrayList arrayList4 = arrayList2;
                    if (i19 < 12) {
                        i5 = i4;
                        arrayList = arrayList4;
                        i6 = 0;
                    } else {
                        i6 = 0;
                        i5 = i4;
                        arrayList = arrayList4;
                        arrayList.add(new Rect(Math.max(0, ((int) (i18 * f)) - 2), Math.max(0, ((int) (i20 * f2)) - 2), Math.min(i3, ((int) ((i21 + 1) * f)) + 2), Math.min(i5, ((int) ((i22 + 1) * f2)) + 2)));
                    }
                }
                i15 = i8 + 1;
                i13 = i6;
                i14 = i7;
                arrayList2 = arrayList;
                i11 = i5;
            }
            i14++;
            arrayList2 = arrayList2;
            i11 = i11;
        }
        return arrayList2;
    }

    private static String[] loadKeys() throws Exception {
        String[] split = new String(readAsset("ocr/ppocr_keys_v1.txt"), UTF8).replace("\r", "").split("\n");
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < split.length; i++) {
            if (split[i].length() > 0) {
                arrayList.add(split[i]);
            }
        }
        arrayList.add(" ");
        return (String[]) arrayList.toArray(new String[0]);
    }

    private static byte[] loadModel(String str) throws Exception {
        File file = new File(App.ctx.getFilesDir(), "ocr");
        if (!file.exists()) {
            file.mkdirs();
        }
        File file2 = new File(file, str);
        if (file2.exists() && file2.length() > 10000) {
            return readFile(file2);
        }
        try {
            byte[] readAsset = readAsset("ocr/" + str);
            if (readAsset.length > 10000) {
                writeFile(file2, readAsset);
                return readAsset;
            }
        } catch (Exception e) {
        }
        AppState.log("下载识别模型 " + str + " …");
        download("https://tapsprite.pages.dev/ocr/" + str, file2);
        if (!file2.exists() || file2.length() < 10000) {
            throw new Exception("模型下载失败 " + str);
        }
        AppState.log("模型就绪 " + str + " " + file2.length());
        return readFile(file2);
    }

    private static byte[] readFile(File file) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(file);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[8192];
        while (true) {
            int read = fileInputStream.read(bArr);
            if (read > 0) {
                byteArrayOutputStream.write(bArr, 0, read);
            } else {
                fileInputStream.close();
                return byteArrayOutputStream.toByteArray();
            }
        }
    }

    private static void writeFile(File file, byte[] bArr) throws Exception {
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        fileOutputStream.write(bArr);
        fileOutputStream.close();
    }

    private static void download(String str, File file) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(15000);
        httpURLConnection.setReadTimeout(120000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty("User-Agent", "TapSprite");
        int responseCode = httpURLConnection.getResponseCode();
        if (responseCode >= 400) {
            httpURLConnection.disconnect();
            throw new Exception("HTTP " + responseCode + " " + str);
        }
        InputStream inputStream = httpURLConnection.getInputStream();
        File file2 = new File(file.getPath() + ".part");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        byte[] bArr = new byte[65536];
        while (true) {
            int read = inputStream.read(bArr);
            if (read <= 0) {
                break;
            } else {
                fileOutputStream.write(bArr, 0, read);
            }
        }
        fileOutputStream.close();
        inputStream.close();
        httpURLConnection.disconnect();
        if (file.exists()) {
            file.delete();
        }
        if (!file2.renameTo(file)) {
            throw new Exception("无法保存 " + file.getName());
        }
    }

    private static byte[] readAsset(String str) throws Exception {
        InputStream open = App.ctx.getAssets().open(str);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[8192];
        while (true) {
            int read = open.read(bArr);
            if (read > 0) {
                byteArrayOutputStream.write(bArr, 0, read);
            } else {
                open.close();
                return byteArrayOutputStream.toByteArray();
            }
        }
    }

    private static String trimLog(String str) {
        String replace = str.replace('\n', ' ');
        return replace.length() > 40 ? replace.substring(0, 40) + "…" : replace;
    }
}

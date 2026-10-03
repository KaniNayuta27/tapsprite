package com.tapsprite.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

/* loaded from: classes.dex */
public final class FileApi {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static String readAsset(String str) {
        try {
            InputStream open = App.ctx.getAssets().open(str);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[4096];
            while (true) {
                int read = open.read(bArr);
                if (read > 0) {
                    byteArrayOutputStream.write(bArr, 0, read);
                } else {
                    open.close();
                    return new String(byteArrayOutputStream.toByteArray(), UTF8);
                }
            }
        } catch (Exception e) {
            AppState.log("读资源失败 " + str + "：" + e.getMessage());
            return "";
        }
    }

    public static File dir() {
        File file = new File(App.ctx.getFilesDir(), "data");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public static boolean write(String str, String str2) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(dir(), safe(str)));
            if (str2 == null) {
                str2 = "";
            }
            fileOutputStream.write(str2.getBytes(UTF8));
            fileOutputStream.close();
            return true;
        } catch (Exception e) {
            AppState.log("WriteFile 失败：" + e.getMessage());
            return false;
        }
    }

    public static String read(String str) {
        try {
            File file = new File(dir(), safe(str));
            if (!file.exists()) {
                return "";
            }
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] bArr = new byte[(int) file.length()];
            int read = fileInputStream.read(bArr);
            fileInputStream.close();
            return new String(bArr, 0, Math.max(0, read), UTF8);
        } catch (Exception e) {
            AppState.log("ReadFile 失败：" + e.getMessage());
            return "";
        }
    }

    public static boolean exists(String str) {
        return new File(dir(), safe(str)).exists();
    }

    public static boolean delete(String str) {
        File file = new File(dir(), safe(str));
        return file.exists() && file.delete();
    }

    private static String safe(String str) {
        if (str == null || str.length() == 0) {
            return "untitled.txt";
        }
        return str.replace("..", "_").replace("/", "_").replace("\\", "_");
    }
}

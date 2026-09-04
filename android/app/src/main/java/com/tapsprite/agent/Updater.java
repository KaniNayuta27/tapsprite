package com.tapsprite.agent;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/* loaded from: classes.dex */
public final class Updater {
    // 检新已改走 PC /api/channel（手机不再直连 GitHub MANIFEST）
    // public static final String MANIFEST = "...";
    public static volatile boolean downloading;
    public static volatile long lastGot;
    public static volatile String lastStatus = "";
    public static volatile long lastTotal;

    public interface Listener {
        void onError(String str);

        void onFound(int i, String str, String str2, String str3);

        void onIdle(String str);

        void onProgress(long j, long j2);

        void onStatus(String str);
    }

    private Updater() {
    }

    public static int currentCode() {
        try {
            return App.ctx.getPackageManager().getPackageInfo(App.ctx.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentName() {
        try {
            return App.ctx.getPackageManager().getPackageInfo(App.ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static void check(final Activity activity, final boolean z, final Listener listener) {
        post(activity, listener, "正在检查更新通道…");
        new Thread(new Runnable() { // from class: com.tapsprite.agent.Updater.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    if (!LanLink.ok() || LanLink.pcAddr().length() == 0) {
                        throw new Exception("请先打开电脑客户端并联机");
                    }
                    JSONObject fetchJson = Updater.getJsonLong(LanLink.pcAddr(), "/api/channel", 3000, 130000);
                    if (!fetchJson.optBoolean("ok", false) && !fetchJson.has("versionCode")) {
                        String err = fetchJson.optString("err", "");
                        if (err.length() == 0) {
                            err = "请先打开电脑客户端并联机";
                        }
                        throw new Exception(shortErr(err));
                    }
                    if (fetchJson.has("ok") && !fetchJson.optBoolean("ok", true)) {
                        throw new Exception(shortErr(fetchJson.optString("err", "网络超时，请开代理或重试")));
                    }
                    final int optInt = fetchJson.optInt("versionCode", 0);
                    final String optString = fetchJson.optString("versionName", "");
                    final String optString2 = fetchJson.optString("apk", "");
                    final String optString3 = fetchJson.optString("notes", "");
                    final int currentCode = Updater.currentCode();
                    activity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.Updater.1.1
                        @Override // java.lang.Runnable
                        public void run() {
                            if (optInt <= currentCode) {
                                String str = "已是最新 " + Updater.currentName() + "（" + currentCode + "）";
                                if (listener != null) {
                                    listener.onIdle(str);
                                }
                                AppState.log("更新检查：" + str);
                                return;
                            }
                            String str2 = "发现 " + optString + "（" + optInt + "）\n" + optString3;
                            AppState.log(str2.replace('\n', ' '));
                            if (listener != null) {
                                listener.onFound(optInt, optString, optString2, optString3);
                                listener.onStatus(str2);
                            }
                            if (z && optString2.length() > 0) {
                                Updater.downloadViaPc(activity, optString2, optString, listener);
                            }
                        }
                    });
                } catch (Exception e) {
                    final String message = shortErr(e.getMessage());
                    AppState.log("检查更新失败：" + message);
                    activity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.Updater.1.2
                        @Override // java.lang.Runnable
                        public void run() {
                            if (listener != null) {
                                listener.onError(message);
                            }
                        }
                    });
                }
            }
        }, "tapsprite-update-check").start();
    }

    private static String shortErr(String message) {
        if (message == null || message.length() == 0) {
            return "网络超时，请开代理或重试";
        }
        String low = message.toLowerCase();
        if (low.contains("timeout") || low.contains("deadline") || low.contains("timed out")
                || low.contains("unable to resolve") || low.contains("failed to connect")
                || message.contains("http://") || message.contains("https://")) {
            return "网络超时，请开代理或重试";
        }
        if (message.contains("请先打开电脑")) {
            return "请先打开电脑客户端并联机";
        }
        if (message.length() > 60) {
            return "网络超时，请开代理或重试";
        }
        return message;
    }

    public static void downloadViaPc(final Activity activity, final String str, final String str2, final Listener listener) {
        if (!LanLink.ok() || LanLink.pcAddr().length() == 0) {
            if (listener != null) {
                listener.onError("请先打开电脑客户端并联机");
                return;
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            if (listener != null) {
                listener.onError("先允许「安装未知应用」，然后再点下载");
            }
            activity.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + activity.getPackageName())));
        } else if (downloading) {
            if (listener != null) {
                listener.onStatus("已经在下载…");
            }
        } else {
            downloading = true;
            lastGot = 0L;
            lastTotal = 0L;
            post(activity, listener, "让电脑下载 " + str2);
            new Thread(new Runnable() { // from class: com.tapsprite.agent.Updater.2
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        String pcAddr = LanLink.pcAddr();
                        if (!Updater.postJson(pcAddr, "/api/fetchapk", "{\"url\":\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"name\":\"" + str2.replace("\"", "") + "\"}")) {
                            throw new Exception("电脑没应答，确认 exe 开着");
                        }
                        long currentTimeMillis = System.currentTimeMillis();
                        boolean ready = false;
                        while (System.currentTimeMillis() - currentTimeMillis < 180000) {
                            JSONObject json = Updater.getJson(pcAddr, "/api/apkstatus");
                            if (!json.has("busy") && !json.has("ready")) {
                                Thread.sleep(500L);
                                continue;
                            }
                            if (json.optString("err", "").length() > 0) {
                                throw new Exception(json.optString("err"));
                            }
                            long optLong = json.optLong("got", 0L);
                            long optLong2 = json.optLong("total", 0L);
                            Updater.lastGot = optLong;
                            Updater.lastTotal = optLong2;
                            Updater.progress(activity, listener, optLong, optLong2);
                            if (!json.optBoolean("ready", false)) {
                                Updater.post(activity, listener, "电脑下载中 " + Updater.formatSize(optLong) + (optLong2 > 0 ? " / " + Updater.formatSize(optLong2) : ""));
                                Thread.sleep(400L);
                            } else {
                                ready = true;
                                break;
                            }
                        }
                        if (ready) {
                            Updater.post(activity, listener, "从电脑取安装包…");
                            File file = new File(activity.getFilesDir(), "update.apk");
                            Updater.downloadLan(pcAddr, "/api/apkfile", file, activity, listener);
                            Updater.post(activity, listener, "下载完成 " + Updater.formatSize(file.length()) + "，正在调起安装…");
                            AppState.log("局域网安装包 " + file.length() + " 字节");
                            Updater.install(activity, file);
                            Updater.post(activity, listener, "已弹出系统安装框，点「安装」即可。");
                        } else {
                            throw new Exception("电脑下载超时");
                        }
                    } catch (Exception e) {
                        AppState.log("更新失败：" + e.getMessage());
                        final String message = shortErr(e.getMessage());
                        activity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.Updater.2.1
                            @Override // java.lang.Runnable
                            public void run() {
                                if (listener != null) {
                                    listener.onError(message.startsWith("请先") || message.contains("超时") ? message : ("更新失败：" + message));
                                }
                            }
                        });
                    } finally {
                        Updater.downloading = false;
                    }
                }
            }, "tapsprite-lan-upd").start();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean postJson(String str, String str2, String str3) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("http://" + str + ":18766" + str2).openConnection();
            httpURLConnection.setConnectTimeout(2000);
            httpURLConnection.setReadTimeout(4000);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setDoOutput(true);
            byte[] bytes = str3.getBytes("UTF-8");
            httpURLConnection.setFixedLengthStreamingMode(bytes.length);
            httpURLConnection.setRequestProperty("Content-Type", "application/json");
            httpURLConnection.getOutputStream().write(bytes);
            int responseCode = httpURLConnection.getResponseCode();
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static JSONObject getJson(String str, String str2) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("http://" + str + ":18766" + str2).openConnection();
            httpURLConnection.setConnectTimeout(2000);
            httpURLConnection.setReadTimeout(4000);
            httpURLConnection.setRequestMethod("GET");
            InputStream inputStream = httpURLConnection.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[2048];
            while (true) {
                int read = inputStream.read(bArr);
                if (read <= 0) {
                    break;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
            inputStream.close();
            return new JSONObject(byteArrayOutputStream.toString("UTF-8"));
        } catch (Exception e) {
            return new JSONObject();
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    /** Longer timeout for /api/channel (PC may try several GH mirrors). */
    private static JSONObject getJsonLong(String str, String str2, int connectMs, int readMs) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL("http://" + str + ":18766" + str2).openConnection();
        try {
            httpURLConnection.setConnectTimeout(connectMs);
            httpURLConnection.setReadTimeout(readMs);
            httpURLConnection.setRequestMethod("GET");
            int code = httpURLConnection.getResponseCode();
            InputStream inputStream = code >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            if (inputStream != null) {
                byte[] bArr = new byte[2048];
                while (true) {
                    int read = inputStream.read(bArr);
                    if (read <= 0) {
                        break;
                    }
                    byteArrayOutputStream.write(bArr, 0, read);
                }
                inputStream.close();
            }
            String body = byteArrayOutputStream.toString("UTF-8");
            if (body.length() == 0) {
                throw new Exception("电脑无应答");
            }
            return new JSONObject(body);
        } finally {
            httpURLConnection.disconnect();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void progress(Activity activity, final Listener listener, final long j, final long j2) {
        lastGot = j;
        lastTotal = j2;
        if (activity == null || listener == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.Updater.3
            @Override // java.lang.Runnable
            public void run() {
                listener.onProgress(j, j2);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void downloadLan(String str, String str2, File file, Activity activity, Listener listener) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL("http://" + str + ":18766" + str2).openConnection();
        httpURLConnection.setConnectTimeout(4000);
        httpURLConnection.setReadTimeout(120000);
        httpURLConnection.setRequestMethod("GET");
        int responseCode = httpURLConnection.getResponseCode();
        if (responseCode >= 400) {
            httpURLConnection.disconnect();
            throw new Exception("电脑没有安装包 HTTP " + responseCode);
        }
        long contentLength = httpURLConnection.getContentLength();
        lastTotal = contentLength;
        InputStream inputStream = httpURLConnection.getInputStream();
        File file2 = new File(file.getPath() + ".part");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        byte[] bArr = new byte[65536];
        long j = 0;
        while (true) {
            int read = inputStream.read(bArr);
            if (read <= 0) {
                break;
            }
            fileOutputStream.write(bArr, 0, read);
            long j2 = j + read;
            lastGot = j2;
            progress(activity, listener, j2, contentLength);
            j = j2;
        }
        fileOutputStream.close();
        inputStream.close();
        httpURLConnection.disconnect();
        if (file2.length() < 10000) {
            throw new IllegalStateException("安装包太小");
        }
        if (file.exists()) {
            file.delete();
        }
        if (!file2.renameTo(file)) {
            throw new Exception("无法保存安装包");
        }
    }

    public static void downloadAndInstall(Activity activity, String str, String str2, Listener listener) {
        downloadViaPc(activity, str, str2, listener);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void post(Activity activity, final Listener listener, final String str) {
        lastStatus = str;
        if (activity == null || listener == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.Updater.4
            @Override // java.lang.Runnable
            public void run() {
                listener.onStatus(str);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static JSONObject fetchJson(String str) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(8000);
        httpURLConnection.setReadTimeout(8000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty("User-Agent", "TapSprite/" + currentName());
        int responseCode = httpURLConnection.getResponseCode();
        InputStream errorStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (errorStream != null) {
            byte[] bArr = new byte[4096];
            while (true) {
                int read = errorStream.read(bArr);
                if (read <= 0) {
                    break;
                }
                sb.append(new String(bArr, 0, read, "UTF-8"));
            }
            errorStream.close();
        }
        httpURLConnection.disconnect();
        if (responseCode >= 400) {
            throw new Exception("HTTP " + responseCode + " " + ((Object) sb));
        }
        return new JSONObject(sb.toString());
    }

    private static void download(String str, File file, Activity activity, final Listener listener) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(15000);
        httpURLConnection.setReadTimeout(60000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty("User-Agent", "TapSprite/" + currentName());
        int responseCode = httpURLConnection.getResponseCode();
        if (responseCode >= 400) {
            InputStream errorStream = httpURLConnection.getErrorStream();
            if (errorStream != null) {
                errorStream.close();
            }
            httpURLConnection.disconnect();
            throw new Exception("HTTP " + responseCode + "  " + str);
        }
        final int contentLength = httpURLConnection.getContentLength();
        lastTotal = contentLength;
        InputStream inputStream = httpURLConnection.getInputStream();
        File file2 = new File(file.getPath() + ".part");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        byte[] bArr = new byte[65536];
        final long[] got = new long[]{0L};
        long j2 = 0;
        while (true) {
            int read = inputStream.read(bArr);
            if (read <= 0) {
                break;
            }
            fileOutputStream.write(bArr, 0, read);
            got[0] += read;
            lastGot = got[0];
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - j2 > 200) {
                activity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.Updater.5
                    @Override // java.lang.Runnable
                    public void run() {
                        Listener listener2 = listener;
                        if (listener2 != null) {
                            listener2.onProgress(got[0], contentLength);
                        }
                    }
                });
                j2 = currentTimeMillis;
            }
        }
        fileOutputStream.close();
        inputStream.close();
        httpURLConnection.disconnect();
        if (file2.length() < 10000) {
            throw new IllegalStateException("APK 太小，地址可能不对");
        }
        if (file.exists()) {
            file.delete();
        }
        if (!file2.renameTo(file)) {
            throw new Exception("无法保存安装包");
        }
        final long length = file.length();
        activity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.Updater.6
            @Override // java.lang.Runnable
            public void run() {
                Listener listener2 = listener;
                if (listener2 != null) {
                    long j3 = length;
                    listener2.onProgress(j3, j3);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void install(Context context, File file) throws Exception {
        int i;
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(1);
        sessionParams.setAppPackageName(context.getPackageName());
        int createSession = packageInstaller.createSession(sessionParams);
        PackageInstaller.Session openSession = packageInstaller.openSession(createSession);
        try {
            OutputStream openWrite = openSession.openWrite("tapsprite.apk", 0L, file.length());
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] bArr = new byte[65536];
            while (true) {
                int read = fileInputStream.read(bArr);
                if (read <= 0) {
                    break;
                } else {
                    openWrite.write(bArr, 0, read);
                }
            }
            fileInputStream.close();
            openSession.fsync(openWrite);
            openWrite.close();
            Intent intent = new Intent(context, (Class<?>) InstallResultReceiver.class);
            intent.setAction("com.tapsprite.agent.INSTALL_RESULT");
            if (Build.VERSION.SDK_INT < 31) {
                i = 134217728;
            } else {
                i = 167772160;
            }
            openSession.commit(PendingIntent.getBroadcast(context, createSession, intent, i).getIntentSender());
        } catch (Exception e) {
            openSession.abandon();
            throw e;
        }
    }

    static String formatSize(long j) {
        if (j < 1024) {
            return j + " B";
        }
        if (j < 1048576) {
            return (j / 1024) + " KB";
        }
        return String.format("%.1f MB", Double.valueOf((j / 1024.0d) / 1024.0d));
    }
}

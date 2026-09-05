package com.tapsprite.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Url 表：同步 HTTP（按键精灵兼容子集）。
 * 超时默认 15s；失败 HttpGet/HttpPost 返回空串，Download 返回 false。
 */
public final class UrlApi {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_BODY = 4 * 1024 * 1024;

    private UrlApi() {
    }

    public static String httpGet(String url) {
        return request("GET", url, null);
    }

    public static String httpPost(String url, String body) {
        return request("POST", url, body == null ? "" : body);
    }

    public static boolean download(String url, String path) {
        if (url == null || url.length() == 0 || path == null || path.length() == 0) {
            return false;
        }
        File out = DirApi.resolveWritable(path);
        if (out == null) {
            AppState.log("Url.Download 路径非法：" + path);
            return false;
        }
        HttpURLConnection conn = null;
        try {
            conn = open(url, "GET");
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null || code >= 400) {
                AppState.log("Url.Download HTTP " + code);
                return false;
            }
            File parent = out.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                total += n;
                if (total > 64L * 1024 * 1024) {
                    fos.close();
                    in.close();
                    out.delete();
                    AppState.log("Url.Download 超过 64MB 上限");
                    return false;
                }
            }
            fos.close();
            in.close();
            AppState.log("Url.Download → " + out.getAbsolutePath() + " (" + total + "B)");
            return true;
        } catch (Exception e) {
            AppState.log("Url.Download 失败：" + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String request(String method, String url, String body) {
        if (url == null || url.length() == 0) {
            return "";
        }
        HttpURLConnection conn = null;
        try {
            conn = open(url, method);
            if (body != null) {
                conn.setDoOutput(true);
                byte[] data = body.getBytes(UTF8);
                conn.setFixedLengthStreamingMode(data.length);
                OutputStream os = conn.getOutputStream();
                os.write(data);
                os.close();
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                total += n;
                if (total > MAX_BODY) {
                    break;
                }
            }
            in.close();
            String text = new String(bos.toByteArray(), UTF8);
            if (code >= 400) {
                AppState.log(method + " HTTP " + code + "（仍返回正文，长度 " + text.length() + "）");
            }
            return text;
        } catch (Exception e) {
            AppState.log("Url." + method + " 失败：" + e.getMessage());
            return "";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent", "TapSprite/0.9.65");
        if ("POST".equals(method)) {
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        }
        return conn;
    }
}

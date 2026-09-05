package com.tapsprite.agent;

import android.net.wifi.WifiManager;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/* loaded from: classes.dex */
public final class LanPush {
    private static volatile String pcHosts = "";

    private LanPush() {
    }

    public static void prependHost(String str) {
        if (str == null || str.length() < 7 || pcHosts.indexOf(str) >= 0) {
            return;
        }
        pcHosts = str + "," + pcHosts;
    }

    public static List<String> hosts() {
        return candidates();
    }

    public static boolean push(byte[] bArr, int i, int i2, String str) {
        if (bArr == null || bArr.length == 0) {
            return false;
        }
        List<String> candidates = candidates();
        AppState.log("局域网尝试 " + candidates);
        Iterator<String> it = candidates.iterator();
        while (it.hasNext()) {
            if (post(it.next(), bArr, i, i2, str)) {
                return true;
            }
        }
        return false;
    }

    private static boolean post(String str, byte[] bArr, int i, int i2, String str2) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("http://" + str + ":18766/api/pushshot").openConnection();
            httpURLConnection.setConnectTimeout(180);
            httpURLConnection.setReadTimeout(700);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setFixedLengthStreamingMode(bArr.length);
            httpURLConnection.setRequestProperty("Content-Type", "application/octet-stream");
            httpURLConnection.setRequestProperty("X-Ts-W", String.valueOf(i));
            httpURLConnection.setRequestProperty("X-Ts-H", String.valueOf(i2));
            if (str2 == null) {
                str2 = "rawz";
            }
            httpURLConnection.setRequestProperty("X-Ts-Mime", str2);
            if (AppState.deviceId != null) {
                httpURLConnection.setRequestProperty("X-Ts-Id", AppState.deviceId);
            }
            if (AppState.deviceName != null) {
                httpURLConnection.setRequestProperty("X-Ts-Name", AppState.deviceName);
            }
            String str3 = "1";
            httpURLConnection.setRequestProperty("X-Ts-A11y", AppState.auto != null ? "1" : "0");
            httpURLConnection.setRequestProperty("X-Ts-Emu", AppState.isEmulator() ? "1" : "0");
            if (!CaptureService.ready) {
                str3 = "0";
            }
            httpURLConnection.setRequestProperty("X-Ts-Cap", str3);
            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(bArr);
            outputStream.flush();
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                AppState.log("局域网成功 " + str + " " + (bArr.length / 1024) + "KB");
                return true;
            }
            AppState.log("局域网 " + str + " HTTP " + responseCode);
            return false;
        } catch (Exception e) {
            AppState.log("局域网 " + str + " " + e.getMessage());
            return false;
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    private static List<String> candidates() {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        if (pcHosts.length() > 0) {
            for (String str : pcHosts.split("[,;\\s]+")) {
                if (str.startsWith("192.168.") || str.startsWith("10.")) {
                    linkedHashSet.add(str);
                }
            }
        }
        String gateway = gateway();
        if (gateway != null) {
            linkedHashSet.add(gateway);
        }
        linkedHashSet.add("10.0.2.2");
        linkedHashSet.add("10.0.3.2");
        if (pcHosts.length() > 0) {
            for (String str2 : pcHosts.split("[,;\\s]+")) {
                if (str2.length() > 6 && str2.indexOf(46) > 0) {
                    linkedHashSet.add(str2);
                }
            }
        }
        ArrayList arrayList = new ArrayList();
        Iterator it = linkedHashSet.iterator();
        while (it.hasNext()) {
            arrayList.add((String) it.next());
            if (arrayList.size() >= 12) {
                break;
            }
        }
        return arrayList;
    }

    private static String gateway() {
        int i;
        try {
            WifiManager wifiManager = (WifiManager) App.ctx.getApplicationContext().getSystemService("wifi");
            if (wifiManager != null && wifiManager.getDhcpInfo() != null && (i = wifiManager.getDhcpInfo().gateway) != 0) {
                return (i & 255) + "." + ((i >> 8) & 255) + "." + ((i >> 16) & 255) + "." + ((i >> 24) & 255);
            }
        } catch (Exception e) {
        }
        try {
            Process start = new ProcessBuilder("sh", "-c", "ip route | grep '^default' | awk '{print $3}'").redirectErrorStream(true).start();
            byte[] bArr = new byte[128];
            int read = start.getInputStream().read(bArr);
            start.waitFor();
            if (read > 0) {
                String trim = new String(bArr, 0, read).trim();
                if (trim.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    return trim;
                }
                return null;
            }
            return null;
        } catch (Exception e2) {
            return null;
        }
    }
}

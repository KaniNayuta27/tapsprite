package com.tapsprite.agent;

import android.net.wifi.WifiManager;
import android.os.Build;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
public final class NetInfo {
    private NetInfo() {
    }

    public static boolean isEmulator() {
        if (containsAny(join(Build.FINGERPRINT, Build.HARDWARE, Build.PRODUCT, Build.MODEL, Build.BRAND, Build.DEVICE, Build.MANUFACTURER, Build.BOARD, Build.HOST, Build.FINGERPRINT).toLowerCase(Locale.US), "generic", "goldfish", "ranchu", "vbox", "emulator", "ttvm", "nox", "mumu", "leidian", "ldplayer", "bluestacks", "andy", "droid4x", "genymotion", "sdk_gphone", "google_sdk", "cancro", "changwan", "microvirt", "tiantian")) {
            return true;
        }
        String lowerCase = Build.HARDWARE == null ? "" : Build.HARDWARE.toLowerCase(Locale.US);
        return lowerCase.contains("vbox") || lowerCase.equals("intel") || lowerCase.contains("goldfish") || lowerCase.contains("ranchu");
    }

    public static boolean isNatIp(String str) {
        return str.startsWith("10.0.2.") || str.startsWith("10.0.3.") || str.startsWith("10.1.1.");
    }

    public static String wifiIPv4() {
        try {
            WifiManager wifiManager = (WifiManager) App.ctx.getApplicationContext().getSystemService("wifi");
            if (wifiManager != null && wifiManager.getDhcpInfo() != null && wifiManager.getDhcpInfo().ipAddress != 0) {
                int i = wifiManager.getDhcpInfo().ipAddress;
                return (i & 255) + "." + ((i >> 8) & 255) + "." + ((i >> 16) & 255) + "." + ((i >> 24) & 255);
            }
        } catch (Exception e) {
        }
        List<String> ipv4 = ipv4();
        for (int i2 = 0; i2 < ipv4.size(); i2++) {
            String str = ipv4.get(i2);
            if (!str.startsWith("127.") && !isNatIp(str)) {
                return str;
            }
        }
        return ipv4.isEmpty() ? "" : ipv4.get(0);
    }

    public static List<String> ipv4() {
        ArrayList arrayList = new ArrayList();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface nextElement = networkInterfaces.nextElement();
                try {
                    if (nextElement.isUp() && !nextElement.isLoopback()) {
                        Enumeration<InetAddress> inetAddresses = nextElement.getInetAddresses();
                        while (inetAddresses.hasMoreElements()) {
                            InetAddress nextElement2 = inetAddresses.nextElement();
                            if ((nextElement2 instanceof Inet4Address) && !nextElement2.isLoopbackAddress()) {
                                String hostAddress = nextElement2.getHostAddress();
                                if (!arrayList.contains(hostAddress)) {
                                    arrayList.add(hostAddress);
                                }
                            }
                        }
                    }
                } catch (SocketException e) {
                }
            }
        } catch (Exception e2) {
        }
        return arrayList;
    }

    public static String notifyText() {
        List<String> ipv4 = ipv4();
        if (ipv4.isEmpty()) {
            return "局域网 :18765";
        }
        return ipv4.get(0) + ":" + AppState.PORT;
    }

    private static String join(String... strArr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strArr.length; i++) {
            if (strArr[i] != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(strArr[i]);
            }
        }
        return sb.toString();
    }

    private static boolean containsAny(String str, String... strArr) {
        for (String str2 : strArr) {
            if (str.contains(str2)) {
                return true;
            }
        }
        return false;
    }
}

package com.tapsprite.agent;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/* loaded from: classes.dex */
public final class LanLink {
    private static volatile boolean genLoaded;
    private static volatile boolean ok;
    private static Thread puller;
    private static Thread udp;
    private static volatile DatagramSocket udpSock;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static volatile String pcHost = "";
    private static volatile String lastHost = "";
    private static final AtomicLong gen = new AtomicLong();
    private static final ConcurrentLinkedQueue<String> traces = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean tracing = new AtomicBoolean(false);
    static volatile String lastStatus = "未联机";

    private LanLink() {
    }

    public static boolean ok() {
        return ok;
    }

    public static String pcAddr() {
        if (pcHost == null || pcHost.length() <= 0) {
            return lastHost == null ? "" : lastHost;
        }
        return pcHost;
    }

    private static synchronized long nextGen() {
        long incrementAndGet;
        synchronized (LanLink.class) {
            if (!genLoaded) {
                try {
                    long j = App.ctx.getSharedPreferences("tapsprite", 0).getLong("lanGen", 0L);
                    if (j > 0) {
                        gen.set(j);
                    }
                } catch (Exception e) {
                }
                genLoaded = true;
            }
            incrementAndGet = gen.incrementAndGet();
            try {
                App.ctx.getSharedPreferences("tapsprite", 0).edit().putLong("lanGen", incrementAndGet).apply();
            } catch (Exception e2) {
            }
        }
        return incrementAndGet;
    }

    public static void hello() {
        AppState.ensureDevice();
        final long nextGen = nextGen();
        new Thread(new Runnable() { // from class: com.tapsprite.agent.LanLink.1
            @Override // java.lang.Runnable
            public void run() {
                LanLink.sendHello(nextGen);
            }
        }, "tapsprite-hello-" + nextGen).start();
    }

    public static void bye() {
        ok = false;
        final long nextGen = nextGen();
        final String str = pcHost.length() > 0 ? pcHost : lastHost;
        DatagramSocket datagramSocket = udpSock;
        udpSock = null;
        if (datagramSocket != null) {
            try {
                datagramSocket.close();
            } catch (Exception e) {
            }
        }
        final byte[] payload = payload(false, nextGen);
        new Thread(new Runnable() { // from class: com.tapsprite.agent.LanLink.2
            @Override // java.lang.Runnable
            public void run() {
                AppState.log("下线 #" + nextGen);
                String str2 = str;
                if (str2 != null && str2.length() > 0) {
                    LanLink.post(str, "/api/bye", payload);
                }
                for (String str3 : LanPush.hosts()) {
                    if (str3 != null && !str3.equals(str)) {
                        LanLink.post(str3, "/api/bye", payload);
                    }
                }
                AppState.log("已发出下线 #" + nextGen);
            }
        }, "tapsprite-bye-" + nextGen).start();
    }

    static void onCaptureChanged() {
        if (AppState.debugToPc) {
            reannounce();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void reannounce() {
        long j = gen.get();
        if (j <= 0) {
            hello();
        } else {
            sendHello(j);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void sendHello(long j) {
        loadSavedHost();
        listenUdp();
        byte[] payload = payload(true, j);
        String manualHost = manualHost();
        if (manualHost.length() == 0) {
            manualHost = pcHost.length() > 0 ? pcHost : lastHost;
        }
        if (manualHost.length() > 0) {
            AppState.log("握手 #" + j + " " + manualHost);
            if (post(manualHost, "/api/hello", payload)) {
                onHelloOk(manualHost, j);
                return;
            }
            AppState.log("上次电脑无应答 " + manualHost);
        }
        int i = 0;
        while (AppState.debugToPc) {
            AtomicLong atomicLong = gen;
            if (atomicLong.get() != j || ok) {
                break;
            }
            discoverPc();
            if (atomicLong.get() != j || !AppState.debugToPc || ok) {
                return;
            }
            scanSubnet(j, i == 0 ? 80 : 500);
            if (atomicLong.get() != j || !AppState.debugToPc || ok) {
                return;
            }
            List<String> hosts = LanPush.hosts();
            AppState.log("尝试电脑 #" + j + " " + hosts);
            for (int i2 = 0; i2 < hosts.size(); i2++) {
                if (gen.get() != j || !AppState.debugToPc || ok) {
                    return;
                }
                String str = hosts.get(i2);
                if (!str.equals(manualHost)) {
                    AppState.log("握手 #" + j + " " + str);
                    if (post(str, "/api/hello", payload)) {
                        onHelloOk(str, j);
                        return;
                    }
                }
            }
            if (ok || gen.get() != j || !AppState.debugToPc) {
                return;
            }
            i++;
            lastStatus = "正在找电脑…";
            if (i == 1) {
                AppState.log("没找到，继续扫");
            }
            sleepQuiet(1500);
        }
        if (gen.get() == j && !ok) {
            lastStatus = "没找到电脑";
            AppState.log("没找到电脑。exe 要先打开，防火墙点允许，手机和电脑同一 WiFi");
            listenUdp();
        }
    }

    public static void setManualHost(String str) {
        String trim = str == null ? "" : str.trim();
        try {
            App.ctx.getSharedPreferences("tapsprite", 0).edit().putString("pcManual", trim).apply();
        } catch (Exception e) {
        }
        if (trim.length() > 6) {
            lastHost = trim;
            LanPush.prependHost(trim);
            AppState.log("指定电脑 " + trim);
            // Always handshake — previously hello() only ran when debugToPc was already on,
            // so tapping「连接」with the switch off did nothing beyond saving the IP.
            hello();
        }
    }

    /** Manual connect: save host, always attempt /api/hello on a background thread, report ok/fail. */
    public static void connectManual(final String host, final ConnectCallback cb) {
        final String trim = host == null ? "" : host.trim();
        if (trim.length() < 7) {
            if (cb != null) {
                cb.onResult(false, trim);
            }
            return;
        }
        try {
            App.ctx.getSharedPreferences("tapsprite", 0).edit().putString("pcManual", trim).apply();
        } catch (Exception e) {
        }
        lastHost = trim;
        LanPush.prependHost(trim);
        ok = false;
        AppState.log("手输连接 " + trim);
        AppState.ensureDevice();
        final long nextGen = nextGen();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    byte[] body = payload(true, nextGen);
                    AppState.log("握手 #" + nextGen + " " + trim);
                    if (post(trim, "/api/hello", body)) {
                        // Caller should have turned debugToPc on; force online state on HTTP success.
                        if (!AppState.debugToPc) {
                            AppState.debugToPc = true;
                        }
                        onHelloOk(trim, nextGen);
                        if (!ok) {
                            pcHost = trim;
                            lastHost = trim;
                            ok = true;
                            lastStatus = "局域网 " + trim;
                            listenUdp();
                            startPull();
                        }
                        success = true;
                    }
                } catch (Exception e) {
                    AppState.log("手输连接异常 " + e.getMessage());
                }
                if (success) {
                    AppState.log("已连上电脑 " + trim);
                } else {
                    AppState.log("连不上电脑 " + trim + "。检查 exe/同 WiFi/防火墙");
                    lastStatus = "连不上 " + trim;
                }
                if (cb != null) {
                    cb.onResult(success, trim);
                }
            }
        }, "tapsprite-manual-" + nextGen).start();
    }

    public interface ConnectCallback {
        void onResult(boolean ok, String host);
    }

    public static String manualHost() {
        try {
            String string = App.ctx.getSharedPreferences("tapsprite", 0).getString("pcManual", "");
            return string == null ? "" : string.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void loadSavedHost() {
        if (lastHost.length() > 0) {
            return;
        }
        try {
            String string = App.ctx.getSharedPreferences("tapsprite", 0).getString("pcHost", "");
            if (string != null && string.length() > 6) {
                lastHost = string;
                LanPush.prependHost(string);
            }
        } catch (Exception e) {
        }
    }

    private static void saveHost(String str) {
        try {
            App.ctx.getSharedPreferences("tapsprite", 0).edit().putString("pcHost", str).apply();
        } catch (Exception e) {
        }
    }

    private static void scanSubnet(final long j, final int i) {
        int i2;
        if (gen.get() != j || !AppState.debugToPc || ok) {
            return;
        }
        List<String> scanPrefixes = scanPrefixes();
        if (scanPrefixes.isEmpty()) {
            AppState.log("本机没有 IPv4，无法扫网段");
            return;
        }
        final ArrayList arrayList = new ArrayList();
        List<String> ipv4 = ConsoleServer.ipv4();
        for (int i3 = 0; i3 < scanPrefixes.size(); i3++) {
            if (gen.get() != j || !AppState.debugToPc || ok) {
                return;
            }
            String str = scanPrefixes.get(i3);
            AppState.log("扫描网段 " + str + "0/24  " + i + "ms");
            ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(48);
            final CountDownLatch countDownLatch = new CountDownLatch(254);
            int i4 = 1;
            for (int i5 = 254; i4 <= i5; i5 = 254) {
                final String str2 = str + i4;
                if (ipv4.contains(str2)) {
                    countDownLatch.countDown();
                    i2 = i4;
                } else {
                    i2 = i4;
                    newFixedThreadPool.execute(new Runnable() { // from class: com.tapsprite.agent.LanLink.3
                        @Override // java.lang.Runnable
                        public void run() {
                            Socket socket;
                            Throwable th;
                            if (LanLink.gen.get() != j || !AppState.debugToPc || LanLink.ok) {
                                countDownLatch.countDown();
                                return;
                            }
                            Socket socket2 = null;
                            try {
                                socket = new Socket();
                                try {
                                    socket.connect(new InetSocketAddress(str2, 18766), i);
                                    synchronized (arrayList) {
                                        if (!arrayList.contains(str2)) {
                                            arrayList.add(str2);
                                        }
                                    }
                                    LanPush.prependHost(str2);
                                    try {
                                        socket.close();
                                    } catch (Exception e) {
                                    }
                                } catch (Exception e2) {
                                    socket2 = socket;
                                    if (socket2 != null) {
                                        try {
                                            socket2.close();
                                        } catch (Exception e3) {
                                        }
                                    }
                                    countDownLatch.countDown();
                                } catch (Throwable th2) {
                                    th = th2;
                                    if (socket != null) {
                                        try {
                                            socket.close();
                                        } catch (Exception e4) {
                                        }
                                    }
                                    countDownLatch.countDown();
                                    throw th;
                                }
                            } catch (Exception e5) {
                            } catch (Throwable th3) {
                                socket = null;
                                th = th3;
                            }
                            countDownLatch.countDown();
                        }
                    });
                }
                i4 = i2 + 1;
            }
            try {
                countDownLatch.await((i * 8) + 1500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            newFixedThreadPool.shutdown();
            try {
                newFixedThreadPool.awaitTermination(1L, TimeUnit.SECONDS);
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
            }
        }
        AppState.log("网段扫到 " + arrayList.size() + " 台 " + arrayList);
    }

    private static List<String> scanPrefixes() {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        List<String> ipv4 = ConsoleServer.ipv4();
        if (ipv4 != null) {
            for (int i = 0; i < ipv4.size(); i++) {
                addNearby(linkedHashSet, ipv4.get(i));
            }
        }
        addNearby(linkedHashSet, dhcpGateway());
        ArrayList arrayList = new ArrayList();
        Iterator it = linkedHashSet.iterator();
        while (it.hasNext()) {
            arrayList.add((String) it.next());
            if (arrayList.size() >= 5) {
                break;
            }
        }
        return arrayList;
    }

    private static void addNearby(Set<String> set, String str) {
        if (str == null || str.length() < 7 || str.startsWith("127.")) {
            return;
        }
        String[] split = str.split("\\.");
        if (split.length != 4) {
            return;
        }
        try {
            int parseInt = Integer.parseInt(split[2]);
            for (int i = -2; i <= 2; i++) {
                int i2 = parseInt + i;
                if (i2 >= 0 && i2 <= 255) {
                    set.add(split[0] + "." + split[1] + "." + i2 + ".");
                }
            }
        } catch (Exception e) {
        }
    }

    private static String dhcpGateway() {
        int i;
        try {
            WifiManager wifiManager = (WifiManager) App.ctx.getApplicationContext().getSystemService("wifi");
            return (wifiManager == null || wifiManager.getDhcpInfo() == null || (i = wifiManager.getDhcpInfo().gateway) == 0) ? "" : (i & 255) + "." + ((i >> 8) & 255) + "." + ((i >> 16) & 255) + "." + ((i >> 24) & 255);
        } catch (Exception e) {
            return "";
        }
    }

    private static void onHelloOk(String str, long j) {
        lastHost = str;
        saveHost(str);
        LanPush.prependHost(str);
        if (!AppState.debugToPc || gen.get() != j) {
            AppState.log("握手 #" + j + " 已发出（开关已变）");
            return;
        }
        pcHost = str;
        ok = true;
        lastStatus = "局域网 " + str;
        AppState.log("已连电脑 " + str + " #" + j);
        try {
            App.ctx.getSharedPreferences("tapsprite", 0).edit().putString("pcManual", str).apply();
        } catch (Exception e) {
        }
        listenUdp();
        startPull();
    }

    /* JADX WARN: Removed duplicated region for block: B:60:0x011a  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private static void discoverPc() {
        DatagramSocket datagramSocket = null;
        try {
            datagramSocket = new DatagramSocket();
            datagramSocket.setBroadcast(true);
            datagramSocket.setSoTimeout(250);
            byte[] bytes = "TSHELLO".getBytes(UTF8);
            try {
                datagramSocket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("255.255.255.255"), 18766));
            } catch (Exception ignored) {
            }
            for (String str : ConsoleServer.ipv4()) {
                try {
                    int lastIndexOf = str.lastIndexOf(46);
                    if (lastIndexOf > 0) {
                        datagramSocket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName(str.substring(0, lastIndexOf + 1) + "255"), 18766));
                    }
                    String[] split = str.split("\\.");
                    if (split.length >= 2) {
                        datagramSocket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName(split[0] + "." + split[1] + ".255.255"), 18766));
                    }
                } catch (Exception ignored) {
                }
            }
            byte[] bArr = new byte[128];
            long deadline = System.currentTimeMillis() + 400;
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket datagramPacket = new DatagramPacket(bArr, 128);
                    datagramSocket.receive(datagramPacket);
                    String hostAddress = datagramPacket.getAddress().getHostAddress();
                    LanPush.prependHost(hostAddress);
                    AppState.log("UDP 发现电脑 " + hostAddress);
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Exception e) {
            AppState.log("UDP 搜索失败 " + e.getMessage());
        } finally {
            if (datagramSocket != null) {
                datagramSocket.close();
            }
        }
    }

    private static synchronized void startPull() {
        synchronized (LanLink.class) {
            Thread thread = puller;
            if (thread == null || !thread.isAlive()) {
                Thread thread2 = new Thread(new Runnable() { // from class: com.tapsprite.agent.LanLink.4
                    @Override // java.lang.Runnable
                    public void run() {
                        AppState.log("开始向电脑取指令");
                        while (AppState.debugToPc) {
                            String str = LanLink.pcHost;
                            if (str == null || str.length() == 0) {
                                LanLink.sleepQuiet(400);
                            } else {
                                String str2 = LanLink.get(str, "/api/pull?id=" + (AppState.deviceId == null ? "" : AppState.deviceId));
                                if (str2 == null) {
                                    AppState.log("电脑暂时连不上，重新握手");
                                    LanLink.reannounce();
                                    LanLink.sleepQuiet(500);
                                } else if (str2.indexOf("\"hello\":true") >= 0 || str2.indexOf("\"hello\": true") >= 0) {
                                    AppState.log("电脑不认识这台设备，重新握手");
                                    LanLink.reannounce();
                                    LanLink.sleepQuiet(1000);
                                } else if (str2.indexOf("\"cmd\":null") < 0 && str2.indexOf("\"cmd\": null") < 0 && str2.indexOf("\"type\"") >= 0) {
                                    AppState.log("收到电脑指令");
                                    LanLink.applyCmd(str2);
                                }
                            }
                        }
                        AppState.log("停止取指令");
                    }
                }, "tapsprite-pull");
                puller = thread2;
                thread2.setDaemon(true);
                puller.start();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void applyCmd(final String str) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: com.tapsprite.agent.LanLink.5
            @Override // java.lang.Runnable
            public void run() {
                String extractString = LanLink.extractString(str, "type");
                if ("script".equals(extractString)) {
                    String extractString2 = LanLink.extractString(str, "script");
                    if (extractString2 == null || extractString2.trim().length() == 0) {
                        return;
                    }
                    AppState.script = extractString2;
                    AppState.pcScript = extractString2;
                    AppState.scriptTab = 2;
                    AppState.log("已接收脚本 " + extractString2.length() + " 字");
                    MainActivity.ping();
                    ScriptActivity.ping();
                    boolean z = str.contains("\"run\":true") || str.contains("\"run\": true");
                    LanLink.startOverlay();
                    if (z) {
                        ScriptEngine.start();
                        return;
                    }
                    return;
                }
                if ("control".equals(extractString)) {
                    String extractString3 = LanLink.extractString(str, "action");
                    if ("start".equals(extractString3)) {
                        LanLink.startOverlay();
                        ScriptEngine.start();
                        return;
                    } else if ("stop".equals(extractString3)) {
                        ScriptEngine.requestStop();
                        return;
                    } else {
                        if ("shot".equals(extractString3) || "capture".equals(extractString3)) {
                            LanLink.sendShot();
                            return;
                        }
                        return;
                    }
                }
                if ("shot".equals(extractString) || "capture".equals(extractString)) {
                    LanLink.sendShot();
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String get(String str, String str2) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("http://" + str + ":18766" + str2).openConnection();
            httpURLConnection.setConnectTimeout(800);
            httpURLConnection.setReadTimeout(18000);
            httpURLConnection.setRequestMethod("GET");
            InputStream errorStream = httpURLConnection.getResponseCode() >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
            if (errorStream == null) {
                return null;
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[4096];
            while (true) {
                int read = errorStream.read(bArr);
                if (read <= 0) {
                    break;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
            return byteArrayOutputStream.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean post(String str, String str2, byte[] bArr) {
        return postEx(str, str2, bArr, true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean postQuiet(String str, String str2, byte[] bArr) {
        return postEx(str, str2, bArr, false);
    }

    private static boolean postEx(String str, String str2, byte[] bArr, boolean z) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("http://" + str + ":18766" + str2).openConnection();
            httpURLConnection.setConnectTimeout(700);
            httpURLConnection.setReadTimeout(900);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setFixedLengthStreamingMode(bArr.length);
            httpURLConnection.setRequestProperty("Content-Type", "application/json");
            if (AppState.deviceId != null) {
                httpURLConnection.setRequestProperty("X-Ts-Id", AppState.deviceId);
            }
            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(bArr);
            outputStream.flush();
            int responseCode = httpURLConnection.getResponseCode();
            if (z && (responseCode < 200 || responseCode >= 300)) {
                AppState.log("握手失败 " + str + " HTTP " + responseCode);
            }
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            if (z) {
                AppState.log("握手失败 " + str + " " + e.getMessage());
            }
            return false;
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String extractString(String str, String str2) {
        if (str == null || str2 == null) {
            return null;
        }
        String key = "\"" + str2 + "\"";
        int keyAt = str.indexOf(key);
        if (keyAt < 0) {
            return null;
        }
        int colon = str.indexOf(':', keyAt + key.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < str.length() && Character.isWhitespace(str.charAt(i))) {
            i++;
        }
        if (i >= str.length()) {
            return null;
        }
        if (str.charAt(i) == 'n' && str.startsWith("null", i)) {
            return null;
        }
        if (str.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length()) {
                char n = str.charAt(i + 1);
                if (n == 'n') {
                    sb.append('\n');
                    i += 2;
                } else if (n == 't') {
                    sb.append('\t');
                    i += 2;
                } else if (n == 'u' && i + 5 < str.length()) {
                    try {
                        sb.append((char) Integer.parseInt(str.substring(i + 2, i + 6), 16));
                        i += 6;
                    } catch (Exception e) {
                        sb.append(n);
                        i += 2;
                    }
                } else {
                    sb.append(n);
                    i += 2;
                }
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
                i++;
            }
        }
        return null;
    }


    public static void startOverlay() {
        try {
            Intent intent = new Intent(App.ctx, (Class<?>) OverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                App.ctx.startForegroundService(intent);
            } else {
                App.ctx.startService(intent);
            }
        } catch (Exception e) {
            AppState.log("悬浮窗启动失败 " + e.getMessage());
        }
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static byte[] payload(boolean z, long j) {
        return ("{\"id\":" + ConsoleServer.jsonStr(AppState.deviceId) + ",\"name\":" + ConsoleServer.jsonStr(AppState.deviceName) + ",\"a11y\":" + (AppState.auto != null) + ",\"cap\":" + CaptureService.ready + ",\"emu\":" + AppState.isEmulator() + ",\"ips\":" + ConsoleServer.jsonStr(join(ConsoleServer.ipv4())) + ",\"online\":" + z + ",\"gen\":" + j + "}").getBytes(UTF8);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void sleepQuiet(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void ack(String str) {
        if (!AppState.debugToPc) {
            return;
        }
        AppState.ensureDevice();
        long j = gen.get();
        if (post(str, "/api/hello", payload(true, j))) {
            onHelloOk(str, j);
        }
    }

    private static synchronized void listenUdp() {
        synchronized (LanLink.class) {
            Thread thread = udp;
            if (thread == null || !thread.isAlive()) {
                Thread thread2 = new Thread(new Runnable() { // from class: com.tapsprite.agent.LanLink.6
                    @Override // java.lang.Runnable
                    public void run() {
                        DatagramSocket datagramSocket = null;
                        try {
                            datagramSocket = new DatagramSocket(AppState.PORT);
                            LanLink.udpSock = datagramSocket;
                            datagramSocket.setBroadcast(true);
                            byte[] bArr = new byte[64];
                            while (AppState.debugToPc) {
                                DatagramPacket datagramPacket = new DatagramPacket(bArr, 64);
                                datagramSocket.receive(datagramPacket);
                                if (!AppState.debugToPc) {
                                    break;
                                }
                                if (new String(datagramPacket.getData(), 0, datagramPacket.getLength(), LanLink.UTF8).startsWith("TS?")) {
                                    String hostAddress = datagramPacket.getAddress().getHostAddress();
                                    LanPush.prependHost(hostAddress);
                                    LanLink.ack(hostAddress);
                                }
                            }
                        } catch (Exception e) {
                            // ignore
                        } finally {
                            if (datagramSocket != null) {
                                datagramSocket.close();
                            }
                            LanLink.udp = null;
                        }
                    }
                }, "tapsprite-udp");
                udp = thread2;
                thread2.setDaemon(true);
                udp.start();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void notifyPc(String str) {
        String str2 = pcHost.length() > 0 ? pcHost : lastHost;
        if (str2 == null || str2.length() == 0) {
            return;
        }
        post(str2, "/api/notice", ("{\"msg\":" + ConsoleServer.jsonStr(str) + ",\"kind\":\"warn\"}").getBytes(UTF8));
    }

    public static void tracePc(String str) {
        ConcurrentLinkedQueue<String> concurrentLinkedQueue;
        if (str == null) {
            str = "";
        }
        String str2 = pcHost.length() > 0 ? pcHost : lastHost;
        if (str2 == null || str2.length() == 0) {
            return;
        }
        while (true) {
            concurrentLinkedQueue = traces;
            if (concurrentLinkedQueue.size() <= 200) {
                break;
            } else {
                concurrentLinkedQueue.poll();
            }
        }
        concurrentLinkedQueue.add(str);
        if (tracing.compareAndSet(false, true)) {
            Thread thread = new Thread(new Runnable() { // from class: com.tapsprite.agent.LanLink.7
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        while (true) {
                            String str3 = (String) LanLink.traces.poll();
                            if (str3 == null) {
                                break;
                            }
                            String str4 = LanLink.pcHost.length() > 0 ? LanLink.pcHost : LanLink.lastHost;
                            if (str4 != null && str4.length() != 0) {
                                LanLink.postQuiet(str4, "/api/notice", ("{\"msg\":" + ConsoleServer.jsonStr(str3) + ",\"kind\":\"trace\"}").getBytes(LanLink.UTF8));
                            }
                        }
                    } finally {
                        LanLink.tracing.set(false);
                        if (!LanLink.traces.isEmpty() && LanLink.tracing.compareAndSet(false, true)) {
                            new Thread(this, "tapsprite-trace").start();
                        }
                    }
                }
            }, "tapsprite-trace");
            thread.setDaemon(true);
            thread.start();
        }
    }

    static void sendShot() {
        new Thread(new Runnable() { // from class: com.tapsprite.agent.LanLink.8
            @Override // java.lang.Runnable
            public void run() {
                int i;
                byte[] bArr;
                if (!CaptureService.ready) {
                    AppState.log("截屏权限未开");
                    LanLink.notifyPc("当前设备未开截屏权限。请在 App 首页点开启，并确认系统弹窗。");
                    return;
                }
                AppState.log("开始截图…");
                long uptimeMillis = SystemClock.uptimeMillis();
                String[] pngBase64 = CaptureService.pngBase64();
                if (pngBase64 == null) {
                    AppState.log("截不到图。先开截屏权限");
                    LanLink.notifyPc("截不到图。请在 App 里打开截屏权限后重试。");
                    return;
                }
                String str = pngBase64.length > 3 ? pngBase64[3] : "png";
                int i2 = 0;
                try {
                    i = Integer.parseInt(pngBase64[0]);
                    try {
                        i2 = Integer.parseInt(pngBase64[1]);
                    } catch (Exception e) {
                    }
                } catch (Exception e2) {
                    i = 0;
                }
                try {
                    bArr = Base64.decode(pngBase64[2], 2);
                } catch (Exception e3) {
                    bArr = null;
                }
                if (bArr != null && LanPush.push(bArr, i, i2, str)) {
                    AppState.log("截图走局域网 " + (SystemClock.uptimeMillis() - uptimeMillis) + "ms");
                } else {
                    AppState.log("局域网截图失败，确认电脑 exe 已打开");
                }
            }
        }, "tapsprite-shot").start();
    }
}

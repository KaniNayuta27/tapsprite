package com.tapsprite.agent;

import android.util.Log;
import com.tapsprite.agent.AppState;
import com.tapsprite.agent.ScriptParser;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
public class ConsoleServer {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private Thread acceptThread;
    private volatile boolean running = false;
    private ServerSocket server;

    public synchronized void start() {
        if (this.running) {
            return;
        }
        this.running = true;
        Thread thread = new Thread(new Runnable() { // from class: com.tapsprite.agent.ConsoleServer.1
            @Override // java.lang.Runnable
            public void run() {
                ConsoleServer.this.acceptLoop();
            }
        }, "tapsprite-http");
        this.acceptThread = thread;
        thread.setDaemon(true);
        this.acceptThread.start();
    }

    public synchronized void stop() {
        this.running = false;
        ServerSocket serverSocket = this.server;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }
    }

    public static List<String> ipv4() {
        return NetInfo.ipv4();
    }

    public static String lanHint() {
        return NetInfo.notifyText();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void acceptLoop() {
        try {
            ServerSocket serverSocket = new ServerSocket();
            this.server = serverSocket;
            serverSocket.setReuseAddress(true);
            this.server.bind(new InetSocketAddress("0.0.0.0", AppState.PORT));
            AppState.log("电脑控制台已打开 " + NetInfo.notifyText());
            while (this.running) {
                try {
                    final Socket accept = this.server.accept();
                    Thread thread = new Thread(new Runnable() { // from class: com.tapsprite.agent.ConsoleServer.2
                        @Override // java.lang.Runnable
                        public void run() {
                            ConsoleServer.this.handle(accept);
                        }
                    });
                    thread.setDaemon(true);
                    thread.start();
                } catch (IOException e) {
                    if (!this.running) {
                        return;
                    }
                }
            }
        } catch (IOException e2) {
            AppState.log("控制台端口打开失败：" + e2.getMessage());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handle(Socket socket) {
        String str;
        try {
            try {
                try {
                    socket.setSoTimeout(8000);
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(socket.getInputStream());
                    OutputStream outputStream = socket.getOutputStream();
                    String readLine = readLine(bufferedInputStream);
                    if (readLine != null && readLine.length() != 0) {
                        String[] split = readLine.split(" ");
                        if (split.length < 2) {
                            write(outputStream, 400, "text/plain; charset=utf-8", "bad request");
                            try {
                                socket.close();
                                return;
                            } catch (IOException e) {
                                return;
                            }
                        }
                        int i = 0;
                        String upperCase = split[0].toUpperCase(Locale.US);
                        String str2 = split[1];
                        int indexOf = str2.indexOf(63);
                        if (indexOf >= 0) {
                            String substring = str2.substring(indexOf + 1);
                            str2 = str2.substring(0, indexOf);
                            str = substring;
                        } else {
                            str = "";
                        }
                        int i2 = 0;
                        while (true) {
                            String readLine2 = readLine(bufferedInputStream);
                            if (readLine2 == null || readLine2.length() == 0) {
                                break;
                            }
                            int indexOf2 = readLine2.indexOf(58);
                            if (indexOf2 > 0) {
                                String lowerCase = readLine2.substring(0, indexOf2).trim().toLowerCase(Locale.US);
                                String trim = readLine2.substring(indexOf2 + 1).trim();
                                if (lowerCase.equals("content-length")) {
                                    try {
                                        i2 = Integer.parseInt(trim);
                                    } catch (NumberFormatException e2) {
                                    }
                                }
                            }
                        }
                        byte[] bArr = new byte[0];
                        if (i2 > 0) {
                            if (i2 > 262144) {
                                write(outputStream, 413, "text/plain; charset=utf-8", "too large");
                                try {
                                    socket.close();
                                    return;
                                } catch (IOException e3) {
                                    return;
                                }
                            } else {
                                bArr = new byte[i2];
                                while (i < i2) {
                                    int read = bufferedInputStream.read(bArr, i, i2 - i);
                                    if (read < 0) {
                                        break;
                                    } else {
                                        i += read;
                                    }
                                }
                            }
                        }
                        if ("OPTIONS".equals(upperCase)) {
                            write(outputStream, 204, "text/plain; charset=utf-8", "");
                            try {
                                socket.close();
                                return;
                            } catch (IOException e4) {
                                return;
                            }
                        } else {
                            route(outputStream, upperCase, str2, str, new String(bArr, UTF8));
                            socket.close();
                            return;
                        }
                    }
                    try {
                        socket.close();
                    } catch (IOException e5) {
                    }
                } catch (Exception e6) {
                    Log.w("TapSprite", "http", e6);
                    socket.close();
                }
            } catch (IOException e7) {
            }
        } catch (Throwable th) {
            try {
                socket.close();
            } catch (IOException e8) {
            }
            throw th;
        }
    }

    private void route(OutputStream outputStream, String str, String str2, String str3, String str4) throws IOException {
        if (str2.equals("/") || str2.equals("/index.html")) {
            write(outputStream, 200, "text/html; charset=utf-8", loadAsset("console.html"));
            return;
        }
        if (str2.equals("/api/status") && str.equals("GET")) {
            write(outputStream, 200, "application/json; charset=utf-8", statusJson());
            return;
        }
        if (str2.equals("/api/logs") && str.equals("GET")) {
            write(outputStream, 200, "application/json; charset=utf-8", logsJson(intQuery(str3, "after", 0)));
            return;
        }
        if (str2.equals("/api/script") && str.equals("GET")) {
            write(outputStream, 200, "application/json; charset=utf-8", "{\"script\":" + jsonStr(AppState.script) + "}");
            return;
        }
        if (str2.equals("/api/script") && str.equals("POST")) {
            String extractScript = extractScript(str4);
            if (extractScript.trim().length() == 0) {
                write(outputStream, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"脚本为空\"}");
                return;
            }
            ScriptParser.Result parse = ScriptParser.parse(extractScript);
            if (parse.error != null && !LuaEngine.looksLikeLua(extractScript) && !extractScript.contains("function") && !extractScript.contains("local ") && !extractScript.contains("then")) {
                write(outputStream, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":" + jsonStr(parse.error) + "}");
                return;
            }
            boolean persist = LanLink.jsonFlag(str4, "persist", true);
            if (LanLink.jsonFlag(str4, "library", false)) {
                persist = false;
            }
            boolean run = str4.contains("\"run\":true") || str4.contains("\"run\": true");
            if (persist) {
                AppState.script = extractScript;
                AppState.pcScript = extractScript;
                AppState.scriptTab = 2;
                AppState.log("已从电脑接收脚本 " + extractScript.length() + " 字" + (LuaEngine.looksLikeLua(extractScript) ? "（Lua）" : ""));
                if (run) {
                    ScriptEngine.start();
                }
            } else {
                AppState.log("脚本库运行 " + extractScript.length() + " 字");
                if (run) {
                    ScriptEngine.start(extractScript);
                }
            }
            write(outputStream, 200, "application/json; charset=utf-8", "{\"ok\":true}");
            return;
        }
        if (str2.equals("/api/control") && str.equals("POST")) {
            String extractAction = extractAction(str4);
            if ("start".equals(extractAction)) {
                write(outputStream, 200, "application/json; charset=utf-8", "{\"ok\":" + ScriptEngine.start() + "}");
                return;
            }
            if ("stop".equals(extractAction)) {
                ScriptEngine.requestStop();
                write(outputStream, 200, "application/json; charset=utf-8", "{\"ok\":true}");
                return;
            } else if ("shot".equals(extractAction) || "capture".equals(extractAction)) {
                LanLink.sendShot();
                write(outputStream, 200, "application/json; charset=utf-8", "{\"ok\":true}");
                return;
            } else {
                write(outputStream, 400, "application/json; charset=utf-8", "{\"ok\":false}");
                return;
            }
        }
        if (str2.equals("/api/shot") && str.equals("POST")) {
            LanLink.sendShot();
            write(outputStream, 200, "application/json; charset=utf-8", "{\"ok\":true}");
        } else {
            write(outputStream, 404, "text/plain; charset=utf-8", "not found");
        }
    }

    private static String extractScript(String str) {
        int indexOf;
        String trim = str.trim();
        if (trim.startsWith("{")) {
            int indexOf2 = trim.indexOf("\"script\"");
            if (indexOf2 < 0 || (indexOf = trim.indexOf(34, trim.indexOf(58, indexOf2 + "\"script\"".length()) + 1)) < 0) {
                return "";
            }
            return unescapeJson(trim, indexOf + 1);
        }
        return trim;
    }

    private static String extractAction(String str) {
        String trim = str.trim();
        if (trim.startsWith("{")) {
            int indexOf = trim.indexOf("\"action\"");
            if (indexOf < 0) {
                return "";
            }
            int indexOf2 = trim.indexOf(34, trim.indexOf(58, indexOf + "\"action\"".length()) + 1);
            if (indexOf2 < 0) {
                return trim.toLowerCase(Locale.US);
            }
            int i = indexOf2 + 1;
            int indexOf3 = trim.indexOf(34, i);
            return indexOf3 < 0 ? "" : trim.substring(i, indexOf3);
        }
        return trim.replace("\"", "").trim();
    }

    static String unescapePublic(String str, int i) {
        return unescapeJson(str, i);
    }

    private static String unescapeJson(String str, int i) {
        char charAt;
        int i2;
        int i3;
        StringBuilder sb = new StringBuilder();
        while (i < str.length() && (charAt = str.charAt(i)) != '\"') {
            if (charAt == '\\' && (i2 = i + 1) < str.length()) {
                char charAt2 = str.charAt(i2);
                if (charAt2 == 'n') {
                    sb.append('\n');
                } else if (charAt2 == 'r') {
                    sb.append('\r');
                } else if (charAt2 == 't') {
                    sb.append('\t');
                } else if (charAt2 == '\"') {
                    sb.append('\"');
                } else if (charAt2 == '\\') {
                    sb.append('\\');
                } else if (charAt2 == 'u' && (i3 = i2 + 4) < str.length()) {
                    try {
                        sb.append((char) Integer.parseInt(str.substring(i2 + 1, i2 + 5), 16));
                        i = i3;
                    } catch (Exception e) {
                        sb.append(charAt2);
                    }
                } else {
                    sb.append(charAt2);
                }
                i = i2;
            } else {
                sb.append(charAt);
            }
            i++;
        }
        return sb.toString();
    }

    private static int intQuery(String str, String str2, int i) {
        if (str == null || str.length() == 0) {
            return i;
        }
        for (String str3 : str.split("&")) {
            int indexOf = str3.indexOf(61);
            if (indexOf > 0 && str3.substring(0, indexOf).equals(str2)) {
                try {
                    return Integer.parseInt(URLDecoder.decode(str3.substring(indexOf + 1), "UTF-8"));
                } catch (Exception e) {
                    return i;
                }
            }
        }
        return i;
    }

    private static String statusJson() {
        List<String> ipv4 = ipv4();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ipv4.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(jsonStr(ipv4.get(i)));
        }
        sb.append(']');
        return "{\"running\":" + AppState.running + ",\"loaded\":" + AppState.loaded + ",\"debug\":" + AppState.debugToPc + ",\"step\":" + jsonStr(AppState.currentStep) + ",\"a11y\":" + (AppState.auto != null) + ",\"port\":" + AppState.PORT + ",\"ips\":" + ((Object) sb) + "}";
    }

    private static String logsJson(int i) {
        List<AppState.LogLine> logsAfter = AppState.logsAfter(i);
        StringBuilder sb = new StringBuilder("{\"lines\":[");
        for (int i2 = 0; i2 < logsAfter.size(); i2++) {
            AppState.LogLine logLine = logsAfter.get(i2);
            if (i2 > 0) {
                sb.append(',');
            }
            sb.append("{\"seq\":").append(logLine.seq).append(",\"t\":").append(logLine.t).append(",\"msg\":").append(jsonStr(logLine.msg)).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    static String jsonStr(String str) {
        if (str == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (charAt == '\"' || charAt == '\\') {
                sb.append('\\').append(charAt);
            } else if (charAt == '\n') {
                sb.append("\\n");
            } else if (charAt == '\r') {
                sb.append("\\r");
            } else if (charAt == '\t') {
                sb.append("\\t");
            } else if (charAt < ' ') {
                sb.append(charAt);
            } else {
                sb.append(charAt);
            }
        }
        sb.append('\"');
        return sb.toString();
    }

    private static String loadAsset(String str) {
        InputStream inputStream = null;
        try {
            inputStream = App.ctx.getAssets().open(str);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[4096];
            while (true) {
                int read = inputStream.read(bArr);
                if (read < 0) {
                    break;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
            String str2 = new String(byteArrayOutputStream.toByteArray(), UTF8);
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
            return str2;
        } catch (IOException e2) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e3) {
                }
            }
            return "<!doctype html><meta charset=utf-8><body>missing console</body>";
        } catch (Throwable th) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e4) {
                }
            }
            throw th;
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:15:0x002b, code lost:
    
        if (r1 != (-2)) goto L20;
     */
    /* JADX WARN: Code restructure failed: missing block: B:16:0x002d, code lost:
    
        return null;
     */
    /* JADX WARN: Code restructure failed: missing block: B:19:0x0039, code lost:
    
        return new java.lang.String(r0.toByteArray(), com.tapsprite.agent.ConsoleServer.UTF8);
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private static String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int i = -1;
        while (true) {
            int read = inputStream.read();
            if (read < 0) {
                if (byteArrayOutputStream.size() == 0) {
                    return null;
                }
            } else {
                if (read == 10) {
                    break;
                }
                if (read != 13) {
                    byteArrayOutputStream.write(read);
                }
                if (byteArrayOutputStream.size() > 8192) {
                    i = read;
                    break;
                }
                i = read;
            }
        }
        return new String(byteArrayOutputStream.toByteArray(), UTF8);
    }

    private static void write(OutputStream outputStream, int i, String str, String str2) throws IOException {
        Charset charset = UTF8;
        byte[] bytes = str2.getBytes(charset);
        outputStream.write(("HTTP/1.1 " + i + " " + (i == 200 ? "OK" : i == 404 ? "Not Found" : "Error") + "\r\nContent-Type: " + str + "\r\nContent-Length: " + bytes.length + "\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(charset));
        outputStream.write(bytes);
        outputStream.flush();
    }
}

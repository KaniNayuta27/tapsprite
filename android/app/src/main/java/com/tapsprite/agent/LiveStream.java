package com.tapsprite.agent;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Low-latency screen mirror: one MediaCodec H.264 encoder fed by the existing
 * MediaProjection VirtualDisplay (Android 14 allows createVirtualDisplay only
 * once per grant, so the surface is swapped and the display object is kept).
 * Stopping releases the encoder. The grant stays so the next 开始 does not
 * re-prompt. Bytes go to the PC on a WebSocket over port 18766.
 *
 * <p>Binary messages (one WebSocket binary frame):
 * <pre>
 * 0x01 + utf-8 JSON meta {encW,encH,physW,physH,rot,ts}
 * 0x02 + u8 flags (bit0 = key) + u64be unix ms + Annex-B access unit
 * 0x03 + utf-8 JSON control (down / move / up / tap / swipe / global / sync / stop / fps / wake / unlock / mapped / log)
 * </pre>
 */
public final class LiveStream {
    static final int MAX_LONG = 1280;
    private static final int MAX_AU = 1_500_000;
    private static final AtomicInteger MAX_FPS = new AtomicInteger(30);
    private static final AtomicInteger FPS_GEN = new AtomicInteger();
    private static final Object LIFE = new Object();
    private static final AtomicInteger SESSION = new AtomicInteger();
    private static final ExecutorService GESTURES = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "tapsprite-live-in");
            t.setDaemon(true);
            return t;
        }
    });

    private static volatile Thread loopThread;
    private static volatile Wire wire;
    private static volatile Disp shown;
    private static volatile boolean active;
    private static volatile Sender activeSender;
    private static volatile String reopenWhy = "";
    private static int lastEncLevel = -1;
    /** Encoder input while a session is running. 抓抓 borrows the projection around it. */
    private static volatile Surface encSurface;
    private static volatile int encW;
    private static volatile int encH;

    private LiveStream() {
    }

    public static boolean active() {
        return active;
    }

    /** Non-blocking. Safe from the socket read thread. */
    public static void requestStop() {
        SESSION.incrementAndGet();
        LivePointer.lift();
        Wire w = wire;
        if (w != null) {
            w.close();
        }
    }

    public static void stop() {
        Thread prev;
        synchronized (LIFE) {
            SESSION.incrementAndGet();
            prev = loopThread;
            loopThread = null;
        }
        LivePointer.lift();
        Wire w = wire;
        if (w != null) {
            w.close();
        }
        if (prev != null && prev != Thread.currentThread()) {
            try {
                prev.join(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void onProjectionStopped() {
        requestStop();
    }

    public static void start() {
        if (!CaptureService.hasDisplay()) {
            MainActivity.askCaptureForLive();
            AppState.log("实时操控等待截屏授权");
            return;
        }
        Thread prev;
        synchronized (LIFE) {
            SESSION.incrementAndGet();
            prev = loopThread;
        }
        if (prev != null && prev != Thread.currentThread()) {
            try {
                prev.join(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        final int id;
        synchronized (LIFE) {
            id = SESSION.incrementAndGet();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    runSession(id);
                }
            }, "tapsprite-live");
            t.setDaemon(true);
            loopThread = t;
            t.start();
        }
    }

    private static void runSession(int id) {
        Wire local = null;
        MediaCodec codec = null;
        Sender sender = null;
        try {
            String host = LanLink.pcAddr();
            if (host == null || host.length() == 0) {
                AppState.log("实时操控没有电脑地址");
                return;
            }
            for (int i = 0; i < 20 && SESSION.get() == id && !CaptureService.hasDisplay(); i++) {
                sleep(100);
            }
            if (SESSION.get() != id) {
                return;
            }
            if (!CaptureService.hasDisplay()) {
                AppState.log("实时操控没有截屏授权");
                return;
            }
            local = Wire.connect(host);
            wire = local;
            sender = new Sender(local);
            activeSender = sender;
            Thread tx = new Thread(sender, "tapsprite-live-tx");
            tx.setDaemon(true);
            tx.start();
            final Sender txRef = sender;
            local.startRead(new Wire.Inbound() {
                @Override
                public void onBinary(byte[] msg) {
                    onInbound(id, msg, txRef);
                }
            });
            active = true;
            while (SESSION.get() == id) {
                Disp d = readDisp();
                shown = d;
                int[] enc = LiveCoords.encodeSize(d.w, d.h, MAX_LONG);
                codec = openEncoder(enc[0], enc[1]);
                if (codec == null) {
                    AppState.log("实时操控编码器打开失败");
                    break;
                }
                int fpsNow = LiveFps.normalize(MAX_FPS.get());
                Surface surface = codec.createInputSurface();
                codec.start();
                limitSurfaceFps(surface, fpsNow);
                if (!CaptureService.attachLiveSurface(surface, enc[0], enc[1])) {
                    AppState.log("实时操控无法挂上投影");
                    break;
                }
                encSurface = surface;
                encW = enc[0];
                encH = enc[1];
                AppState.log("实时操控开始 " + enc[0] + "x" + enc[1]
                        + " 屏 " + d.w + "x" + d.h + " rot " + d.rotDeg
                        + " L" + lastEncLevel + " " + fpsNow + "fps"
                        + (fpsNow <= LiveFps.LOW ? " 锁定" : "") + " "
                        + (fpsNow >= 60 ? 6 : 3) + "Mbps");
                sender.urgent(metaMessage(d, enc[0], enc[1]));
                requestSync(codec);
                boolean reopen = pump(codec, sender, id, d, enc[0], enc[1]);
                encSurface = null;
                releaseCodec(codec);
                codec = null;
                CaptureService.restoreShotSurface();
                if (!reopen || SESSION.get() != id) {
                    break;
                }
                String why = reopenWhy;
                reopenWhy = "";
                AppState.log("实时操控重开编码 " + why);
            }
        } catch (Throwable t) {
            AppState.log("实时操控中断 " + t.getMessage());
        } finally {
            encSurface = null;
            releaseCodec(codec);
            LivePointer.lift();
            if (activeSender == sender) {
                activeSender = null;
            }
            if (sender != null) {
                sender.close();
            }
            if (local != null) {
                local.close();
                if (wire == local) {
                    wire = null;
                }
            }
            // start() joins this thread before opening the next session, so restoring
            // the screenshot surface here cannot clobber a newer encoder.
            CaptureService.restoreShotSurface();
            if (loopThread == Thread.currentThread() || SESSION.get() == id) {
                active = false;
            }
            if (SESSION.get() == id) {
                AppState.log("实时操控结束");
            }
        }
    }

    /** @return true if the caller should reopen the encoder (rotation, size, or fps). */
    private static boolean pump(MediaCodec codec, Sender sender, int id, Disp d, int encW, int encH) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] csd = null;
        long nextWatch = SystemClock.elapsedRealtime() + 200;
        int watchRot = d.rotDeg;
        int watchW = d.w;
        int watchH = d.h;
        int watchGen = FPS_GEN.get();
        while (SESSION.get() == id) {
            if (FPS_GEN.get() != watchGen) {
                reopenWhy = "帧率";
                return true;
            }
            if (syncWanted) {
                requestSync(codec);
            }
            if (SystemClock.elapsedRealtime() >= nextWatch) {
                Disp n = readDisp();
                shown = n;
                if (n.rotDeg != watchRot || n.w != watchW || n.h != watchH) {
                    reopenWhy = "旋转";
                    return true;
                }
                nextWatch = SystemClock.elapsedRealtime() + 200;
            }
            int idx;
            try {
                idx = codec.dequeueOutputBuffer(info, 10000);
            } catch (Exception e) {
                return false;
            }
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue;
            }
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                csd = mergeCsd(csd, codec.getOutputFormat());
                continue;
            }
            if (idx < 0) {
                continue;
            }
            boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            byte[] annex = null;
            try {
                ByteBuffer buf = codec.getOutputBuffer(idx);
                if (buf != null && info.size > 0) {
                    annex = toAnnexB(buf, info.offset, info.size);
                }
            } catch (Exception ignored) {
            }
            try {
                codec.releaseOutputBuffer(idx, false);
            } catch (Exception ignored) {
            }
            if (annex == null || annex.length == 0) {
                continue;
            }
            if (config || (!containsNal(annex, 1) && !containsNal(annex, 5))) {
                csd = annex;
                continue;
            }
            boolean key = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 || containsNal(annex, 5);
            if (key && csd != null && !containsNal(annex, 7)) {
                annex = concat(csd, annex);
            }
            if (annex.length > MAX_AU) {
                sender.dropUntilKey();
                syncWanted = true;
                if (key) {
                    AppState.log("实时操控关键帧过大，请求新关键帧");
                }
                continue;
            }
            sender.video(videoMessage(annex, key, System.currentTimeMillis()));
        }
        return false;
    }

    private static void onInbound(final int id, byte[] msg, Sender sender) {
        if (msg == null || msg.length < 2 || msg[0] != 3) {
            return;
        }
        final String json = new String(msg, 1, msg.length - 1, StandardCharsets.UTF_8);
        String op = "";
        try {
            op = new JSONObject(json).optString("op", "");
        } catch (Exception e) {
            return;
        }
        if ("stop".equals(op)) {
            requestStop();
            return;
        }
        if ("sync".equals(op)) {
            syncWanted = true;
            return;
        }
        if ("fps".equals(op)) {
            applyFps(json);
            return;
        }
        if ("wake".equals(op)) {
            WakeActivity.launch(false);
            return;
        }
        if ("unlock".equals(op)) {
            WakeActivity.launch(true);
            return;
        }
        if ("down".equals(op) || "move".equals(op) || "up".equals(op)) {
            if (SESSION.get() != id) {
                return;
            }
            try {
                handlePointer(json, op, sender);
            } catch (Exception e) {
                AppState.log("实时操控手势失败 " + e.getMessage());
            }
            return;
        }
        if ("global".equals(op)) {
            GESTURES.execute(new Runnable() {
                @Override
                public void run() {
                    if (SESSION.get() != id) {
                        return;
                    }
                    try {
                        handleGlobal(json);
                    } catch (Exception e) {
                        AppState.log("实时操控按键失败 " + e.getMessage());
                    }
                }
            });
            return;
        }
        if (!"tap".equals(op) && !"swipe".equals(op)) {
            return;
        }
        GESTURES.execute(new Runnable() {
            @Override
            public void run() {
                if (SESSION.get() != id) {
                    return;
                }
                try {
                    handleGesture(json, sender);
                } catch (Exception e) {
                    AppState.log("实时操控手势失败 " + e.getMessage());
                }
            }
        });
    }

    private static volatile boolean syncWanted;

    private static void requestSync(MediaCodec codec) {
        syncWanted = false;
        try {
            Bundle b = new Bundle();
            b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            codec.setParameters(b);
        } catch (Exception ignored) {
        }
    }

    /**
     * Ask SurfaceFlinger to deliver frames at {@code fps}. KEY_FRAME_RATE alone
     * is a hint; many encoders still emit the panel refresh (often 60) unless
     * the input surface requests a fixed rate. API 31 must pass ALWAYS or the
     * change from the display rate is refused as non-seamless.
     */
    private static void limitSurfaceFps(Surface surface, int fps) {
        if (surface == null || Build.VERSION.SDK_INT < 30) {
            return;
        }
        float rate = LiveFps.normalize(fps);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS);
            } else {
                surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
            }
        } catch (Throwable t) {
            AppState.log("实时操控帧率限制失败 " + t.getMessage());
        }
    }

    /**
     * Point the one MediaProjection display back at the screenshot reader for a
     * single fresh frame, then return it to the encoder. targetSdk 34 allows
     * only one virtual display per grant, so 抓抓 and 实时操控 cannot capture at
     * the same time without this borrow.
     *
     * @return true when a new reader frame arrived
     */
    static boolean lendForShot() {
        Surface surface = encSurface;
        int w = encW;
        int h = encH;
        if (surface == null || w < 2 || h < 2) {
            return false;
        }
        long prev = CaptureService.frameSeq();
        CaptureService.restoreShotSurface();
        boolean fresh = CaptureService.awaitFrameAfter(prev, 800);
        if (encSurface != surface) {
            return fresh;
        }
        if (!CaptureService.attachLiveSurface(surface, w, h)) {
            AppState.log("抓抓后未能挂回实时画面");
            return fresh;
        }
        limitSurfaceFps(surface, MAX_FPS.get());
        syncWanted = true;
        return fresh;
    }

    /** 30 (default) or 60. Anything at or above 60 is the 60 fps / 6 Mbps mode. */
    private static void applyFps(String json) {
        int requested = 30;
        try {
            requested = new JSONObject(json).optInt("max", 30);
        } catch (Exception ignored) {
        }
        int fps = LiveFps.normalize(requested);
        int prev = MAX_FPS.getAndSet(fps);
        if (prev != fps) {
            FPS_GEN.incrementAndGet();
            AppState.log("实时操控帧率 " + fps);
        }
    }

    private static void handlePointer(String json, String op, Sender sender) throws Exception {
        JSONObject o = new JSONObject(json);
        double nx = o.optDouble("nx", 0);
        double ny = o.optDouble("ny", 0);
        Disp d = shown != null ? shown : readDisp();
        int[] px = LiveCoords.toPhysical(nx, ny, d.w, d.h, d.rotDeg, false);
        if ("down".equals(op) || "up".equals(op)) {
            logMap(op, px[0], px[1], nx, ny, d);
            sendMapped(sender, nx, ny, px[0], px[1], d);
        }
        long held = -1;
        if ("up".equals(op) && o.has("held")) {
            held = o.optLong("held", -1);
        }
        LivePointer.input(op, px[0], px[1], held);
    }

    /** Phone log line that also shows up in the PC console log. */
    static void reportLive(String msg) {
        if (msg == null || msg.length() == 0) {
            return;
        }
        AppState.log(msg);
        Sender s = activeSender;
        if (s != null) {
            try {
                JSONObject o = new JSONObject();
                o.put("op", "log");
                o.put("msg", msg);
                byte[] body = o.toString().getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[1 + body.length];
                frame[0] = 3;
                System.arraycopy(body, 0, frame, 1, body.length);
                s.urgent(frame);
                return;
            } catch (Exception ignored) {
            }
        }
        LanLink.tracePc(msg);
    }

    /** API &lt; 26, or no accessibility service: one swipe after release. Not used while a live finger is streaming. */
    static void playFallback(final float[] xs, final float[] ys, final int ms) {
        GESTURES.execute(new Runnable() {
            @Override
            public void run() {
                if (xs == null || ys == null || xs.length == 0 || ys.length == 0) {
                    return;
                }
                int n = Math.min(xs.length, ys.length);
                int x0 = Math.round(xs[0]);
                int y0 = Math.round(ys[0]);
                boolean same = true;
                for (int i = 1; i < n; i++) {
                    if (Math.round(xs[i]) != x0 || Math.round(ys[i]) != y0) {
                        same = false;
                        break;
                    }
                }
                if (n == 1 || same) {
                    execHold(xs[0], ys[0], ms);
                } else {
                    execPath(xs, ys, n, ms);
                }
            }
        });
    }

    private static void handleGlobal(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        String action = o.optString("action", "");
        if (LiveActions.codeFor(action, Build.VERSION.SDK_INT) < 0) {
            AppState.log("实时操控按键不支持 " + action + " api " + Build.VERSION.SDK_INT);
            return;
        }
        synchronized (DeviceGate.LOCK) {
            AutoService auto = AppState.auto;
            if (auto == null) {
                AppState.log("实时操控无障碍未连，按键未发送");
                return;
            }
            boolean ok = auto.performNamedGlobal(action);
            AppState.log("实时操控按键 " + action + (ok ? "" : " 失败"));
        }
    }

    private static void handleGesture(String json, Sender sender) throws Exception {
        JSONObject o = new JSONObject(json);
        String op = o.optString("op", "");
        Disp d = shown != null ? shown : readDisp();
        if ("tap".equals(op)) {
            double nx = o.optDouble("nx", 0);
            double ny = o.optDouble("ny", 0);
            int[] px = LiveCoords.toPhysical(nx, ny, d.w, d.h, d.rotDeg, false);
            logMap("tap", px[0], px[1], nx, ny, d);
            sendMapped(sender, nx, ny, px[0], px[1], d);
            execTap(px[0], px[1]);
            return;
        }
        if (!"swipe".equals(op)) {
            return;
        }
        JSONArray pts = o.optJSONArray("pts");
        if (pts == null || pts.length() == 0) {
            return;
        }
        int ms = o.optInt("ms", 180);
        if (ms < 40) {
            ms = 40;
        }
        if (ms > 10000) {
            ms = 10000;
        }
        int n = Math.min(pts.length(), 64);
        float[] xs = new float[n];
        float[] ys = new float[n];
        int m = 0;
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        double lastNx = 0;
        double lastNy = 0;
        double firstNx = 0;
        double firstNy = 0;
        for (int i = 0; i < n; i++) {
            JSONArray p = pts.optJSONArray(i);
            if (p == null || p.length() < 2) {
                continue;
            }
            double nx = p.optDouble(0, 0);
            double ny = p.optDouble(1, 0);
            int[] px = LiveCoords.toPhysical(nx, ny, d.w, d.h, d.rotDeg, false);
            if (px[0] == lastX && px[1] == lastY) {
                continue;
            }
            if (m == 0) {
                firstNx = nx;
                firstNy = ny;
            }
            xs[m] = px[0];
            ys[m] = px[1];
            lastX = px[0];
            lastY = px[1];
            lastNx = nx;
            lastNy = ny;
            m++;
        }
        if (m <= 0) {
            return;
        }
        if (m == 1) {
            logMap("tap", (int) xs[0], (int) ys[0], firstNx, firstNy, d);
            sendMapped(sender, firstNx, firstNy, (int) xs[0], (int) ys[0], d);
            execTap(xs[0], ys[0]);
            return;
        }
        AppState.log(String.format(Locale.US,
                "实时操控 swipe %d,%d → %d,%d %dms n=%.4f,%.4f rot=%d %dx%d",
                (int) xs[0], (int) ys[0], (int) xs[m - 1], (int) ys[m - 1], ms,
                lastNx, lastNy, d.rotDeg, d.w, d.h));
        sendMapped(sender, lastNx, lastNy, (int) xs[m - 1], (int) ys[m - 1], d);
        execPath(xs, ys, m, ms);
    }

    private static void logMap(String op, int x, int y, double nx, double ny, Disp d) {
        AppState.log(String.format(Locale.US,
                "实时操控 %s %d,%d n=%.4f,%.4f rot=%d %dx%d",
                op, x, y, nx, ny, d.rotDeg, d.w, d.h));
    }

    private static void sendMapped(Sender sender, double nx, double ny, int x, int y, Disp d) {
        if (sender == null) {
            return;
        }
        String json = String.format(Locale.US,
                "{\"op\":\"mapped\",\"nx\":%.5f,\"ny\":%.5f,\"px\":%d,\"py\":%d,\"physW\":%d,\"physH\":%d,\"rot\":%d,\"encW\":%d,\"encH\":%d}",
                nx, ny, x, y, d.w, d.h, d.rotDeg, 0, 0);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] msg = new byte[1 + body.length];
        msg[0] = 3;
        System.arraycopy(body, 0, msg, 1, body.length);
        sender.urgent(msg);
    }

    private static void execTap(float x, float y) {
        synchronized (DeviceGate.LOCK) {
            AutoService auto = AppState.auto;
            if (auto != null) {
                auto.tap(x, y);
            } else {
                AppState.log("实时操控无障碍未连，改用 input tap");
                ShellInput.tap(x, y);
            }
        }
    }

    /** Stationary press for the API &lt; 26 fallback. Duration is the real hold, so long-press can fire after release. */
    private static void execHold(float x, float y, int ms) {
        synchronized (DeviceGate.LOCK) {
            AutoService auto = AppState.auto;
            if (auto != null) {
                auto.touch(x, y, ms);
            } else {
                AppState.log("实时操控无障碍未连，改用 input swipe");
                ShellInput.swipe(x, y, x, y, ms);
            }
        }
    }

    private static void execPath(float[] xs, float[] ys, int n, int ms) {
        synchronized (DeviceGate.LOCK) {
            AutoService auto = AppState.auto;
            if (auto != null) {
                auto.strokePath(xs, ys, n, ms);
            } else {
                AppState.log("实时操控无障碍未连，改用 input swipe");
                ShellInput.swipe(xs[0], ys[0], xs[n - 1], ys[n - 1], ms);
            }
        }
    }

    private static byte[] metaMessage(Disp d, int encW, int encH) {
        int api = Build.VERSION.SDK_INT;
        String json = "{\"encW\":" + encW + ",\"encH\":" + encH
                + ",\"physW\":" + d.w + ",\"physH\":" + d.h
                + ",\"rot\":" + d.rotDeg
                + ",\"api\":" + api
                + ",\"actions\":" + LiveActions.actionsJson(api)
                + ",\"fps\":" + LiveFps.normalize(MAX_FPS.get())
                + ",\"ts\":" + System.currentTimeMillis() + "}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] msg = new byte[1 + body.length];
        msg[0] = 1;
        System.arraycopy(body, 0, msg, 1, body.length);
        return msg;
    }

    private static byte[] videoMessage(byte[] annex, boolean key, long unixMs) {
        byte[] msg = new byte[10 + annex.length];
        msg[0] = 2;
        msg[1] = (byte) (key ? 1 : 0);
        long ts = unixMs;
        for (int i = 0; i < 8; i++) {
            msg[2 + i] = (byte) ((ts >>> ((7 - i) * 8)) & 0xff);
        }
        System.arraycopy(annex, 0, msg, 10, annex.length);
        return msg;
    }

    static final class Disp {
        int w = 1080;
        int h = 2400;
        int rotDeg;
        int dpi = 480;
    }

    /** Current logical display: WindowMetrics on API 30+, else getRealMetrics. */
    static Disp readDisp() {
        Disp d = new Disp();
        try {
            WindowManager wm = (WindowManager) App.ctx.getSystemService("window");
            if (wm == null) {
                return d;
            }
            Display display = wm.getDefaultDisplay();
            int rot = display.getRotation();
            d.rotDeg = (rot == 1 ? 90 : rot == 2 ? 180 : rot == 3 ? 270 : 0);
            DisplayMetrics real = new DisplayMetrics();
            display.getRealMetrics(real);
            if (real.widthPixels > 0 && real.heightPixels > 0) {
                d.w = real.widthPixels;
                d.h = real.heightPixels;
            }
            if (real.densityDpi > 0) {
                d.dpi = real.densityDpi;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Rect b = wm.getMaximumWindowMetrics().getBounds();
                if (b.width() > 1 && b.height() > 1) {
                    d.w = b.width();
                    d.h = b.height();
                }
            }
        } catch (Throwable t) {
            AppState.log("实时操控读屏失败 " + t.getMessage());
        }
        if (d.w < 2) {
            d.w = 2;
        }
        if (d.h < 2) {
            d.h = 2;
        }
        return d;
    }

    private static MediaCodec openEncoder(int w, int h) {
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            tryConfigure(codec, w, h, 2);
            lastEncLevel = 2;
            return codec;
        } catch (Exception first) {
            releaseCodec(codec);
        }
        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            tryConfigure(codec, w, h, 1);
            lastEncLevel = 1;
            return codec;
        } catch (Exception second) {
            releaseCodec(codec);
        }
        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            tryConfigure(codec, w, h, 0);
            lastEncLevel = 0;
            return codec;
        } catch (Exception third) {
            releaseCodec(codec);
            AppState.log("实时操控 configure 失败 " + third.getMessage());
            return null;
        }
    }

    /**
     * level 2 = baseline + low latency, 1 = no profile, 0 = minimal (no fps cap, no CBR).
     * 30 fps is 3 Mbps / Level 3.1. 60 fps is 6 Mbps / Level 4.0. GOP stays 1 second.
     * Intra refresh is left unset: one lost frame would smear for the whole period.
     */
    private static void tryConfigure(MediaCodec codec, int w, int h, int level) {
        int fps = LiveFps.normalize(MAX_FPS.get());
        int bitrate = fps >= 60 ? 6_000_000 : 3_000_000;
        int avcLevel = fps >= 60
                ? MediaCodecInfo.CodecProfileLevel.AVCLevel4
                : MediaCodecInfo.CodecProfileLevel.AVCLevel31;
        MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        if (level >= 1 && Build.VERSION.SDK_INT >= 21) {
            fmt.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        }
        if (level >= 1 && Build.VERSION.SDK_INT >= 23) {
            fmt.setInteger(MediaFormat.KEY_PRIORITY, 0);
            fmt.setFloat(MediaFormat.KEY_OPERATING_RATE, fps);
        }
        if (level >= 1 && Build.VERSION.SDK_INT >= 29) {
            // String key: the public constant arrived in API 31, and many API 29 encoders honor it.
            // The drop happens on input frames, so the bitstream stays decodable.
            fmt.setInteger("max-fps-to-encoder", fps);
            // String form: this SDK names the same key KEY_PREPEND_HEADER_TO_SYNC_FRAMES.
            fmt.setInteger("prepend-sps-pps-to-idr-frames", 1);
        }
        if (level >= 2 && Build.VERSION.SDK_INT >= 29) {
            fmt.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
        }
        if (level >= 2 && Build.VERSION.SDK_INT >= 30) {
            fmt.setInteger(MediaFormat.KEY_LATENCY, 1);
        }
        if (level >= 2) {
            fmt.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            fmt.setInteger(MediaFormat.KEY_LEVEL, avcLevel);
        }
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private static void releaseCodec(MediaCodec codec) {
        if (codec == null) {
            return;
        }
        try {
            codec.stop();
        } catch (Exception ignored) {
        }
        try {
            codec.release();
        } catch (Exception ignored) {
        }
    }

    private static byte[] mergeCsd(byte[] csd, MediaFormat fmt) {
        try {
            ByteBuffer c0 = fmt.getByteBuffer("csd-0");
            ByteBuffer c1 = fmt.getByteBuffer("csd-1");
            byte[] a = c0 == null ? null : toAnnexB(c0, c0.position(), c0.remaining());
            byte[] b = c1 == null ? null : toAnnexB(c1, c1.position(), c1.remaining());
            if (a == null) {
                return csd;
            }
            if (b == null) {
                return a;
            }
            return concat(a, b);
        } catch (Exception e) {
            return csd;
        }
    }

    static byte[] toAnnexB(ByteBuffer buf, int offset, int size) {
        if (size <= 0) {
            return new byte[0];
        }
        ByteBuffer dup = buf.duplicate();
        dup.position(offset);
        dup.limit(offset + size);
        byte[] raw = new byte[size];
        dup.get(raw);
        return LiveAnnex.toAnnexB(raw);
    }

    static boolean containsNal(byte[] annex, int type) {
        int i = 0;
        while (i + 4 < annex.length) {
            int sc = 0;
            if (annex[i] == 0 && annex[i + 1] == 0 && annex[i + 2] == 1) {
                sc = 3;
            } else if (annex[i] == 0 && annex[i + 1] == 0 && annex[i + 2] == 0 && annex[i + 3] == 1) {
                sc = 4;
            }
            if (sc > 0 && i + sc < annex.length) {
                if ((annex[i + sc] & 0x1f) == type) {
                    return true;
                }
                i += sc + 1;
            } else {
                i++;
            }
        }
        return false;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] o = new byte[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Three-frame video queue plus a short urgent queue. Overflow waits for the next keyframe. */
    static final class Sender implements Runnable {
        private final Wire wire;
        private final Object lock = new Object();
        private final ArrayDeque<byte[]> urgent = new ArrayDeque<byte[]>();
        private final LiveAuQueue video = new LiveAuQueue();
        private boolean closed;

        Sender(Wire wire) {
            this.wire = wire;
        }

        void urgent(byte[] msg) {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                while (urgent.size() >= 8) {
                    urgent.pollFirst();
                }
                urgent.addLast(msg);
                lock.notifyAll();
            }
        }

        void video(byte[] msg) {
            boolean needKey;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                needKey = video.offer(msg);
                lock.notifyAll();
            }
            if (needKey) {
                syncWanted = true;
            }
        }

        void dropUntilKey() {
            synchronized (lock) {
                video.dropUntilKey();
                lock.notifyAll();
            }
        }

        void close() {
            synchronized (lock) {
                closed = true;
                lock.notifyAll();
            }
        }

        @Override
        public void run() {
            while (true) {
                byte[] msg;
                synchronized (lock) {
                    while (!closed && urgent.isEmpty() && video.isEmpty()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    if (!urgent.isEmpty()) {
                        msg = urgent.pollFirst();
                    } else if (!video.isEmpty()) {
                        msg = video.poll();
                    } else {
                        return;
                    }
                }
                try {
                    wire.sendBinary(msg);
                } catch (Exception e) {
                    requestStop();
                    return;
                }
            }
        }
    }

    /** Minimal WebSocket client. TCP_NODELAY, masked client frames. */
    static final class Wire {
        interface Inbound {
            void onBinary(byte[] msg);
        }

        private final Socket socket;
        private final OutputStream out;
        private final InputStream in;
        private final Object writeLock = new Object();
        private final SecureRandom rnd = new SecureRandom();
        private volatile boolean closed;

        private Wire(Socket socket) throws IOException {
            this.socket = socket;
            this.out = socket.getOutputStream();
            this.in = socket.getInputStream();
        }

        static Wire connect(String host) throws IOException {
            IOException last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                Socket s = new Socket();
                try {
                    s.setTcpNoDelay(true);
                    s.setKeepAlive(true);
                    s.setSendBufferSize(256 * 1024);
                    s.setReceiveBufferSize(256 * 1024);
                    s.connect(new InetSocketAddress(host, 18766), 4000);
                    s.setSoTimeout(4000);
                    Wire w = new Wire(s);
                    w.handshake(host);
                    s.setSoTimeout(0);
                    return w;
                } catch (IOException e) {
                    last = e;
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                    sleep(200);
                }
            }
            throw last != null ? last : new IOException("connect");
        }

        private void handshake(String host) throws IOException {
            byte[] keyRaw = new byte[16];
            rnd.nextBytes(keyRaw);
            String key = Base64.encodeToString(keyRaw, Base64.NO_WRAP);
            String id = AppState.deviceId == null ? "" : AppState.deviceId;
            try {
                id = java.net.URLEncoder.encode(id, "UTF-8");
            } catch (Exception ignored) {
            }
            String req = "GET /api/live/phone?id=" + id + " HTTP/1.1\r\n"
                    + "Host: " + host + ":18766\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int prev = 0;
            int prev2 = 0;
            int prev3 = 0;
            while (head.size() < 4096) {
                int b = in.read();
                if (b < 0) {
                    throw new IOException("handshake eof");
                }
                head.write(b);
                if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && b == '\n') {
                    break;
                }
                prev3 = prev2;
                prev2 = prev;
                prev = b;
            }
            String text = head.toString("UTF-8");
            if (text.indexOf("101") < 0) {
                throw new IOException("handshake " + text.replace('\r', ' ').replace('\n', ' '));
            }
        }

        void startRead(final Inbound inbound) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!closed) {
                            byte[] msg = readOne();
                            if (msg != null && inbound != null) {
                                inbound.onBinary(msg);
                            }
                        }
                    } catch (Exception e) {
                        if (!closed) {
                            requestStop();
                        }
                    }
                }
            }, "tapsprite-live-rx");
            t.setDaemon(true);
            t.start();
        }

        void sendBinary(byte[] payload) throws IOException {
            sendFrame(0x2, payload);
        }

        private void sendFrame(int opcode, byte[] payload) throws IOException {
            if (closed) {
                throw new IOException("closed");
            }
            int n = payload == null ? 0 : payload.length;
            int hdr;
            if (n < 126) {
                hdr = 2;
            } else if (n <= 65535) {
                hdr = 4;
            } else {
                hdr = 10;
            }
            byte[] header = new byte[hdr + 4];
            header[0] = (byte) (0x80 | (opcode & 0x0f));
            if (n < 126) {
                header[1] = (byte) (0x80 | n);
            } else if (n <= 65535) {
                header[1] = (byte) (0x80 | 126);
                header[2] = (byte) ((n >> 8) & 0xff);
                header[3] = (byte) (n & 0xff);
            } else {
                header[1] = (byte) (0x80 | 127);
                long ln = n;
                for (int i = 0; i < 8; i++) {
                    header[2 + i] = (byte) ((ln >>> ((7 - i) * 8)) & 0xff);
                }
            }
            byte[] mask = new byte[4];
            rnd.nextBytes(mask);
            System.arraycopy(mask, 0, header, hdr, 4);
            byte[] masked = new byte[n];
            for (int i = 0; i < n; i++) {
                masked[i] = (byte) (payload[i] ^ mask[i & 3]);
            }
            synchronized (writeLock) {
                out.write(header);
                if (n > 0) {
                    out.write(masked);
                }
                out.flush();
            }
        }

        private byte[] readOne() throws IOException {
            while (!closed) {
                int b0 = readByte();
                int b1 = readByte();
                int opcode = b0 & 0x0f;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7f;
                if (len == 126) {
                    len = ((readByte() & 0xff) << 8) | (readByte() & 0xff);
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) {
                        len = (len << 8) | (readByte() & 0xff);
                    }
                }
                if (len < 0 || len > 2 * 1024 * 1024) {
                    throw new IOException("frame len");
                }
                byte[] mask = masked ? readFully(4) : null;
                byte[] payload = readFully((int) len);
                if (mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ mask[i & 3]);
                    }
                }
                if (opcode == 0x8) {
                    throw new IOException("close");
                }
                if (opcode == 0x9) {
                    try {
                        sendFrame(0xA, payload);
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (opcode == 0xA || opcode == 0x0) {
                    continue;
                }
                return payload;
            }
            throw new IOException("closed");
        }

        private int readByte() throws IOException {
            int b = in.read();
            if (b < 0) {
                throw new IOException("eof");
            }
            return b;
        }

        private byte[] readFully(int n) throws IOException {
            byte[] b = new byte[n];
            int o = 0;
            while (o < n) {
                int r = in.read(b, o, n - o);
                if (r < 0) {
                    throw new IOException("eof");
                }
                o += r;
            }
            return b;
        }

        void close() {
            closed = true;
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
}

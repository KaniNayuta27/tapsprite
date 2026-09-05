package com.tapsprite.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

/* loaded from: classes.dex */
public class CaptureService extends Service {
    private static final String CH = "tapsprite-capture";
    private static final int NOTIF = 18;
    static volatile CaptureService instance;
    static volatile Bitmap lastSrc;
    static volatile boolean ready;
    private static volatile boolean starting;
    private VirtualDisplay display;
    private Handler handler;
    private Bitmap last;
    private final Object lock = new Object();
    private MediaProjection projection;
    private ImageReader reader;
    private boolean secureWarned;
    private HandlerThread thread;

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        instance = this;
        HandlerThread handlerThread = new HandlerThread("tapsprite-cap");
        this.thread = handlerThread;
        handlerThread.start();
        this.handler = new Handler(this.thread.getLooper());
        startAsForeground();
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        startAsForeground();
        if (intent == null || !intent.hasExtra("code") || !intent.hasExtra("data") || ((ready && this.projection != null) || starting)) {
            return 1;
        }
        starting = true;
        try {
            startProjection(intent.getIntExtra("code", 0), (Intent) intent.getParcelableExtra("data"));
        } catch (Throwable th) {
            starting = false;
            AppState.log("截屏启动失败 " + th.getMessage());
        }
        return 1;
    }

    @Override // android.app.Service
    public void onDestroy() {
        stopProjection();
        if (instance == this) {
            instance = null;
            ready = false;
        }
        HandlerThread handlerThread = this.thread;
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        super.onDestroy();
    }

    private void startAsForeground() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannel = new NotificationChannel(CH, "截屏找色", 2);
            NotificationManager notificationManager = (NotificationManager) getSystemService("notification");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
        PendingIntent activity = PendingIntent.getActivity(this, 1, new Intent(this, (Class<?>) MainActivity.class), 67108864);
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CH);
        } else {
            builder = new Notification.Builder(this);
        }
        Notification build = builder.setContentTitle("触控精灵截屏").setContentText("找色 / 取色使用中").setSmallIcon(android.R.drawable.ic_menu_camera).setContentIntent(activity).setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(18, build, 32);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(18, build, 32);
            } else {
                startForeground(18, build);
            }
        } catch (Exception e) {
            startForeground(18, build);
        }
    }

    private void startProjection(int i, Intent intent) {
        int i2;
        stopProjection();
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService("media_projection");
        if (mediaProjectionManager == null || intent == null) {
            AppState.log("截屏服务启动失败");
            return;
        }
        try {
            MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(i, intent);
            this.projection = mediaProjection;
            if (mediaProjection == null) {
                AppState.log("截屏授权无效");
                return;
            }
            mediaProjection.registerCallback(new MediaProjection.Callback() { // from class: com.tapsprite.agent.CaptureService.1
                @Override // android.media.projection.MediaProjection.Callback
                public void onStop() {
                    CaptureService.ready = false;
                    boolean unused = CaptureService.starting = false;
                    AppState.log("截屏已停止");
                    LanLink.onCaptureChanged();
                }
            }, this.handler);
            DisplayMetrics metrics = DeviceApi.metrics();
            Point realSize = DeviceApi.realSize();
            int max = Math.max(metrics.widthPixels, realSize.x);
            int max2 = Math.max(metrics.heightPixels, realSize.y);
            int[] wmPhysical = DeviceApi.wmPhysical();
            if (wmPhysical != null) {
                int i3 = wmPhysical[0];
                if (i3 > max) {
                    max = i3;
                }
                int i4 = wmPhysical[1];
                if (i4 > max2) {
                    max2 = i4;
                }
            }
            if (max == 540 && max2 >= 1170 && max2 <= 1210) {
                max2 *= 2;
                max = 1080;
            }
            int i5 = metrics.densityDpi;
            if (i5 > 0) {
                i2 = i5;
            } else {
                i2 = 480;
            }
            ImageReader newInstance = ImageReader.newInstance(max, max2, 1, 2);
            this.reader = newInstance;
            newInstance.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { // from class: com.tapsprite.agent.CaptureService.2
                @Override // android.media.ImageReader.OnImageAvailableListener
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = null;
                    try {
                        try {
                            image = imageReader.acquireLatestImage();
                        } catch (SecurityException e) {
                            if (!CaptureService.this.secureWarned) {
                                CaptureService.this.secureWarned = true;
                                AppState.log("画面受保护，无法截屏（FLAG_SECURE）");
                            }
                            if (0 == 0) {
                                return;
                            }
                        } catch (Exception e2) {
                            if (0 == 0) {
                                return;
                            }
                        }
                        if (image == null) {
                            if (image != null) {
                                image.close();
                                return;
                            }
                            return;
                        }
                        Bitmap bitmap = CaptureService.toBitmap(image);
                        if (bitmap != null) {
                            synchronized (CaptureService.this.lock) {
                                if (CaptureService.this.last != null && CaptureService.this.last != ScreenApi.frozen) {
                                    CaptureService.this.last.recycle();
                                }
                                CaptureService.this.last = bitmap;
                            }
                            CaptureService.ready = true;
                            boolean unused = CaptureService.starting = false;
                            LanLink.onCaptureChanged();
                        }
                        if (image == null) {
                            return;
                        }
                        image.close();
                    } catch (Throwable th) {
                        if (0 != 0) {
                            image.close();
                        }
                        throw th;
                    }
                }
            }, this.handler);
            try {
                this.display = this.projection.createVirtualDisplay("tapsprite", max, max2, i2, 16, this.reader.getSurface(), null, this.handler);
                AppState.log("截屏已打开 " + max + "x" + max2 + "，找色可用");
            } catch (Exception e) {
                starting = false;
                AppState.log("创建虚拟屏失败：" + e.getMessage());
            }
        } catch (Exception e2) {
            AppState.log("截屏授权无效：" + e2.getMessage());
        }
    }

    private void stopProjection() {
        ready = false;
        starting = false;
        try {
            VirtualDisplay virtualDisplay = this.display;
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
        } catch (Exception e) {
        }
        this.display = null;
        try {
            ImageReader imageReader = this.reader;
            if (imageReader != null) {
                imageReader.close();
            }
        } catch (Exception e2) {
        }
        this.reader = null;
        try {
            MediaProjection mediaProjection = this.projection;
            if (mediaProjection != null) {
                mediaProjection.stop();
            }
        } catch (Exception e3) {
        }
        this.projection = null;
    }

    Bitmap copyLatest() {
        synchronized (this.lock) {
            Bitmap bitmap = this.last;
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }
            try {
                return upscaleHalf(this.last.copy(Bitmap.Config.ARGB_8888, false));
            } catch (Exception e) {
                return null;
            }
        }
    }

    Bitmap latest() {
        Bitmap bitmap;
        synchronized (this.lock) {
            bitmap = this.last;
        }
        return bitmap;
    }

    public static byte[] screencapPng() {
        try {
            Process start = new ProcessBuilder("/system/bin/screencap", "-p").redirectErrorStream(true).start();
            InputStream inputStream = start.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[16384];
            while (true) {
                int read = inputStream.read(bArr);
                if (read < 0) {
                    break;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
            int waitFor = start.waitFor();
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            if (waitFor == 0 && byteArray.length > 200) {
                if (byteArray[0] == -119) {
                    return byteArray;
                }
            }
        } catch (Exception e) {
        }
        try {
            Process start2 = new ProcessBuilder("screencap", "-p").redirectErrorStream(true).start();
            InputStream inputStream2 = start2.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream2 = new ByteArrayOutputStream();
            byte[] bArr2 = new byte[16384];
            while (true) {
                int read2 = inputStream2.read(bArr2);
                if (read2 < 0) {
                    break;
                }
                byteArrayOutputStream2.write(bArr2, 0, read2);
            }
            start2.waitFor();
            byte[] byteArray2 = byteArrayOutputStream2.toByteArray();
            if (byteArray2.length <= 200) {
                return null;
            }
            if (byteArray2[0] == -119) {
                return byteArray2;
            }
            return null;
        } catch (Exception e2) {
            return null;
        }
    }

    /** Packed grab-to-PC shot: raw bytes (rawz preferred), never Base64. */
    public static final class PackedShot {
        public final int width;
        public final int height;
        public final String mime;
        public final byte[] data;

        PackedShot(int width, int height, String mime, byte[] data) {
            this.width = width;
            this.height = height;
            this.mime = mime;
            this.data = data;
        }
    }

    /**
     * Pack the latest MediaProjection frame for 抓抓 (phone→PC).
     * Projection only: if copyLatest is null, fail — do not fall back to screencap.
     */
    public static PackedShot packShot() {
        CaptureService captureService = instance;
        Bitmap bitmap = captureService == null ? null : captureService.copyLatest();
        if (bitmap == null) {
            AppState.log("抓抓失败：投影无帧（未回退 screencap）");
            return null;
        }
        Bitmap upscaled = upscaleHalf(bitmap);
        int width = upscaled.getWidth();
        int height = upscaled.getHeight();
        long uptimeMillis = SystemClock.uptimeMillis();
        String mime;
        byte[] packed = packLossless(upscaled);
        if (packed == null || packed.length < 32) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            upscaled.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            packed = byteArrayOutputStream.toByteArray();
            mime = "png";
        } else {
            mime = "rawz";
        }
        lastSrc = upscaled;
        AppState.log("抓抓截图 " + width + "x" + height + "  " + mime + " bin " + (packed.length / 1024) + "KB  " + (SystemClock.uptimeMillis() - uptimeMillis) + "ms mp=" + width);
        return new PackedShot(width, height, mime, packed);
    }

    static byte[] packLossless(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int i = width * height;
        int[] iArr = new int[i];
        bitmap.getPixels(iArr, 0, width, 0, 0, width, height);
        int i2 = i * 3;
        byte[] bArr = new byte[i2];
        for (int i3 = 0; i3 < i; i3++) {
            int i4 = iArr[i3];
            int i5 = i3 * 3;
            bArr[i5] = (byte) ((i4 >> 16) & 255);
            bArr[i5 + 1] = (byte) ((i4 >> 8) & 255);
            bArr[i5 + 2] = (byte) (i4 & 255);
        }
        Deflater deflater = new Deflater(1);
        deflater.setInput(bArr);
        deflater.finish();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(i2 / 3);
        byte[] bArr2 = new byte[32768];
        while (!deflater.finished()) {
            int deflate = deflater.deflate(bArr2);
            if (deflate > 0) {
                byteArrayOutputStream.write(bArr2, 0, deflate);
            }
        }
        deflater.end();
        return byteArrayOutputStream.toByteArray();
    }

    static Bitmap upscaleHalf(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width == 540 && height >= 960 && height <= 1210) {
            Bitmap createScaledBitmap = Bitmap.createScaledBitmap(bitmap, width * 2, height * 2, false);
            if (createScaledBitmap != bitmap) {
                bitmap.recycle();
            }
            return createScaledBitmap;
        }
        return bitmap;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            return null;
        }
        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int i = rowStride - (pixelStride * width);
        Bitmap createBitmap = Bitmap.createBitmap((pixelStride == 0 ? 0 : i / pixelStride) + width, height, Bitmap.Config.ARGB_8888);
        buffer.rewind();
        createBitmap.copyPixelsFromBuffer(buffer);
        if (i == 0) {
            return createBitmap;
        }
        Bitmap createBitmap2 = Bitmap.createBitmap(createBitmap, 0, 0, width, height);
        createBitmap.recycle();
        return createBitmap2;
    }
}

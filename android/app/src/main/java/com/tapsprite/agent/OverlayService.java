package com.tapsprite.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/* loaded from: classes.dex */
public class OverlayService extends Service {
    private static final String CH = "tapsprite";
    private static final int NOTIF = 17;
    private LinearLayout bubble;
    private TextView bubbleHint;
    private TextView bubbleMark;
    private WindowManager.LayoutParams bubbleParams;
    private FrameLayout modal;
    private WindowManager.LayoutParams modalParams;
    private Runnable pendingStart;
    private FrameLayout picker;
    private TextView pickerHud;
    private TextView pickerSwatch;
    private WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HashMap<String, FwWin> fws = new HashMap<>();
    private final HashMap<String, TextView> fwTexts = new HashMap<>();
    private boolean dockLeft = false;

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        AppState.overlay = this;
        this.wm = (WindowManager) getSystemService("window");
        startAsForeground();
        if (canOverlay()) {
            addBubble();
        }
        AppState.ensureServer();
        AppState.loaded = true;
        AppState.currentStep = "已加载，点击悬浮窗开始";
        AppState.log("脚本已加载。点小圆 2 秒后开始，再点取消。");
        if (AppState.keepAwake) {
            DeviceApi.keepAwake(true);
        }
        if (AppState.debugToPc) {
            AppState.log("电脑打开 exe 即可联机");
        }
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        if (intent != null && intent.getBooleanExtra("picker", false)) {
            this.main.post(new Runnable() { // from class: com.tapsprite.agent.OverlayService.1
                @Override // java.lang.Runnable
                public void run() {
                    OverlayService.this.startPicker();
                }
            });
            return 1;
        }
        return 1;
    }

    @Override // android.app.Service
    public void onDestroy() {
        cancelPendingStart();
        ScriptEngine.requestStop();
        DeviceApi.keepAwake(false);
        if (AppState.overlay == this) {
            AppState.overlay = null;
        }
        AppState.loaded = false;
        try {
            LinearLayout linearLayout = this.bubble;
            if (linearLayout != null) {
                this.wm.removeView(linearLayout);
            }
        } catch (Exception e) {
        }
        removeModal();
        stopPicker();
        fwCloseAll();
        super.onDestroy();
    }

    public void refreshBubble() {
        this.main.post(new Runnable() { // from class: com.tapsprite.agent.OverlayService.2
            @Override // java.lang.Runnable
            public void run() {
                OverlayService.this.applyBubbleState();
            }
        });
    }

    public void onStatus(String str, String str2) {
    }

    public void showPopup(final String str, final String str2) {
        this.main.post(new Runnable() { // from class: com.tapsprite.agent.OverlayService.3
            @Override // java.lang.Runnable
            public void run() {
                OverlayService.this.attachModal(str, str2);
            }
        });
    }

    boolean canOverlay() {
        return Settings.canDrawOverlays(this);
    }

    boolean canShowPrompt() {
        return canOverlay() && this.wm != null;
    }

    private void startAsForeground() {
        Notification.Builder builder;
        String str;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannel = new NotificationChannel(CH, "脚本控制", 2);
            notificationChannel.setDescription("触控精灵前台运行");
            NotificationManager notificationManager = (NotificationManager) getSystemService("notification");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
        PendingIntent activity = PendingIntent.getActivity(this, 0, new Intent(this, (Class<?>) MainActivity.class), 67108864);
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CH);
        } else {
            builder = new Notification.Builder(this);
        }
        if (AppState.debugToPc) {
            str = "电脑联机已开";
        } else {
            str = "点悬浮窗开始运行";
        }
        Notification build = builder.setContentTitle("触控精灵已加载").setContentText(str).setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(activity).setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(17, build, 1073741824);
            } else {
                startForeground(17, build);
            }
        } catch (Exception e) {
            startForeground(17, build);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int dp(int i) {
        return Math.round(TypedValue.applyDimension(1, i, getResources().getDisplayMetrics()));
    }

    private void addBubble() {
        int i;
        LinearLayout linearLayout = new LinearLayout(this);
        this.bubble = linearLayout;
        linearLayout.setOrientation(1);
        this.bubble.setGravity(1);
        this.bubbleMark = new TextView(this);
        this.bubbleMark.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(44)));
        this.bubbleMark.setGravity(17);
        this.bubbleMark.setTextColor(-16052980);
        this.bubbleMark.setTextSize(2, 11.0f);
        this.bubbleMark.setTypeface(Typeface.SANS_SERIF, 1);
        this.bubbleMark.setText("开");
        this.bubble.addView(this.bubbleMark);
        if (Build.VERSION.SDK_INT >= 26) {
            i = 2038;
        } else {
            i = 2002;
        }
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-2, -2, i, 904, -3);
        this.bubbleParams = layoutParams;
        layoutParams.gravity = 8388659;
        this.bubbleParams.x = 0;
        this.bubbleParams.y = dp(160);
        attachDrag(this.bubble);
        this.wm.addView(this.bubble, this.bubbleParams);
        this.dockLeft = false;
        snapToEdge();
        applyKeepScreenFlag();
        applyBubbleState();
    }

    void applyKeepScreenFlag() {
        if (this.bubbleParams == null || this.bubble == null || this.wm == null) {
            return;
        }
        if (AppState.keepAwake) {
            this.bubbleParams.flags |= 128;
        } else {
            this.bubbleParams.flags &= -129;
        }
        try {
            this.wm.updateViewLayout(this.bubble, this.bubbleParams);
        } catch (Exception e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void snapToEdge() {
        if (this.bubbleParams == null || this.wm == null) {
            return;
        }
        int i = getResources().getDisplayMetrics().widthPixels;
        int dp = dp(22);
        int dp2 = dp(44);
        int i2 = this.bubbleParams.x;
        TextView textView = this.bubbleMark;
        boolean z = i2 + (textView != null ? textView.getWidth() / 2 : dp) < i / 2;
        this.dockLeft = z;
        this.bubbleParams.x = z ? 0 : Math.max(0, i - dp);
        if (this.bubbleMark != null) {
            this.bubbleMark.setLayoutParams(new LinearLayout.LayoutParams(dp, dp2));
        }
        try {
            this.wm.updateViewLayout(this.bubble, this.bubbleParams);
        } catch (Exception e) {
        }
        applyBubbleState();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyBubbleState() {
        if (this.bubbleMark == null) {
            return;
        }
        boolean z = AppState.running;
        GradientDrawable gradientDrawable = new GradientDrawable();
        float dp = dp(22);
        if (this.dockLeft) {
            gradientDrawable.setCornerRadii(new float[]{0.0f, 0.0f, dp, dp, dp, dp, 0.0f, 0.0f});
        } else {
            gradientDrawable.setCornerRadii(new float[]{dp, dp, 0.0f, 0.0f, 0.0f, 0.0f, dp, dp});
        }
        if (z) {
            gradientDrawable.setColor(-1117457);
            this.bubbleMark.setText("停");
        } else if (this.pendingStart != null) {
            gradientDrawable.setColor(-3812148);
        } else {
            gradientDrawable.setColor(-3812148);
            if (this.pendingStart == null) {
                this.bubbleMark.setText("开");
            }
        }
        this.bubbleMark.setTextColor(-16052980);
        this.bubbleMark.setBackground(gradientDrawable);
    }

    private void attachDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() { // from class: com.tapsprite.agent.OverlayService.4
            long downAt;
            float downX;
            float downY;
            boolean moved;
            int startX;
            int startY;

            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View view2, MotionEvent motionEvent) {
                switch (motionEvent.getActionMasked()) {
                    case 0:
                        this.downX = motionEvent.getRawX();
                        this.downY = motionEvent.getRawY();
                        this.startX = OverlayService.this.bubbleParams.x;
                        this.startY = OverlayService.this.bubbleParams.y;
                        this.moved = false;
                        this.downAt = System.currentTimeMillis();
                        return true;
                    case 1:
                    case 3:
                        if (this.moved) {
                            OverlayService.this.snapToEdge();
                        } else {
                            OverlayService.this.onBubbleClick();
                        }
                        return true;
                    case 2:
                        float rawX = motionEvent.getRawX() - this.downX;
                        float rawY = motionEvent.getRawY() - this.downY;
                        if (Math.abs(rawX) + Math.abs(rawY) > OverlayService.this.dp(8)) {
                            this.moved = true;
                        }
                        OverlayService.this.bubbleParams.x = Math.max(0, this.startX + Math.round(rawX));
                        OverlayService.this.bubbleParams.y = Math.max(0, this.startY + Math.round(rawY));
                        try {
                            OverlayService.this.wm.updateViewLayout(OverlayService.this.bubble, OverlayService.this.bubbleParams);
                        } catch (Exception e) {
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onBubbleClick() {
        if (AppState.running || this.pendingStart != null) {
            cancelPendingStart();
            ScriptEngine.requestStop();
            AppState.log("已请求停止");
            applyBubbleState();
            return;
        }
        AppState.log("打开配置");
        showConfigModal();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void beginCountdown() {
        AppState.log("2 秒后开始");
        this.bubbleMark.setText("2");
        Runnable runnable = new Runnable() { // from class: com.tapsprite.agent.OverlayService.5
            @Override // java.lang.Runnable
            public void run() {
                if (OverlayService.this.bubbleMark != null) {
                    OverlayService.this.bubbleMark.setText("1");
                }
                OverlayService.this.pendingStart = new Runnable() { // from class: com.tapsprite.agent.OverlayService.5.1
                    @Override // java.lang.Runnable
                    public void run() {
                        OverlayService.this.pendingStart = null;
                        if (ScriptEngine.start() || OverlayService.this.bubbleMark == null || AppState.running) {
                            OverlayService.this.applyBubbleState();
                        } else {
                            OverlayService.this.bubbleMark.setText("!");
                        }
                    }
                };
                OverlayService.this.main.postDelayed(OverlayService.this.pendingStart, 1000L);
            }
        };
        this.pendingStart = runnable;
        this.main.postDelayed(runnable, 1000L);
        applyBubbleState();
    }

    private void cancelPendingStart() {
        Runnable runnable = this.pendingStart;
        if (runnable != null) {
            this.main.removeCallbacks(runnable);
            this.pendingStart = null;
        }
        if (this.bubbleMark != null && !AppState.running) {
            this.bubbleMark.setText("开");
        }
    }

    private void showConfigModal() {
        int i;
        removeModal();
        if (!canOverlay()) {
            beginCountdown();
            return;
        }
        FrameLayout frameLayout = new FrameLayout(this);
        this.modal = frameLayout;
        frameLayout.setBackgroundColor(-1727329012);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22), dp(20), dp(22), dp(18));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-15460330);
        gradientDrawable.setCornerRadius(dp(22));
        gradientDrawable.setStroke(1, 871297775);
        linearLayout.setBackground(gradientDrawable);
        TextView textView = new TextView(this);
        textView.setText("UIConfig");
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 18.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText(ConfigApi.dump());
        textView2.setTextColor(-3812148);
        textView2.setTextSize(2, 14.0f);
        textView2.setPadding(0, dp(12), 0, dp(18));
        textView2.setLineSpacing(0.0f, 1.3f);
        linearLayout.addView(textView2);
        TextView textView3 = new TextView(this);
        textView3.setText("启动");
        textView3.setGravity(17);
        textView3.setTextColor(-16052980);
        textView3.setTextSize(2, 16.0f);
        textView3.setTypeface(Typeface.SANS_SERIF, 1);
        textView3.setPadding(0, dp(12), 0, dp(12));
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(-3812148);
        gradientDrawable2.setCornerRadius(dp(12));
        textView3.setBackground(gradientDrawable2);
        textView3.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.OverlayService.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                OverlayService.this.removeModal();
                OverlayService.this.beginCountdown();
            }
        });
        linearLayout.addView(textView3);
        this.modal.addView(linearLayout, new FrameLayout.LayoutParams(dp(300), -2, 17));
        this.modal.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.OverlayService.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                OverlayService.this.removeModal();
            }
        });
        if (Build.VERSION.SDK_INT >= 26) {
            i = 2038;
        } else {
            i = 2002;
        }
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-1, -1, i, 256, -3);
        this.modalParams = layoutParams;
        layoutParams.gravity = 17;
        try {
            this.wm.addView(this.modal, this.modalParams);
        } catch (Exception e) {
            AppState.log("弹窗失败：" + e.getMessage());
            beginCountdown();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void attachModal(String str, String str2) {
        int i;
        removeModal();
        if (!canOverlay()) {
            return;
        }
        FrameLayout frameLayout = new FrameLayout(this);
        this.modal = frameLayout;
        frameLayout.setBackgroundColor(-1727329012);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22), dp(20), dp(22), dp(18));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-15460330);
        gradientDrawable.setCornerRadius(dp(22));
        gradientDrawable.setStroke(1, 871297775);
        linearLayout.setBackground(gradientDrawable);
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 18.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText(str2);
        textView2.setTextColor(-7629682);
        textView2.setTextSize(2, 15.0f);
        textView2.setPadding(0, dp(10), 0, dp(18));
        linearLayout.addView(textView2);
        TextView textView3 = new TextView(this);
        textView3.setText("确定");
        textView3.setGravity(17);
        textView3.setTextColor(-16052980);
        textView3.setTextSize(2, 15.0f);
        textView3.setTypeface(Typeface.SANS_SERIF, 1);
        textView3.setPadding(0, dp(12), 0, dp(12));
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(-3812148);
        gradientDrawable2.setCornerRadius(dp(12));
        textView3.setBackground(gradientDrawable2);
        textView3.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.OverlayService.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                OverlayService.this.removeModal();
            }
        });
        linearLayout.addView(textView3);
        this.modal.addView(linearLayout, new FrameLayout.LayoutParams(dp(280), -2, 17));
        this.modal.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.OverlayService.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                OverlayService.this.removeModal();
            }
        });
        if (Build.VERSION.SDK_INT >= 26) {
            i = 2038;
        } else {
            i = 2002;
        }
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-1, -1, i, 256, -3);
        this.modalParams = layoutParams;
        layoutParams.gravity = 17;
        try {
            this.wm.addView(this.modal, this.modalParams);
        } catch (Exception e) {
            AppState.log("弹窗失败：" + e.getMessage());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removeModal() {
        FrameLayout frameLayout = this.modal;
        if (frameLayout != null) {
            try {
                this.wm.removeView(frameLayout);
            } catch (Exception e) {
            }
            this.modal = null;
        }
    }

    void startPicker() {
        int i;
        if (!canOverlay() || this.wm == null) {
            AppState.log("取色器需要悬浮窗权限");
            return;
        }
        if (this.picker != null) {
            return;
        }
        FrameLayout frameLayout = new FrameLayout(this);
        this.picker = frameLayout;
        frameLayout.setBackgroundColor(570425344);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-267118570);
        gradientDrawable.setCornerRadius(dp(14));
        gradientDrawable.setStroke(1, 871297775);
        linearLayout.setBackground(gradientDrawable);
        this.pickerSwatch = new TextView(this);
        this.pickerSwatch.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(-7829368);
        gradientDrawable2.setCornerRadius(dp(8));
        this.pickerSwatch.setBackground(gradientDrawable2);
        linearLayout.addView(this.pickerSwatch);
        TextView textView = new TextView(this);
        this.pickerHud = textView;
        textView.setText("点屏幕取色 · 再点关闭");
        this.pickerHud.setTextColor(-1117457);
        this.pickerHud.setTextSize(2, 13.0f);
        this.pickerHud.setPadding(dp(10), 0, dp(10), 0);
        linearLayout.addView(this.pickerHud);
        TextView textView2 = new TextView(this);
        textView2.setText("关闭");
        textView2.setTextColor(-16052980);
        textView2.setTextSize(2, 13.0f);
        textView2.setTypeface(Typeface.SANS_SERIF, 1);
        textView2.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable gradientDrawable3 = new GradientDrawable();
        gradientDrawable3.setColor(-3812148);
        gradientDrawable3.setCornerRadius(dp(10));
        textView2.setBackground(gradientDrawable3);
        textView2.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.OverlayService.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                OverlayService.this.stopPicker();
            }
        });
        linearLayout.addView(textView2);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-2, -2, 49);
        layoutParams.topMargin = dp(48);
        this.picker.addView(linearLayout, layoutParams);
        this.picker.setOnTouchListener(new View.OnTouchListener() { // from class: com.tapsprite.agent.OverlayService.11
            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == 1 || motionEvent.getActionMasked() == 0) {
                    OverlayService.this.sample(Math.round(motionEvent.getRawX()), Math.round(motionEvent.getRawY()));
                }
                return true;
            }
        });
        if (Build.VERSION.SDK_INT >= 26) {
            i = 2038;
        } else {
            i = 2002;
        }
        try {
            this.wm.addView(this.picker, new WindowManager.LayoutParams(-1, -1, i, 768, -3));
            AppState.log("取色器已打开，点屏幕读取颜色");
        } catch (Exception e) {
            this.picker = null;
            AppState.log("取色器失败：" + e.getMessage());
        }
    }

    void stopPicker() {
        FrameLayout frameLayout = this.picker;
        if (frameLayout != null) {
            try {
                this.wm.removeView(frameLayout);
            } catch (Exception e) {
            }
            this.picker = null;
            this.pickerHud = null;
            this.pickerSwatch = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sample(final int i, final int i2) {
        new Thread(new Runnable() { // from class: com.tapsprite.agent.OverlayService.12
            @Override // java.lang.Runnable
            public void run() {
                final String pixelColor = ScreenApi.getPixelColor(i, i2);
                OverlayService.this.main.post(new Runnable() { // from class: com.tapsprite.agent.OverlayService.12.1
                    @Override // java.lang.Runnable
                    public void run() {
                        if (OverlayService.this.pickerHud != null) {
                            OverlayService.this.pickerHud.setText(i + ", " + i2 + "  #" + pixelColor);
                        }
                        if (OverlayService.this.pickerSwatch != null) {
                            GradientDrawable gradientDrawable = new GradientDrawable();
                            gradientDrawable.setColor(ColorUtil.parseHex(pixelColor) | (-16777216));
                            gradientDrawable.setCornerRadius(OverlayService.this.dp(8));
                            OverlayService.this.pickerSwatch.setBackground(gradientDrawable);
                        }
                    }
                });
                AppState.log("取色 (" + i + "," + i2 + ") = " + pixelColor);
            }
        }, "tapsprite-pick").start();
    }

    /**
     * Blocking prompt on the script thread.
     * @return typed text; "" if cancelled; null if overlay cannot be shown
     */
    String prompt(final String str, final String str2) {
        if (!canShowPrompt()) {
            return null;
        }
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicReference<String> atomicReference = new AtomicReference<>(null);
        final AtomicBoolean shown = new AtomicBoolean(false);
        this.main.post(new Runnable() { // from class: com.tapsprite.agent.OverlayService.13
            @Override // java.lang.Runnable
            public void run() {
                OverlayService.this.showPrompt(str, str2, atomicReference, countDownLatch, shown);
            }
        });
        try {
            while (!countDownLatch.await(200L, TimeUnit.MILLISECONDS)) {
                if (ScriptEngine.isStopRequested()) {
                    this.main.post(new Runnable() {
                        @Override
                        public void run() {
                            OverlayService.this.removeModal();
                            countDownLatch.countDown();
                        }
                    });
                    return "";
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    OverlayService.this.removeModal();
                    countDownLatch.countDown();
                }
            });
            return "";
        }
        if (!shown.get()) {
            return null;
        }
        String r = atomicReference.get();
        return r != null ? r : "";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPrompt(String str, String str2, final AtomicReference<String> atomicReference, final CountDownLatch countDownLatch, final AtomicBoolean shown) {
        int i;
        removeModal();
        if (!canShowPrompt()) {
            countDownLatch.countDown();
            return;
        }
        FrameLayout frameLayout = new FrameLayout(this);
        this.modal = frameLayout;
        frameLayout.setBackgroundColor(-1727329012);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22), dp(20), dp(22), dp(18));
        linearLayout.setClickable(true);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-15460330);
        gradientDrawable.setCornerRadius(dp(22));
        gradientDrawable.setStroke(1, 871297775);
        linearLayout.setBackground(gradientDrawable);
        TextView textView = new TextView(this);
        if (str == null) {
            str = "输入";
        }
        textView.setText(str);
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 17.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        linearLayout.addView(textView);
        final EditText editText = new EditText(this);
        if (str2 == null) {
            str2 = "";
        }
        editText.setText(str2);
        editText.setTextColor(-3812148);
        editText.setHintTextColor(-7629682);
        editText.setSingleLine(true);
        editText.setImeOptions(6);
        editText.setPadding(dp(10), dp(12), dp(10), dp(12));
        linearLayout.addView(editText);
        TextView textView2 = new TextView(this);
        textView2.setText("确定");
        textView2.setGravity(17);
        textView2.setTextColor(-16052980);
        textView2.setTypeface(Typeface.SANS_SERIF, 1);
        textView2.setPadding(0, dp(12), 0, dp(12));
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(-3812148);
        gradientDrawable2.setCornerRadius(dp(12));
        textView2.setBackground(gradientDrawable2);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(12);
        textView2.setLayoutParams(layoutParams);
        textView2.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.OverlayService.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                atomicReference.set(editText.getText().toString());
                OverlayService.this.removeModal();
                countDownLatch.countDown();
            }
        });
        linearLayout.addView(textView2);
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setGravity(17);
        cancel.setTextColor(-3812148);
        cancel.setPadding(0, dp(10), 0, dp(4));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, -2);
        cancelLp.topMargin = dp(4);
        cancel.setLayoutParams(cancelLp);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                atomicReference.set("");
                OverlayService.this.removeModal();
                countDownLatch.countDown();
            }
        });
        linearLayout.addView(cancel);
        this.modal.addView(linearLayout, new FrameLayout.LayoutParams(dp(280), -2, 17));
        this.modal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                atomicReference.set("");
                OverlayService.this.removeModal();
                countDownLatch.countDown();
            }
        });
        if (Build.VERSION.SDK_INT >= 26) {
            i = 2038;
        } else {
            i = 2002;
        }
        WindowManager.LayoutParams layoutParams2 = new WindowManager.LayoutParams(-1, -1, i, 256, -3);
        this.modalParams = layoutParams2;
        try {
            this.wm.addView(this.modal, layoutParams2);
            shown.set(true);
            editText.requestFocus();
        } catch (Exception e) {
            AppState.log("InputBox 悬浮窗失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
            this.modal = null;
            countDownLatch.countDown();
        }
    }

    boolean hasFw(String str) {
        return this.fws.containsKey(str);
    }

    void fwNew(String str, int i, int i2, int i3, int i4) {
        int i5;
        if (!canOverlay() || this.wm == null || str == null) {
            return;
        }
        fwClose(str);
        FwWin fwWin = new FwWin();
        fwWin.name = str;
        fwWin.root = new FrameLayout(this);
        fwWin.root.setBackgroundColor(0);
        if (Build.VERSION.SDK_INT >= 26) {
            i5 = 2038;
        } else {
            i5 = 2002;
        }
        fwWin.lp = new WindowManager.LayoutParams(Math.max(1, i3), Math.max(1, i4), i5, 792, -3);
        fwWin.lp.gravity = 8388659;
        fwWin.lp.x = i;
        fwWin.lp.y = i2;
        try {
            this.wm.addView(fwWin.root, fwWin.lp);
            this.fws.put(str, fwWin);
        } catch (Exception e) {
            AppState.log("FW 创建失败：" + e.getMessage());
        }
    }

    void fwBackColor(String str, String str2) {
        FwWin fwWin = this.fws.get(str);
        if (fwWin == null) {
            return;
        }
        int parseHex = ColorUtil.parseHex(str2);
        int i = fwWin.bgAlpha;
        fwWin.bgColor = parseHex;
        fwWin.root.setBackgroundColor((parseHex & 16777215) | (i << 24));
    }

    void fwOpacity(String str, int i) {
        int min;
        FwWin fwWin = this.fws.get(str);
        if (fwWin == null) {
            return;
        }
        if (i <= 0) {
            min = 0;
        } else if (i <= 100) {
            min = Math.round(i * 2.55f);
        } else {
            min = Math.min(255, i);
        }
        fwWin.bgAlpha = min;
        fwWin.root.setBackgroundColor((fwWin.bgColor & 16777215) | (min << 24));
    }

    void fwAddText(String str, String str2, String str3, int i, int i2, int i3, int i4) {
        FwWin fwWin = this.fws.get(str);
        if (fwWin == null) {
            return;
        }
        TextView textView = new TextView(this);
        textView.setText(str3 == null ? "" : str3.replace("\\n", "\n"));
        textView.setTextColor(-1);
        textView.setTextSize(2, 12.0f);
        if (i3 <= 0) {
            i3 = -1;
        }
        if (i4 <= 0) {
            i4 = -2;
        }
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(i3, i4);
        layoutParams.leftMargin = Math.max(0, i);
        layoutParams.topMargin = Math.max(0, i2);
        fwWin.root.addView(textView, layoutParams);
        this.fwTexts.put(str2, textView);
    }

    void fwTextColor(String str, String str2) {
        TextView textView = this.fwTexts.get(str);
        if (textView != null) {
            textView.setTextColor(ColorUtil.parseHex(str2) | (-16777216));
        }
    }

    void fwTextSize(String str, int i) {
        TextView textView = this.fwTexts.get(str);
        if (textView != null) {
            textView.setTextSize(2, Math.max(8, i));
        }
    }

    void fwSetText(String str, String str2) {
        TextView textView = this.fwTexts.get(str);
        if (textView != null) {
            textView.setText(str2 == null ? "" : str2.replace("\\n", "\n"));
        }
    }

    void fwShow(String str, boolean z) {
        FwWin fwWin = this.fws.get(str);
        if (fwWin != null) {
            fwWin.root.setVisibility(z ? 0 : 8);
        }
    }

    void fwClose(String str) {
        FwWin remove = this.fws.remove(str);
        if (remove == null) {
            return;
        }
        try {
            this.wm.removeView(remove.root);
        } catch (Exception e) {
        }
    }

    void fwCloseAll() {
        for (String str : (String[]) this.fws.keySet().toArray(new String[0])) {
            fwClose(str);
        }
        this.fwTexts.clear();
    }

    private static final class FwWin {
        int bgAlpha;
        int bgColor;
        WindowManager.LayoutParams lp;
        String name;
        FrameLayout root;

        private FwWin() {
            this.bgColor = 0;
            this.bgAlpha = 0;
        }
    }
}

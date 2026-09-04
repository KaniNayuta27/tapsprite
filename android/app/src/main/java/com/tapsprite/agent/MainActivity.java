package com.tapsprite.agent;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.tapsprite.agent.AppState;
import java.util.List;

/* loaded from: classes.dex */
public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 91;
    private static MainActivity live;
    static volatile boolean pendingCapturePrompt;
    static volatile boolean pendingShotAfterCapture;
    private TextView a11yStatus;
    private Switch awakeSwitch;
    private boolean captureAsking;
    private TextView captureStatus;
    private TextView cloudLine;
    private TextView copyAdbBtn;
    private TextView copyPcBtn;
    private Switch debugSwitch;
    CompoundButton.OnCheckedChangeListener debugListener;
    private TextView ipGo;
    private TextView lanLine;
    private TextView loadBtn;
    private TextView logBox;
    private TextView openLocalBtn;
    private TextView overlayStatus;
    private View pageHome;
    private View pageScripts;
    private TextView pcBox;
    private EditText pcIpEdit;
    private TextView phoneIpLine;
    private LinearLayout scriptListWrap;
    private TextView statusKicker;
    private TextView tabBasic;
    private TextView tabHome;
    private TextView tabPet;
    private TextView tabScripts;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable tickRun = new Runnable() { // from class: com.tapsprite.agent.MainActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (!MainActivity.this.isFinishing()) {
                MainActivity.this.refresh();
                MainActivity.this.tick.postDelayed(this, 800L);
            }
        }
    };

    static void ping() {
        final MainActivity mainActivity = live;
        if (mainActivity != null) {
            mainActivity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.MainActivity.2
                @Override // java.lang.Runnable
                public void run() {
                    mainActivity.refresh();
                    mainActivity.rebuildScriptList();
                }
            });
        }
    }

    /** Bring App to front and pop the system MediaProjection dialog (抓抓 / LAN shot). */
    static void askCaptureForShot() {
        pendingShotAfterCapture = true;
        pendingCapturePrompt = true;
        final android.content.Context ctx = App.ctx;
        if (ctx == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(ctx, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    i.putExtra("request_capture", true);
                    ctx.startActivity(i);
                } catch (Exception e) {
                    AppState.log("无法弹出截屏授权：" + e.getMessage());
                }
            }
        });
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(-16052980);
        getWindow().setNavigationBarColor(-16052980);
        setContentView(buildUi());
        live = this;
    }

    @Override // android.app.Activity
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeCapturePrompt();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        live = this;
        refresh();
        consumeCapturePrompt();
        if (AppState.debugToPc) {
            AppState.ensureServer();
        }
        this.tick.removeCallbacks(this.tickRun);
        this.tick.postDelayed(this.tickRun, 400L);
    }

    void consumeCapturePrompt() {
        Intent it = getIntent();
        boolean extra = it != null && it.getBooleanExtra("request_capture", false);
        if (extra) {
            it.removeExtra("request_capture");
        }
        if (pendingCapturePrompt || extra) {
            pendingCapturePrompt = false;
            if (!CaptureService.ready) {
                requestCapture();
            }
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        this.tick.removeCallbacks(this.tickRun);
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        if (live == this) {
            live = null;
        }
        super.onDestroy();
    }


    static boolean isLikelyIPv4(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 7 || t.length() > 15) return false;
        String[] p = t.split("\\.");
        if (p.length != 4) return false;
        try {
            for (String x : p) {
                int n = Integer.parseInt(x);
                if (n < 0 || n > 255) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int dp(int i) {
        return Math.round(TypedValue.applyDimension(1, i, getResources().getDisplayMetrics()));
    }

    private View buildUi() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(-16052980);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.pageHome = buildHome();
        View buildScriptList = buildScriptList();
        this.pageScripts = buildScriptList;
        buildScriptList.setVisibility(8);
        frameLayout.addView(this.pageHome);
        frameLayout.addView(this.pageScripts);
        linearLayout.addView(frameLayout);
        linearLayout.addView(buildTabs());
        return linearLayout;
    }

    private View buildTabs() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(17);
        linearLayout.setPadding(0, dp(8), 0, dp(10));
        new GradientDrawable().setColor(-15460330);
        linearLayout.setBackgroundColor(-16052980);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        this.tabHome = tabItem("首页");
        this.tabScripts = tabItem("脚本");
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        this.tabHome.setLayoutParams(layoutParams);
        this.tabScripts.setLayoutParams(layoutParams);
        this.tabHome.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.showPage(0);
            }
        });
        this.tabScripts.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.showPage(1);
            }
        });
        linearLayout.addView(this.tabHome);
        linearLayout.addView(this.tabScripts);
        showPage(0);
        return linearLayout;
    }

    private TextView tabItem(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextSize(2, 13.0f);
        textView.setPadding(0, dp(8), 0, dp(8));
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        return textView;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPage(int i) {
        this.pageHome.setVisibility(i == 0 ? 0 : 8);
        this.pageScripts.setVisibility(i != 1 ? 8 : 0);
        TextView textView = this.tabHome;
        if (textView != null) {
            textView.setTextColor(i == 0 ? -1117457 : -7629682);
            this.tabScripts.setTextColor(i != 1 ? -7629682 : -1117457);
        }
        if (i == 1) {
            rebuildScriptList();
        }
    }

    private View buildHome() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(-16052980);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22), dp(28), dp(22), dp(24));
        TextView textView = new TextView(this);
        textView.setText("TAPSPRITE  ·  " + Updater.currentName());
        textView.setTextColor(-7629682);
        textView.setTextSize(2, 11.0f);
        textView.setLetterSpacing(0.12f);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textView2 = new TextView(this);
        textView2.setText("更新  ›");
        textView2.setTextColor(-3812148);
        textView2.setTextSize(2, 13.0f);
        textView2.setPadding(dp(8), dp(6), 0, dp(6));
        textView2.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) UpdateActivity.class));
            }
        });
        linearLayout2.addView(textView);
        linearLayout2.addView(textView2);
        linearLayout.addView(linearLayout2);
        TextView textView3 = new TextView(this);
        textView3.setText("触控精灵");
        textView3.setTextColor(-1117457);
        textView3.setTextSize(2, 34.0f);
        textView3.setTypeface(Typeface.SANS_SERIF, 1);
        textView3.setPadding(0, dp(6), 0, 0);
        linearLayout.addView(textView3);
        TextView textView4 = new TextView(this);
        this.statusKicker = textView4;
        textView4.setText("加载脚本");
        this.statusKicker.setTextColor(-3812148);
        this.statusKicker.setTextSize(2, 16.0f);
        this.statusKicker.setPadding(0, dp(6), 0, dp(18));
        linearLayout.addView(this.statusKicker);
        linearLayout.addView(cardPermissions());
        linearLayout.addView(space(14));
        linearLayout.addView(cardDebug());
        linearLayout.addView(space(18));
        TextView ghostButton = ghostButton("结束并关闭悬浮窗");
        ghostButton.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.stopService(new Intent(MainActivity.this, (Class<?>) OverlayService.class));
                ScriptEngine.requestStop();
                AppState.loaded = false;
                AppState.currentStep = "待命";
                MainActivity.this.refresh();
            }
        });
        linearLayout.addView(ghostButton);
        linearLayout.addView(space(16));
        LinearLayout card = card();
        card.addView(sectionTitle("电脑下发"));
        TextView textView5 = new TextView(this);
        this.pcBox = textView5;
        textView5.setTextColor(-3812148);
        this.pcBox.setTextSize(2, 12.0f);
        this.pcBox.setTypeface(Typeface.MONOSPACE, 0);
        this.pcBox.setPadding(0, dp(10), dp(8), dp(8));
        this.pcBox.setTextIsSelectable(true);
        this.pcBox.setHorizontallyScrolling(true);
        this.pcBox.setSingleLine(false);
        this.pcBox.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(this);
        horizontalScrollView.setHorizontalScrollBarEnabled(true);
        horizontalScrollView.setScrollbarFadingEnabled(false);
        horizontalScrollView.setFillViewport(false);
        horizontalScrollView.setOverScrollMode(1);
        horizontalScrollView.addView(this.pcBox);
        card.addView(horizontalScrollView);
        linearLayout.addView(card);
        scrollView.addView(linearLayout);
        return scrollView;
    }

    private View buildScriptList() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(-16052980);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22), dp(28), dp(22), dp(24));
        TextView textView = new TextView(this);
        textView.setText("脚本");
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 28.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText("点一项进入配置和加载");
        textView2.setTextColor(-7629682);
        textView2.setTextSize(2, 13.0f);
        textView2.setPadding(0, dp(6), 0, dp(16));
        linearLayout.addView(textView2);
        LinearLayout linearLayout2 = new LinearLayout(this);
        this.scriptListWrap = linearLayout2;
        linearLayout2.setOrientation(1);
        linearLayout.addView(this.scriptListWrap);
        scrollView.addView(linearLayout);
        return scrollView;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void rebuildScriptList() {
        LinearLayout linearLayout = this.scriptListWrap;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        addScriptRow("宠物脚本", "1080×2400 · 上课/冒险/打工", 0);
        addScriptRow("基础测试", "Home / Tap / Back", 1);
        String str = AppState.pcScript == null ? "" : AppState.pcScript;
        addScriptRow("电脑下发", str.length() == 0 ? "还没有收到" : str.length() + " 字", 2);
    }

    private void addScriptRow(String str, String str2, final int i) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-15460330);
        gradientDrawable.setCornerRadius(dp(16));
        gradientDrawable.setStroke(1, 586085103);
        linearLayout.setBackground(gradientDrawable);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(10);
        linearLayout.setLayoutParams(layoutParams);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 16.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        TextView textView2 = new TextView(this);
        textView2.setText(str2);
        textView2.setTextColor(-7629682);
        textView2.setTextSize(2, 12.0f);
        textView2.setPadding(0, dp(4), 0, 0);
        linearLayout2.addView(textView);
        linearLayout2.addView(textView2);
        TextView textView3 = new TextView(this);
        textView3.setText("›");
        textView3.setTextColor(-3812148);
        textView3.setTextSize(2, 22.0f);
        linearLayout.addView(linearLayout2);
        linearLayout.addView(textView3);
        linearLayout.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                AppState.selectTab(i);
                MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) ScriptActivity.class));
            }
        });
        this.scriptListWrap.addView(linearLayout);
    }

    private View cardPermissions() {
        LinearLayout card = card();
        card.addView(sectionTitle("权限"));
        this.overlayStatus = permRow(card, "悬浮窗", new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.requestOverlay();
            }
        });
        this.a11yStatus = permRow(card, "无障碍 · 触控精灵", new AnonymousClass9());
        this.captureStatus = permRow(card, "截屏（电脑抓抓需要）", new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.requestCapture();
            }
        });
        return card;
    }

    /* renamed from: com.tapsprite.agent.MainActivity$9, reason: invalid class name */
    class AnonymousClass9 implements View.OnClickListener {
        AnonymousClass9() {
        }

        @Override // android.view.View.OnClickListener
        public void onClick(View view) {
            new Thread(new Runnable() { // from class: com.tapsprite.agent.MainActivity.9.1
                @Override // java.lang.Runnable
                public void run() {
                    final boolean reconnect = AutoService.reconnect();
                    MainActivity.this.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.MainActivity.9.1.1
                        @Override // java.lang.Runnable
                        public void run() {
                            if (reconnect) {
                                Toast.makeText(MainActivity.this, "无障碍已连上", 0).show();
                                MainActivity.this.refresh();
                            } else {
                                MainActivity.this.startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
                                Toast.makeText(MainActivity.this, "自动重连失败，请关掉再打开「触控精灵」", 1).show();
                            }
                        }
                    });
                }
            }, "a11y-re").start();
        }
    }

    private View cardDebug() {
        LinearLayout card = card();
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        linearLayout2.setLayoutParams(layoutParams);
        TextView textView = new TextView(this);
        textView.setText("电脑联机");
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 15.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        this.phoneIpLine = textView2;
        textView2.setTextColor(-7629682);
        this.phoneIpLine.setTextSize(2, 12.0f);
        this.phoneIpLine.setTypeface(Typeface.MONOSPACE, 0);
        this.phoneIpLine.setPadding(dp(12), 0, dp(8), 0);
        String wifiIPv4 = NetInfo.wifiIPv4();
        this.phoneIpLine.setText(wifiIPv4.length() > 0 ? "ip: " + wifiIPv4 : "ip: —");
        linearLayout.addView(this.phoneIpLine);
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1.0f));
        linearLayout.addView(view);
        Switch r5 = new Switch(this);
        this.debugSwitch = r5;
        r5.setChecked(AppState.debugToPc);
        this.debugSwitch.setShowText(false);
        this.debugListener = new CompoundButton.OnCheckedChangeListener() { // from class: com.tapsprite.agent.MainActivity.11
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                AppState.debugToPc = z;
                try {
                    MainActivity.this.getSharedPreferences("tapsprite", 0).edit().putBoolean("debugToPc", z).apply();
                } catch (Exception e) {
                }
                if (z) {
                    AppState.ensureServer();
                    AppState.log("已上线");
                } else {
                    AppState.stopServer();
                }
                MainActivity.this.refresh();
            }
        };
        this.debugSwitch.setOnCheckedChangeListener(this.debugListener);
        linearLayout.addView(this.debugSwitch);
        card.addView(linearLayout);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(0);
        linearLayout3.setGravity(16);
        linearLayout3.setPadding(0, dp(12), 0, 0);
        EditText editText = new EditText(this);
        this.pcIpEdit = editText;
        editText.setHint("电脑 IP");
        this.pcIpEdit.setText(LanLink.manualHost());
        this.pcIpEdit.setTextColor(-1117457);
        this.pcIpEdit.setHintTextColor(-9735058);
        this.pcIpEdit.setTextSize(2, 14.0f);
        this.pcIpEdit.setSingleLine(true);
        this.pcIpEdit.setBackgroundColor(-16052980);
        this.pcIpEdit.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams2.rightMargin = dp(14);
        this.pcIpEdit.setLayoutParams(layoutParams2);
        linearLayout3.addView(this.pcIpEdit);
        this.ipGo = permChip("连接");
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams3.leftMargin = dp(8);
        this.ipGo.setLayoutParams(layoutParams3);
        this.ipGo.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                String trim = MainActivity.this.pcIpEdit.getText() == null ? "" : MainActivity.this.pcIpEdit.getText().toString().trim();
                if (!isLikelyIPv4(trim)) {
                    Toast.makeText(MainActivity.this, "填电脑 Wi-Fi 的 IPv4", 0).show();
                    return;
                }
                // Ensure online: switch may be off — setManualHost used to skip hello() then.
                if (!AppState.debugToPc) {
                    AppState.debugToPc = true;
                    try {
                        MainActivity.this.getSharedPreferences("tapsprite", 0).edit().putBoolean("debugToPc", true).apply();
                    } catch (Exception e) {
                    }
                    if (MainActivity.this.debugSwitch != null) {
                        MainActivity.this.debugSwitch.setOnCheckedChangeListener(null);
                        MainActivity.this.debugSwitch.setChecked(true);
                        MainActivity.this.debugSwitch.setOnCheckedChangeListener(MainActivity.this.debugListener);
                    }
                }
                AppState.ensureServer();
                Toast.makeText(MainActivity.this, "正在连 " + trim, 0).show();
                AppState.log("正在连 " + trim);
                LanLink.connectManual(trim, new LanLink.ConnectCallback() {
                    @Override
                    public void onResult(final boolean ok, final String host) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (ok) {
                                    Toast.makeText(MainActivity.this, "已连上电脑 " + host, 1).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "连不上 " + host + "。检查 exe/同 WiFi/防火墙", 1).show();
                                }
                                MainActivity.this.refresh();
                            }
                        });
                    }
                });
            }
        });
        linearLayout3.addView(this.ipGo);
        card.addView(linearLayout3);
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(0);
        linearLayout4.setGravity(16);
        linearLayout4.setPadding(0, dp(14), 0, 0);
        LinearLayout linearLayout5 = new LinearLayout(this);
        linearLayout5.setOrientation(1);
        linearLayout5.setLayoutParams(layoutParams);
        TextView textView3 = new TextView(this);
        textView3.setText("屏幕常亮");
        textView3.setTextColor(-1117457);
        textView3.setTextSize(2, 15.0f);
        textView3.setTypeface(Typeface.SANS_SERIF, 1);
        linearLayout5.addView(textView3);
        linearLayout4.addView(linearLayout5);
        Switch r4 = new Switch(this);
        this.awakeSwitch = r4;
        r4.setChecked(AppState.keepAwake);
        this.awakeSwitch.setShowText(false);
        this.awakeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.tapsprite.agent.MainActivity.13
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                DeviceApi.keepAwake(z);
                OverlayService overlayService = AppState.overlay;
                if (overlayService != null) {
                    overlayService.applyKeepScreenFlag();
                }
            }
        });
        linearLayout4.addView(this.awakeSwitch);
        card.addView(linearLayout4);
        TextView textView4 = new TextView(this);
        this.lanLine = textView4;
        textView4.setVisibility(8);
        TextView textView5 = new TextView(this);
        this.cloudLine = textView5;
        textView5.setVisibility(8);
        TextView ghostButton = ghostButton("检测连接");
        this.copyPcBtn = ghostButton;
        ghostButton.setVisibility(8);
        return card;
    }

    private void refreshDebug() {
        TextView textView = this.cloudLine;
        if (textView != null) {
            textView.setVisibility(8);
        }
        if (this.logBox != null) {
            List<AppState.LogLine> logsAfter = AppState.logsAfter(0);
            StringBuilder sb = new StringBuilder();
            for (int max = Math.max(0, logsAfter.size() - 80); max < logsAfter.size(); max++) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(logsAfter.get(max).msg);
            }
            if (sb.length() == 0) {
                sb.append("还没有日志。点检测连接。");
            }
            this.logBox.setText(sb.toString());
        }
    }

    private void copyText(String str, String str2) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("tapsprite", str));
        }
        Toast.makeText(this, str2, 0).show();
    }

    private View cardScript() {
        LinearLayout card = card();
        card.addView(sectionTitle("内置脚本"));
        card.addView(muted("点选哪个，加载后悬浮窗跑哪个。电脑下发会覆盖当前内容。宠物脚本按 1080×2400 480dpi。"));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setPadding(0, dp(12), 0, 0);
        this.tabPet = tabChip("宠物 1080×2400");
        this.tabBasic = tabChip("基础测试");
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.rightMargin = dp(8);
        this.tabPet.setLayoutParams(layoutParams);
        this.tabBasic.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        this.tabPet.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                AppState.selectTab(0);
                MainActivity.this.paintTabs();
            }
        });
        this.tabBasic.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.15
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                AppState.selectTab(1);
                MainActivity.this.paintTabs();
            }
        });
        linearLayout.addView(this.tabPet);
        linearLayout.addView(this.tabBasic);
        card.addView(linearLayout);
        paintTabs();
        return card;
    }

    private TextView tabChip(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextSize(2, 14.0f);
        textView.setPadding(dp(8), dp(10), dp(8), dp(10));
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        return textView;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void paintTabs() {
        styleTab(this.tabPet, AppState.scriptTab == 0);
        styleTab(this.tabBasic, AppState.scriptTab == 1);
    }

    private void styleTab(TextView textView, boolean z) {
        if (textView == null) {
            return;
        }
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(dp(12));
        if (z) {
            gradientDrawable.setColor(-3812148);
            textView.setTextColor(-16052980);
        } else {
            gradientDrawable.setColor(-15460330);
            gradientDrawable.setStroke(1, 871297775);
            textView.setTextColor(-3812148);
        }
        textView.setBackground(gradientDrawable);
    }

    private View cardMode() {
        LinearLayout card = card();
        card.addView(sectionTitle("宠物模式（ReadUIConfig）"));
        card.addView(muted("互斥。对应原来的多选框。洗澡/小号数量在选中对应模式后点下面的附加项。"));
        final String[] strArr = {"上课/打工/冒险", "雇佣+投喂", "小号循环", "PK", "只洗澡", "给大号投喂"};
        final String[] strArr2 = {"多选框1", "多选框2", "多选框3", "多选框6", "多选框5", "多选框7"};
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(0, dp(8), 0, 0);
        for (int _i = 0; _i < 6; _i++) {
            final int i = _i;
            TextView ghostButton = ghostButton(strArr[i]);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.topMargin = dp(6);
            ghostButton.setLayoutParams(layoutParams);
            ghostButton.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.16
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    int i2 = 0;
                    while (true) {
                        String[] strArr3 = strArr2;
                        if (i2 < strArr3.length) {
                            ConfigApi.writeBool(strArr3[i2], i2 == i);
                            i2++;
                        } else {
                            Toast.makeText(MainActivity.this, "模式 → " + strArr[i], 0).show();
                            return;
                        }
                    }
                }
            });
            linearLayout.addView(ghostButton);
        }
        card.addView(linearLayout);
        TextView ghostButton2 = ghostButton("切换：雇佣洗澡 / 大号洗澡 / 小号+1");
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = dp(10);
        ghostButton2.setLayoutParams(layoutParams2);
        ghostButton2.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.MainActivity.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                boolean z = !ConfigApi.readBool("多选框8", false);
                ConfigApi.writeBool("多选框8", z);
                boolean z2 = !ConfigApi.readBool("多选框9", false);
                ConfigApi.writeBool("多选框9", z2);
                int readInt = ConfigApi.readInt("下拉框3", 7) + 1;
                int i2 = readInt <= 10 ? readInt : 0;
                ConfigApi.writeInt("下拉框3", i2);
                Toast.makeText(MainActivity.this, "洗澡8=" + z + " 洗澡9=" + z2 + " 小号数量下拉=" + i2, 1).show();
            }
        });
        card.addView(ghostButton2);
        return card;
    }

    private TextView permRow(LinearLayout linearLayout, String str, View.OnClickListener onClickListener) {
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(0, dp(12), 0, 0);
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 14.0f);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView permChip = permChip("去开启");
        linearLayout2.setOnClickListener(onClickListener);
        linearLayout2.addView(textView);
        linearLayout2.addView(permChip);
        linearLayout.addView(linearLayout2);
        return permChip;
    }

    private TextView permChip(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextColor(-16052980);
        textView.setTextSize(2, 12.0f);
        textView.setPadding(dp(10), dp(6), dp(10), dp(6));
        textView.setMinWidth(dp(72));
        textView.setSingleLine(true);
        textView.setClickable(false);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-3812148);
        gradientDrawable.setCornerRadius(dp(999));
        textView.setBackground(gradientDrawable);
        return textView;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refresh() {
        boolean hasOverlay = hasOverlay();
        boolean z = AppState.auto != null;
        setPerm(this.overlayStatus, hasOverlay);
        setPerm(this.a11yStatus, z);
        if (this.a11yStatus != null && isA11yEnabled() && AppState.auto == null) {
            this.a11yStatus.setText("需重开");
        }
        setPerm(this.captureStatus, CaptureService.ready);
        if (this.phoneIpLine != null) {
            String wifiIPv4 = NetInfo.wifiIPv4();
            this.phoneIpLine.setText(wifiIPv4.length() > 0 ? "ip: " + wifiIPv4 : "ip: —");
        }
        if (this.pcIpEdit != null && LanLink.ok() && !this.pcIpEdit.hasFocus()) {
            String pcAddr = LanLink.pcAddr();
            if (pcAddr.length() > 6) {
                this.pcIpEdit.setText(pcAddr);
            }
        }
        if (AppState.debugToPc) {
            this.lanLine.setVisibility(8);
            TextView textView = this.copyPcBtn;
            if (textView != null) {
                textView.setVisibility(8);
            }
            refreshDebug();
        } else {
            this.lanLine.setVisibility(8);
            TextView textView2 = this.cloudLine;
            if (textView2 != null) {
                textView2.setVisibility(8);
            }
            TextView textView3 = this.copyPcBtn;
            if (textView3 != null) {
                textView3.setVisibility(8);
            }
            TextView textView4 = this.copyAdbBtn;
            if (textView4 != null) {
                textView4.setVisibility(8);
            }
            TextView textView5 = this.openLocalBtn;
            if (textView5 != null) {
                textView5.setVisibility(8);
            }
        }
        TextView textView6 = this.statusKicker;
        if (textView6 != null) {
            textView6.setText(AppState.loaded ? "悬浮窗已在后台" : LanLink.ok() ? "局域网已连电脑" : "打开即可联机");
        }
        if (this.pcBox != null) {
            String trim = AppState.pcScript == null ? "" : AppState.pcScript.trim();
            if (trim.length() == 0) {
                this.pcBox.setText("还没有。电脑点「发送到手机」后显示在这里。");
            } else {
                this.pcBox.setText(AppState.withLineNumbers(trim));
            }
        }
        TextView textView7 = this.loadBtn;
        if (textView7 != null) {
            textView7.setText(AppState.loaded ? "已加载 · 再次点按回到桌面" : "加载脚本");
        }
    }

    private void setPerm(TextView textView, boolean z) {
        if (textView == null) {
            return;
        }
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(dp(999));
        if (z) {
            textView.setText("已开启");
            textView.setTextColor(-16052980);
            gradientDrawable.setColor(-3812148);
        } else {
            textView.setText("去开启");
            textView.setTextColor(-1117457);
            gradientDrawable.setColor(-14011858);
        }
        textView.setBackground(gradientDrawable);
    }

    private boolean hasOverlay() {
        return Settings.canDrawOverlays(this);
    }

    private boolean isA11yEnabled() {
        try {
            String string = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
            if (string == null) {
                return false;
            }
            if (string.indexOf(getPackageName() + "/") >= 0 || string.indexOf("com.tapsprite.agent/.AutoService") >= 0) {
                return true;
            }
            return string.indexOf("com.tapsprite.agent.AutoService") >= 0;
        } catch (Exception e) {
            return AppState.auto != null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void requestCapture() {
        if (CaptureService.ready) {
            Toast.makeText(this, "截屏已经开着", 0).show();
            return;
        }
        if (this.captureAsking) {
            return;
        }
        try {
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService("media_projection");
            if (mediaProjectionManager == null) {
                Toast.makeText(this, "这台设备没有截屏接口", 1).show();
            } else {
                this.captureAsking = true;
                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
            }
        } catch (Exception e) {
            this.captureAsking = false;
            Toast.makeText(this, "无法申请截屏：" + e.getMessage(), 1).show();
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i != REQ_CAPTURE) {
            return;
        }
        this.captureAsking = false;
        if (i2 == -1 && intent != null) {
            Intent intent2 = new Intent(this, (Class<?>) CaptureService.class);
            intent2.putExtra("code", i2);
            intent2.putExtra("data", intent);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent2);
            } else {
                startService(intent2);
            }
            AppState.log("已授权截屏");
            Toast.makeText(this, "截屏已开，找色可用", 0).show();
            if (pendingShotAfterCapture) {
                final Handler h = new Handler(Looper.getMainLooper());
                h.postDelayed(new Runnable() {
                    int tries;
                    @Override
                    public void run() {
                        if (CaptureService.ready) {
                            pendingShotAfterCapture = false;
                            LanLink.sendShot();
                        } else if (tries++ < 20) {
                            h.postDelayed(this, 200L);
                        } else {
                            pendingShotAfterCapture = false;
                            AppState.log("截屏授权后仍未就绪");
                        }
                    }
                }, 400L);
            }
        } else {
            pendingShotAfterCapture = false;
            AppState.log("未授权截屏，找色会失败");
            Toast.makeText(this, "没有截屏权限，FindColor 会失败", 1).show();
        }
        refresh();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void requestOverlay() {
        startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName())));
    }

    private void onLoad() {
        if (!hasOverlay()) {
            Toast.makeText(this, "请先允许悬浮窗", 1).show();
            requestOverlay();
            return;
        }
        if (!isA11yEnabled() && AppState.auto == null) {
            Toast.makeText(this, "请先打开无障碍「触控精灵」", 1).show();
            startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
        Intent intent = new Intent(this, (Class<?>) OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        AppState.loaded = true;
        refresh();
        moveTaskToBack(true);
    }

    private LinearLayout card() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-15460330);
        gradientDrawable.setCornerRadius(dp(20));
        gradientDrawable.setStroke(1, 586085103);
        linearLayout.setBackground(gradientDrawable);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return linearLayout;
    }

    private TextView sectionTitle(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 15.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        return textView;
    }

    private TextView muted(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-7629682);
        textView.setTextSize(2, 13.0f);
        textView.setPadding(0, dp(6), 0, 0);
        textView.setLineSpacing(0.0f, 1.35f);
        return textView;
    }

    private TextView primaryButton(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextColor(-16052980);
        textView.setTextSize(2, 16.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        textView.setPadding(0, dp(16), 0, dp(16));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-1117457);
        gradientDrawable.setCornerRadius(dp(16));
        textView.setBackground(gradientDrawable);
        textView.setClickable(true);
        textView.setFocusable(true);
        return textView;
    }

    private TextView ghostButton(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 14.0f);
        textView.setPadding(0, dp(14), 0, dp(14));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(0);
        gradientDrawable.setCornerRadius(dp(16));
        gradientDrawable.setStroke(1, 871297775);
        textView.setBackground(gradientDrawable);
        textView.setClickable(true);
        textView.setFocusable(true);
        return textView;
    }

    private View space(int i) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(i)));
        return view;
    }
}

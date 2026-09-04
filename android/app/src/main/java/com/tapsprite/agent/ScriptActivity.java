package com.tapsprite.agent;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/* loaded from: classes.dex */
public class ScriptActivity extends Activity {
    private static ScriptActivity live;
    private TextView cfgBox;
    private HorizontalScrollView codeScroll;
    private TextView codeView;
    private TextView loadBtn;
    private View modeWrap;
    private TextView srcBtn;
    private boolean srcOpen;
    private TextView titleView;

    static void ping() {
        final ScriptActivity scriptActivity = live;
        if (scriptActivity != null) {
            scriptActivity.runOnUiThread(new Runnable() { // from class: com.tapsprite.agent.ScriptActivity.1
                @Override // java.lang.Runnable
                public void run() {
                    scriptActivity.paint();
                }
            });
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(-16052980);
        getWindow().setNavigationBarColor(-16052980);
        setContentView(buildUi());
        live = this;
        paint();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        live = this;
        paint();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        if (live == this) {
            live = null;
        }
        super.onDestroy();
    }

    private int dp(int i) {
        return Math.round(TypedValue.applyDimension(1, i, getResources().getDisplayMetrics()));
    }

    private View buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(-16052980);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22), dp(28), dp(22), dp(36));
        TextView textView = new TextView(this);
        textView.setText("←  返回");
        textView.setTextColor(-3812148);
        textView.setTextSize(2, 14.0f);
        textView.setPadding(0, 0, 0, dp(16));
        textView.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.ScriptActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ScriptActivity.this.finish();
            }
        });
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        this.titleView = textView2;
        textView2.setText("脚本");
        this.titleView.setTextColor(-1117457);
        this.titleView.setTextSize(2, 28.0f);
        this.titleView.setTypeface(Typeface.SANS_SERIF, 1);
        linearLayout.addView(this.titleView);
        linearLayout.addView(space(16));
        linearLayout.addView(section("UIConfig"));
        TextView muted = muted("");
        this.cfgBox = muted;
        muted.setPadding(0, dp(8), 0, 0);
        linearLayout.addView(this.cfgBox);
        View modeButtons = modeButtons();
        this.modeWrap = modeButtons;
        linearLayout.addView(modeButtons);
        this.loadBtn = primary("加载脚本");
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(18);
        this.loadBtn.setLayoutParams(layoutParams);
        this.loadBtn.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.ScriptActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ScriptActivity.this.onLoad();
            }
        });
        linearLayout.addView(this.loadBtn);
        TextView ghost = ghost("结束并关闭悬浮窗");
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = dp(10);
        ghost.setLayoutParams(layoutParams2);
        ghost.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.ScriptActivity.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ScriptActivity.this.stopService(new Intent(ScriptActivity.this, (Class<?>) OverlayService.class));
                ScriptEngine.requestStop();
                AppState.loaded = false;
                AppState.currentStep = "待命";
                ScriptActivity.this.paint();
            }
        });
        linearLayout.addView(ghost);
        linearLayout.addView(space(18));
        this.srcBtn = ghost("查看源码");
        this.srcBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        this.srcBtn.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.ScriptActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ScriptActivity.this.srcOpen = !ScriptActivity.this.srcOpen;
                int i = ScriptActivity.this.srcOpen ? 0 : 8;
                ScriptActivity.this.codeView.setVisibility(i);
                if (ScriptActivity.this.codeScroll != null) {
                    ScriptActivity.this.codeScroll.setVisibility(i);
                }
                ScriptActivity.this.srcBtn.setText(ScriptActivity.this.srcOpen ? "收起源码" : "查看源码");
            }
        });
        linearLayout.addView(this.srcBtn);
        TextView textView3 = new TextView(this);
        this.codeView = textView3;
        textView3.setTextColor(-3812148);
        this.codeView.setTextSize(2, 12.0f);
        this.codeView.setTypeface(Typeface.MONOSPACE, 0);
        this.codeView.setPadding(0, dp(10), dp(8), dp(8));
        this.codeView.setTextIsSelectable(true);
        this.codeView.setHorizontallyScrolling(true);
        this.codeView.setSingleLine(false);
        this.codeView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
        this.codeView.setVisibility(8);
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(this);
        this.codeScroll = horizontalScrollView;
        horizontalScrollView.setHorizontalScrollBarEnabled(true);
        this.codeScroll.setScrollbarFadingEnabled(false);
        this.codeScroll.setFillViewport(false);
        this.codeScroll.setVisibility(8);
        this.codeScroll.addView(this.codeView);
        linearLayout.addView(this.codeScroll);
        scrollView.addView(linearLayout);
        return scrollView;
    }

    private View modeButtons() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(0, dp(8), 0, 0);
        final String[] strArr = {"上课/打工/冒险", "雇佣+投喂", "小号循环", "PK", "只洗澡", "给大号投喂"};
        final String[] strArr2 = {"多选框1", "多选框2", "多选框3", "多选框6", "多选框5", "多选框7"};
        for (int _i = 0; _i < 6; _i++) {
            final int i = _i;
            TextView ghost = ghost(strArr[i]);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.topMargin = dp(6);
            ghost.setLayoutParams(layoutParams);
            ghost.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.ScriptActivity.6
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    int i2 = 0;
                    while (true) {
                        String[] strArr3 = strArr2;
                        if (i2 >= strArr3.length) {
                            ScriptActivity.this.paint();
                            Toast.makeText(ScriptActivity.this, "模式 → " + strArr[i], 0).show();
                            return;
                        } else {
                            ConfigApi.writeBool(strArr3[i2], i2 == i);
                            i2++;
                        }
                    }
                }
            });
            linearLayout.addView(ghost);
        }
        return linearLayout;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onLoad() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗", 1).show();
            return;
        }
        if (AppState.auto == null) {
            Toast.makeText(this, "请先打开无障碍「触控精灵」", 1).show();
            startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
            return;
        }
        Intent intent = new Intent(this, (Class<?>) OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        AppState.loaded = true;
        moveTaskToBack(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void paint() {
        int i = AppState.scriptTab;
        TextView textView = this.titleView;
        if (textView != null) {
            if (i == 1) {
                textView.setText("基础测试");
            } else if (i == 2) {
                textView.setText("电脑下发");
            } else {
                textView.setText("宠物脚本");
            }
        }
        View view = this.modeWrap;
        if (view != null) {
            view.setVisibility(i == 0 ? 0 : 8);
        }
        this.codeView.setText(AppState.withLineNumbers(AppState.script == null ? "" : AppState.script));
        this.cfgBox.setText(i == 0 ? ConfigApi.dump() : "这个脚本不用 UIConfig。");
        this.loadBtn.setText(AppState.loaded ? "已加载 · 再点回桌面" : "加载");
    }

    private void style(TextView textView, boolean z) {
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

    private TextView chip(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextSize(2, 14.0f);
        textView.setPadding(dp(8), dp(10), dp(8), dp(10));
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        return textView;
    }

    private TextView section(String str) {
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
        textView.setLineSpacing(0.0f, 1.25f);
        return textView;
    }

    private View space(int i) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(i)));
        return view;
    }

    private TextView primary(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextColor(-16052980);
        textView.setTextSize(2, 16.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        textView.setPadding(0, dp(14), 0, dp(14));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-1117457);
        gradientDrawable.setCornerRadius(dp(16));
        textView.setBackground(gradientDrawable);
        return textView;
    }

    private TextView ghost(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 14.0f);
        textView.setPadding(0, dp(12), 0, dp(12));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(0);
        gradientDrawable.setCornerRadius(dp(14));
        gradientDrawable.setStroke(1, 871297775);
        textView.setBackground(gradientDrawable);
        return textView;
    }
}

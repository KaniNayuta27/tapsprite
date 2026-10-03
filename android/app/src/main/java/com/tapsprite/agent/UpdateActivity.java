package com.tapsprite.agent;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.tapsprite.agent.Updater;

/* loaded from: classes.dex */
public class UpdateActivity extends Activity implements Updater.Listener {
    private ProgressBar bar;
    private TextView checkBtn;
    private TextView downloadBtn;
    private String pendingApk = "";
    private String pendingName = "";
    private TextView percent;
    private TextView status;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(-16052980);
        getWindow().setNavigationBarColor(-16052980);
        setContentView(buildUi());
        if (getIntent() != null && getIntent().getBooleanExtra("ready", false)) {
            this.status.setText("电脑已下好安装包，正在传到手机…");
            Updater.installReadyFromPc(this, this);
        } else if (getIntent() != null && getIntent().getBooleanExtra("auto", false)) {
            Updater.check(this, true, this);
        }
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
        textView.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.UpdateActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                UpdateActivity.this.finish();
            }
        });
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText("TAPSPRITE");
        textView2.setTextColor(-7629682);
        textView2.setTextSize(2, 11.0f);
        textView2.setLetterSpacing(0.12f);
        linearLayout.addView(textView2);
        TextView textView3 = new TextView(this);
        textView3.setText("检查更新");
        textView3.setTextColor(-1117457);
        textView3.setTextSize(2, 32.0f);
        textView3.setTypeface(Typeface.SANS_SERIF, 1);
        textView3.setPadding(0, dp(4), 0, dp(18));
        linearLayout.addView(textView3);
        LinearLayout card = card();
        TextView textView4 = new TextView(this);
        textView4.setText("当前版本  " + Updater.currentName() + "  (" + Updater.currentCode() + ")");
        textView4.setTextColor(-1117457);
        textView4.setTextSize(2, 15.0f);
        card.addView(textView4);
        TextView textView5 = new TextView(this);
        this.percent = textView5;
        textView5.setText("尚未开始");
        this.percent.setTextColor(-3812148);
        this.percent.setTextSize(2, 20.0f);
        this.percent.setTypeface(Typeface.SANS_SERIF, 1);
        this.percent.setPadding(0, dp(16), 0, dp(8));
        card.addView(this.percent);
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        this.bar = progressBar;
        progressBar.setMax(1000);
        this.bar.setProgress(0);
        this.bar.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(10)));
        card.addView(this.bar);
        TextView textView6 = new TextView(this);
        this.status = textView6;
        textView6.setText("点检查。有新版本会让电脑下载，再经局域网传到手机。");
        this.status.setTextColor(-7629682);
        this.status.setTextSize(2, 13.0f);
        this.status.setPadding(0, dp(14), 0, 0);
        this.status.setLineSpacing(0.0f, 1.25f);
        card.addView(this.status);
        linearLayout.addView(card);
        linearLayout.addView(space(16));
        TextView primary = primary("检查更新");
        this.checkBtn = primary;
        primary.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.UpdateActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                UpdateActivity updateActivity = UpdateActivity.this;
                Updater.check(updateActivity, false, updateActivity);
            }
        });
        linearLayout.addView(this.checkBtn);
        linearLayout.addView(space(10));
        TextView ghost = ghost("让电脑下载并安装");
        this.downloadBtn = ghost;
        ghost.setOnClickListener(new View.OnClickListener() { // from class: com.tapsprite.agent.UpdateActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                if (UpdateActivity.this.pendingApk.length() == 0) {
                    UpdateActivity updateActivity = UpdateActivity.this;
                    Updater.check(updateActivity, true, updateActivity);
                } else {
                    UpdateActivity updateActivity2 = UpdateActivity.this;
                    Updater.downloadAndInstall(updateActivity2, updateActivity2.pendingApk, UpdateActivity.this.pendingName, UpdateActivity.this);
                }
            }
        });
        linearLayout.addView(this.downloadBtn);
        scrollView.addView(linearLayout);
        return scrollView;
    }

    @Override // com.tapsprite.agent.Updater.Listener
    public void onFound(int i, String str, String str2, String str3) {
        if (str2 == null) {
            str2 = "";
        }
        this.pendingApk = str2;
        this.pendingName = str != null ? str : "";
        this.percent.setText("发现 " + str);
        this.downloadBtn.setText("下载 " + str);
    }

    @Override // com.tapsprite.agent.Updater.Listener
    public void onStatus(String str) {
        this.status.setText(str);
    }

    @Override // com.tapsprite.agent.Updater.Listener
    public void onProgress(long j, long j2) {
        if (j2 > 0) {
            this.bar.setProgress((int) Math.min(1000L, (j * 1000) / j2));
            this.percent.setText(((100 * j) / j2) + "%   " + Updater.formatSize(j) + " / " + Updater.formatSize(j2));
        } else {
            this.bar.setIndeterminate(true);
            this.percent.setText("已下 " + Updater.formatSize(j));
        }
        this.status.setText("正在下载安装包，请留在这一页。");
    }

    @Override // com.tapsprite.agent.Updater.Listener
    public void onError(String str) {
        this.bar.setIndeterminate(false);
        this.percent.setText("失败");
        this.status.setText(str);
    }

    @Override // com.tapsprite.agent.Updater.Listener
    public void onIdle(String str) {
        this.bar.setProgress(0);
        this.percent.setText("已是最新");
        this.status.setText(str);
    }

    private LinearLayout card() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-15460330);
        gradientDrawable.setCornerRadius(dp(20));
        gradientDrawable.setStroke(1, 619639535);
        linearLayout.setBackground(gradientDrawable);
        return linearLayout;
    }

    private View space(int i) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(i)));
        return view;
    }

    private TextView primary(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextColor(-16052980);
        textView.setTextSize(2, 15.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        textView.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-1117457);
        gradientDrawable.setCornerRadius(dp(12));
        textView.setBackground(gradientDrawable);
        return textView;
    }

    private TextView ghost(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextColor(-1117457);
        textView.setTextSize(2, 15.0f);
        textView.setTypeface(Typeface.SANS_SERIF, 1);
        textView.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(0);
        gradientDrawable.setCornerRadius(dp(12));
        gradientDrawable.setStroke(1, 871297775);
        textView.setBackground(gradientDrawable);
        return textView;
    }
}

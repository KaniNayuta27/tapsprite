package com.tapsprite.agent;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.WindowManager;

/**
 * Transparent activity that turns the screen on over the lock screen.
 * Unlock additionally asks the system to show the PIN / password bouncer.
 * Some ROMs still require the vendor 「后台弹出界面」 and 「锁屏显示」 switches.
 */
public class WakeActivity extends Activity {
    private static final long UNLOCK_TIMEOUT_MS = 20000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean unlock;
    private boolean asked;
    private boolean reported;
    private PowerManager.WakeLock wakeLock;
    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            report("error");
        }
    };
    private final Runnable finishSoon = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing()) {
                finish();
            }
        }
    };

    static void launch(final boolean unlock) {
        final Context ctx = AppState.auto != null ? AppState.auto : App.ctx;
        if (ctx == null) {
            LiveStream.reportLive(unlock ? "实时操控解锁 error 无上下文" : "实时操控亮屏失败 无上下文");
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(ctx, WakeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    i.putExtra("unlock", unlock);
                    ctx.startActivity(i);
                    if (!unlock) {
                        LiveStream.reportLive("实时操控亮屏");
                    }
                } catch (Exception e) {
                    String m = e.getMessage() == null ? e.toString() : e.getMessage();
                    LiveStream.reportLive(unlock ? "实时操控解锁 error " + m : "实时操控亮屏失败 " + m);
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        unlock = getIntent() != null && getIntent().getBooleanExtra("unlock", false);
        applyWakeFlags();
        View v = new View(this);
        v.setBackgroundColor(0);
        setContentView(v);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        unlock = intent != null && intent.getBooleanExtra("unlock", false);
        asked = false;
        reported = false;
        applyWakeFlags();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!unlock) {
            handler.removeCallbacks(finishSoon);
            handler.postDelayed(finishSoon, 200);
            return;
        }
        if (asked) {
            return;
        }
        asked = true;
        dismissKeyguard();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(finishSoon);
        releaseWakeLock();
        super.onDestroy();
    }

    private void applyWakeFlags() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        acquireWakeLock();
    }

    @SuppressWarnings("deprecation")
    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) {
                return;
            }
            releaseWakeLock();
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "tapsprite:wake");
            wl.setReferenceCounted(false);
            wl.acquire(3000);
            wakeLock = wl;
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        PowerManager.WakeLock wl = wakeLock;
        wakeLock = null;
        if (wl == null) {
            return;
        }
        try {
            if (wl.isHeld()) {
                wl.release();
            }
        } catch (Exception ignored) {
        }
    }

    private void dismissKeyguard() {
        if (Build.VERSION.SDK_INT < 26) {
            report("error");
            return;
        }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null) {
            report("error");
            return;
        }
        handler.postDelayed(timeout, UNLOCK_TIMEOUT_MS);
        try {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    report("success");
                }

                @Override
                public void onDismissCancelled() {
                    report("cancel");
                }

                @Override
                public void onDismissError() {
                    report("error");
                }
            });
        } catch (Exception e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            LiveStream.reportLive("实时操控解锁 error " + m);
            reported = true;
            finish();
        }
    }

    private void report(String result) {
        if (reported) {
            return;
        }
        reported = true;
        handler.removeCallbacks(timeout);
        LiveStream.reportLive("实时操控解锁 " + result);
        if (!isFinishing()) {
            finish();
        }
    }
}

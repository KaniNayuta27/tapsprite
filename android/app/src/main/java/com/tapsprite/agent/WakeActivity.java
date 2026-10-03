package com.tapsprite.agent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;

/**
 * Transparent activity that turns the screen on over the lock screen.
 * Unlock wakes the screen, waits, then swipes up from the bottom center so the
 * PIN pad appears. {@code requestDismissKeyguard} is not used: on this phone it
 * returns cancel and can swallow the swipe. Some ROMs still require the vendor
 * 「后台弹出界面」 and 「锁屏显示」 switches.
 */
public class WakeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean unlock;
    private boolean asked;
    private PowerManager.WakeLock wakeLock;
    private final Runnable swipeTask = new Runnable() {
        @Override
        public void run() {
            swipeUnlock();
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
        handler.removeCallbacks(swipeTask);
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
        pokeScreen(this);
        LiveStream.reportLive("实时操控解锁 亮屏");
        LiveStream.reportLive("实时操控解锁 跳过 dismiss（requestDismissKeyguard 会 cancel）");
        handler.postDelayed(swipeTask, LiveUnlock.WAKE_WAIT_MS);
    }

    @Override
    protected void onDestroy() {
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

    /** Keep the panel lit after this activity finishes, so the swipe hits the keyguard. */
    @SuppressWarnings("deprecation")
    private static void pokeScreen(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(POWER_SERVICE);
            if (pm == null) {
                return;
            }
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "tapsprite:unlock");
            wl.setReferenceCounted(false);
            wl.acquire(4000);
        } catch (Exception ignored) {
        }
    }

    private void swipeUnlock() {
        LiveStream.Disp d = LiveStream.readDisp();
        final int[] s = LiveUnlock.swipePx(d.w, d.h);
        LiveStream.reportLive("实时操控解锁 上滑 " + s[0] + "," + s[1]
                + " → " + s[0] + "," + s[2] + " " + s[3] + "ms");
        if (!isFinishing()) {
            finish();
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                // Let the translucent activity leave so the swipe lands on the keyguard.
                SystemClock.sleep(80);
                AutoService auto = AppState.auto;
                if (auto == null) {
                    LiveStream.reportLive("实时操控解锁 上滑失败 无障碍未连");
                    return;
                }
                boolean ok = auto.swipe(s[0], s[1], s[0], s[2], s[3]);
                LiveStream.reportLive(ok ? "实时操控解锁 上滑完成" : "实时操控解锁 上滑失败");
            }
        }, "tapsprite-unlock");
        t.setDaemon(true);
        t.start();
    }
}

package com.tapsprite.agent;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.LuaError;

/** Blocking InputBox for the script thread (never the UI thread). */
public final class DialogApi {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile AlertDialog currentDialog;

    private DialogApi() {
    }

    /**
     * Match 按键 InputBox: returns typed text. Cancel / empty → "".
     * Overlay first; if overlay is missing or cannot draw, fall back to an
     * Activity AlertDialog (bringing MainActivity forward) or a
     * TYPE_APPLICATION_OVERLAY dialog on the app context.
     */
    public static String inputBox(String prompt, String def) {
        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = "输入";
        }
        if (def == null) {
            def = "";
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AppState.log("InputBox 不能在 UI 线程阻塞");
            return def;
        }
        LuaEngine.checkStop();

        OverlayService overlay = AppState.overlay;
        if (overlay != null) {
            String fromOverlay = overlay.prompt(prompt, def);
            throwIfStopped();
            if (fromOverlay != null) {
                AppState.log("InputBox = " + fromOverlay);
                return fromOverlay;
            }
        }

        if (canDrawOverlays()) {
            String fromWindow = promptOnAppOverlay(prompt, def);
            throwIfStopped();
            if (fromWindow != null) {
                AppState.log("InputBox = " + fromWindow);
                return fromWindow;
            }
        }

        String fromActivity = promptOnActivity(prompt, def);
        throwIfStopped();
        if (fromActivity != null) {
            AppState.log("InputBox = " + fromActivity);
            return fromActivity;
        }

        MainActivity.bringToFront();
        long deadline = SystemClock.uptimeMillis() + 2500L;
        while (MainActivity.live() == null && SystemClock.uptimeMillis() < deadline) {
            throwIfStopped();
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fromActivity = promptOnActivity(prompt, def);
        throwIfStopped();
        if (fromActivity != null) {
            AppState.log("InputBox = " + fromActivity);
            return fromActivity;
        }
        AppState.log("InputBox 无法弹出，返回空");
        return "";
    }

    static boolean canDrawOverlays() {
        Context ctx = App.ctx;
        return ctx != null && Settings.canDrawOverlays(ctx);
    }

    private static String promptOnActivity(final String prompt, final String def) {
        final MainActivity activity = MainActivity.live();
        if (activity == null || activity.isFinishing()) {
            return null;
        }
        return showAlert(activity, prompt, def, false);
    }

    private static String promptOnAppOverlay(final String prompt, final String def) {
        Context ctx = App.ctx;
        if (ctx == null || !Settings.canDrawOverlays(ctx)) {
            return null;
        }
        Context themed = new ContextThemeWrapper(ctx, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        return showAlert(themed, prompt, def, true);
    }

    private static String showAlert(final Context ctx, final String prompt, final String def, final boolean overlayType) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> value = new AtomicReference<>(null);
        final AtomicBoolean shown = new AtomicBoolean(false);
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    dismissCurrent();
                    final EditText edit = new EditText(ctx);
                    edit.setText(def == null ? "" : def);
                    edit.setSingleLine(true);
                    edit.setSelectAllOnFocus(true);
                    FrameLayout wrap = new FrameLayout(ctx);
                    int pad = Math.round(16f * ctx.getResources().getDisplayMetrics().density);
                    wrap.setPadding(pad, pad / 2, pad, 0);
                    wrap.addView(edit);
                    AlertDialog dialog = new AlertDialog.Builder(ctx)
                            .setTitle(prompt)
                            .setView(wrap)
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    value.set(edit.getText() == null ? "" : edit.getText().toString());
                                }
                            })
                            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    value.set("");
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface d) {
                                    value.set("");
                                }
                            })
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface d) {
                                    if (currentDialog == d) {
                                        currentDialog = null;
                                    }
                                    if (value.get() == null) {
                                        value.set("");
                                    }
                                    latch.countDown();
                                }
                            })
                            .create();
                    dialog.setCanceledOnTouchOutside(true);
                    Window w = dialog.getWindow();
                    if (overlayType && w != null) {
                        int type = Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE;
                        w.setType(type);
                    }
                    currentDialog = dialog;
                    dialog.show();
                    edit.requestFocus();
                    shown.set(true);
                } catch (Exception e) {
                    AppState.log("InputBox 弹窗失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
                    shown.set(false);
                    latch.countDown();
                }
            }
        });
        awaitLatch(latch, new Runnable() {
            @Override
            public void run() {
                dismissCurrent();
            }
        });
        if (!shown.get()) {
            return null;
        }
        String r = value.get();
        return r != null ? r : "";
    }

    private static void awaitLatch(CountDownLatch latch, final Runnable dismiss) {
        try {
            while (!latch.await(200L, TimeUnit.MILLISECONDS)) {
                if (ScriptEngine.isStopRequested()) {
                    MAIN.post(dismiss);
                    throw new LuaError("脚本已停止");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            MAIN.post(dismiss);
            if (ScriptEngine.isStopRequested()) {
                throw new LuaError("脚本已停止");
            }
        }
    }

    private static void dismissCurrent() {
        AlertDialog d = currentDialog;
        currentDialog = null;
        if (d != null) {
            try {
                if (d.isShowing()) {
                    d.dismiss();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void throwIfStopped() {
        if (ScriptEngine.isStopRequested()) {
            throw new LuaError("脚本已停止");
        }
    }
}

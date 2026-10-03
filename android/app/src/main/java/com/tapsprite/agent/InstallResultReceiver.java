package com.tapsprite.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/* loaded from: classes.dex */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        int intExtra = intent.getIntExtra("android.content.pm.extra.STATUS", 1);
        String stringExtra = intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
        if (intExtra == -1) {
            Intent intent2 = (Intent) intent.getParcelableExtra("android.intent.extra.INTENT");
            if (intent2 != null) {
                intent2.addFlags(268435456);
                context.startActivity(intent2);
                return;
            }
            return;
        }
        if (intExtra == 0) {
            AppState.log("更新安装成功");
        } else {
            AppState.log("更新安装未完成 status=" + intExtra + " " + stringExtra);
        }
    }
}

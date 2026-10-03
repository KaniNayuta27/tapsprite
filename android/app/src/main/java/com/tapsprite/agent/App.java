package com.tapsprite.agent;

import android.app.Application;

/* loaded from: classes.dex */
public class App extends Application {
    public static App ctx;

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        ctx = this;
        AppState.init();
        AppState.ensureServer();
    }
}

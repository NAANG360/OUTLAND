package com.outland.game;

import android.app.Application;

/** Runs before AndroidLauncher so fatal startup failures have a journal handler. */
public final class OutlandApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashJournal.install(this);
    }
}

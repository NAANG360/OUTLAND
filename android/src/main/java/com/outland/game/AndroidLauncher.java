package com.outland.game;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public class AndroidLauncher extends AndroidApplication {
    private static final String TAG = "OUTLAND";
    private static final int LOG_PERMISSION_REQUEST = 7301;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashJournal.record("AndroidLauncher.onCreate", null);
        // Ask before libGDX boot so repeated early startup failures don't prevent the prompt.
        requestLogStoragePermission();
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGL30 = false;
        config.numSamples = 0;
        config.depth = 16;
        config.disableAudio = true;
        config.useImmersiveMode = true;
        try {
            initialize(new OutlandGame(), config);
        } catch (Throwable error) {
            CrashJournal.record("libGDX Android initialization failed", error);
            Log.e(TAG, "libGDX Android initialization failed", error);
            throw error;
        }
    }

    private void requestLogStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, LOG_PERMISSION_REQUEST);
        } else {
            CrashJournal.flushPending(this);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOG_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) CrashJournal.flushPending(this);
            else CrashJournal.record("PUBLIC_LOG_PERMISSION_DENIED; staged logs retained in app-private files/outlandlogs", null);
        }
    }

    @Override protected void onPause() { CrashJournal.record("AndroidLauncher.onPause", null); super.onPause(); }
    @Override protected void onResume() { super.onResume(); CrashJournal.record("AndroidLauncher.onResume", null); CrashJournal.flushPending(this); }
}

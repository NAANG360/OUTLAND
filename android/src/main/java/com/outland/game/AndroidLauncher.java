package com.outland.game;

import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public class AndroidLauncher extends AndroidApplication {
    private static final String TAG = "OUTLAND";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGL30 = false;       // Force the GLES 2 backend.
        config.numSamples = 0;        // Avoid fragile MSAA EGL configs.
        config.depth = 16;
        config.disableAudio = true;   // Audio is unused; remove its startup/native path.
        config.useImmersiveMode = true;
        try {
            initialize(new OutlandGame(), config);
        } catch (Throwable error) {
            Log.e(TAG, "libGDX Android initialization failed", error);
            throw error;
        }
    }
}

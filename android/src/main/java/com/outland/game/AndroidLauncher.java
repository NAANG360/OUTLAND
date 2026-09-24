package com.outland.game;

import android.os.Bundle;
import android.util.Log;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public class AndroidLauncher extends AndroidApplication {
    private static final String TAG = "OUTLAND";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        // Keep the renderer on the broadly supported GLES 2 path. Do not request
        // multisampling/depth settings that can fail on older/mobile GPU drivers.
        config.useGL30 = false;
        config.numSamples = 0;
        config.depth = 16;

        try {
            initialize(new OutlandGame(), config);
        } catch (RuntimeException | LinkageError error) {
            Log.e(TAG, "Failed to initialize libGDX", error);
            throw error;
        }
    }
}

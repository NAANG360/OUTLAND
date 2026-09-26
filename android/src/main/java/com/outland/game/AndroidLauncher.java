package com.outland.game;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

/**
 * OUTLAND Android entry point.
 *
 * Intentionally kept aligned with the standard libGDX launcher:
 * Activity -> configuration -> initialize(game).
 * Game-specific diagnostics and permissions must not participate in GL startup.
 */
public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGL30 = false;
        config.numSamples = 0;
        config.depth = 16;
        config.disableAudio = true;

        initialize(new OutlandGame(), config);
    }
}

package com.outland.game;
import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
public class AndroidLauncher extends AndroidApplication {
 @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);AndroidApplicationConfiguration c=new AndroidApplicationConfiguration();c.useAccelerometer=false;c.useCompass=false;initialize(new OutlandGame(),c);}
}

package com.outland.game.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;

/** Owns only 2D HUD resources; receives already-formatted view state. */
public final class HudRenderer {
    private SpriteBatch batch;
    private BitmapFont font;
    public void create(){ batch=new SpriteBatch(); font=new BitmapFont(); }
    public void render(int width,int height,String[] lines) {
        if(batch==null||font==null)return;
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,width,height));
        batch.begin();
        try { for(int i=0;i<lines.length;i++) font.draw(batch,lines[i],14,height-16-i*22); }
        finally { batch.end(); }
    }
    public void dispose(){if(batch!=null){batch.dispose();batch=null;}if(font!=null){font.dispose();font=null;}}
}

package com.outland.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;

/** Owns the 2D HUD and the visible touch controls. */
public final class HudRenderer {
    private SpriteBatch batch;
    private BitmapFont font;
    private ShapeRenderer shapes;

    public void create(){
        batch=new SpriteBatch();
        font=new BitmapFont();
        shapes=new ShapeRenderer();
    }

    public void render(int width,int height,String[] lines) {
        if(batch==null||font==null||shapes==null)return;

        // Touch controls: thumbstick on the lower-left; action cluster on lower-right.
        float scale=Math.max(.72f,Math.min(width,height)/800f);
        float cx=96f*scale, cy=height-96f*scale, base=58f*scale, knob=25f*scale;
        float bx=width-82f*scale, by=height-78f*scale, r=30f*scale, gap=72f*scale;

        shapes.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,width,height));
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(new Color(0.04f,0.06f,0.08f,.42f));
        shapes.circle(cx,cy,base);
        shapes.setColor(new Color(.75f,.80f,.84f,.24f));
        shapes.circle(cx,cy,base-3f*scale);
        shapes.setColor(new Color(.88f,.91f,.94f,.58f));
        shapes.circle(cx,cy,knob);

        drawButton(bx,by,r);
        drawButton(bx-gap,by,r);
        drawButton(bx,by+gap,r);
        drawButton(bx-gap,by+gap,r);
        shapes.end();

        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,width,height));
        batch.begin();
        try {
            // Keep diagnostics readable but out of the control zones.
            for(int i=0;i<Math.min(lines.length,5);i++) font.draw(batch,lines[i],14,height-16-i*22);

            font.draw(batch,"●",cx-8f*scale,cy+7f*scale);
            font.draw(batch,"M",bx-gap-7f,by+7f);
            font.draw(batch,"P",bx-7f,by+7f);
            font.draw(batch,"+",bx-gap-7f,by+gap+7f);
            font.draw(batch,"J",bx-7f,by+gap+7f);
        } finally { batch.end(); }
    }

    private void drawButton(float x,float y,float radius){
        shapes.setColor(new Color(.05f,.07f,.09f,.62f));
        shapes.circle(x,y,radius);
        shapes.setColor(new Color(.82f,.86f,.90f,.32f));
        shapes.circle(x,y,radius-3f);
    }

    public void dispose(){
        ShapeRenderer oldShapes=shapes;shapes=null;
        if(oldShapes!=null)try{oldShapes.dispose();}catch(Throwable ignored){}
        SpriteBatch oldBatch=batch;batch=null;
        if(oldBatch!=null)try{oldBatch.dispose();}catch(Throwable ignored){}
        BitmapFont oldFont=font;font=null;
        if(oldFont!=null)try{oldFont.dispose();}catch(Throwable ignored){}
    }
}

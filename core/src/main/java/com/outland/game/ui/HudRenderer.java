package com.outland.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.outland.game.world.BlockType;

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

    public void render(int width,int height,String[] lines,int selectedBlock,int[] inventory) {
        if(batch==null||font==null||shapes==null)return;

        // Touch controls: thumbstick on the lower-left; action cluster on lower-right.
        // A compact Minecraft/Roblox-style hotbar sits above the lower edge.
        float scale=Math.max(.72f,Math.min(width,height)/800f);
        float cx=96f*scale, cy=96f*scale, base=58f*scale, knob=25f*scale;
        float bx=width-82f*scale, by=78f*scale, r=30f*scale, gap=72f*scale;
        float slot=48f*scale, slotGap=5f*scale;
        float hotbarWidth=BlockType.values().length*slot+(BlockType.values().length-1)*slotGap;
        float hotbarX=(width-hotbarWidth)*.5f, hotbarY=20f*scale;

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

        // Hotbar background and seven item slots.
        shapes.setColor(new Color(.03f,.04f,.05f,.72f));
        shapes.rect(hotbarX-8f*scale,hotbarY-8f*scale,hotbarWidth+16f*scale,slot+16f*scale);
        for(int i=0;i<BlockType.values().length;i++){
            float sx=hotbarX+i*(slot+slotGap);
            shapes.setColor(i==selectedBlock?new Color(.95f,.91f,.72f,.92f):new Color(.55f,.60f,.64f,.48f));
            shapes.rect(sx-2f*scale,hotbarY-2f*scale,slot+4f*scale,slot+4f*scale);
            shapes.setColor(new Color(.08f,.10f,.11f,.88f));
            shapes.rect(sx,hotbarY,slot,slot);
            drawItemIcon(sx+slot*.5f,hotbarY+slot*.56f,slot*.34f,i);
        }
        shapes.end();

        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,width,height));
        batch.begin();
        try {
            // Keep diagnostics readable but out of the control zones.
            for(int i=0;i<Math.min(lines.length,7);i++) font.draw(batch,lines[i],14,height-16-i*22);

            font.draw(batch,"●",cx-8f*scale,cy+7f*scale);
            // Top row: NEXT / JUMP. Bottom row: MINE / PLACE.
            font.draw(batch,"M",bx-gap-7f,by+7f);
            font.draw(batch,"P",bx-7f,by+7f);
            font.draw(batch,"+",bx-gap-7f,by+gap+7f);
            font.draw(batch,"J",bx-7f,by+gap+7f);

            for(int i=0;i<BlockType.values().length;i++){
                float sx=hotbarX+i*(slot+slotGap);
                font.draw(batch,String.valueOf(i+1),sx+4f*scale,hotbarY+slot-5f*scale);
                int count=(inventory!=null&&i<inventory.length)?inventory[i]:0;
                String text=count>999?"999+":String.valueOf(count);
                font.draw(batch,text,sx+slot-20f*scale,hotbarY+7f*scale);
            }
        } finally { batch.end(); }
    }

    private void drawItemIcon(float x,float y,float size,int id){
        Color c;
        switch(BlockType.values()[id]){
            case GRASS:c=new Color(.32f,.52f,.25f,1);break;
            case DIRT:c=new Color(.47f,.29f,.16f,1);break;
            case STONE:c=new Color(.48f,.50f,.48f,1);break;
            case WOOD:c=new Color(.38f,.22f,.12f,1);break;
            case LEAVES:c=new Color(.20f,.42f,.24f,1);break;
            case CACTUS:c=new Color(.27f,.58f,.30f,1);break;
            default:c=new Color(.55f,.82f,.38f,1);break;
        }
        shapes.setColor(c);
        shapes.circle(x,y,size);
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

package com.outland.game;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.*;
import java.util.*;

/** OUTLAND: conservative mobile-first voxel prototype. */
public class OutlandGame extends ApplicationAdapter {
    private static final String TAG = "OUTLAND";
    private static final int WORLD_RADIUS = 9;
    private static final float RENDER_DISTANCE2 = 24f * 24f;
    private PerspectiveCamera camera;
    private ModelBatch batch;
    private Environment env;
    private final Map<Long, Block> blocks = new HashMap<>();
    private final Random rng = new Random();
    private final Vector3 pos = new Vector3(0, 8, 0), tmp = new Vector3();
    private final String[] types = {"Grass", "Dirt", "Stone", "Wood", "Leaves"};
    private final int[] inventory = {24, 16, 0, 0, 0};
    private final Model[] models = new Model[5];
    private BitmapFont font;
    private SpriteBatch hud;
    private long seed;
    private int selected, health = 100;
    private float yaw, pitch, vy, moveForward, moveSide, touchX, touchY;
    private boolean grounded, draggingLook;
    private int movePointer = -1;
    private String toast = "OUTLAND loading...";
    private String startupError;

    private static final class Block {
        final int x, y, z, type;
        final ModelInstance instance;
        Block(int x, int y, int z, int type, Model model) {
            this.x=x; this.y=y; this.z=z; this.type=type;
            instance = new ModelInstance(model);
            instance.transform.setToTranslation(x,y,z);
        }
    }
    private long key(int x,int y,int z) {
        return (((long)(x+2048)&4095)<<24) | (((long)(y+512)&1023)<<14) | ((z+2048)&4095);
    }

    @Override public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        try {
            Gdx.app.log(TAG, "create: begin; GL=" + Gdx.graphics.getGLVersion());
            seed = System.currentTimeMillis() & 0x7fffffffL;
            rng.setSeed(seed);
            hud = new SpriteBatch();
            font = new BitmapFont();
            batch = new ModelBatch();
            env = new Environment();
            env.set(new ColorAttribute(ColorAttribute.AmbientLight, .8f, .82f, .86f, 1f));
            env.add(new DirectionalLight().set(.9f,.88f,.78f,-1f,-2f,-.5f));
            int[] colors = {0x68a94fff,0x8a603fff,0x858b91ff,0x79502fff,0x438443ff};
            for (int i=0;i<models.length;i++) {
                models[i] = new ModelBuilder().createBox(1,1,1,
                    new Material(ColorAttribute.createDiffuse(new Color(colors[i]))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
            }
            camera = new PerspectiveCamera(70, Math.max(1,Gdx.graphics.getWidth()), Math.max(1,Gdx.graphics.getHeight()));
            camera.near=.1f; camera.far=60f;
            generateWorld();
            pos.set(0,heightAt(0,0)+2.2f,0);
            camera.position.set(pos);
            installInput();
            toast = "World ready · tap MINE / PLACE";
            Gdx.app.log(TAG, "create: complete; blocks="+blocks.size());
        } catch (Throwable t) {
            startupError = t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());
            if (Gdx.app != null) Gdx.app.error(TAG,"Startup failed",t);
            try { if (hud==null) hud=new SpriteBatch(); if(font==null) font=new BitmapFont(); } catch(Throwable ignored) {}
        }
    }

    private void installInput() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int k) {
                if(k>=Input.Keys.NUM_1 && k<=Input.Keys.NUM_5) selected=k-Input.Keys.NUM_1;
                if(k==Input.Keys.SPACE) jump();
                if(k==Input.Keys.F) interact(false);
                if(k==Input.Keys.G) interact(true);
                return true;
            }
            @Override public boolean touchDown(int x,int y,int pointer,int button) {
                float w=Gdx.graphics.getWidth(), h=Gdx.graphics.getHeight();
                float nx=x/w, ny=y/h;
                if(ny>.78f && nx>.48f) {
                    if(nx>.84f) jump(); else if(nx>.72f) selected=(selected+1)%types.length;
                    else if(nx>.60f) interact(true); else interact(false);
                    return true;
                }
                if(nx>.42f) { draggingLook=true; touchX=x; touchY=y; }
                else { movePointer=pointer; moveForward=ny>.55f?1:-1; moveSide=nx<.19f?-1:(nx>.29f?1:0); }
                return true;
            }
            @Override public boolean touchDragged(int x,int y,int pointer) {
                if(draggingLook) { yaw-=(x-touchX)*.004f; pitch=MathUtils.clamp(pitch-(y-touchY)*.004f,-1.25f,1.25f); touchX=x; touchY=y; }
                return true;
            }
            @Override public boolean touchUp(int x,int y,int pointer,int button) {
                if(pointer==movePointer){movePointer=-1;moveForward=moveSide=0;}
                draggingLook=false; return true;
            }
            @Override public boolean touchCancelled(int x,int y,int pointer,int button) {
                movePointer=-1;moveForward=moveSide=0;draggingLook=false;return true;
            }
        });
    }

    private int heightAt(int x,int z) {
        double n=Math.sin((x+seed%97)*.13)*1.5+Math.cos((z-seed%53)*.11)*1.4+Math.sin((x+z)*.07)*1.5;
        return 3+(int)Math.round(n);
    }
    private void generateWorld() {
        for(int x=-WORLD_RADIUS;x<=WORLD_RADIUS;x++) for(int z=-WORLD_RADIUS;z<=WORLD_RADIUS;z++) {
            int h=heightAt(x,z);
            addBlock(x,h,z,0); addBlock(x,h-1,z,1); addBlock(x,h-2,z,1); addBlock(x,h-3,z,2);
            if(h>3 && rng.nextFloat()>.985f) makeTree(x,h+1,z);
        }
    }
    private void makeTree(int x,int y,int z) {
        for(int i=0;i<3;i++) addBlock(x,y+i,z,3);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) for(int dy=2;dy<=4;dy++)
            if(Math.abs(dx)+Math.abs(dz)+(dy==4?1:0)<4) addBlock(x+dx,y+dy,z+dz,4);
    }
    private void addBlock(int x,int y,int z,int type) {
        long k=key(x,y,z); if(!blocks.containsKey(k)) blocks.put(k,new Block(x,y,z,type,models[type]));
    }
    private void jump(){if(grounded){vy=6.5f;grounded=false;}}
    private Vector3 forward(){return new Vector3(MathUtils.sin(yaw),0,-MathUtils.cos(yaw)).nor();}
    private void interact(boolean place) {
        Vector3 dir=new Vector3(MathUtils.sin(yaw)*MathUtils.cos(pitch),MathUtils.sin(pitch),-MathUtils.cos(yaw)*MathUtils.cos(pitch)).nor();
        for(float d=.5f;d<5.5f;d+=.2f) {
            Vector3 p=new Vector3(pos).mulAdd(dir,d);
            int x=Math.round(p.x),y=Math.round(p.y),z=Math.round(p.z);
            Block b=blocks.get(key(x,y,z)); if(b==null) continue;
            if(!place){blocks.remove(key(b.x,b.y,b.z));inventory[b.type]++;toast="Mined "+types[b.type];}
            else {
                int t=selected;if(inventory[t]<=0){toast="No "+types[t];return;}
                Vector3 q=new Vector3(p).mulAdd(dir,-.65f);int bx=Math.round(q.x),by=Math.round(q.y),bz=Math.round(q.z);
                if(Math.abs(bx-pos.x)<1.2f&&Math.abs(bz-pos.z)<1.2f)return;
                long k=key(bx,by,bz);if(!blocks.containsKey(k)){addBlock(bx,by,bz,t);inventory[t]--;toast="Placed "+types[t];}
            }
            return;
        }
        toast="Aim at a block";
    }

    @Override public void render() {
        if(Gdx.gl==null)return;
        float dt=Math.min(Gdx.graphics.getDeltaTime(),.033f);
        try {
            if(startupError!=null){drawError();return;}
            if(Gdx.input.isKeyPressed(Input.Keys.W))moveForward=1;else if(Gdx.input.isKeyPressed(Input.Keys.S))moveForward=-1;else if(movePointer<0)moveForward=0;
            if(Gdx.input.isKeyPressed(Input.Keys.A))moveSide=-1;else if(Gdx.input.isKeyPressed(Input.Keys.D))moveSide=1;else if(movePointer<0)moveSide=0;
            if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE))jump();
            Vector3 f=forward(),right=new Vector3(f).crs(Vector3.Y).nor();
            pos.mulAdd(f,moveForward*4.2f*dt).mulAdd(right,moveSide*4.2f*dt);
            vy-=17f*dt;pos.y+=vy*dt;
            int ground=heightAt(Math.round(pos.x),Math.round(pos.z));
            if(pos.y<ground+1.7f){pos.y=ground+1.7f;vy=0;grounded=true;}
            camera.position.set(pos);
            camera.direction.set(MathUtils.sin(yaw)*MathUtils.cos(pitch),MathUtils.sin(pitch),-MathUtils.cos(yaw)*MathUtils.cos(pitch)).nor();
            camera.up.set(Vector3.Y);camera.update();
            Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            Gdx.gl.glClearColor(.48f,.72f,.88f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
            batch.begin(camera);
            for(Block b:blocks.values()) if(b.instance.transform.getTranslation(tmp).dst2(pos)<RENDER_DISTANCE2) batch.render(b.instance,env);
            batch.end();
            hud.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight()));hud.begin();
            float sh=Gdx.graphics.getHeight();
            font.draw(hud,"OUTLAND  ·  "+seed,14,sh-16);
            font.draw(hud,"HP "+health+"   XYZ "+(int)pos.x+" / "+(int)pos.y+" / "+(int)pos.z,14,sh-38);
            font.draw(hud,"[1]Grass "+inventory[0]+" [2]Dirt "+inventory[1]+" [3]Stone "+inventory[2],14,sh-60);
            font.draw(hud,"[4]Wood "+inventory[3]+" [5]Leaves "+inventory[4]+"  Selected: "+types[selected],14,sh-82);
            font.draw(hud,toast,14,sh-106);
            font.draw(hud,"MOVE: left · LOOK: right drag",14,22);
            font.draw(hud,"MINE     PLACE     NEXT     JUMP",Gdx.graphics.getWidth()*.50f,22);
            hud.end();
        } catch(Throwable t) {
            startupError=t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());
            Gdx.app.error(TAG,"Render failed",t);
            try{drawError();}catch(Throwable ignored){}
        }
    }
    private void drawError(){
        if(hud==null||font==null)return;
        Gdx.gl.glClearColor(.08f,.08f,.10f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        hud.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight()));
        hud.begin();font.draw(hud,"OUTLAND SAFE MODE",24,Gdx.graphics.getHeight()-35);
        font.draw(hud,"Startup/render error:",24,Gdx.graphics.getHeight()-75);
        font.draw(hud,String.valueOf(startupError),24,Gdx.graphics.getHeight()-105);hud.end();
    }
    @Override public void resize(int w,int h){if(camera!=null){camera.viewportWidth=Math.max(1,w);camera.viewportHeight=Math.max(1,h);camera.update();}}
    @Override public void dispose(){
        try{if(batch!=null)batch.dispose();if(hud!=null)hud.dispose();if(font!=null)font.dispose();for(Model m:models)if(m!=null)m.dispose();}catch(Throwable t){if(Gdx.app!=null)Gdx.app.error(TAG,"Dispose failed",t);}
    }
}

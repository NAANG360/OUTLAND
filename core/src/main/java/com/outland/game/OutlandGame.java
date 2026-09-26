package com.outland.game;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.*;
import com.outland.game.diagnostics.RuntimeDiagnostics;
import com.outland.game.engine.EngineConfig;
import com.outland.game.engine.FrameClock;
import com.outland.game.input.InputState;
import com.outland.game.player.*;
import com.outland.game.render.WorldRenderer;
import com.outland.game.save.WorldSave;
import com.outland.game.ui.HudRenderer;
import com.outland.game.world.*;
import java.io.*;

/** Composition root. Owns subsystem order; individual systems own their resources/state. */
public final class OutlandGame extends ApplicationAdapter {
    private static final String TAG="OUTLAND";
    private final PlayerState player=new PlayerState();
    private final PlayerController playerController=new PlayerController();
    private final InputState input=new InputState();
    private final WorldRenderer worldRenderer=new WorldRenderer();
    private final HudRenderer hud=new HudRenderer();
    private final FrameClock frameClock=new FrameClock();
    private World world;
    private PerspectiveCamera camera;
    private long seed;
    private String status="Booting OUTLAND";
    private String fatalMessage;
    private int movePointer=-1, lookPointer=-1;
    private float moveOriginX,moveOriginY,lastLookX,lastLookY;
    private int startupFrames = 0;

    @Override public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        stage("bootstrap.begin");
        try {
            stage("hud.create"); hud.create();
            loadOrGenerateWorld();
            stage("camera.create");
            camera=new PerspectiveCamera(EngineConfig.CAMERA_FOV,Math.max(1,Gdx.graphics.getWidth()),Math.max(1,Gdx.graphics.getHeight()));
            camera.near=EngineConfig.CAMERA_NEAR; camera.far=EngineConfig.CAMERA_FAR;
            stage("renderer.create"); worldRenderer.create();
            stage("input.install"); installInput();
            stage("bootstrap.complete blocks="+world.size());
        } catch(Throwable failure) {
            fatalMessage=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());
            logError("Bootstrap failed at "+status,failure);
        }
    }

    private void loadOrGenerateWorld(){
        File saveFile=Gdx.files.local("saves/world.dat").file();
        if(saveFile.isFile()){
            stage("save.load");
            try(FileInputStream in=new FileInputStream(saveFile)){
                WorldSave.Snapshot snapshot=WorldSave.read(in);
                world=snapshot.world;copyPlayer(snapshot.player);seed=world.seed();
                status="Loaded saved world";return;
            }catch(Throwable failure){logError("Save load failed; generating a fresh world",failure);}
        }
        seed=System.currentTimeMillis()&0x7fffffffL;
        stage("world.generate");world=new TerrainGenerator().generate(seed);
        player.position.set(0,TerrainGenerator.heightAt(0,0,seed)+2.2f,0);
        status="New world generated";
    }

    private void copyPlayer(PlayerState source){
        player.position.set(source.position);player.yaw=source.yaw;player.pitch=source.pitch;
        player.verticalVelocity=source.verticalVelocity;player.grounded=source.grounded;
        player.health=source.health;player.selectedBlock=MathUtils.clamp(source.selectedBlock,0,BlockType.values().length-1);
        System.arraycopy(source.inventory,0,player.inventory,0,Math.min(source.inventory.length,player.inventory.length));
    }

    private void saveWorld(){
        if(world==null)return;
        try{
            File file=Gdx.files.local("saves/world.dat").file();
            File parent=file.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Could not create save directory");
            try(FileOutputStream out=new FileOutputStream(file,false)){WorldSave.write(out,world,player,world.seed());out.getFD().sync();}
            RuntimeDiagnostics.record(status,"world save committed: "+file.getAbsolutePath(),null);
        }catch(Throwable failure){logError("World save failed",failure);}
    }

    private void stage(String value){status=value;Gdx.app.log(TAG,"stage="+value);RuntimeDiagnostics.record(value,"lifecycle",null);}
    private void logError(String message,Throwable error){
        RuntimeDiagnostics.record(status,message,error);
        if(Gdx.app!=null)Gdx.app.error(TAG,message,error);
    }

    private void installInput(){
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean keyDown(int key){
                if(key>=Input.Keys.NUM_1&&key<=Input.Keys.NUM_7)
                    player.selectedBlock=MathUtils.clamp(key-Input.Keys.NUM_1,0,BlockType.values().length-1);
                if(key==Input.Keys.SPACE)input.jump=true;
                if(key==Input.Keys.F)input.mine=true;
                if(key==Input.Keys.G)input.place=true;
                return true;
            }
            @Override public boolean touchDown(int x,int y,int pointer,int button){
                // HUD uses an upward-positive orthographic Y axis; Android touch input is
                // downward-positive. Convert once here so hitboxes and visuals share a space.
                float width=Math.max(1,Gdx.graphics.getWidth()),height=Math.max(1,Gdx.graphics.getHeight());
                float hudY=height-y;
                float scale=Math.max(.72f,Math.min(width,height)/800f);
                float bx=width-82f*scale,by=78f*scale,r=30f*scale,gap=72f*scale;
                float slot=48f*scale,slotGap=5f*scale;
                float hotbarWidth=BlockType.values().length*slot+(BlockType.values().length-1)*slotGap;
                float hotbarX=(width-hotbarWidth)*.5f,hotbarY=20f*scale;
                // Hit the same four circles HudRenderer draws; do not approximate them with
                // normalized screen bands, which made both columns overlap on narrow phones.
                float dx= x-(bx-gap),dy=hudY-(by+gap);
                if(dx*dx+dy*dy<=r*r){input.nextBlock=true;return true;}
                dx=x-bx;dy=hudY-(by+gap);
                if(dx*dx+dy*dy<=r*r){input.jump=true;return true;}
                dx=x-(bx-gap);dy=hudY-by;
                if(dx*dx+dy*dy<=r*r){input.mine=true;return true;}
                dx=x-bx;dy=hudY-by;
                if(dx*dx+dy*dy<=r*r){input.place=true;return true;}
                if(hudY>=hotbarY-2f*scale&&hudY<=hotbarY+slot+2f*scale&&x>=hotbarX-2f*scale&&x<=hotbarX+hotbarWidth+2f*scale){
                    int selected=MathUtils.floor((x-hotbarX)/(slot+slotGap));
                    if(selected>=0&&selected<BlockType.values().length){
                        float local=x-(hotbarX+selected*(slot+slotGap));
                        if(local<=slot){player.selectedBlock=selected;return true;}
                    }
                }
                if(x>width*.42f){lookPointer=pointer;lastLookX=x;lastLookY=y;}
                else {movePointer=pointer;moveOriginX=x;moveOriginY=hudY;input.forward=0;input.strafe=0;}
                return true;
            }
            @Override public boolean touchDragged(int x,int y,int pointer){
                if(pointer==lookPointer){input.lookX+=x-lastLookX;input.lookY+=y-lastLookY;lastLookX=x;lastLookY=y;}
                if(pointer==movePointer){
                    float height=Math.max(1,Gdx.graphics.getHeight());
                    float hudY=height-y;
                    input.forward=MathUtils.clamp((hudY-moveOriginY)/100f,-1,1);
                    input.strafe=MathUtils.clamp((x-moveOriginX)/100f,-1,1);
                }
                return true;
            }
            @Override public boolean touchUp(int x,int y,int pointer,int button){
                if(pointer==movePointer){movePointer=-1;input.forward=input.strafe=0;}
                if(pointer==lookPointer)lookPointer=-1;
                return true;
            }
            @Override public boolean touchCancelled(int x,int y,int pointer,int button){
                movePointer=lookPointer=-1;input.forward=input.strafe=input.lookX=input.lookY=0;return true;
            }
        });
    }

    @Override public void render(){
        if(Gdx.gl==null)return;
        if(startupFrames < 2){
            if(startupFrames == 0) Gdx.app.log(TAG, "stage=render.first_frame.enter");
            Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            Gdx.gl.glClearColor(.10f,.16f,.20f,1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            Gdx.gl.glFlush();
            startupFrames++;
            if(startupFrames == 2) Gdx.app.log(TAG, "stage=render.first_frame.complete");
            return;
        }
        float dt=Math.min(Gdx.graphics.getDeltaTime(),.033f);
        try{
            if(fatalMessage!=null){drawSafeMode();return;}
            if(Gdx.input.isKeyPressed(Input.Keys.W))input.forward=1;
            else if(Gdx.input.isKeyPressed(Input.Keys.S))input.forward=-1;
            else if(movePointer<0)input.forward=0;
            if(Gdx.input.isKeyPressed(Input.Keys.A))input.strafe=-1;
            else if(Gdx.input.isKeyPressed(Input.Keys.D))input.strafe=1;
            else if(movePointer<0)input.strafe=0;
            if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE))input.jump=true;
            if(input.nextBlock){player.selectedBlock=(player.selectedBlock+1)%BlockType.values().length;input.nextBlock=false;}
            final boolean mine=input.mine, place=input.place;
            frameClock.advance(dt,new FrameClock.Step(){
                @Override public void tick(float step){
                    playerController.update(player,input,step,world);
                }
            });
            if(mine)interact(false);
            if(place)interact(true);
            input.clearTransient();
            camera.position.set(player.position);
            camera.direction.set(MathUtils.sin(player.yaw)*MathUtils.cos(player.pitch),MathUtils.sin(player.pitch),-MathUtils.cos(player.yaw)*MathUtils.cos(player.pitch)).nor();
            camera.up.set(Vector3.Y);camera.viewportWidth=Math.max(1,Gdx.graphics.getWidth());camera.viewportHeight=Math.max(1,Gdx.graphics.getHeight());camera.update();
            Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            Gdx.gl.glClearColor(.60f,.70f,.73f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
            worldRenderer.render(world,camera,player.position);
            String[] lines={
                "OUTLAND · "+seed,
                "HP "+player.health+"   XYZ "+(int)player.position.x+" / "+(int)player.position.y+" / "+(int)player.position.z,
                "1 Grass "+player.inventory[BlockType.GRASS.id()]+"   2 Dirt "+player.inventory[BlockType.DIRT.id()]+"   3 Stone "+player.inventory[BlockType.STONE.id()],
                "4 Wood "+player.inventory[BlockType.WOOD.id()]+"   5 Leaves "+player.inventory[BlockType.LEAVES.id()]+"   6 Cactus "+player.inventory[BlockType.CACTUS.id()]+"   7 Uranium "+player.inventory[BlockType.URANIUM.id()],
                "Selected: "+BlockType.values()[player.selectedBlock],
                status,"MOVE: left · LOOK: right drag"};
            hud.render(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),lines,player.selectedBlock,player.inventory);
        }catch(Throwable failure){
            fatalMessage=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());
            logError("Runtime failure",failure);
        }
    }

    private void interact(boolean place){
        Vector3 direction=new Vector3(MathUtils.sin(player.yaw)*MathUtils.cos(player.pitch),MathUtils.sin(player.pitch),-MathUtils.cos(player.yaw)*MathUtils.cos(player.pitch)).nor();
        int lastX=Math.round(player.position.x),lastY=Math.round(player.position.y),lastZ=Math.round(player.position.z);
        for(float distance=.5f;distance<5.5f;distance+=.12f){
            Vector3 point=new Vector3(player.position).mulAdd(direction,distance);
            int x=Math.round(point.x),y=Math.round(point.y),z=Math.round(point.z);
            World.Block block=world.getBlock(x,y,z);
            if(block==null){lastX=x;lastY=y;lastZ=z;continue;}
            if(!place){
                World.Block removed=world.removeBlock(x,y,z);
                if(removed!=null){player.inventory[removed.type.id()]++;status="Mined "+removed.type;}
            }else{
                int selected=player.selectedBlock;
                if(player.inventory[selected]<=0){status="No "+BlockType.values()[selected];return;}
                // Place into the empty voxel immediately before the surface hit.
                int bx=lastX,by=lastY,bz=lastZ;
                if(Math.abs(bx-player.position.x)<1.0f&&Math.abs(by-player.position.y)<1.0f&&Math.abs(bz-player.position.z)<1.0f){
                    status="Too close to place";return;
                }
                if(world.setBlock(bx,by,bz,BlockType.values()[selected])){
                    player.inventory[selected]--;status="Placed "+BlockType.values()[selected];
                } else {
                    status="Can't place there";
                }
            }
            return;
        }
        status="Aim at a block";
    }

    private void drawSafeMode(){
        Gdx.gl.glClearColor(.08f,.08f,.10f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        hud.render(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),new String[]{"OUTLAND SAFE MODE","Failure at: "+status,String.valueOf(fatalMessage),"Private diagnostic journal: app files/outlandlogs/runtime.log"},player.selectedBlock,player.inventory);
    }
    @Override public void resize(int width,int height){if(camera!=null){camera.viewportWidth=Math.max(1,width);camera.viewportHeight=Math.max(1,height);camera.update();}}
    @Override public void pause(){stage("lifecycle.pause");saveWorld();}
    @Override public void resume(){stage("lifecycle.resume");}
    @Override public void dispose(){
        saveWorld();stage("lifecycle.dispose");
        try{hud.dispose();}catch(Throwable t){logError("HUD dispose failed",t);}
        try{worldRenderer.dispose();}catch(Throwable t){logError("Renderer dispose failed",t);}
    }
}

package com.outland.game;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.*;
import com.outland.game.diagnostics.RuntimeDiagnostics;
import com.outland.game.input.InputState;
import com.outland.game.player.*;
import com.outland.game.render.WorldRenderer;
import com.outland.game.ui.HudRenderer;
import com.outland.game.world.*;

/** Composition root. Owns subsystem order; individual systems own their resources/state. */
public final class OutlandGame extends ApplicationAdapter {
    private static final String TAG="OUTLAND";
    private final PlayerState player=new PlayerState();
    private final PlayerController playerController=new PlayerController();
    private final InputState input=new InputState();
    private final WorldRenderer worldRenderer=new WorldRenderer();
    private final HudRenderer hud=new HudRenderer();
    private World world;
    private PerspectiveCamera camera;
    private long seed;
    private String status="Booting OUTLAND";
    private String fatalMessage;
    private int movePointer=-1, lookPointer=-1;
    private float moveOriginX,moveOriginY,lastLookX,lastLookY;

    @Override public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        stage("bootstrap.begin");
        try {
            seed=System.currentTimeMillis()&0x7fffffffL;
            stage("world.generate");
            world=new TerrainGenerator().generate(seed);
            player.position.set(0,TerrainGenerator.heightAt(0,0,seed)+2.2f,0);
            stage("camera.create");
            camera=new PerspectiveCamera(70,Math.max(1,Gdx.graphics.getWidth()),Math.max(1,Gdx.graphics.getHeight()));
            camera.near=.1f; camera.far=60f;
            stage("renderer.create"); worldRenderer.create();
            stage("hud.create"); hud.create();
            stage("input.install"); installInput();
            status="World ready · tap MINE / PLACE";
            stage("bootstrap.complete blocks="+world.size());
        } catch(Throwable failure) {
            fatalMessage=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());
            logError("Bootstrap failed at "+status,failure);
        }
    }

    private void stage(String value){status=value;Gdx.app.log(TAG,"stage="+value);RuntimeDiagnostics.record(value,"lifecycle",null);}
    private void logError(String message,Throwable error){
        RuntimeDiagnostics.record(status,message,error);
        if(Gdx.app!=null)Gdx.app.error(TAG,message,error);
    }

    private void installInput(){
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean keyDown(int key){
                if(key>=Input.Keys.NUM_1&&key<=Input.Keys.NUM_5)player.selectedBlock=key-Input.Keys.NUM_1;
                if(key==Input.Keys.SPACE)input.jump=true;
                if(key==Input.Keys.F)input.mine=true;
                if(key==Input.Keys.G)input.place=true;
                return true;
            }
            @Override public boolean touchDown(int x,int y,int pointer,int button){
                float nx=x/(float)Math.max(1,Gdx.graphics.getWidth()), ny=y/(float)Math.max(1,Gdx.graphics.getHeight());
                if(ny>.78f&&nx>.48f){
                    if(nx>.84f)input.jump=true;
                    else if(nx>.72f)input.nextBlock=true;
                    else if(nx>.60f)input.place=true;
                    else input.mine=true;
                    return true;
                }
                if(nx>.42f){lookPointer=pointer;lastLookX=x;lastLookY=y;}
                else {movePointer=pointer;moveOriginX=x;moveOriginY=y;input.forward=ny>.55f?1:-1;input.strafe=nx<.19f?-1:(nx>.29f?1:0);}
                return true;
            }
            @Override public boolean touchDragged(int x,int y,int pointer){
                if(pointer==lookPointer){input.lookX+=x-lastLookX;input.lookY+=y-lastLookY;lastLookX=x;lastLookY=y;}
                if(pointer==movePointer){input.forward=MathUtils.clamp((moveOriginY-y)/100f,-1,1);input.strafe=MathUtils.clamp((x-moveOriginX)/100f,-1,1);}
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
            playerController.update(player,input,dt,seed);
            if(input.mine)interact(false);
            if(input.place)interact(true);
            input.clearTransient();
            camera.position.set(player.position);
            camera.direction.set(MathUtils.sin(player.yaw)*MathUtils.cos(player.pitch),MathUtils.sin(player.pitch),-MathUtils.cos(player.yaw)*MathUtils.cos(player.pitch)).nor();
            camera.up.set(Vector3.Y);camera.viewportWidth=Math.max(1,Gdx.graphics.getWidth());camera.viewportHeight=Math.max(1,Gdx.graphics.getHeight());camera.update();
            Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            Gdx.gl.glClearColor(.48f,.72f,.88f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
            worldRenderer.render(world,camera,player.position);
            String[] lines={"OUTLAND · "+seed,"HP "+player.health+"   XYZ "+(int)player.position.x+" / "+(int)player.position.y+" / "+(int)player.position.z,
                "[1]Grass "+player.inventory[0]+" [2]Dirt "+player.inventory[1]+" [3]Stone "+player.inventory[2],
                "[4]Wood "+player.inventory[3]+" [5]Leaves "+player.inventory[4]+" Selected: "+BlockType.values()[player.selectedBlock],
                status,"MOVE: left · LOOK: right drag","MINE       PLACE       NEXT       JUMP"};
            hud.render(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),lines);
        }catch(Throwable failure){
            fatalMessage=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());
            logError("Runtime failure",failure);
        }
    }

    private void interact(boolean place){
        Vector3 direction=new Vector3(MathUtils.sin(player.yaw)*MathUtils.cos(player.pitch),MathUtils.sin(player.pitch),-MathUtils.cos(player.yaw)*MathUtils.cos(player.pitch)).nor();
        for(float distance=.5f;distance<5.5f;distance+=.2f){
            Vector3 point=new Vector3(player.position).mulAdd(direction,distance);
            int x=Math.round(point.x),y=Math.round(point.y),z=Math.round(point.z);
            World.Block block=world.getBlock(x,y,z);if(block==null)continue;
            if(!place){world.removeBlock(x,y,z);player.inventory[block.type.id()]++;status="Mined "+block.type;}
            else{
                int selected=player.selectedBlock;
                if(player.inventory[selected]<=0){status="No "+BlockType.values()[selected];return;}
                Vector3 target=new Vector3(point).mulAdd(direction,-.65f);
                int bx=Math.round(target.x),by=Math.round(target.y),bz=Math.round(target.z);
                if(Math.abs(bx-player.position.x)<1.2f&&Math.abs(bz-player.position.z)<1.2f)return;
                if(world.setBlock(bx,by,bz,BlockType.values()[selected])){player.inventory[selected]--;status="Placed "+BlockType.values()[selected];}
            }
            return;
        }
        status="Aim at a block";
    }

    private void drawSafeMode(){
        Gdx.gl.glClearColor(.08f,.08f,.10f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        hud.render(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),new String[]{"OUTLAND SAFE MODE","Failure at: "+status,String.valueOf(fatalMessage),"Private diagnostic journal: app files/outlandlogs/runtime.log"});
    }
    @Override public void resize(int width,int height){if(camera!=null){camera.viewportWidth=Math.max(1,width);camera.viewportHeight=Math.max(1,height);camera.update();}}
    @Override public void pause(){stage("lifecycle.pause");}
    @Override public void resume(){stage("lifecycle.resume");}
    @Override public void dispose(){
        stage("lifecycle.dispose");
        try{hud.dispose();}catch(Throwable t){logError("HUD dispose failed",t);}
        try{worldRenderer.dispose();}catch(Throwable t){logError("Renderer dispose failed",t);}
    }
}

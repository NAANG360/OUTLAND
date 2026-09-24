package com.outland.game;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import java.util.*;

/**
 * OUTLAND native voxel survival foundation.
 * Procedural seeded terrain, first-person camera, touch look/move, mining/placing,
 * hotbar selection and lightweight survival stats.
 */
public class OutlandGame extends ApplicationAdapter {
    private PerspectiveCamera camera;
    private ModelBatch batch;
    private ModelBuilder builder;
    private Environment env;
    private final Map<Long, Block> blocks = new HashMap<>();
    private final Array<ModelInstance> visible = new Array<>();
    private final Random rng = new Random();
    private long seed;
    private int selected = 0, health = 100, wood = 0;
    private final String[] types = {"Grass","Dirt","Stone","Wood","Leaves"};
    private final int[] inventory = {24,16,0,0,0};
    private final Vector3 pos = new Vector3(0,12,0);
    private float yaw=0, pitch=0, vy=0, moveSpeed=5f;
    private boolean grounded=false;
    private Model[] models = new Model[5];
    private Material[] materials = new Material[5];
    private float touchX, touchY;
    private boolean draggingLook=false;
    private int movePointer=-1;
    private float moveForward, moveSide;
    private BitmapFont font;
    private SpriteBatch hud;
    private String toast="Gather resources. Survive.";

    private static class Block {
        int x,y,z,type; ModelInstance instance;
        Block(int x,int y,int z,int type,ModelInstance i){this.x=x;this.y=y;this.z=z;this.type=type;instance=i;}
    }
    private long key(int x,int y,int z){return (((long)(x+2048)&4095)<<24)|(((long)(y+512)&1023)<<14)|((z+2048)&4095);}
    @Override public void create(){
        seed=System.currentTimeMillis() & 0x7fffffff; rng.setSeed(seed);
        batch=new ModelBatch(); builder=new ModelBuilder(); hud=new SpriteBatch();font=new BitmapFont();
        env=new Environment();env.set(new ColorAttribute(ColorAttribute.AmbientLight,0.82f,0.84f,0.88f,1));
        env.add(new DirectionalLight().set(0.95f,0.91f,0.78f,-1,-2,-0.5f));
        int[] colors={0x68a94fff,0x8a603fff,0x858b91ff,0x79502fff,0x438443ff};
        for(int i=0;i<5;i++){materials[i]=new Material(ColorAttribute.createDiffuse(new Color(colors[i])));models[i]=builder.createBox(1,1,1,materials[i],VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal);}
        camera=new PerspectiveCamera(72,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());camera.near=.1f;camera.far=100;
        generateWorld();pos.set(0,heightAt(0,0)+2.5f,0);camera.position.set(pos);
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean keyDown(int k){if(k>=Input.Keys.NUM_1&&k<=Input.Keys.NUM_5)selected=k-Input.Keys.NUM_1;if(k==Input.Keys.SPACE)jump();return true;}
            @Override public boolean touchDown(int x,int y,int p,int b){if(x>Gdx.graphics.getWidth()*.42f){draggingLook=true;touchX=x;touchY=y;}else{movePointer=p;moveForward=y>Gdx.graphics.getHeight()*.55f?1:-1;moveSide=x<Gdx.graphics.getWidth()*.18f?-1:(x>Gdx.graphics.getWidth()*.32f?1:0);}return true;}
            @Override public boolean touchDragged(int x,int y,int p){if(draggingLook){yaw-=(x-touchX)*.004f;pitch=MathUtils.clamp(pitch-(y-touchY)*.004f,-1.35f,1.35f);touchX=x;touchY=y;}return true;}
            @Override public boolean touchUp(int x,int y,int p,int b){if(p==movePointer){movePointer=-1;moveForward=moveSide=0;}draggingLook=false;return true;}
            @Override public boolean touchCancelled(int x,int y,int p,int b){moveForward=moveSide=0;draggingLook=false;return true;}
        });
    }
    private int heightAt(int x,int z){double n=Math.sin((x+seed%97)*.13)*1.7+Math.cos((z-seed%53)*.11)*1.6+Math.sin((x+z)*.07)*2.2;return 4+(int)Math.round(n);}
    private void generateWorld(){
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            int h=heightAt(x,z);
            for(int y=-3;y<=h;y++){int t=y==-3?2:(y==h?0:(y>h-3?1:2));addBlock(x,y,z,t);}
            if(h>4&&rng.nextFloat()>.985f)makeTree(x,h+1,z);
        }
    }
    private void makeTree(int x,int y,int z){for(int i=0;i<4;i++)addBlock(x,y+i,z,3);for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=2;dy<=5;dy++)if(Math.abs(dx)+Math.abs(dz)+(dy==5?1:0)<5)addBlock(x+dx,y+dy,z+dz,4);}
    private void addBlock(int x,int y,int z,int type){long k=key(x,y,z);if(blocks.containsKey(k))return;ModelInstance i=new ModelInstance(models[type]);i.transform.setToTranslation(x,y,z);blocks.put(k,new Block(x,y,z,type,i));}
    private void remove(Block b){blocks.remove(key(b.x,b.y,b.z));}
    private void jump(){if(grounded){vy=7;grounded=false;}}
    private Vector3 forward(){return new Vector3(MathUtils.sin(yaw),0,-MathUtils.cos(yaw)).nor();}
    private void interact(boolean place){
        Vector3 dir=new Vector3(MathUtils.sin(yaw)*MathUtils.cos(pitch),MathUtils.sin(pitch),-MathUtils.cos(yaw)*MathUtils.cos(pitch)).nor();
        for(float d=.5f;d<6;d+=.25f){Vector3 p=new Vector3(pos).mulAdd(dir,d);int x=Math.round(p.x),y=Math.round(p.y),z=Math.round(p.z);Block b=blocks.get(key(x,y,z));
            if(b!=null){if(!place){remove(b);inventory[b.type]++;toast="Mined "+types[b.type];}
                else {int t=selected;if(inventory[t]<=0){toast="No "+types[t];return;}Vector3 q=new Vector3(p).mulAdd(dir,-.55f);int bx=Math.round(q.x),by=Math.round(q.y),bz=Math.round(q.z);if(Math.abs(bx-pos.x)<1&&Math.abs(bz-pos.z)<1)return;addBlock(bx,by,bz,t);inventory[t]--;toast="Placed "+types[t];}return;}
        }
    }
    @Override public void render(){
        float dt=Math.min(Gdx.graphics.getDeltaTime(),.04f);
        if(Gdx.input.isKeyPressed(Input.Keys.W))moveForward=1;else if(Gdx.input.isKeyPressed(Input.Keys.S))moveForward=-1;else if(movePointer<0)moveForward=0;
        if(Gdx.input.isKeyPressed(Input.Keys.A))moveSide=-1;else if(Gdx.input.isKeyPressed(Input.Keys.D))moveSide=1;else if(movePointer<0)moveSide=0;
        if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE))jump();
        Vector3 f=forward(),right=new Vector3(f).crs(Vector3.Y).nor();
        pos.mulAdd(f,moveForward*moveSpeed*dt).mulAdd(right,moveSide*moveSpeed*dt);
        vy-=18*dt;pos.y+=vy*dt;int ground=heightAt(Math.round(pos.x),Math.round(pos.z));
        if(pos.y<ground+1.8f){pos.y=ground+1.8f;vy=0;grounded=true;}
        camera.position.set(pos);camera.direction.set(MathUtils.sin(yaw)*MathUtils.cos(pitch),MathUtils.sin(pitch),-MathUtils.cos(yaw)*MathUtils.cos(pitch)).nor();camera.up.set(Vector3.Y);camera.update();
        Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());Gdx.gl.glClearColor(.48f,.72f,.88f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
        batch.begin(camera);for(Block b:blocks.values())if(b.instance.transform.getTranslation(tmp).dst2(pos)<48*48)batch.render(b.instance,env);batch.end();
        hud.setProjectionMatrix(new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight()));hud.begin();
        font.draw(hud,"OUTLAND  |  seed "+seed,18,Gdx.graphics.getHeight()-20);
        font.draw(hud,"HP "+health+"    Wood "+inventory[3],18,Gdx.graphics.getHeight()-44);
        font.draw(hud,"XYZ "+(int)pos.x+" / "+(int)pos.y+" / "+(int)pos.z,18,Gdx.graphics.getHeight()-66);
        font.draw(hud,"[1]Grass "+inventory[0]+" [2]Dirt "+inventory[1]+" [3]Stone "+inventory[2]+" [4]Wood "+inventory[3]+" [5]Leaves "+inventory[4],18,28);
        font.draw(hud,toast+"   |   Mine: tap MINE area · Place: PLACE area",18,50);
        font.draw(hud,"MOVE ◀ ▶ ▲ ▼     LOOK: drag right side     JUMP: Space",18,75);
        hud.end();
    }
    private final Vector3 tmp=new Vector3();
    @Override public void resize(int w,int h){camera.viewportWidth=w;camera.viewportHeight=h;camera.update();}
    @Override public void dispose(){batch.dispose();hud.dispose();font.dispose();for(Model m:models)if(m!=null)m.dispose();}
}

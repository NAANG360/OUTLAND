package com.outland.game.render;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.outland.game.world.*;
import java.util.*;

/**
 * Wasteland renderer: voxel-compatible storage/collision with organic prop silhouettes.
 * Static terrain is merged into small ModelCache chunks so Android is not asked to
 * submit one ModelInstance per block every frame.
 */
public final class WorldRenderer {
    private static final int CHUNK_SIZE=8;
    private static final float RENDER_RADIUS=42f;

    private final Model[] models=new Model[BlockType.values().length];
    private final Map<Long,Chunk> chunks=new HashMap<>();
    private final Vector3 scratch=new Vector3();
    private ModelBatch batch;
    private Environment environment;
    private long cachedRevision=-1;
    private boolean created;

    private static final class Chunk {
        final int cx,cz;
        final ModelCache cache;
        final Vector3 center;
        Chunk(int cx,int cz){
            this.cx=cx;this.cz=cz;
            this.cache=new ModelCache();
            this.center=new Vector3(cx*CHUNK_SIZE+CHUNK_SIZE*.5f,0,cz*CHUNK_SIZE+CHUNK_SIZE*.5f);
        }
        void build(Array<ModelInstance> instances){
            cache.begin();
            cache.add(instances);
            cache.end();
        }
        void dispose(){cache.dispose();}
    }

    public void create(){
        if(created)return;
        ModelBuilder builder=new ModelBuilder();
        int[] colors={0x68a94fff,0x8a603fff,0x858b91ff,0x79502fff,0x438443ff,0x4f9f54ff,0x77a96aff};
        try{
            long attrs=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal;
            models[BlockType.GRASS.id()]=builder.createBox(1,1,1,new Material(ColorAttribute.createDiffuse(new Color(colors[0]))),attrs);
            models[BlockType.DIRT.id()]=builder.createBox(1,1,1,new Material(ColorAttribute.createDiffuse(new Color(colors[1]))),attrs);
            models[BlockType.STONE.id()]=builder.createBox(1,1,1,new Material(ColorAttribute.createDiffuse(new Color(colors[2]))),attrs);
            models[BlockType.WOOD.id()]=builder.createCylinder(.22f,1f,.22f,8,new Material(ColorAttribute.createDiffuse(new Color(colors[3]))),attrs);
            models[BlockType.LEAVES.id()]=builder.createSphere(.78f,.78f,.78f,8,5,new Material(ColorAttribute.createDiffuse(new Color(colors[4]))),attrs);
            models[BlockType.CACTUS.id()]=builder.createCylinder(.18f,1f,.18f,10,new Material(ColorAttribute.createDiffuse(new Color(colors[5]))),attrs);
            models[BlockType.URANIUM.id()]=builder.createSphere(1.05f,.85f,.92f,7,4,new Material(ColorAttribute.createDiffuse(new Color(colors[6]))),attrs);
            batch=new ModelBatch();environment=new Environment();
            environment.set(new ColorAttribute(ColorAttribute.AmbientLight,.38f,.40f,.43f,1f));
            environment.add(new DirectionalLight().set(.55f,.52f,.46f,-1f,-2f,-.5f));
            created=true;
        }catch(Throwable failure){dispose();throw new IllegalStateException("Renderer initialization failed",failure);}
    }

    public void render(World world,Camera camera,Vector3 playerPosition){
        if(!created)throw new IllegalStateException("WorldRenderer not created");
        sync(world);
        batch.begin(camera);
        try{
            for(Chunk chunk:chunks.values()){
                scratch.set(chunk.center.x,0,chunk.center.z);
                float dx=scratch.x-playerPosition.x,dz=scratch.z-playerPosition.z;
                if(dx*dx+dz*dz>RENDER_RADIUS*RENDER_RADIUS)continue;
                // ModelBatch does not perform frustum culling itself; reject whole chunks first.
                scratch.y=playerPosition.y;
                if(!camera.frustum.sphereInFrustum(scratch,CHUNK_SIZE*.9f))continue;
                batch.render(chunk.cache,environment);
            }
        }finally{batch.end();}
    }

    private void sync(World world){
        if(cachedRevision==world.revision())return;
        for(Chunk chunk:chunks.values())chunk.dispose();
        chunks.clear();

        Map<Long,Array<ModelInstance>> pending=new HashMap<>();
        for(World.Block b:world.snapshot()){
            // Underground layers are retained for collision/mining but are not submitted
            // until exposed. This keeps the initial scene focused on the actual surface.
            boolean base=b.type==BlockType.GRASS||b.type==BlockType.DIRT||b.type==BlockType.STONE;
            if(base&&isFullyEnclosed(world,b))continue;

            int cx=Math.floorDiv(b.x,CHUNK_SIZE),cz=Math.floorDiv(b.z,CHUNK_SIZE);
            long chunkKey=chunkKey(cx,cz);
            Array<ModelInstance> list=pending.get(chunkKey);
            if(list==null){list=new Array<>();pending.put(chunkKey,list);}
            ModelInstance instance=new ModelInstance(models[b.type.id()]);
            if(b.type==BlockType.CACTUS||b.type==BlockType.URANIUM||b.type==BlockType.LEAVES){
                float s=.88f+((int)(Math.abs(World.key(b.x,b.y,b.z))%17))/100f;
                instance.transform.setToTranslation(b.x,b.y,b.z).scale(s,s,s);
            }else instance.transform.setToTranslation(b.x,b.y,b.z);
            list.add(instance);
        }

        for(Map.Entry<Long,Array<ModelInstance>> entry:pending.entrySet()){
            int cx=(int)(entry.getKey()>>32),cz=(int)(long)entry.getKey();
            Chunk chunk=new Chunk(cx,cz);
            chunk.build(entry.getValue());
            chunks.put(entry.getKey(),chunk);
        }
        cachedRevision=world.revision();
    }

    private static boolean isFullyEnclosed(World world,World.Block b){
        return world.getBlock(b.x,b.y+1,b.z)!=null
                &&world.getBlock(b.x,b.y-1,b.z)!=null
                &&world.getBlock(b.x+1,b.y,b.z)!=null
                &&world.getBlock(b.x-1,b.y,b.z)!=null
                &&world.getBlock(b.x,b.y,b.z+1)!=null
                &&world.getBlock(b.x,b.y,b.z-1)!=null;
    }

    private static long chunkKey(int cx,int cz){return ((long)cx<<32)^(cz&0xffffffffL);}

    public void dispose(){
        ModelBatch oldBatch=batch;batch=null;if(oldBatch!=null)try{oldBatch.dispose();}catch(Throwable ignored){}
        for(Chunk chunk:chunks.values())chunk.dispose();
        chunks.clear();
        for(int i=0;i<models.length;i++){Model model=models[i];models[i]=null;if(model!=null)try{model.dispose();}catch(Throwable ignored){}}
        environment=null;created=false;cachedRevision=-1;
    }
}

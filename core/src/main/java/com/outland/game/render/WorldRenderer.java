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
 * Wasteland renderer: voxel-compatible storage/collision with stylized organic props.
 * Static terrain is merged into small ModelCache chunks so Android is not asked to
 * submit one ModelInstance per block every frame.
 */
public final class WorldRenderer {
    private static final int CHUNK_SIZE=8;
    private static final float RENDER_RADIUS=42f;

    private final Model[] models=new Model[BlockType.values().length];
    private final Map<Long,Chunk> chunks=new HashMap<>();
    private final Map<Long,Map<Long,World.Block>> blocksByChunk=new HashMap<>();
    private final Vector3 scratch=new Vector3();
    private Model treeTrunk,treeBranch,treeLeaf,cactusBody,cactusArm,cactusTip;
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
        void build(Array<ModelInstance> instances){cache.begin();cache.add(instances);cache.end();}
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
            models[BlockType.URANIUM.id()]=builder.createSphere(1.05f,.85f,.92f,7,4,new Material(ColorAttribute.createDiffuse(new Color(colors[6]))),attrs);

            Material trunkMat=new Material(ColorAttribute.createDiffuse(new Color(0x69452fff)));
            Material barkMat=new Material(ColorAttribute.createDiffuse(new Color(0x533521ff)));
            Material leafMat=new Material(ColorAttribute.createDiffuse(new Color(0x39733cff)));
            
            Material cactusMat=new Material(ColorAttribute.createDiffuse(new Color(0x4f9f54ff)));

            // One-piece-ish stylized tree parts. Multiple organic lobes beat the
            // old isolated green balls while staying cheap enough for Android.
            treeTrunk=builder.createCylinder(.30f,3.9f,.30f,9,trunkMat,attrs);
            treeBranch=builder.createCylinder(.15f,1.35f,.15f,8,barkMat,attrs);
            treeLeaf=builder.createSphere(1f,.72f,.92f,8,6,leafMat,attrs);
            cactusBody=builder.createCylinder(.34f,3.5f,.34f,10,cactusMat,attrs);
            cactusArm=builder.createCylinder(.22f,1.05f,.22f,10,cactusMat,attrs);
            cactusTip=builder.createSphere(.36f,.28f,.36f,10,6,cactusMat,attrs);

            // Keep terrain lighting restrained so the ground does not wash out.
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
                scratch.y=playerPosition.y;
                if(!camera.frustum.sphereInFrustum(scratch,CHUNK_SIZE*.9f))continue;
                batch.render(chunk.cache,environment);
            }
        }finally{batch.end();}
    }

    private void sync(World world){
        if(cachedRevision==world.revision())return;

        Long changedKey=world.consumeChangedBlockKey();
        if(cachedRevision<0 || changedKey==null){
            rebuildAll(world);
            cachedRevision=world.revision();
            world.consumeChangedBlockKey();
            return;
        }

        updateBlockIndex(world,changedKey);
        int changedX=World.xFromKey(changedKey), changedZ=World.zFromKey(changedKey);
        int changedCx=Math.floorDiv(changedX,CHUNK_SIZE), changedCz=Math.floorDiv(changedZ,CHUNK_SIZE);

        // Terrain exposure and organic props can cross a chunk edge, so rebuild
        // the changed chunk plus its immediate 8 neighbors only.
        for(int dz=-1;dz<=1;dz++) for(int dx=-1;dx<=1;dx++){
            int cx=changedCx+dx,cz=changedCz+dz;
            long key=chunkKey(cx,cz);
            Chunk old=chunks.remove(key);
            if(old!=null)old.dispose();
            Chunk rebuilt=buildChunk(world,cx,cz);
            if(rebuilt!=null)chunks.put(key,rebuilt);
        }
        cachedRevision=world.revision();
    }

    private void rebuildAll(World world){
        for(Chunk chunk:chunks.values())chunk.dispose();
        chunks.clear();
        blocksByChunk.clear();

        for(World.Block b:world.snapshot()){
            long key=chunkKey(Math.floorDiv(b.x,CHUNK_SIZE),Math.floorDiv(b.z,CHUNK_SIZE));
            Map<Long,World.Block> list=blocksByChunk.get(key);
            if(list==null){list=new HashMap<>();blocksByChunk.put(key,list);}
            list.put(World.key(b.x,b.y,b.z),b);
        }

        for(long key:blocksByChunk.keySet()){
            int cx=(int)(key>>32),cz=(int)key;
            Chunk rebuilt=buildChunk(world,cx,cz);
            if(rebuilt!=null)chunks.put(key,rebuilt);
        }
    }

    private void updateBlockIndex(World world,long changedKey){
        int x=World.xFromKey(changedKey),z=World.zFromKey(changedKey);
        long chunk=chunkKey(Math.floorDiv(x,CHUNK_SIZE),Math.floorDiv(z,CHUNK_SIZE));
        Map<Long,World.Block> list=blocksByChunk.get(chunk);
        World.Block current=world.getBlock(x,World.yFromKey(changedKey),z);
        if(current==null){
            if(list!=null){
                list.remove(changedKey);
                if(list.isEmpty())blocksByChunk.remove(chunk);
            }
        }else{
            if(list==null){list=new HashMap<>();blocksByChunk.put(chunk,list);}
            list.put(changedKey,current);
        }
    }

    private Chunk buildChunk(World world,int cx,int cz){
        Map<Long,Array<ModelInstance>> pending=new HashMap<>();
        long targetKey=chunkKey(cx,cz);

        // Terrain cells belong to their own chunk.
        Map<Long,World.Block> own=blocksByChunk.get(targetKey);
        if(own!=null){
            for(World.Block b:own.values()){
                boolean base=b.type==BlockType.GRASS||b.type==BlockType.DIRT||b.type==BlockType.STONE;
                if(base&&isFullyEnclosed(world,b))continue;
                if(b.type==BlockType.WOOD||b.type==BlockType.LEAVES||b.type==BlockType.CACTUS)continue;
                addInstance(pending,b.type,b.x,b.y,b.z,1f);
            }
        }

        // Props may straddle chunk boundaries. Read only the surrounding 3x3
        // chunk index instead of rescanning all ~17k world blocks per interaction.
        for(int dz=-1;dz<=1;dz++) for(int dx=-1;dx<=1;dx++){
            Map<Long,World.Block> nearby=blocksByChunk.get(chunkKey(cx+dx,cz+dz));
            if(nearby==null)continue;
            for(World.Block b:nearby.values()){
                if(b.type==BlockType.WOOD&&isTreeBase(world,b))addTree(pending,b);
                else if(b.type==BlockType.CACTUS&&isCactusBase(world,b))addCactus(pending,world,b);
            }
        }

        Array<ModelInstance> instances=pending.get(targetKey);
        if(instances==null||instances.size==0)return null;
        Chunk chunk=new Chunk(cx,cz);
        chunk.build(instances);
        return chunk;
    }

    private boolean isTreeBase(World world,World.Block b){
        World.Block below=world.getBlock(b.x,b.y-1,b.z);
        return below==null||below.type!=BlockType.WOOD;
    }

    private boolean isCactusBase(World world,World.Block b){
        World.Block below=world.getBlock(b.x,b.y-1,b.z);
        return below==null||below.type!=BlockType.CACTUS;
    }

    private void addTree(Map<Long,Array<ModelInstance>> pending,World.Block b){
        float seed=Math.abs(World.key(b.x,b.y,b.z)%1000)/1000f;
        float lean=(seed-.5f)*14f;
        addProp(pending,treeTrunk,b.x,b.y+1.45f,b.z,1f,lean,0f,0f);

        addBranch(pending,b.x,b.y+1.9f,b.z,b.x+0.7f,b.y+2.35f,b.z,lean);
        addBranch(pending,b.x,b.y+2.35f,b.z,b.x-0.55f,b.y+2.8f,b.z,lean);
        addBranch(pending,b.x,b.y+2.7f,b.z,b.x,b.y+3.05f,b.z+0.65f,lean);

        addLeaf(pending,b.x,b.y+3.0f,b.z,1.00f);
        addLeaf(pending,b.x+0.72f,b.y+2.7f,b.z+0.05f,.72f);
        addLeaf(pending,b.x-0.62f,b.y+3.0f,b.z+0.10f,.66f);
        addLeaf(pending,b.x+0.10f,b.y+3.15f,b.z-0.68f,.76f);
        addLeaf(pending,b.x-0.05f,b.y+3.55f,b.z+0.05f,.58f);
    }

    private void addBranch(Map<Long,Array<ModelInstance>> pending,float x1,float y1,float z1,float x2,float y2,float z2,float lean){
        float dx=x2-x1,dy=y2-y1,dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        ModelInstance i=new ModelInstance(treeBranch);
        i.transform.setToTranslation((x1+x2)*.5f,(y1+y2)*.5f,(z1+z2)*.5f);
        i.transform.scale(1f,len/1.35f,1f);
        i.transform.rotate(Vector3.Z,(float)Math.toDegrees(Math.atan2(dx,dy)));
        i.transform.rotate(Vector3.X,-(float)Math.toDegrees(Math.atan2(dz,dy)));
        i.transform.rotate(Vector3.Y,lean);
        add(pending,i,(int)Math.floor((x1+x2)*.5f),(int)Math.floor((z1+z2)*.5f));
    }

    private void addLeaf(Map<Long,Array<ModelInstance>> pending,float x,float y,float z,float scale){
        addProp(pending,treeLeaf,x,y,z,scale,0f,0f,0f);
    }

    private void addCactus(Map<Long,Array<ModelInstance>> pending,World world,World.Block b){
        int height=1;
        while(world.getBlock(b.x,b.y+height,b.z)!=null && world.getBlock(b.x,b.y+height,b.z).type==BlockType.CACTUS)height++;
        addProp(pending,cactusBody,b.x,b.y+(height-1)*.5f,b.z,1f,0f,0f,0f);
        // Scale the 3.5-unit source to the actual stored height.
        Array<ModelInstance> list=getList(pending,b.x,b.z);
        ModelInstance body=list.peek();
        body.transform.scale(1f,height/3.5f,1f);

        addProp(pending,cactusTip,b.x,b.y+height-.02f,b.z,1f,0f,0f,0f);
        if(height>=3){
            addProp(pending,cactusArm,b.x+.48f,b.y+1.05f,b.z,1f,0f,0f,90f);
            addProp(pending,cactusTip,b.x+1.0f,b.y+1.05f,b.z,.72f,0f,0f,0f);
        }
        if(height>=4){
            addProp(pending,cactusArm,b.x-.48f,b.y+1.95f,b.z,1f,0f,0f,-90f);
            addProp(pending,cactusTip,b.x-1.0f,b.y+1.95f,b.z,.72f,0f,0f,0f);
        }
    }

    private void addProp(Map<Long,Array<ModelInstance>> pending,Model model,float x,float y,float z,float scale,float rotX,float rotY,float rotZ){
        ModelInstance i=new ModelInstance(model);
        i.transform.setToTranslation(x,y,z).scale(scale,scale,scale);
        if(rotX!=0)i.transform.rotate(Vector3.X,rotX);
        if(rotY!=0)i.transform.rotate(Vector3.Y,rotY);
        if(rotZ!=0)i.transform.rotate(Vector3.Z,rotZ);
        add(pending,i,(int)Math.floor(x),(int)Math.floor(z));
    }

    private Array<ModelInstance> getList(Map<Long,Array<ModelInstance>> pending,int x,int z){
        int cx=Math.floorDiv(x,CHUNK_SIZE),cz=Math.floorDiv(z,CHUNK_SIZE);
        long key=chunkKey(cx,cz);
        Array<ModelInstance> list=pending.get(key);
        if(list==null){list=new Array<>();pending.put(key,list);}
        return list;
    }

    private void add(Map<Long,Array<ModelInstance>> pending,ModelInstance i,int x,int z){
        getList(pending,x,z).add(i);
    }

    private void addInstance(Map<Long,Array<ModelInstance>> pending,BlockType type,int x,int y,int z,float scale){
        ModelInstance i=new ModelInstance(models[type.id()]);
        i.transform.setToTranslation(x,y,z).scale(scale,scale,scale);
        add(pending,i,x,z);
    }

    private static boolean isFullyEnclosed(World world,World.Block b){
        return isOpaqueTerrainCell(world.getBlock(b.x,b.y+1,b.z))
                &&isOpaqueTerrainCell(world.getBlock(b.x,b.y-1,b.z))
                &&isOpaqueTerrainCell(world.getBlock(b.x+1,b.y,b.z))
                &&isOpaqueTerrainCell(world.getBlock(b.x-1,b.y,b.z))
                &&isOpaqueTerrainCell(world.getBlock(b.x,b.y,b.z+1))
                &&isOpaqueTerrainCell(world.getBlock(b.x,b.y,b.z-1));
    }

    private static boolean isOpaqueTerrainCell(World.Block block){
        return block!=null&&(block.type==BlockType.GRASS||block.type==BlockType.DIRT||block.type==BlockType.STONE);
    }

    private static long chunkKey(int cx,int cz){return ((long)cx<<32)^(cz&0xffffffffL);}

    public void dispose(){
        ModelBatch oldBatch=batch;batch=null;if(oldBatch!=null)try{oldBatch.dispose();}catch(Throwable ignored){}
        for(Chunk chunk:chunks.values())chunk.dispose();
        chunks.clear();
        blocksByChunk.clear();
        for(int i=0;i<models.length;i++){Model model=models[i];models[i]=null;if(model!=null)try{model.dispose();}catch(Throwable ignored){}}
        Model[] props={treeTrunk,treeBranch,treeLeaf,cactusBody,cactusArm,cactusTip};
        treeTrunk=treeBranch=treeLeaf=cactusBody=cactusArm=cactusTip=null;
        for(Model model:props)if(model!=null)try{model.dispose();}catch(Throwable ignored){}
        environment=null;created=false;cachedRevision=-1;
    }
}

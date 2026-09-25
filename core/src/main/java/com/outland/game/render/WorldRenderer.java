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
    private final Model[] grassVariants=new Model[3];
    private final Model[] dirtVariants=new Model[2];
    private final Map<Long,Chunk> chunks=new HashMap<>();
    private final Map<Long,Map<Long,World.Block>> blocksByChunk=new HashMap<>();
    private final Vector3 scratch=new Vector3();
    private Model treeTrunk,treeTrunkTop,treeBranch,treeBranchThin,treeLeaf,treeLeafDark,cactusBody,cactusArm,cactusTip,rock,rockDark,grassTuft,bush;
    private ModelBatch batch;
    private Environment environment;
    private long cachedRevision=-1;
    private boolean created;

    private static final class Chunk {
        final int cx,cz;
        final ModelCache cache;
        final Vector3 center;
        Model terrainModel;
        ModelInstance terrainInstance;
        Chunk(int cx,int cz){
            this.cx=cx;this.cz=cz;
            this.cache=new ModelCache();
            this.center=new Vector3(cx*CHUNK_SIZE+CHUNK_SIZE*.5f,0,cz*CHUNK_SIZE+CHUNK_SIZE*.5f);
        }
        void build(Array<ModelInstance> instances,Model terrain){
            terrainModel=terrain;
            terrainInstance=terrain==null?null:new ModelInstance(terrain);
            cache.begin();cache.add(instances);cache.end();
        }
        void dispose(){
            cache.dispose();
            if(terrainModel!=null){try{terrainModel.dispose();}catch(Throwable ignored){} terrainModel=null;terrainInstance=null;}
        }
    }

    public void create(){
        if(created)return;
        ModelBuilder builder=new ModelBuilder();
        int[] colors={0x5f8f4fff,0x765238ff,0x777b7aff,0x6b4734ff,0x3f743fff,0x4b914bff,0x7dbb68ff};
        try{
            long attrs=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal;
            models[BlockType.GRASS.id()]=builder.createBox(1.08f,.46f,1.08f,new Material(ColorAttribute.createDiffuse(new Color(colors[0]))),attrs);
            grassVariants[0]=models[BlockType.GRASS.id()];
            grassVariants[1]=builder.createBox(1.08f,.40f,1.08f,new Material(ColorAttribute.createDiffuse(new Color(0x668f50ff))),attrs);
            grassVariants[2]=builder.createBox(1.08f,.34f,1.08f,new Material(ColorAttribute.createDiffuse(new Color(0x587f47ff))),attrs);
            models[BlockType.DIRT.id()]=builder.createBox(1,1,1,new Material(ColorAttribute.createDiffuse(new Color(colors[1]))),attrs);
            dirtVariants[0]=models[BlockType.DIRT.id()];
            dirtVariants[1]=builder.createBox(1,1,1,new Material(ColorAttribute.createDiffuse(new Color(0x69482fff))),attrs);
            models[BlockType.STONE.id()]=builder.createBox(1,1,1,new Material(ColorAttribute.createDiffuse(new Color(colors[2]))),attrs);
            models[BlockType.URANIUM.id()]=builder.createSphere(1.05f,.85f,.92f,7,4,new Material(ColorAttribute.createDiffuse(new Color(colors[6]))),attrs);

            Material trunkMat=new Material(ColorAttribute.createDiffuse(new Color(0x66442fff)));
            Material trunkDarkMat=new Material(ColorAttribute.createDiffuse(new Color(0x503624ff)));
            Material barkMat=new Material(ColorAttribute.createDiffuse(new Color(0x573925ff)));
            Material leafMat=new Material(ColorAttribute.createDiffuse(new Color(0x32653aff)));
            Material leafDarkMat=new Material(ColorAttribute.createDiffuse(new Color(0x285531ff)));
            
            Material cactusMat=new Material(ColorAttribute.createDiffuse(new Color(0x4f9f54ff)));

            // One-piece-ish stylized tree parts. Multiple organic lobes beat the
            // old isolated green balls while staying cheap enough for Android.
            treeTrunk=builder.createCylinder(.38f,3.2f,.38f,9,trunkMat,attrs);
            treeTrunkTop=builder.createCylinder(.27f,2.0f,.27f,9,trunkDarkMat,attrs);
            treeBranch=builder.createCylinder(.13f,1.05f,.13f,8,barkMat,attrs);
            treeBranchThin=builder.createCylinder(.09f,.78f,.09f,7,barkMat,attrs);
            treeLeaf=builder.createSphere(1.35f,.82f,1.05f,8,6,leafMat,attrs);
            treeLeafDark=builder.createSphere(1.05f,.66f,.86f,8,6,leafDarkMat,attrs);
            cactusBody=builder.createCylinder(.34f,3.5f,.34f,10,cactusMat,attrs);
            cactusArm=builder.createCylinder(.22f,1.05f,.22f,10,cactusMat,attrs);
            cactusTip=builder.createSphere(.36f,.28f,.36f,10,6,cactusMat,attrs);
            Material rockMat=new Material(ColorAttribute.createDiffuse(new Color(0x5f625fff)));
            Material rockDarkMat=new Material(ColorAttribute.createDiffuse(new Color(0x4d504eff)));
            rock=builder.createSphere(1.0f,.68f,.82f,7,4,rockMat,attrs);
            rockDark=builder.createSphere(.78f,.52f,.64f,7,4,rockDarkMat,attrs);
            Material tuftMat=new Material(ColorAttribute.createDiffuse(new Color(0x536b3fff)));
            Material bushMat=new Material(ColorAttribute.createDiffuse(new Color(0x3f683fff)));
            grassTuft=builder.createCone(.18f,.62f,.18f,5,tuftMat,attrs);
            bush=builder.createSphere(1.35f,.62f,1.0f,8,5,bushMat,attrs);

            // Keep terrain lighting restrained so the ground does not wash out.
            batch=new ModelBatch();environment=new Environment();
            environment.set(new ColorAttribute(ColorAttribute.AmbientLight,.34f,.36f,.37f,1f));
            environment.set(new ColorAttribute(ColorAttribute.Fog,.47f,.62f,.70f,1f));
            environment.add(new DirectionalLight().set(.82f,.72f,.58f,-1f,-2f,-.45f));
            environment.add(new DirectionalLight().set(.12f,.15f,.16f,.55f,-.4f,.7f));
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
                if(!camera.frustum.sphereInFrustum(scratch,CHUNK_SIZE*1.4f))continue;
                if(chunk.terrainInstance!=null)batch.render(chunk.terrainInstance,environment);
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
                // Base terrain is drawn by the low-poly surface mesh below. Keep
                // voxel terrain in storage/collision, but do not expose cube faces.
                if(base)continue;
                if(b.type==BlockType.WOOD||b.type==BlockType.LEAVES||b.type==BlockType.CACTUS)continue;
                if(b.type==BlockType.GRASS && world.getBlock(b.x,b.y+1,b.z)==null){
                    int roll=Math.floorMod(b.x*92821+b.z*68917,1000);
                    if(roll<18)addRock(pending,b);
                    else if(roll<42)addGroundBush(pending,b);
                    else if(roll<112)addGrassTuft(pending,b);
                }
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
        if(instances==null)instances=new Array<>();
        Model terrainModel=buildTerrainSurface(world,cx,cz);
        if(instances.size==0&&terrainModel==null)return null;
        Chunk chunk=new Chunk(cx,cz);
        chunk.build(instances,terrainModel);
        return chunk;
    }

    /**
     * Builds the visible ground as a sloped low-poly surface while World keeps
     * its voxel representation for saves, mining and collision.
     */
    private Model buildTerrainSurface(World world,int cx,int cz){
        ModelBuilder builder=new ModelBuilder();
        long attrs=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal;
        Material grassMat=new Material(ColorAttribute.createDiffuse(new Color(0x6f7f4fff)));
        Material dirtMat=new Material(ColorAttribute.createDiffuse(new Color(0x875a3cff)));
        Material rockMat=new Material(ColorAttribute.createDiffuse(new Color(0x66645fff)));
        MeshPartBuilder top=builder.part("ground",GL20.GL_TRIANGLES,attrs,grassMat);
        MeshPartBuilder dirt=builder.part("sides",GL20.GL_TRIANGLES,attrs,dirtMat);
        MeshPartBuilder rockSide=builder.part("rock",GL20.GL_TRIANGLES,attrs,rockMat);
        boolean any=false;
        Vector3 a=new Vector3(),b=new Vector3(),c=new Vector3(),d=new Vector3(),normal=new Vector3();

        for(int x=cx*CHUNK_SIZE;x<cx*CHUNK_SIZE+CHUNK_SIZE;x++){
            for(int z=cz*CHUNK_SIZE;z<cz*CHUNK_SIZE+CHUNK_SIZE;z++){
                int h=world.highestTerrainY(x,z);
                if(h<-512)continue;
                any=true;
                float y00=cornerHeight(world,x,z);
                float y10=cornerHeight(world,x+1,z);
                float y11=cornerHeight(world,x+1,z+1);
                float y01=cornerHeight(world,x,z+1);
                a.set(x-.5f,y00,z-.5f);
                b.set(x+.5f,y10,z-.5f);
                c.set(x+.5f,y11,z+.5f);
                d.set(x-.5f,y01,z+.5f);
                normal.set(d).sub(a).crs(new Vector3(b).sub(a)).nor();
                if(normal.y<0)normal.scl(-1f);
                top.rect(a,b,c,d,normal);

                addTerrainEdge(world,dirt,rockSide,x,z,x+1,z,y10,y11);
                addTerrainEdge(world,dirt,rockSide,x+1,z+1,z+1,x+1,y11,y10);
                addTerrainEdge(world,dirt,rockSide,x,z+1,x,z,y01,y00);
                addTerrainEdge(world,dirt,rockSide,x,z,x,z+1,y00,y01);
            }
        }
        if(!any)return null;
        return builder.end();
    }

    private float cornerHeight(World world,int x,int z){
        float total=0f;int count=0;
        int[] xs={x-1,x,x-1,x};
        int[] zs={z-1,z-1,z,z};
        for(int i=0;i<4;i++){
            int h=world.highestTerrainY(xs[i],zs[i]);
            if(h>=-512){total+=h+.5f;count++;}
        }
        if(count==0)return 0f;
        return total/count;
    }

    private void addTerrainEdge(World world,MeshPartBuilder dirt,MeshPartBuilder rockSide,
                                int x1,int z1,int x2,int z2,float top1,float top2){
        int neighborH=world.highestTerrainY(x2,z2);
        if(neighborH>=-512 && neighborH>=Math.min(top1-.5f,top2-.5f))return;
        float bottom=Math.max(-1f,Math.min(top1,top2)-2.6f);
        float nx=z2-z1;
        float nz=-(x2-x1);
        Vector3 n=new Vector3(nx,0,nz).nor();
        if(n.len2()<.01f)n.set(0,1,0);
        dirt.rect(x1-.5f,top1,x1+.5f, x2-.5f,top2,x2+.5f,
                  x2-.5f,bottom,x2+.5f, x1-.5f,bottom,x1+.5f,n);
        if(top1-bottom>.9f){
            float band=Math.max(bottom,Math.min(top1,top2)-1.15f);
            rockSide.rect(x1-.5f,band,x1+.5f, x2-.5f,Math.min(top2,band),x2+.5f,
                          x2-.5f,bottom,x2+.5f, x1-.5f,bottom,x1+.5f,n);
        }
    }

    private boolean isTreeBase(World world,World.Block b){
        World.Block below=world.getBlock(b.x,b.y-1,b.z);
        return below==null||below.type!=BlockType.WOOD;
    }

    private boolean isCactusBase(World world,World.Block b){
        World.Block below=world.getBlock(b.x,b.y-1,b.z);
        return below==null||below.type!=BlockType.CACTUS;
    }

    private void addGrassTuft(Map<Long,Array<ModelInstance>> pending,World.Block b){
        long hash=World.key(b.x,b.y,b.z)*0xD6E8FEB86659FD93L;
        float seed=(float)((hash>>>20)&0xffff)/65535f;
        float ox=((float)((hash>>>6)&255)/255f-.5f)*.58f;
        float oz=((float)((hash>>>14)&255)/255f-.5f)*.58f;
        addProp(pending,grassTuft,b.x+ox,b.y+.54f,b.z+oz,.72f+seed*.5f,(seed-.5f)*18f,seed*130f,0f);
        if(seed>.58f)addProp(pending,grassTuft,b.x-ox*.45f,b.y+.50f,b.z-oz*.45f,.48f+seed*.28f,-(seed-.5f)*14f,seed*80f,0f);
    }

    private void addGroundBush(Map<Long,Array<ModelInstance>> pending,World.Block b){
        long hash=World.key(b.x,b.y,b.z)*0x9E3779B97F4A7C15L;
        float seed=(float)((hash>>>16)&0xffff)/65535f;
        float ox=((float)((hash>>>4)&255)/255f-.5f)*.45f;
        float oz=((float)((hash>>>12)&255)/255f-.5f)*.45f;
        addProp(pending,bush,b.x+ox,b.y+.42f,b.z+oz,.42f+seed*.30f,(seed-.5f)*12f,seed*160f,(seed-.5f)*8f);
    }

    private void addRock(Map<Long,Array<ModelInstance>> pending,World.Block b){
        long hash=World.key(b.x,b.y,b.z)*0x9E3779B97F4A7C15L;
        float seed=(float)((hash>>>16)&0xffff)/65535f;
        float scale=.55f+seed*.55f;
        float ox=((float)((hash>>>4)&255)/255f-.5f)*.55f;
        float oz=((float)((hash>>>12)&255)/255f-.5f)*.55f;
        Model model=(seed>.62f)?rock:rockDark;
        ModelInstance i=new ModelInstance(model);
        i.transform.setToTranslation(b.x+ox,b.y+.5f+.20f*scale,b.z+oz).scale(scale,scale*.58f,scale);
        i.transform.rotate(Vector3.Y,seed*137f);
        add(pending,i,b.x,b.z);
    }

    private void addTree(Map<Long,Array<ModelInstance>> pending,World.Block b){
        float seed=Math.abs(World.key(b.x,b.y,b.z)%10000)/10000f;
        float lean=(seed-.5f)*8f;
        float sway=(seed*.7f-.35f)*10f;

        addProp(pending,treeTrunk,b.x,b.y+1.35f,b.z,1f,lean,0f,0f);
        addProp(pending,treeTrunkTop,b.x+.03f,b.y+3.15f,b.z-.02f,.92f,lean*.65f,0f,0f);

        // Branches leave the trunk gradually and terminate inside the canopy.
        addBranch(pending,b.x,b.y+2.25f,b.z,b.x+.72f,b.y+2.72f,b.z+.10f,lean);
        addBranch(pending,b.x,b.y+2.85f,b.z,b.x-.62f,b.y+3.28f,b.z+.08f,sway);
        addBranch(pending,b.x+.03f,b.y+3.25f,b.z,b.x+.12f,b.y+3.72f,b.z+.58f,sway);
        addThinBranch(pending,b.x+.02f,b.y+3.55f,b.z,b.x+.62f,b.y+3.9f,b.z-.35f);

        // Layered, offset foliage gives a natural crown instead of a lollipop.
        addLeaf(pending,b.x-.10f,b.y+3.25f,b.z+.05f,1.00f,false);
        addLeaf(pending,b.x+.62f,b.y+3.05f,b.z+.08f,.76f,true);
        addLeaf(pending,b.x-.60f,b.y+3.48f,b.z+.10f,.72f,true);
        addLeaf(pending,b.x+.12f,b.y+3.78f,b.z+.48f,.70f,false);
        addLeaf(pending,b.x+.20f,b.y+4.02f,b.z-.35f,.58f,true);
        addLeaf(pending,b.x-.05f,b.y+4.18f,b.z+.02f,.54f,false);
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

    private void addThinBranch(Map<Long,Array<ModelInstance>> pending,float x1,float y1,float z1,float x2,float y2,float z2){
        float dx=x2-x1,dy=y2-y1,dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        ModelInstance i=new ModelInstance(treeBranchThin);
        i.transform.setToTranslation((x1+x2)*.5f,(y1+y2)*.5f,(z1+z2)*.5f);
        i.transform.scale(1f,len/.78f,1f);
        i.transform.rotate(Vector3.Z,(float)Math.toDegrees(Math.atan2(dx,dy)));
        i.transform.rotate(Vector3.X,-(float)Math.toDegrees(Math.atan2(dz,dy)));
        add(pending,i,(int)Math.floor((x1+x2)*.5f),(int)Math.floor((z1+z2)*.5f));
    }

    private void addLeaf(Map<Long,Array<ModelInstance>> pending,float x,float y,float z,float scale,boolean dark){
        addProp(pending,dark?treeLeafDark:treeLeaf,x,y,z,scale,0f,0f,0f);
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
        Model model=models[type.id()];
        if(type==BlockType.GRASS)model=grassVariants[Math.floorMod(x*31+z*17,grassVariants.length)];
        else if(type==BlockType.DIRT)model=dirtVariants[Math.floorMod(x*13+z*29,dirtVariants.length)];
        ModelInstance i=new ModelInstance(model);
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
        for(int i=1;i<grassVariants.length;i++)if(grassVariants[i]!=null)try{grassVariants[i].dispose();}catch(Throwable ignored){}
        for(int i=1;i<dirtVariants.length;i++)if(dirtVariants[i]!=null)try{dirtVariants[i].dispose();}catch(Throwable ignored){}
        Model[] props={treeTrunk,treeTrunkTop,treeBranch,treeBranchThin,treeLeaf,treeLeafDark,cactusBody,cactusArm,cactusTip,rock,rockDark,grassTuft,bush};
        treeTrunk=treeTrunkTop=treeBranch=treeBranchThin=treeLeaf=treeLeafDark=cactusBody=cactusArm=cactusTip=rock=rockDark=grassTuft=bush=null;
        for(Model model:props)if(model!=null)try{model.dispose();}catch(Throwable ignored){}
        environment=null;created=false;cachedRevision=-1;
    }
}

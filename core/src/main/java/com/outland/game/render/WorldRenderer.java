package com.outland.game.render;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.outland.game.world.*;
import java.util.*;

/**
 * Mobile wasteland renderer: voxel storage/collision, continuous low-poly
 * heightfield terrain, and cached organic props. Terrain is geometry, not a
 * stack of visible cubes, so hills read as hills while mining remains voxel-safe.
 */
public final class WorldRenderer {
    private static final int CHUNK_SIZE=8;
    private static final float RENDER_RADIUS=64f;
    private static final int MAX_VISIBLE_CHUNKS=225;
    private final Model[] models=new Model[BlockType.values().length];
    private final Model[] grassVariants=new Model[3];
    private final Model[] dirtVariants=new Model[2];
    private final Map<Long,Chunk> chunks=new HashMap<>();
    private final Map<Long,Map<Long,World.Block>> blocksByChunk=new HashMap<>();
    private Model treeTrunk,treeTrunkTop,treeBranch,treeLeaf,treeLeafDark;
    private Model cactusBody,cactusArm,cactusTip,rock,rockDark,grassTuft,bush,uranium;
    private ModelBatch batch;
    private Environment environment;
    private long cachedRevision=-1;
    private boolean created;
    private boolean firstFrame=true;
    private int buildBudget=1;

    private static final class Chunk {
        final int cx,cz;
        final ModelCache cache=new ModelCache();
        final Vector3 center;
        Model terrainModel;
        ModelInstance terrainInstance;
        Chunk(int cx,int cz){
            this.cx=cx;this.cz=cz;
            center=new Vector3(cx*CHUNK_SIZE+CHUNK_SIZE*.5f,0,cz*CHUNK_SIZE+CHUNK_SIZE*.5f);
        }
        void build(Array<ModelInstance> instances,Model terrain){
            terrainModel=terrain;
            terrainInstance=terrain==null?null:new ModelInstance(terrain);
            cache.begin();cache.add(instances);cache.end();
        }
        void dispose(){
            cache.dispose();
            if(terrainModel!=null){terrainModel.dispose();terrainModel=null;terrainInstance=null;}
        }
    }

    public void create(){
        if(created)return;
        ModelBuilder b=new ModelBuilder();
        try{
            long attrs=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal;
            models[BlockType.GRASS.id()]=b.createBox(1.08f,.46f,1.08f,new Material(ColorAttribute.createDiffuse(new Color(0x5f8f4fff))),attrs);
            grassVariants[0]=models[BlockType.GRASS.id()];
            grassVariants[1]=b.createBox(1.08f,.40f,1.08f,new Material(ColorAttribute.createDiffuse(new Color(0x668f50ff))),attrs);
            grassVariants[2]=b.createBox(1.08f,.34f,1.08f,new Material(ColorAttribute.createDiffuse(new Color(0x587f47ff))),attrs);
            models[BlockType.DIRT.id()]=b.createBox(1f,1f,1f,new Material(ColorAttribute.createDiffuse(new Color(0x765238ff))),attrs);
            dirtVariants[0]=models[BlockType.DIRT.id()];
            dirtVariants[1]=b.createBox(1f,1f,1f,new Material(ColorAttribute.createDiffuse(new Color(0x69482fff))),attrs);
            models[BlockType.STONE.id()]=b.createBox(1f,1f,1f,new Material(ColorAttribute.createDiffuse(new Color(0x777b7aff))),attrs);
            Material trunk=new Material(ColorAttribute.createDiffuse(new Color(0x66442fff)));
            Material trunkDark=new Material(ColorAttribute.createDiffuse(new Color(0x503624ff)));
            Material leaf=new Material(ColorAttribute.createDiffuse(new Color(0x32653aff)));
            Material leafDark=new Material(ColorAttribute.createDiffuse(new Color(0x285531ff)));
            Material cactus=new Material(ColorAttribute.createDiffuse(new Color(0x4f9f54ff)));
            Material rockMat=new Material(ColorAttribute.createDiffuse(new Color(0x5f625fff)));
            Material rockDarkMat=new Material(ColorAttribute.createDiffuse(new Color(0x4d504eff)));
            Material tuft=new Material(ColorAttribute.createDiffuse(new Color(0x536b3fff)));
            Material bushMat=new Material(ColorAttribute.createDiffuse(new Color(0x3f683fff)));
            Material uraniumMat=new Material(ColorAttribute.createDiffuse(new Color(0x7dbb68ff)));
            treeTrunk=b.createCylinder(.48f,3.0f,.42f,7,trunk,attrs);
            treeTrunkTop=b.createCylinder(.32f,1.7f,.28f,7,trunkDark,attrs);
            treeBranch=b.createCylinder(.15f,1.15f,.12f,6,trunk,attrs);
            treeLeaf=b.createSphere(1.25f,.72f,1.05f,7,4,leaf,attrs);
            treeLeafDark=b.createSphere(1.0f,.58f,.82f,7,4,leafDark,attrs);
            cactusBody=b.createCylinder(.38f,3.5f,.35f,8,cactus,attrs);
            cactusArm=b.createCylinder(.25f,1.1f,.22f,8,cactus,attrs);
            cactusTip=b.createSphere(.36f,.34f,.36f,8,4,cactus,attrs);
            rock=b.createSphere(1.0f,.68f,.82f,7,4,rockMat,attrs);
            rockDark=b.createSphere(.78f,.52f,.64f,7,4,rockDarkMat,attrs);
            grassTuft=b.createCone(.18f,.62f,.18f,5,tuft,attrs);
            bush=b.createSphere(1.35f,.62f,1.0f,8,5,bushMat,attrs);
            uranium=b.createSphere(1.05f,.85f,.92f,7,4,uraniumMat,attrs);
            batch=new ModelBatch();
            environment=new Environment();
            environment.set(new ColorAttribute(ColorAttribute.AmbientLight,.34f,.36f,.37f,1f));
            environment.set(new ColorAttribute(ColorAttribute.Fog,.47f,.62f,.70f,1f));
            environment.add(new DirectionalLight().set(.82f,.72f,.58f,-1f,-2f,-.45f));
            environment.add(new DirectionalLight().set(.12f,.15f,.16f,.55f,-.4f,.7f));
            created=true;
        }catch(Throwable t){dispose();throw new IllegalStateException("Renderer initialization failed",t);}
    }

    public void render(World world,Camera camera,Vector3 playerPosition){
        if(!created)throw new IllegalStateException("WorldRenderer not created");
        if(firstFrame){
            syncIndexOnly(world);
            firstFrame=false;
        } else {
            sync(world);
            buildVisibleChunks(world,playerPosition,buildBudget);
        }
        batch.begin(camera);
        try{
            int visible=0;
            for(Chunk c:chunks.values()){
                if(visible>=MAX_VISIBLE_CHUNKS)break;
                float dx=c.center.x-playerPosition.x,dz=c.center.z-playerPosition.z;
                if(dx*dx+dz*dz>RENDER_RADIUS*RENDER_RADIUS)continue;
                if(c.terrainInstance!=null)batch.render(c.terrainInstance,environment);
                batch.render(c.cache,environment);
                visible++;
            }
        }finally{batch.end();}
    }

    private void syncIndexOnly(World world){
        if(cachedRevision==world.revision())return;
        rebuildIndexOnly(world);
        cachedRevision=world.revision();
        world.consumeChangedBlockKey();
    }

    private void buildVisibleChunks(World world,Vector3 playerPosition,int budget){
        if(budget<=0||blocksByChunk.isEmpty())return;
        int built=0;
        int pcx=Math.floorDiv((int)Math.floor(playerPosition.x),CHUNK_SIZE);
        int pcz=Math.floorDiv((int)Math.floor(playerPosition.z),CHUNK_SIZE);
        while(built<budget){
            long bestKey=0L;
            float bestDistance=Float.MAX_VALUE;
            boolean found=false;
            for(Long key:blocksByChunk.keySet()){
                if(chunks.containsKey(key))continue;
                int cx=(int)(key>>32),cz=(int)(long)key;
                float dx=(cx-pcx)*CHUNK_SIZE+CHUNK_SIZE*.5f;
                float dz=(cz-pcz)*CHUNK_SIZE+CHUNK_SIZE*.5f;
                float distance=dx*dx+dz*dz;
                if(distance<=RENDER_RADIUS*RENDER_RADIUS&&distance<bestDistance){
                    bestDistance=distance;bestKey=key;found=true;
                }
            }
            if(!found)break;
            rebuildChunk(world,(int)(bestKey>>32),(int)bestKey);
            built++;
        }
    }

    private void sync(World world){
        if(cachedRevision==world.revision())return;
        Long changed=world.consumeChangedBlockKey();
        if(cachedRevision<0||changed==null){
            rebuildIndexOnly(world);
            cachedRevision=world.revision();
            world.consumeChangedBlockKey();
            return;
        }
        updateIndex(world,changed);
        int x=World.xFromKey(changed),z=World.zFromKey(changed);
        int ccx=Math.floorDiv(x,CHUNK_SIZE),ccz=Math.floorDiv(z,CHUNK_SIZE);
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)rebuildChunk(world,ccx+dx,ccz+dz);
        cachedRevision=world.revision();
    }

    private void rebuildIndexOnly(World world){
        for(Chunk c:chunks.values())c.dispose();
        chunks.clear();
        blocksByChunk.clear();
        for(World.Block block:world.snapshot()){
            long k=chunkKey(Math.floorDiv(block.x,CHUNK_SIZE),Math.floorDiv(block.z,CHUNK_SIZE));
            Map<Long,World.Block> map=blocksByChunk.get(k);
            if(map==null){map=new HashMap<>();blocksByChunk.put(k,map);}
            map.put(World.key(block.x,block.y,block.z),block);
        }
        for(long k:blocksByChunk.keySet())rebuildChunk(world,(int)(k>>32),(int)k);
    }

    private void updateIndex(World world,long key){
        int x=World.xFromKey(key),y=World.yFromKey(key),z=World.zFromKey(key);
        long ck=chunkKey(Math.floorDiv(x,CHUNK_SIZE),Math.floorDiv(z,CHUNK_SIZE));
        Map<Long,World.Block> map=blocksByChunk.get(ck);
        World.Block now=world.getBlock(x,y,z);
        if(now==null){
            if(map!=null){
                map.remove(key);
                if(map.isEmpty())blocksByChunk.remove(ck);
            }
        }else{
            if(map==null){map=new HashMap<>();blocksByChunk.put(ck,map);}
            map.put(key,now);
        }
    }

    private void rebuildChunk(World world,int cx,int cz){
        long key=chunkKey(cx,cz);
        Chunk old=chunks.remove(key);
        if(old!=null)old.dispose();

        Map<Long,Array<ModelInstance>> pending=new HashMap<>();
        Map<Long,World.Block> own=blocksByChunk.get(key);
        if(own!=null)for(World.Block b:own.values()){
            boolean terrain=b.type==BlockType.GRASS||b.type==BlockType.DIRT||b.type==BlockType.STONE;
            if(terrain&&!isFullyEnclosed(world,b))addInstance(pending,b.type,b.x,b.y,b.z);
            if(b.type==BlockType.URANIUM)addProp(pending,uranium,b.x,b.y+.55f,b.z,1f,0,0,0);
            if(b.type==BlockType.GRASS&&world.getBlock(b.x,b.y+1,b.z)==null){
                int roll=Math.floorMod(b.x*92821+b.z*68917,1000);
                if(roll<18)addGround(pending,rock,b);
                else if(roll<42)addGround(pending,bush,b);
                else if(roll<112)addGround(pending,grassTuft,b);
            }
        }

        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            Map<Long,World.Block> near=blocksByChunk.get(chunkKey(cx+dx,cz+dz));
            if(near==null)continue;
            for(World.Block b:near.values()){
                if(b.type==BlockType.WOOD&&isTreeBase(world,b))addTree(pending,b);
                else if(b.type==BlockType.CACTUS&&isCactusBase(world,b))addCactus(pending,world,b);
            }
        }

        Array<ModelInstance> instances=pending.get(key);
        if(instances==null)instances=new Array<>();
        Model terrain=buildLowPolyTerrain(world,cx,cz);
        if(instances.size==0&&terrain==null)return;
        Chunk c=new Chunk(cx,cz);
        c.build(instances,terrain);
        chunks.put(key,c);
    }

    /**
     * Continuous low-poly terrain surface. The world remains voxel-backed for
     * collision/mining, but the player sees a shared triangulated surface.
     * Every surface vertex has a real fallback height, so missing columns can
     * never turn into sky-colored holes.
     */
    private Model buildLowPolyTerrain(World world,int cx,int cz){
        ModelBuilder b=new ModelBuilder();
        long attrs=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal|VertexAttributes.Usage.ColorPacked;
        b.begin();
        Material mat=new Material(ColorAttribute.createDiffuse(Color.WHITE));
        MeshPartBuilder mesh=b.part("terrain",GL20.GL_TRIANGLES,attrs,mat);
        boolean any=false;
        int sx=cx*CHUNK_SIZE,sz=cz*CHUNK_SIZE;

        for(int x=sx;x<sx+CHUNK_SIZE;x++){
            for(int z=sz;z<sz+CHUNK_SIZE;z++){
                float h00=surfaceHeight(world,x,z);
                float h10=surfaceHeight(world,x+1,z);
                float h11=surfaceHeight(world,x+1,z+1);
                float h01=surfaceHeight(world,x,z+1);
                if(h00<-511f&&h10<-511f&&h11<-511f&&h01<-511f)continue;
                any=true;

                Vector3 a=new Vector3(x-.5f,h00,z-.5f);
                Vector3 bb=new Vector3(x+.5f,h10,z-.5f);
                Vector3 c=new Vector3(x+.5f,h11,z+.5f);
                Vector3 d=new Vector3(x-.5f,h01,z+.5f);
                Vector3 normal=new Vector3(bb).sub(a).crs(new Vector3(c).sub(a)).nor();
                if(normal.y<0)normal.scl(-1f);

                int shade=Math.floorMod(x*92821+z*68917,3);
                if(shade==0)mesh.setColor(new Color(0x667f50ff));
                else if(shade==1)mesh.setColor(new Color(0x5f774aff));
                else mesh.setColor(new Color(0x6c8554ff));
                mesh.rect(a,bb,c,d,normal);

                addLowPolyCliff(mesh,world,x,z,x-1,z,a,d);
                addLowPolyCliff(mesh,world,x,z,x+1,z,bb,c);
                addLowPolyCliff(mesh,world,x,z,x,z-1,a,bb);
                addLowPolyCliff(mesh,world,x,z,x,z+1,c,d);
            }
        }
        return any?b.end():null;
    }

    private float surfaceHeight(World world,int x,int z){
        float sum=0f;
        int count=0;
        for(int dx=-1;dx<=0;dx++)for(int dz=-1;dz<=0;dz++){
            int h=world.highestTerrainY(x+dx,z+dz);
            if(h>=-512){sum+=h+.5f;count++;}
        }
        if(count>0)return sum/count;
        // A deleted/empty column inherits the nearest terrain sample instead
        // of becoming a void. This is visual fallback only; collision is unchanged.
        for(int radius=1;radius<=3;radius++){
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                int h=world.highestTerrainY(x+dx,z+dz);
                if(h>=-512)return h+.5f;
            }
        }
        return -512f;
    }

    private void addLowPolyCliff(MeshPartBuilder mesh,World world,int x,int z,int nx,int nz,Vector3 p0,Vector3 p1){
        int h=world.highestTerrainY(x,z);
        int nh=world.highestTerrainY(nx,nz);
        if(h<-512||nh>=h)return;
        float bottom=nh>=-512?nh+.5f:Math.max(-1f,Math.min(p0.y,p1.y)-3f);
        if(bottom>=Math.min(p0.y,p1.y)-.01f)return;
        Vector3 q0=new Vector3(p0.x,bottom,p0.z);
        Vector3 q1=new Vector3(p1.x,bottom,p1.z);
        Vector3 normal=new Vector3(p1).sub(p0).crs(new Vector3(q0).sub(p0)).nor();
        mesh.setColor(new Color(0x62452fff));
        mesh.rect(p0,p1,q1,q0,normal);
    }

    private boolean isTreeBase(World world,World.Block b){
        World.Block below=world.getBlock(b.x,b.y-1,b.z);
        return below==null||below.type!=BlockType.WOOD;
    }
    private boolean isCactusBase(World world,World.Block b){
        World.Block below=world.getBlock(b.x,b.y-1,b.z);
        return below==null||below.type!=BlockType.CACTUS;
    }

    private void addTree(Map<Long,Array<ModelInstance>> p,World.Block b){
        float s=Math.abs(World.key(b.x,b.y,b.z)%10000)/10000f;
        addProp(p,treeTrunk,b.x,b.y+1.25f,b.z,1f,(s-.5f)*7f,s*35f,0);
        addProp(p,treeTrunkTop,b.x+.04f,b.y+2.65f,b.z-.03f,.92f,(s-.5f)*3f,s*20f,0);
        branch(p,b.x,b.y+2.1f,b.z,b.x+.72f,b.y+2.75f,b.z+.08f);
        branch(p,b.x,b.y+2.5f,b.z,b.x-.66f,b.y+3.08f,b.z+.12f);
        leaf(p,b.x-.48f,b.y+3.18f,b.z,.82f,false);
        leaf(p,b.x+.42f,b.y+3.05f,b.z+.08f,.88f,true);
        leaf(p,b.x+.05f,b.y+3.52f,b.z+.34f,.92f,false);
        leaf(p,b.x-.18f,b.y+3.82f,b.z-.12f,.68f,true);
        if(s>.32f)leaf(p,b.x+.54f,b.y+3.66f,b.z-.28f,.62f,false);
        if(s<.68f)leaf(p,b.x-.62f,b.y+3.62f,b.z+.22f,.58f,true);
    }

    private void branch(Map<Long,Array<ModelInstance>> p,float x1,float y1,float z1,float x2,float y2,float z2){
        float dx=x2-x1,dy=y2-y1,dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        ModelInstance i=new ModelInstance(treeBranch);
        i.transform.setToTranslation((x1+x2)/2,(y1+y2)/2,(z1+z2)/2).scale(1,len/1.35f,1);
        i.transform.rotate(Vector3.Z,(float)Math.toDegrees(Math.atan2(dx,dy)));
        i.transform.rotate(Vector3.X,-(float)Math.toDegrees(Math.atan2(dz,dy)));
        add(p,i,(int)x1,(int)z1);
    }

    private void leaf(Map<Long,Array<ModelInstance>> p,float x,float y,float z,float s,boolean dark){
        addProp(p,dark?treeLeafDark:treeLeaf,x,y,z,s,0,0,0);
    }

    private void addCactus(Map<Long,Array<ModelInstance>> p,World world,World.Block b){
        int h=1;
        while(world.getBlock(b.x,b.y+h,b.z)!=null&&world.getBlock(b.x,b.y+h,b.z).type==BlockType.CACTUS)h++;
        addProp(p,cactusBody,b.x,b.y+(h-1)*.5f,b.z,1,0,0,0);
        addProp(p,cactusTip,b.x,b.y+h-.02f,b.z,.92f,0,0,0);
        if(h>=3){
            addProp(p,cactusArm,b.x+.36f,b.y+1.15f,b.z,1,0,0,90);
            addProp(p,cactusTip,b.x+.78f,b.y+1.15f,b.z,.72f,0,0,0);
        }
        if(h>=4){
            addProp(p,cactusArm,b.x-.36f,b.y+1.9f,b.z,1,0,0,-90);
            addProp(p,cactusTip,b.x-.78f,b.y+1.9f,b.z,.72f,0,0,0);
        }
    }

    private void addGround(Map<Long,Array<ModelInstance>> p,Model m,World.Block b){
        addProp(p,m,b.x,b.y+.5f,b.z,.65f,0,0,0);
    }

    private void addProp(Map<Long,Array<ModelInstance>> p,Model m,float x,float y,float z,float s,float rx,float ry,float rz){
        ModelInstance i=new ModelInstance(m);
        i.transform.setToTranslation(x,y,z).scale(s,s,s);
        if(rx!=0)i.transform.rotate(Vector3.X,rx);
        if(ry!=0)i.transform.rotate(Vector3.Y,ry);
        if(rz!=0)i.transform.rotate(Vector3.Z,rz);
        add(p,i,(int)Math.floor(x),(int)Math.floor(z));
    }

    private void add(Map<Long,Array<ModelInstance>> p,ModelInstance i,int x,int z){
        long k=chunkKey(Math.floorDiv(x,CHUNK_SIZE),Math.floorDiv(z,CHUNK_SIZE));
        Array<ModelInstance> a=p.get(k);
        if(a==null){a=new Array<>();p.put(k,a);}
        a.add(i);
    }

    private static long chunkKey(int x,int z){
        return ((long)x<<32)^(z&0xffffffffL);
    }

    private void addInstance(Map<Long,Array<ModelInstance>> pending,BlockType type,int x,int y,int z){
        Model model=models[type.id()];
        if(type==BlockType.GRASS)model=grassVariants[Math.floorMod(x*31+z*17,grassVariants.length)];
        else if(type==BlockType.DIRT)model=dirtVariants[Math.floorMod(x*13+z*29,dirtVariants.length)];
        ModelInstance i=new ModelInstance(model);
        i.transform.setToTranslation(x,y,z);
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

    public void dispose(){
        if(batch!=null){batch.dispose();batch=null;}
        for(Chunk c:chunks.values())c.dispose();
        chunks.clear();
        blocksByChunk.clear();
        for(int i=0;i<models.length;i++){Model m=models[i];models[i]=null;if(m!=null)m.dispose();}
        for(int i=1;i<grassVariants.length;i++)if(grassVariants[i]!=null){grassVariants[i].dispose();grassVariants[i]=null;}
        for(int i=1;i<dirtVariants.length;i++)if(dirtVariants[i]!=null){dirtVariants[i].dispose();dirtVariants[i]=null;}
        Model[] all={treeTrunk,treeTrunkTop,treeBranch,treeLeaf,treeLeafDark,cactusBody,cactusArm,cactusTip,rock,rockDark,grassTuft,bush,uranium};
        treeTrunk=treeTrunkTop=treeBranch=treeLeaf=treeLeafDark=cactusBody=cactusArm=cactusTip=rock=rockDark=grassTuft=bush=uranium=null;
        for(Model m:all)if(m!=null)m.dispose();
        environment=null;
        created=false;
        cachedRevision=-1;
        firstFrame=true;
    }}
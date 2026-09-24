package com.outland.game.render;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.outland.game.world.*;
import java.util.*;

/** Owns all GPU resources; simulation/world classes never reference these objects. */
public final class WorldRenderer {
    private final Model[] models=new Model[BlockType.values().length];
    private final Map<Long,ModelInstance> instances=new HashMap<>();
    private final Vector3 scratch=new Vector3();
    private List<World.Block> cachedBlocks=Collections.emptyList();
    private ModelBatch batch;
    private Environment environment;
    private long cachedRevision=-1;
    private boolean created;

    public void create() {
        if(created) return;
        ModelBuilder builder=new ModelBuilder();
        int[] colors={0x68a94fff,0x8a603fff,0x858b91ff,0x79502fff,0x438443ff};
        try {
            for(int i=0;i<models.length;i++) models[i]=builder.createBox(1,1,1,
                new Material(ColorAttribute.createDiffuse(new Color(colors[i]))),
                VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal);
            batch=new ModelBatch(); environment=new Environment();
            environment.set(new ColorAttribute(ColorAttribute.AmbientLight,.8f,.82f,.86f,1f));
            environment.add(new DirectionalLight().set(.9f,.88f,.78f,-1f,-2f,-.5f));
            created=true;
        } catch(Throwable failure) { dispose(); throw new IllegalStateException("Renderer initialization failed",failure); }
    }
    public void render(World world, Camera camera, Vector3 playerPosition) {
        if(!created) throw new IllegalStateException("WorldRenderer not created");
        sync(world);
        batch.begin(camera);
        try {
            for(World.Block block:cachedBlocks) {
                ModelInstance instance=instances.get(World.key(block.x,block.y,block.z));
                if(instance!=null && instance.transform.getTranslation(scratch).dst2(playerPosition)<24f*24f)
                    batch.render(instance,environment);
            }
        } finally { batch.end(); }
    }
    private void sync(World world) {
        if(cachedRevision==world.revision()) return;
        cachedBlocks=new ArrayList<>(world.snapshot());
        instances.clear();
        for(World.Block b:cachedBlocks) {
            ModelInstance instance=new ModelInstance(models[b.type.id()]);
            instance.transform.setToTranslation(b.x,b.y,b.z);
            instances.put(World.key(b.x,b.y,b.z),instance);
        }
        cachedRevision=world.revision();
    }
    public void dispose() {
        ModelBatch oldBatch=batch; batch=null;
        if(oldBatch!=null)try{oldBatch.dispose();}catch(Throwable ignored){}
        for(int i=0;i<models.length;i++) {
            Model model=models[i];models[i]=null;
            if(model!=null)try{model.dispose();}catch(Throwable ignored){}
        }
        instances.clear();cachedBlocks=Collections.emptyList();environment=null;created=false;cachedRevision=-1;
    }
}

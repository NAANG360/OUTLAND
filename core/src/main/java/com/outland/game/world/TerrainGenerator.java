package com.outland.game.world;

import java.util.Random;

/** Deterministic wasteland terrain builder with broad ridges and natural props. */
public final class TerrainGenerator {
    private final int radius;
    public TerrainGenerator(){this(32);}
    public TerrainGenerator(int radius){
        if(radius<1||radius>100)throw new IllegalArgumentException("radius out of range");
        this.radius=radius;
    }
    public World generate(long seed){
        World world=new World(seed);
        Random random=new Random(seed);
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++){
            int h=heightAt(x,z,seed);
            world.setBlock(x,h,z,BlockType.GRASS);
            world.setBlock(x,h-1,z,BlockType.DIRT);
            world.setBlock(x,h-2,z,BlockType.DIRT);
            world.setBlock(x,h-3,z,BlockType.STONE);

            float roll=random.nextFloat();
            if(roll>.997f) world.replaceBlock(x,h-3,z,BlockType.URANIUM);
            else if(h>3&&roll>.994f) makeTree(world,x,h+1,z);
            else if(h>3&&roll<.010f) makeCactus(world,x,h+1,z);
        }
        return world;
    }

    public static int heightAt(int x,int z,long seed){
        double broad=Math.sin((x+seed%97)*.055)*2.4
                +Math.cos((z-seed%53)*.047)*2.0;
        double ridge=Math.sin((x+z)*.12)*1.35
                +Math.cos((x-z)*.085)*1.1;
        double detail=Math.sin(x*.31+z*.17+seed*.00003)*.45;
        return 3+(int)Math.round(broad+ridge+detail);
    }

    private static void makeTree(World world,int x,int y,int z){
        // Collision/storage stays voxel-based; the renderer builds the organic silhouette.
        for(int i=0;i<5;i++)world.setBlock(x,y+i,z,BlockType.WOOD);

        // A compact branching scaffold keeps foliage attached to the trunk.
        world.setBlock(x+1,y+2,z,BlockType.WOOD);
        world.setBlock(x+1,y+3,z,BlockType.WOOD);
        world.setBlock(x-1,y+3,z,BlockType.WOOD);
        world.setBlock(x,y+3,z+1,BlockType.WOOD);
        world.setBlock(x,y+4,z+1,BlockType.WOOD);

        // Leaf cells are intentionally clustered around branch ends rather than
        // forming a Minecraft-style sphere.
        world.setBlock(x+1,y+3,z+1,BlockType.LEAVES);
        world.setBlock(x+2,y+3,z,BlockType.LEAVES);
        world.setBlock(x-1,y+4,z,BlockType.LEAVES);
        world.setBlock(x,y+4,z+1,BlockType.LEAVES);
        world.setBlock(x+1,y+4,z+1,BlockType.LEAVES);
        world.setBlock(x,y+5,z,BlockType.LEAVES);
    }

    private static void makeCactus(World world,int x,int y,int z){
        int height=3+(int)(Math.abs(x*31L+z*17L)%2);
        for(int i=0;i<height;i++)world.setBlock(x,y+i,z,BlockType.CACTUS);
        // Short, offset arms make a recognizable saguaro silhouette.
        world.setBlock(x+1,y+1,z,BlockType.CACTUS);
        if(height>=4)world.setBlock(x-1,y+2,z,BlockType.CACTUS);
    }
}

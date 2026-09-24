package com.outland.game.world;

import java.util.Random;

/** Deterministic terrain builder separated from rendering and application lifecycle. */
public final class TerrainGenerator {
    private final int radius;
    public TerrainGenerator() { this(9); }
    public TerrainGenerator(int radius) {
        if(radius < 1 || radius > 100) throw new IllegalArgumentException("radius out of range");
        this.radius=radius;
    }
    public World generate(long seed) {
        World world=new World(seed);
        Random random=new Random(seed);
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            int h=heightAt(x,z,seed);
            world.setBlock(x,h,z,BlockType.GRASS);
            world.setBlock(x,h-1,z,BlockType.DIRT);
            world.setBlock(x,h-2,z,BlockType.DIRT);
            world.setBlock(x,h-3,z,BlockType.STONE);
            if(h>3 && random.nextFloat()>.985f) makeTree(world,x,h+1,z);
        }
        return world;
    }
    public static int heightAt(int x,int z,long seed) {
        double n=Math.sin((x+seed%97)*.13)*1.5+Math.cos((z-seed%53)*.11)*1.4+Math.sin((x+z)*.07)*1.5;
        return 3+(int)Math.round(n);
    }
    private static void makeTree(World world,int x,int y,int z) {
        for(int i=0;i<3;i++) world.setBlock(x,y+i,z,BlockType.WOOD);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) for(int dy=2;dy<=4;dy++)
            if(Math.abs(dx)+Math.abs(dz)+(dy==4?1:0)<4) world.setBlock(x+dx,y+dy,z+dz,BlockType.LEAVES);
    }
}

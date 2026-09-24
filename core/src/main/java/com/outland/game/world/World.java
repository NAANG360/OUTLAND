package com.outland.game.world;

import java.util.*;

/** Pure Java voxel storage. No GL objects or libGDX types are permitted here. */
public final class World {
    public static final class Block {
        public final int x, y, z;
        public final BlockType type;
        public Block(int x, int y, int z, BlockType type) {
            this.x=x; this.y=y; this.z=z; this.type=Objects.requireNonNull(type, "type");
        }
    }
    private final Map<Long, Block> blocks = new HashMap<>();
    private final long seed;
    public World(long seed) { this.seed=seed; }
    public long seed() { return seed; }
    public int size() { return blocks.size(); }
    public Block getBlock(int x,int y,int z) { return blocks.get(key(x,y,z)); }
    public boolean setBlock(int x,int y,int z,BlockType type) {
        long k=key(x,y,z); if(blocks.containsKey(k)) return false;
        blocks.put(k,new Block(x,y,z,type)); return true;
    }
    public Block removeBlock(int x,int y,int z) { return blocks.remove(key(x,y,z)); }
    public Collection<Block> snapshot() { return Collections.unmodifiableList(new ArrayList<>(blocks.values())); }
    public void clear() { blocks.clear(); }

    /** Collision-free packed key for the supported prototype coordinate range. */
    public static long key(int x,int y,int z) {
        if(x < -2048 || x > 2047 || z < -2048 || z > 2047 || y < -512 || y > 511)
            throw new IllegalArgumentException("Voxel coordinate out of range: "+x+","+y+","+z);
        return (((long)(x+2048)&4095)<<24) | (((long)(y+512)&1023)<<12) | ((z+2048)&4095L);
    }
}

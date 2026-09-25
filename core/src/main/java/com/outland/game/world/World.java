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
    /** Derived cache for the visible terrain surface. Keyed by x/z column. */
    private final Map<Long, Integer> terrainColumnHeights = new HashMap<>();
    private final long seed;
    private long revision;
    private long lastChangedKey;
    private boolean changedSinceRead;
    public World(long seed) { this.seed=seed; }
    public long seed() { return seed; }
    public long revision() { return revision; }
    public int size() { return blocks.size(); }
    public Block getBlock(int x,int y,int z) { return blocks.get(key(x,y,z)); }

    /** Highest currently stored solid voxel in a column, so mined blocks really open holes. */
    public int highestSolidY(int x,int z) {
        for(int y=511;y>=-512;y--) {
            Block block=blocks.get(key(x,y,z));
            if(block!=null && isSolid(block.type)) return y;
        }
        return -513;
    }

    /** Highest terrain voxel, ignoring trees and other props above the ground. */
    public int highestTerrainY(int x,int z) {
        Integer cached=terrainColumnHeights.get(columnKey(x,z));
        return cached==null?-513:cached;
    }

    public int highestTerrainY(int x,int z,int fromY) {
        int start=Math.min(511,fromY);
        Integer cached=terrainColumnHeights.get(columnKey(x,z));
        if(cached!=null && cached<=start) return cached;
        for(int y=start;y>=-512;y--) {
            Block block=blocks.get(key(x,y,z));
            if(block!=null && isTerrain(block.type)) return y;
        }
        return -513;
    }

    public static boolean isTerrain(BlockType type) {
        return type==BlockType.GRASS || type==BlockType.DIRT || type==BlockType.STONE;
    }

    public static boolean isSolid(BlockType type) {
        return type==BlockType.GRASS || type==BlockType.DIRT || type==BlockType.STONE
                || type==BlockType.WOOD || type==BlockType.CACTUS;
    }
    public boolean setBlock(int x,int y,int z,BlockType type) {
        long k=key(x,y,z); if(blocks.containsKey(k)) return false;
        blocks.put(k,new Block(x,y,z,type));
        if(isTerrain(type)) {
            long column=columnKey(x,z);
            Integer current=terrainColumnHeights.get(column);
            if(current==null || y>current) terrainColumnHeights.put(column,y);
        }
        revision++; markChanged(k); return true;
    }
    /** Replaces an existing block in one revision step; false when no block exists. */
    public boolean replaceBlock(int x,int y,int z,BlockType type) {
        long k=key(x,y,z); Block old=blocks.get(k); if(old==null) return false;
        blocks.put(k,new Block(x,y,z,type));
        refreshTerrainColumn(x,y,z,old.type,type);
        revision++; markChanged(k); return true;
    }
    public Block removeBlock(int x,int y,int z) {
        long k=key(x,y,z); Block removed=blocks.remove(k);
        if(removed!=null){
            if(isTerrain(removed.type) && terrainColumnHeights.getOrDefault(columnKey(x,z),-513)==y)
                rebuildTerrainColumn(x,z);
            revision++; markChanged(k);
        }
        return removed;
    }
    public Collection<Block> snapshot() { return Collections.unmodifiableList(new ArrayList<>(blocks.values())); }

    /** Returns the latest mutated block key once, then clears the pending marker. */
    public Long consumeChangedBlockKey() {
        if(!changedSinceRead) return null;
        changedSinceRead=false;
        return lastChangedKey;
    }

    private void markChanged(long k) {
        lastChangedKey=k;
        changedSinceRead=true;
    }
    public void clear() {
        if(!blocks.isEmpty()){blocks.clear();terrainColumnHeights.clear();revision++;}
    }

    private void refreshTerrainColumn(int x,int y,int z,BlockType oldType,BlockType newType) {
        long column=columnKey(x,z);
        int cached=terrainColumnHeights.getOrDefault(column,-513);
        if(isTerrain(newType) && y>=cached) {
            terrainColumnHeights.put(column,y);
        } else if(isTerrain(oldType) && !isTerrain(newType) && y==cached) {
            rebuildTerrainColumn(x,z);
        } else if(isTerrain(newType) && y<cached) {
            // Highest cached terrain remains unchanged.
        } else if(!isTerrain(oldType) && isTerrain(newType) && y>cached) {
            terrainColumnHeights.put(column,y);
        }
    }

    private void rebuildTerrainColumn(int x,int z) {
        for(int y=511;y>=-512;y--) {
            Block block=blocks.get(key(x,y,z));
            if(block!=null && isTerrain(block.type)) {
                terrainColumnHeights.put(columnKey(x,z),y);
                return;
            }
        }
        terrainColumnHeights.remove(columnKey(x,z));
    }

    private static long columnKey(int x,int z) {
        return ((long)(x+2048)<<32) ^ ((z+2048)&0xffffffffL);
    }

    /** Collision-free packed key for the supported prototype coordinate range. */
    public static int xFromKey(long key) { return (int)((key >>> 24) & 4095L)-2048; }
    public static int yFromKey(long key) { return (int)((key >>> 12) & 1023L)-512; }
    public static int zFromKey(long key) { return (int)(key & 4095L)-2048; }

    public static long key(int x,int y,int z) {
        if(x < -2048 || x > 2047 || z < -2048 || z > 2047 || y < -512 || y > 511)
            throw new IllegalArgumentException("Voxel coordinate out of range: "+x+","+y+","+z);
        return (((long)(x+2048)&4095)<<24) | (((long)(y+512)&1023)<<12) | ((z+2048)&4095L);
    }
}

package com.outland.game.world;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorldTest {
    @Test public void addLookupRemoveUpdatesRevision() {
        World world=new World(1L);
        assertNull(world.getBlock(1,2,3));
        assertTrue(world.setBlock(1,2,3,BlockType.GRASS));
        assertEquals(BlockType.GRASS,world.getBlock(1,2,3).type);
        assertFalse(world.setBlock(1,2,3,BlockType.STONE));
        assertEquals(1,world.size());
        assertNotNull(world.removeBlock(1,2,3));
        assertNull(world.getBlock(1,2,3));
        assertEquals(2L,world.revision());
    }
    @Test public void packedKeysAreDistinctAcrossAxes() {
        assertNotEquals(World.key(1,2,3),World.key(2,2,3));
        assertNotEquals(World.key(1,2,3),World.key(1,3,3));
        assertNotEquals(World.key(1,2,3),World.key(1,2,4));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnrepresentableCoordinates(){World.key(2048,0,0);}
    @Test public void terrainIsDeterministicForSeed() {
        TerrainGenerator generator=new TerrainGenerator(5);
        World a=generator.generate(1234), b=generator.generate(1234);
        assertEquals(a.size(),b.size());
        for(World.Block block:a.snapshot()) {
            World.Block other=b.getBlock(block.x,block.y,block.z);
            assertNotNull(other); assertEquals(block.type,other.type);
        }
    }
}

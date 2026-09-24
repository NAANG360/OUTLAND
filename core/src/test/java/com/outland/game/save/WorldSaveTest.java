package com.outland.game.save;

import com.outland.game.player.PlayerState;
import com.outland.game.world.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldSaveTest {
    @Test public void roundTripsWorldAndPlayer() throws Exception {
        World world=new World(987L);world.setBlock(-4,7,12,BlockType.LEAVES);world.setBlock(0,2,0,BlockType.STONE);
        PlayerState player=new PlayerState();player.position.set(3.5f,9f,-2f);player.yaw=.7f;player.health=83;player.inventory[2]=14;player.selectedBlock=2;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();WorldSave.write(bytes,world,player,world.seed());
        WorldSave.Snapshot loaded=WorldSave.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(987L,loaded.world.seed());assertEquals(2,loaded.world.size());
        assertEquals(BlockType.LEAVES,loaded.world.getBlock(-4,7,12).type);
        assertEquals(3.5f,loaded.player.position.x,0f);assertEquals(83,loaded.player.health);
        assertEquals(14,loaded.player.inventory[2]);assertEquals(2,loaded.player.selectedBlock);
    }
    @Test public void rejectsUnknownFormat() throws Exception {
        try { WorldSave.read(new ByteArrayInputStream(new byte[]{0,0,0,0,0,0,0,1}));fail("expected IOException"); }
        catch(IOException expected) { assertTrue(expected.getMessage().contains("OUTLAND")); }
    }
}

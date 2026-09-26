package com.outland.game.player;

import com.outland.game.input.InputState;
import com.outland.game.world.BlockType;
import com.outland.game.world.TerrainGenerator;
import com.outland.game.world.World;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerControllerTest {
    @Test public void clampsPlayerToLiveWorldFloor() {
        World world=new TerrainGenerator(5).generate(42L);
        PlayerState player=new PlayerState();
        player.position.set(0,-10,0);
        new PlayerController().update(player,new InputState(),.2f,world);
        assertTrue(player.grounded);
        assertEquals(world.highestTerrainY(0,0)+2.2f,player.position.y,.0001f);
    }

    @Test public void minedBlockChangesCollisionFloor() {
        World world=new World(42L);
        world.setBlock(0,0,0,BlockType.STONE);
        world.setBlock(0,1,0,BlockType.GRASS);
        PlayerState player=new PlayerState();
        player.position.set(0,3.2f,0);
        player.grounded=true;
        world.removeBlock(0,1,0);
        new PlayerController().update(player,new InputState(),.033f,world);
        assertTrue("Player should begin falling through the mined space",player.position.y<3.2f);
        for(int i=0;i<40;i++) new PlayerController().update(player,new InputState(),.033f,world);
        assertEquals(world.highestTerrainY(0,0)+2.2f,player.position.y,.0001f);
    }

    @Test public void jumpFollowsContinuousArc() {
        World world=new World(42L);
        world.setBlock(0,0,0,BlockType.STONE);
        PlayerState player=new PlayerState();
        player.position.set(0,2.2f,0);
        player.grounded=true;
        InputState input=new InputState();
        input.jump=true;
        new PlayerController().update(player,input,.016f,world);
        float first=player.position.y;
        input.jump=false;
        new PlayerController().update(player,input,.016f,world);
        assertTrue(first>2.2f);
        assertTrue(player.position.y>2.2f);
        assertFalse(player.grounded);
    }
}

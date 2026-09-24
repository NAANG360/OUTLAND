package com.outland.game.player;

import com.outland.game.input.InputState;
import com.outland.game.world.TerrainGenerator;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerControllerTest {
    @Test public void clampsPlayerToTerrainAndMarksGrounded() {
        PlayerState player=new PlayerState();player.position.set(0,-10,0);
        new PlayerController().update(player,new InputState(),.2f,42L);
        assertTrue(player.grounded);
        assertEquals(TerrainGenerator.heightAt(0,0,42L)+1.7f,player.position.y,.0001f);
    }
    @Test public void jumpMovesPlayerAboveGround() {
        PlayerState player=new PlayerState();player.position.set(0,TerrainGenerator.heightAt(0,0,42L)+1.7f,0);player.grounded=true;
        InputState input=new InputState();input.jump=true;
        new PlayerController().update(player,input,.033f,42L);
        assertFalse(player.grounded);assertTrue(player.position.y>TerrainGenerator.heightAt(0,0,42L)+1.7f);
    }
}

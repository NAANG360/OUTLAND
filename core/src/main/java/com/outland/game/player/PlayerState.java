package com.outland.game.player;

import com.badlogic.gdx.math.Vector3;
import com.outland.game.world.BlockType;

/** Mutable simulation state; intentionally contains no renderer-owned objects. */
public final class PlayerState {
    public final Vector3 position = new Vector3(0, 8, 0);
    public float yaw, pitch, verticalVelocity;
    public boolean grounded;
    public int health=100, selectedBlock;
    /** One inventory slot per registered block type; keeps block IDs and inventory indices aligned. */
    public final int[] inventory = new int[BlockType.values().length];
    public PlayerState() {
        inventory[BlockType.GRASS.id()]=24;
        inventory[BlockType.DIRT.id()]=16;
    }
    public Vector3 forward(Vector3 out) { return out.set((float)Math.sin(yaw),0,(float)-Math.cos(yaw)).nor(); }
}

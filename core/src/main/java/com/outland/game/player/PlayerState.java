package com.outland.game.player;

import com.badlogic.gdx.math.Vector3;

/** Mutable simulation state; intentionally contains no renderer-owned objects. */
public final class PlayerState {
    public final Vector3 position = new Vector3(0, 8, 0);
    public float yaw, pitch, verticalVelocity;
    public boolean grounded;
    public int health=100, selectedBlock;
    public final int[] inventory={24,16,0,0,0};
    public Vector3 forward(Vector3 out) { return out.set((float)Math.sin(yaw),0,(float)-Math.cos(yaw)).nor(); }
}

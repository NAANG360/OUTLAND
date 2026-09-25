package com.outland.game.player;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.outland.game.input.InputState;
import com.outland.game.world.TerrainGenerator;

/** Frame-rate-bounded first-person movement and grounding. */
public final class PlayerController {
    private final Vector3 forward=new Vector3(), right=new Vector3();
    public void update(PlayerState player, InputState input, float delta, long seed) {
        float dt=MathUtils.clamp(delta,0f,0.033f);
        // Screen drag to the right turns the camera to the right.
        player.yaw+=input.lookX*.004f;
        player.pitch=MathUtils.clamp(player.pitch-input.lookY*.004f,-1.25f,1.25f);
        player.forward(forward);
        // Y x forward is the player's true right vector. The old forward x Y
        // vector mirrored strafing, making A/D feel swapped.
        right.set(Vector3.Y).crs(forward).nor();
        player.position.mulAdd(forward,input.forward*4.2f*dt).mulAdd(right,input.strafe*4.2f*dt);
        if(input.jump && player.grounded){player.verticalVelocity=6.5f;player.grounded=false;}
        player.verticalVelocity-=17f*dt;
        player.position.y+=player.verticalVelocity*dt;
        int ground=TerrainGenerator.heightAt(Math.round(player.position.x),Math.round(player.position.z),seed);
        if(player.position.y<ground+1.7f){player.position.y=ground+1.7f;player.verticalVelocity=0;player.grounded=true;}
    }
}

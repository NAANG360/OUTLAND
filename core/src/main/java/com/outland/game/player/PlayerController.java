package com.outland.game.player;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.outland.game.input.InputState;
import com.outland.game.world.World;

/** Frame-rate-bounded first-person movement with real world-backed grounding. */
public final class PlayerController {
    private static final float EYE_HEIGHT=2.2f;
    private static final float WALK_SPEED=4.2f;
    private static final float GRAVITY=17f;
    private static final float JUMP_SPEED=6.5f;
    private static final float MAX_STEP=.7f;

    private final Vector3 forward=new Vector3(), right=new Vector3();
    private final Vector3 oldPosition=new Vector3();

    public void update(PlayerState player, InputState input, float delta, World world) {
        float dt=MathUtils.clamp(delta,0f,0.033f);

        player.yaw+=input.lookX*.004f;
        player.pitch=MathUtils.clamp(player.pitch-input.lookY*.004f,-1.25f,1.25f);

        oldPosition.set(player.position);
        player.forward(forward);
        right.set(forward).crs(Vector3.Y).nor();
        player.position.mulAdd(forward,input.forward*WALK_SPEED*dt)
                .mulAdd(right,input.strafe*WALK_SPEED*dt);

        int scanY=Math.min(511,MathUtils.floor(player.position.y)+4);
        int oldGround=world.highestTerrainY(MathUtils.round(oldPosition.x),MathUtils.round(oldPosition.z),scanY);
        int newGround=world.highestTerrainY(MathUtils.round(player.position.x),MathUtils.round(player.position.z),scanY);
        if(player.grounded && newGround>oldGround+MAX_STEP) {
            player.position.x=oldPosition.x;
            player.position.z=oldPosition.z;
            newGround=oldGround;
        }

        if(input.jump && player.grounded){
            player.verticalVelocity=JUMP_SPEED;
            player.grounded=false;
        }

        player.verticalVelocity-=GRAVITY*dt;
        player.position.y+=player.verticalVelocity*dt;

        float floorY=newGround<-512 ? -510f : newGround+EYE_HEIGHT;
        if(player.position.y<floorY){
            player.position.y=floorY;
            player.verticalVelocity=0f;
            player.grounded=true;
        } else {
            player.grounded=false;
        }
    }
}

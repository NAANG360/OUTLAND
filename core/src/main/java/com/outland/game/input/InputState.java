package com.outland.game.input;

/** Per-frame intent, decoupled from Android/libGDX event delivery. */
public final class InputState {
    public float forward, strafe, lookX, lookY;
    public boolean jump, mine, place, nextBlock;
    public void clearTransient() { jump=false; mine=false; place=false; nextBlock=false; lookX=lookY=0; }
}

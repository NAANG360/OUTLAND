package com.outland.game.engine;

/**
 * Fixed-step simulation clock. Rendering can run at whatever cadence the device
 * supplies while gameplay remains deterministic at 60 simulation steps/second.
 */
public final class FrameClock {
    private float accumulator;

    public int advance(float frameDelta,Step step){
        float dt=Math.min(Math.max(frameDelta,0f),EngineConfig.MAX_FRAME_DELTA);
        accumulator+=dt;
        int steps=0;
        while(accumulator>=EngineConfig.FIXED_STEP && steps<4){
            step.tick(EngineConfig.FIXED_STEP);
            accumulator-=EngineConfig.FIXED_STEP;
            steps++;
        }
        if(steps==4) accumulator=0f;
        return steps;
    }

    public float alpha(){
        return accumulator/EngineConfig.FIXED_STEP;
    }

    public void reset(){accumulator=0f;}

    public interface Step{
        void tick(float dt);
    }
}

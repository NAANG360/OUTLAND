package com.outland.game.engine;

/** Runtime tuning for the rebuilt mobile-first engine. */
public final class EngineConfig {
    private EngineConfig(){}

    /** Simulation remains deterministic and independent of render cadence. */
    public static final float FIXED_STEP=1f/60f;
    public static final float MAX_FRAME_DELTA=.10f;

    /** World streaming is chunk based; rendering uses a conservative distance ring. */
    public static final int CHUNK_SIZE=8;
    public static final int ACTIVE_CHUNK_RADIUS=7;
    public static final float RENDER_DISTANCE=64f;

    /** Keep procedural generation work out of the render loop. */
    public static final int MAX_GENERATION_COLUMNS_PER_FRAME=96;

    /** Low-end friendly geometry budgets. */
    public static final int MAX_VISIBLE_CHUNKS=225;
    public static final int MAX_PROP_INSTANCES_PER_CHUNK=160;

    /** Camera defaults tuned for a stable mobile perspective. */
    public static final float CAMERA_FOV=70f;
    public static final float CAMERA_NEAR=.08f;
    public static final float CAMERA_FAR=96f;
}

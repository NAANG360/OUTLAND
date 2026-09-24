package com.outland.game.player;

/** Frame-rate-independent survival needs. Values are normalized to [0, 100]. */
public final class SurvivalState {
    private float hunger = 100f;
    private float thirst = 100f;

    /** Advances needs by elapsed seconds; invalid time is ignored. */
    public void advance(float seconds) {
        if (!(seconds > 0f) || Float.isInfinite(seconds)) return;
        hunger = clamp(hunger - seconds / 60f);
        thirst = clamp(thirst - seconds / 40f);
    }

    public void consumeFood(float nutrition) {
        if (nutrition > 0f && !Float.isInfinite(nutrition)) hunger = clamp(hunger + nutrition);
    }

    public void drinkWater(float hydration) {
        if (hydration > 0f && !Float.isInfinite(hydration)) thirst = clamp(thirst + hydration);
    }

    public float hunger() { return hunger; }
    public float thirst() { return thirst; }

    public void restore(float hunger, float thirst) {
        this.hunger = clamp(hunger);
        this.thirst = clamp(thirst);
    }

    private static float clamp(float value) {
        if (Float.isNaN(value) || value <= 0f) return 0f;
        return Math.min(100f, value);
    }
}

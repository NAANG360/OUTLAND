package com.outland.game.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class SurvivalStateTest {
    @Test public void needsDrainOnlyDuringElapsedTimeAndClampAtZero() {
        SurvivalState state = new SurvivalState();
        state.advance(60f);
        assertEquals(99f, state.hunger(), 0.001f);
        assertEquals(98.5f, state.thirst(), 0.001f);
        state.advance(10000f);
        assertEquals(0f, state.hunger(), 0f);
        assertEquals(0f, state.thirst(), 0f);
    }

    @Test public void invalidOrNegativeTimeCannotCreateOrDrainNeeds() {
        SurvivalState state = new SurvivalState();
        state.advance(-10f);
        state.advance(Float.NaN);
        assertEquals(100f, state.hunger(), 0f);
        assertEquals(100f, state.thirst(), 0f);
    }

    @Test public void suppliesRestoreNeedsWithinBounds() {
        SurvivalState state = new SurvivalState();
        state.advance(600f);
        state.consumeFood(500f);
        state.drinkWater(500f);
        assertEquals(100f, state.hunger(), 0f);
        assertEquals(100f, state.thirst(), 0f);
        state.consumeFood(-5f);
        assertEquals(100f, state.hunger(), 0f);
    }
}

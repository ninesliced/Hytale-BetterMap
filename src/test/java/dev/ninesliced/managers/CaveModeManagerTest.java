package dev.ninesliced.managers;

import dev.ninesliced.managers.CaveModeManager.DynamicCaveModeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaveModeManagerTest {
    @Test
    void thresholdFiveMeansBelowYFiveRatherThanFiveBlocksBelowTheSurface() {
        var state = new DynamicCaveModeState();
        state.setUndergroundThreshold(5);

        assertFalse(CaveModeManager.updateUndergroundState(state, 70, true));
        assertFalse(state.isCurrentlyUnderground());
        assertFalse(CaveModeManager.updateUndergroundState(state, 5, true));
        assertTrue(CaveModeManager.updateUndergroundState(state, 4, true));
        assertTrue(state.isCurrentlyUnderground());
        assertEquals(4, state.getRenderYLevel());
        assertTrue(CaveModeManager.updateUndergroundState(state, 5, true));
        assertFalse(state.isCurrentlyUnderground());
    }

    @Test
    void defaultThresholdAllowsEntryLayerChangesAndReturnToSurface() {
        var state = new DynamicCaveModeState();
        state.setLayerSize(10);

        assertTrue(CaveModeManager.updateUndergroundState(state, 75, true));
        assertTrue(state.isCurrentlyUnderground());
        assertEquals(70, state.getCurrentLayer());
        assertFalse(state.isLayerChanged());

        assertFalse(CaveModeManager.updateUndergroundState(state, 65, true));
        assertTrue(state.isLayerChanged());
        assertEquals(70, state.getPreviousLayer());
        assertEquals(60, state.getCurrentLayer());
        assertEquals(65, state.getRenderYLevel());

        assertFalse(CaveModeManager.updateUndergroundState(state, 64, true));
        assertFalse(state.isLayerChanged());
        assertTrue(CaveModeManager.updateUndergroundState(state, 100, true));
        assertFalse(state.isCurrentlyUnderground());
    }

    @Test
    void disablingCaveModeExitsTheOverlayEvenBelowTheThreshold() {
        var state = new DynamicCaveModeState();
        CaveModeManager.updateUndergroundState(state, 65, true);
        state.setDynamicModeEnabled(false);

        assertTrue(CaveModeManager.updateUndergroundState(state, 65, true));
        assertFalse(state.isCurrentlyUnderground());
        assertFalse(state.isLayerChanged());
    }
}

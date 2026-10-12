package dev.ninesliced.providers;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LocationHudProviderTest {
    @Test
    void mapVisibilityIsPerPlayerAndClearedOnCloseAndCleanup() {
        var provider = new LocationHudProvider();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        provider.setWorldMapVisible(first, true);
        assertTrue(provider.isWorldMapVisible(first));
        assertFalse(provider.isWorldMapVisible(second));
        provider.setWorldMapVisible(first, false);
        assertFalse(provider.isWorldMapVisible(first));
        provider.setWorldMapVisible(second, true);
        provider.cleanup();
        assertFalse(provider.isWorldMapVisible(second));
    }
}

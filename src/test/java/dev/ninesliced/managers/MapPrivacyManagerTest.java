package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager.MarkerProvider;
import dev.ninesliced.providers.PlayerRadarProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapPrivacyManagerTest {
    private static final MarkerProvider NATIVE_PLAYERS = (world, player, collector) -> {};
    private static final MarkerProvider RADAR = new PlayerRadarProvider();

    @Test
    void installsRadarBeforeRemovingNativeIconsInAnUntrackedInstance() {
        Map<String, MarkerProvider> providers = new HashMap<>() {
            @Override
            public MarkerProvider remove(Object key) {
                assertSame(RADAR, get(PlayerRadarProvider.PROVIDER_ID));
                return super.remove(key);
            }
        };
        providers.put("players", NATIVE_PLAYERS);
        var backups = new HashMap<String, MarkerProvider>();

        MapPrivacyManager.synchronizePlayerProviders(providers, backups, RADAR, true);

        assertSame(RADAR, providers.get(PlayerRadarProvider.PROVIDER_ID));
        assertFalse(providers.containsKey("players"));
        assertSame(NATIVE_PLAYERS, backups.get("players"));
    }

    @Test
    void registersInEachNewInstanceAndRepairsARemovedReplacement() {
        var firstInstance = new HashMap<String, MarkerProvider>();
        var recreatedInstance = new HashMap<String, MarkerProvider>();
        var backups = new HashMap<String, MarkerProvider>();

        MapPrivacyManager.synchronizePlayerProviders(firstInstance, backups, RADAR, true);
        MapPrivacyManager.synchronizePlayerProviders(recreatedInstance, backups, RADAR, true);
        assertSame(RADAR, recreatedInstance.get(PlayerRadarProvider.PROVIDER_ID));

        firstInstance.clear();
        MapPrivacyManager.synchronizePlayerProviders(firstInstance, backups, RADAR, true);
        MapPrivacyManager.synchronizePlayerProviders(firstInstance, backups, RADAR, true);
        assertEquals(Map.of(PlayerRadarProvider.PROVIDER_ID, RADAR), firstInstance);
    }

    @Test
    void disablingRadarRestoresBothNativeProviderNames() {
        var providers = new HashMap<String, MarkerProvider>();
        providers.put("players", NATIVE_PLAYERS);
        providers.put("playerIcons", NATIVE_PLAYERS);
        var backups = new HashMap<String, MarkerProvider>();

        MapPrivacyManager.synchronizePlayerProviders(providers, backups, RADAR, true);
        MapPrivacyManager.synchronizePlayerProviders(providers, backups, RADAR, true);
        MapPrivacyManager.synchronizePlayerProviders(providers, backups, null, false);

        assertSame(NATIVE_PLAYERS, providers.get("players"));
        assertSame(NATIVE_PLAYERS, providers.get("playerIcons"));
    }

    @Test
    void globalHideStillRemovesNativeIconsWhenRadarIsDisabled() {
        var providers = new HashMap<String, MarkerProvider>();
        providers.put("players", NATIVE_PLAYERS);
        var backups = new HashMap<String, MarkerProvider>();

        MapPrivacyManager.synchronizePlayerProviders(providers, backups, null, true);

        assertTrue(providers.isEmpty());
        assertSame(NATIVE_PLAYERS, backups.get("players"));
    }
}

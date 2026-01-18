package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.compat.EasyClaimsCompat;
import dev.ninesliced.listeners.ExplorationEventListener;
import dev.ninesliced.providers.EasyClaimsClaimMarkerProvider;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Installs a BetterMap-aware EasyClaims marker provider.
 */
public final class EasyClaimsCompatManager {
    private static final Logger LOGGER = Logger.getLogger(EasyClaimsCompatManager.class.getName());
    private static final String EASYCLAIMS_PROVIDER_CLASS = "com.easyclaims.map.ClaimMapOverlayProvider";
    private static final String PROVIDER_ID = "BetterMapEasyClaimsOverlay";
    private static final EasyClaimsClaimMarkerProvider PROVIDER = new EasyClaimsClaimMarkerProvider();

    private EasyClaimsCompatManager() {
    }

    /**
     * Replaces the EasyClaims overlay provider in the given world with a filtered version.
     */
    public static void apply(World world) {
        if (world == null) {
            return;
        }
        if (!EasyClaimsCompat.isAvailable()) {
            return;
        }
        if (!ExplorationEventListener.isTrackedWorld(world)) {
            return;
        }

        WorldMapManager mapManager = world.getWorldMapManager();
        if (mapManager == null) {
            return;
        }

        Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
        if (providers == null) {
            return;
        }

        boolean removedOriginal = false;
        for (Iterator<Map.Entry<String, WorldMapManager.MarkerProvider>> it = providers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, WorldMapManager.MarkerProvider> entry = it.next();
            WorldMapManager.MarkerProvider provider = entry.getValue();
            if (provider != null && provider.getClass().getName().equals(EASYCLAIMS_PROVIDER_CLASS)) {
                it.remove();
                removedOriginal = true;
            }
        }

        if (!(providers.get(PROVIDER_ID) instanceof EasyClaimsClaimMarkerProvider)) {
            mapManager.addMarkerProvider(PROVIDER_ID, PROVIDER);
        }

        if (removedOriginal) {
            LOGGER.fine("Replaced EasyClaims overlay provider for world: " + world.getName());
        }
    }
}

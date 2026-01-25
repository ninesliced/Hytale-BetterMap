package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.providers.PoiPrivacyProvider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages visibility of POI markers on the world map.
 */
public class PoiPrivacyManager {
    private static final Logger LOGGER = Logger.getLogger(PoiPrivacyManager.class.getName());
    private static PoiPrivacyManager instance;
    private final Set<World> monitoredWorlds = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<World, WorldMapManager.MarkerProvider> backedUpProviders = new WeakHashMap<>();
    private final PoiPrivacyProvider poiPrivacyProvider = new PoiPrivacyProvider();

    private PoiPrivacyManager() {
    }

    public static synchronized PoiPrivacyManager getInstance() {
        if (instance == null) {
            instance = new PoiPrivacyManager();
        }
        return instance;
    }

    public void initialize() {
        BetterMap plugin = BetterMap.get();
        if (plugin == null) {
            LOGGER.severe("BetterMap instance is null, cannot initialize PoiPrivacyManager");
            return;
        }

        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            World world = event.getWorld();
            if (world != null) {
                this.monitoredWorlds.add(world);
            }
            if (shouldFilterPois()) {
                this.replaceProvider(world);
            }
        });

        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            World world = event.getPlayer().getWorld();
            if (world != null) {
                this.monitoredWorlds.add(world);
            }
            if (shouldFilterPois()) {
                this.replaceProvider(world);
            }
        });

        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                if (!shouldFilterPois()) return;

                for (World world : this.monitoredWorlds) {
                    if (world == null) continue;
                    this.replaceProvider(world);
                }
            } catch (Exception e) {
                LOGGER.warning("Error in POI privacy provider poller: " + e.getMessage());
            }
        }, 5L, 30L, TimeUnit.SECONDS);

        LOGGER.info("PoiPrivacyManager initialized.");
    }

    /**
     * Updates the POI visibility state for all tracked worlds.
     */
    public void updatePrivacyState() {
        boolean filter = shouldFilterPois();

        try {
            for (World world : this.monitoredWorlds) {
                if (world == null) continue;

                if (filter) {
                    this.replaceProvider(world);
                } else {
                    this.restoreProvider(world);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error updating POI privacy state: " + e.getMessage());
        }
    }

    private void replaceProvider(World world) {
        try {
            if (world == null) return;

            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) return;

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            WorldMapManager.MarkerProvider existing = providers.get(PoiPrivacyProvider.PROVIDER_ID);
            if (existing == null || existing instanceof PoiPrivacyProvider) {
                return;
            }

            backedUpProviders.putIfAbsent(world, existing);
            providers.put(PoiPrivacyProvider.PROVIDER_ID, poiPrivacyProvider);

            if (ModConfig.getInstance().isDebug()) {
                LOGGER.info("Replaced POI provider in world " + world.getName());
            }
        } catch (Exception e) {
            LOGGER.severe("Error replacing POI provider: " + e.getMessage());
        }
    }

    private void restoreProvider(World world) {
        try {
            if (world == null) return;

            WorldMapManager.MarkerProvider original = backedUpProviders.remove(world);
            if (original == null) return;

            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) return;

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            providers.put(PoiPrivacyProvider.PROVIDER_ID, original);

            if (ModConfig.getInstance().isDebug()) {
                LOGGER.info("Restored POI provider in world " + world.getName());
            }
        } catch (Exception e) {
            LOGGER.severe("Error restoring POI provider: " + e.getMessage());
        }
    }

    private boolean shouldFilterPois() {
        ModConfig config = ModConfig.getInstance();
        return config.isHideAllPoiOnMap()
            || config.isHideUnexploredPoiOnMap()
            || (config.getHiddenPoiNames() != null && !config.getHiddenPoiNames().isEmpty());
    }
}

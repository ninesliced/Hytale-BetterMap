package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.providers.WarpPrivacyProvider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages visibility of other players' warp markers on the world map.
 */
public class WarpPrivacyManager {
    private static final Logger LOGGER = Logger.getLogger(WarpPrivacyManager.class.getName());
    private static WarpPrivacyManager instance;
    private final Set<World> monitoredWorlds = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<World, WorldMapManager.MarkerProvider> backedUpProviders = new WeakHashMap<>();
    private final WarpPrivacyProvider warpPrivacyProvider = new WarpPrivacyProvider();

    private WarpPrivacyManager() {
    }

    public static synchronized WarpPrivacyManager getInstance() {
        if (instance == null) {
            instance = new WarpPrivacyManager();
        }
        return instance;
    }

    public void initialize() {
        BetterMap plugin = BetterMap.get();
        if (plugin == null) {
            LOGGER.severe("BetterMap instance is null, cannot initialize WarpPrivacyManager");
            return;
        }

        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            World world = event.getWorld();
            if (world != null) {
                this.monitoredWorlds.add(world);
            }
            if (shouldFilterWarps()) {
                this.replaceProvider(world);
            }
        });

        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            World world = event.getPlayer().getWorld();
            if (world != null) {
                this.monitoredWorlds.add(world);
            }
            if (shouldFilterWarps()) {
                this.replaceProvider(world);
            }
        });

        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                if (!shouldFilterWarps()) return;

                for (World world : this.monitoredWorlds) {
                    if (world == null) continue;
                    this.replaceProvider(world);
                }
            } catch (Exception e) {
                LOGGER.warning("Error in warp privacy provider poller: " + e.getMessage());
            }
        }, 5L, 30L, TimeUnit.SECONDS);

        LOGGER.info("WarpPrivacyManager initialized.");
    }

    /**
     * Updates the warp visibility state for all tracked worlds.
     */
    public void updatePrivacyState() {
        boolean hide = shouldFilterWarps();

        try {
            for (World world : this.monitoredWorlds) {
                if (world == null) continue;

                if (hide) {
                    this.replaceProvider(world);
                } else {
                    this.restoreProvider(world);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error updating warp privacy state: " + e.getMessage());
        }
    }

    private void replaceProvider(World world) {
        try {
            if (world == null) return;

            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) return;

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            WorldMapManager.MarkerProvider existing = providers.get(WarpPrivacyProvider.PROVIDER_ID);
            if (existing == null || existing instanceof WarpPrivacyProvider) {
                return;
            }

            backedUpProviders.putIfAbsent(world, existing);
            providers.put(WarpPrivacyProvider.PROVIDER_ID, warpPrivacyProvider);

            if (ModConfig.getInstance().isDebug()) {
                LOGGER.info("Replaced warp provider in world " + world.getName());
            }
        } catch (Exception e) {
            LOGGER.severe("Error replacing warp provider: " + e.getMessage());
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

            providers.put(WarpPrivacyProvider.PROVIDER_ID, original);

            if (ModConfig.getInstance().isDebug()) {
                LOGGER.info("Restored warp provider in world " + world.getName());
            }
        } catch (Exception e) {
            LOGGER.severe("Error restoring warp provider: " + e.getMessage());
        }
    }

    private boolean shouldFilterWarps() {
        ModConfig config = ModConfig.getInstance();
        return config.isHideOtherWarpsOnMap()
            || config.isHideUnexploredWarpsOnMap();
    }
}

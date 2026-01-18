package dev.ninesliced.managers;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.BetterMapConfig;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages player privacy on the map by hiding players if configured.
 * Implements logic similar to the NoPlayersOnMap mod but integrated into BetterMap.
 */
public class MapPrivacyManager {
    private static final Logger LOGGER = Logger.getLogger(MapPrivacyManager.class.getName());
    private static MapPrivacyManager instance;
    private final Set<World> monitoredWorlds = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<World, Map<String, WorldMapManager.MarkerProvider>> backedUpProviders = new WeakHashMap<>();

    private MapPrivacyManager() {
    }

    /**
     * Gets the singleton instance of MapPrivacyManager.
     *
     * @return The instance.
     */
    public static synchronized MapPrivacyManager getInstance() {
        if (instance == null) {
            instance = new MapPrivacyManager();
        }
        return instance;
    }

    /**
     * Initializes the manager, registering event listeners and scheduled tasks.
     */
    public void initialize() {
        BetterMap plugin = BetterMap.get();
        if (plugin == null) {
            LOGGER.severe("BetterMap instance is null, cannot initialize MapPrivacyManager");
            return;
        }

        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null) return;
            Holder<EntityStore> holder = playerRef.getHolder();
            if (holder == null) return;
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) return;

            this.applyPlayerSettings(player, event.getWorld());
        });

        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            this.applyPlayerSettings(event.getPlayer(), event.getPlayer().getWorld());
        });

        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                if (!BetterMapConfig.getInstance().isHidePlayersOnMap()) return;

                for (World world : this.monitoredWorlds) {
                    if (world == null) continue;
                    this.removeProvider(world);
                }
            } catch (Exception e) {
                LOGGER.warning("Error in privacy provider poller: " + e.getMessage());
            }
        }, 5L, 30L, TimeUnit.SECONDS);

        LOGGER.info("MapPrivacyManager initialized.");
    }

    /**
     * Updates the privacy state for all players and worlds immediately.
     * This can be called to refresh the privacy settings without restarting the server.
     */
    public void updatePrivacyState() {
        BetterMapConfig config = BetterMapConfig.getInstance();
        boolean hide = config.isHidePlayersOnMap();
        boolean allowMarkerTeleports = config.isAllowMapMarkerTeleports();

        try {
            for (World world : this.monitoredWorlds) {
                if (world == null) continue;

                if (hide) {
                    this.removeProvider(world);
                }

                try {
                    for (PlayerRef playerRef : world.getPlayerRefs()) {
                        if (playerRef == null) continue;

                        Holder<EntityStore> holder = playerRef.getHolder();
                        if (holder == null) continue;
                        Player player = holder.getComponent(Player.getComponentType());
                        if (player == null) continue;

                        WorldMapTracker tracker = player.getWorldMapTracker();
                        tracker.setPlayerMapFilter(_ -> !hide);
                        tracker.setAllowTeleportToMarkers(world, allowMarkerTeleports);
                    }
                } catch (Exception e) {}
            }

        } catch (Exception e) {
            LOGGER.severe("Error updating privacy state: " + e.getMessage());
        }
    }

    private void applyPlayerSettings(Player player, World world) {
        BetterMapConfig config = BetterMapConfig.getInstance();
        boolean hide = config.isHidePlayersOnMap();
        boolean allowMarkerTeleports = config.isAllowMapMarkerTeleports();

        if (world != null) {
            this.monitoredWorlds.add(world);
            if (hide) {
                this.removeProvider(world);
            }
        }

        try {
            WorldMapTracker tracker = player.getWorldMapTracker();
            tracker.setPlayerMapFilter(_ -> !hide);
            tracker.setAllowTeleportToMarkers(world, allowMarkerTeleports);
        } catch (Exception e) {
            LOGGER.severe("Error applying privacy filter: " + e.getMessage());
        }
    }

    private void removeProvider(World world) {
        try {
            if (world == null) return;

            WorldMapManager mapManager = world.getWorldMapManager();

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            List<String> targetKeys = Arrays.asList("playerMarkers", "playerIcons", "players");

            Map<String, WorldMapManager.MarkerProvider> worldBackups = backedUpProviders.computeIfAbsent(world, _ -> new HashMap<>());

            for (String key : targetKeys) {
                if (!providers.containsKey(key)) continue;

                // Backup before remove
                worldBackups.put(key, providers.get(key));

                providers.remove(key);

                if (BetterMapConfig.getInstance().isDebug()) {
                    LOGGER.info("Removed and backed up provider key: " + key + " from world " + world.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error removing provider: " + e.getMessage());
        }
    }

    private void restoreProvider(World world) {
        try {
            if (world == null) return;

            Map<String, WorldMapManager.MarkerProvider> backups = backedUpProviders.remove(world);
            if (backups == null || backups.isEmpty()) return;

            WorldMapManager mapManager = world.getWorldMapManager();

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            for (Map.Entry<String, WorldMapManager.MarkerProvider> entry : backups.entrySet()) {
                if (!providers.containsKey(entry.getKey())) {
                    providers.put(entry.getKey(), entry.getValue());
                    if (BetterMapConfig.getInstance().isDebug()) {
                        LOGGER.info("Restored provider key: " + entry.getKey() + " to world " + world.getName());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error restoring provider: " + e.getMessage());
        }
    }
}

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
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.utils.PermissionsUtil;

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
        boolean radarEnabled = config.isRadarEnabled();
        int radarRange = config.getRadarRange();
        boolean allowMarkerTeleports = config.isAllowMapMarkerTeleports();

        try {
            for (World world : this.monitoredWorlds) {
                if (world == null) continue;

                world.execute(() -> {
                    this.removeProvider(world);

                    try {
                        for (PlayerRef playerRef : world.getPlayerRefs()) {
                            if (playerRef == null) continue;

                            Holder<EntityStore> holder = playerRef.getHolder();
                            if (holder == null) continue;
                            Player player = holder.getComponent(Player.getComponentType());
                            if (player == null) continue;

                            WorldMapTracker tracker = player.getWorldMapTracker();

                            if (hide) {
                                tracker.setPlayerMapFilter(ignored -> false);
                            } else if (radarEnabled && radarRange >= 0) {
                                final int rangeSq = radarRange * radarRange;
                                final String worldName = world.getName();
                                final String viewerUuid = playerRef.getUuid().toString();

                                tracker.setPlayerMapFilter(otherPlayer -> {
                                    try {
                                        var radarDataList = PlayerRadarManager.getInstance().getRadarData(worldName);

                                        PlayerRadarManager.RadarData viewerData = null;
                                        for (var data : radarDataList) {
                                            if (data.uuid.equals(viewerUuid)) {
                                                viewerData = data;
                                                break;
                                            }
                                        }
                                        if (viewerData == null) return true;

                                        String otherUuid = null;
                                        var otherRef = otherPlayer.getReference();
                                        if (otherRef != null) {
                                            var store = otherRef.getStore();
                                            var otherPlayerRef = store.getComponent(otherRef, PlayerRef.getComponentType());
                                            if (otherPlayerRef != null) {
                                                otherUuid = otherPlayerRef.getUuid().toString();
                                            }
                                        }
                                        if (otherUuid == null) return true;

                                        PlayerRadarManager.RadarData otherData = null;
                                        for (var data : radarDataList) {
                                            if (data.uuid.equals(otherUuid)) {
                                                otherData = data;
                                                break;
                                            }
                                        }
                                        if (otherData == null) return true;

                                        double dx = otherData.position.x - viewerData.position.x;
                                        double dy = otherData.position.y - viewerData.position.y;
                                        double dz = otherData.position.z - viewerData.position.z;
                                        double distanceSquared = dx * dx + dy * dy + dz * dz;

                                        return distanceSquared <= rangeSq;
                                    } catch (Exception e) {
                                        return true;
                                    }
                                });
                            } else {
                                tracker.setPlayerMapFilter(null);
                            }

                            // TODO: setAllowTeleportToMarkers method no longer exists in the new API
                            // boolean canTeleportMarkers = allowMarkerTeleports && PermissionsUtil.canTeleport(player);
                            // tracker.setAllowTeleportToMarkers(world, canTeleportMarkers);
                        }
                    } catch (Exception _) {}
                });
            }

        } catch (Exception e) {
            LOGGER.severe("Error updating privacy state: " + e.getMessage());
        }
    }

    private void applyPlayerSettings(Player player, World world) {
        BetterMapConfig config = BetterMapConfig.getInstance();
        boolean hide = config.isHidePlayersOnMap();
        boolean radarEnabled = config.isRadarEnabled();
        int radarRange = config.getRadarRange();
        boolean allowMarkerTeleports = config.isAllowMapMarkerTeleports();

        if (world != null) {
            this.monitoredWorlds.add(world);
            this.removeProvider(world);
        }

        try {
            WorldMapTracker tracker = player.getWorldMapTracker();

            if (hide) {
                tracker.setPlayerMapFilter(ignored -> false);
            } else if (radarEnabled && radarRange >= 0 && world != null) {
                final int rangeSq = radarRange * radarRange;
                final String worldName = world.getName();

                String viewerUuid = null;
                var ref = player.getReference();
                if (ref != null) {
                    var store = ref.getStore();
                    var pRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (pRef != null) {
                        viewerUuid = pRef.getUuid().toString();
                    }
                }
                final String finalViewerUuid = viewerUuid;

                if (finalViewerUuid != null) {
                    tracker.setPlayerMapFilter(otherPlayer -> {
                        try {
                            var radarDataList = PlayerRadarManager.getInstance().getRadarData(worldName);

                            PlayerRadarManager.RadarData viewerData = null;
                            for (var data : radarDataList) {
                                if (data.uuid.equals(finalViewerUuid)) {
                                    viewerData = data;
                                    break;
                                }
                            }
                            if (viewerData == null) return true;

                            String otherUuid = null;
                            var otherRef = otherPlayer.getReference();
                            if (otherRef != null) {
                                var store = otherRef.getStore();
                                var otherPlayerRef = store.getComponent(otherRef, PlayerRef.getComponentType());
                                if (otherPlayerRef != null) {
                                    otherUuid = otherPlayerRef.getUuid().toString();
                                }
                            }
                            if (otherUuid == null) return true;

                            PlayerRadarManager.RadarData otherData = null;
                            for (var data : radarDataList) {
                                if (data.uuid.equals(otherUuid)) {
                                    otherData = data;
                                    break;
                                }
                            }
                            if (otherData == null) return true;

                            double dx = otherData.position.x - viewerData.position.x;
                            double dy = otherData.position.y - viewerData.position.y;
                            double dz = otherData.position.z - viewerData.position.z;
                            double distanceSquared = dx * dx + dy * dy + dz * dz;

                            return distanceSquared <= rangeSq;
                        } catch (Exception e) {
                            return true;
                        }
                    });
                } else {
                    tracker.setPlayerMapFilter(null);
                }
            } else {
                tracker.setPlayerMapFilter(null);
            }

            // TODO: setAllowTeleportToMarkers method no longer exists in the new API
            // boolean canTeleportMarkers = allowMarkerTeleports && PermissionsUtil.canTeleport(player);
            // tracker.setAllowTeleportToMarkers(world, canTeleportMarkers);
        } catch (Exception e) {
            LOGGER.severe("Error applying privacy filter: " + e.getMessage());
        }
    }

    private void removeProvider(World world) {
        BetterMapConfig config = BetterMapConfig.getInstance();
        boolean shouldRemove = config.isRadarEnabled() || config.isHidePlayersOnMap();

        try {
            if (world == null) return;

            WorldMapManager mapManager = world.getWorldMapManager();

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            List<String> targetKeys = Arrays.asList("playerMarkers", "playerIcons", "players");

            Map<String, WorldMapManager.MarkerProvider> worldBackups = backedUpProviders.computeIfAbsent(world, ignored -> new HashMap<>());

            if (shouldRemove) {
                for (String key : targetKeys) {
                    if (!providers.containsKey(key)) continue;

                    worldBackups.put(key, providers.get(key));
                    providers.remove(key);
                }
            } else {
                for (String key : targetKeys) {
                    if (worldBackups.containsKey(key) && !providers.containsKey(key)) {
                        providers.put(key, worldBackups.get(key));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error managing provider: " + e.getMessage());
        }
    }
}

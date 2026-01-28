package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.providers.BlockMapMarkerPrivacyProvider;
import dev.ninesliced.providers.DeathPrivacyProvider;
import dev.ninesliced.providers.PoiPlayerMarkerProvider;
import dev.ninesliced.providers.PoiPrivacyProvider;
import dev.ninesliced.providers.SpawnPrivacyProvider;
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
    private final Map<World, WorldMapManager.MarkerProvider> backedUpPlayerMarkerProviders = new WeakHashMap<>();
    private final Map<World, WorldMapManager.MarkerProvider> backedUpSpawnProviders = new WeakHashMap<>();
    private final Map<World, WorldMapManager.MarkerProvider> backedUpBlockMarkerProviders = new WeakHashMap<>();
    private final Map<World, WorldMapManager.MarkerProvider> backedUpDeathProviders = new WeakHashMap<>();
    private final Map<World, Boolean> compassUpdatingState = new WeakHashMap<>();
    private final PoiPrivacyProvider poiPrivacyProvider = new PoiPrivacyProvider();
    private final PoiPlayerMarkerProvider poiPlayerMarkerProvider = new PoiPlayerMarkerProvider();
    private final SpawnPrivacyProvider spawnPrivacyProvider = new SpawnPrivacyProvider();
    private final BlockMapMarkerPrivacyProvider blockMapMarkerPrivacyProvider = new BlockMapMarkerPrivacyProvider();
    private final DeathPrivacyProvider deathPrivacyProvider = new DeathPrivacyProvider();

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
            this.trackWorld(world);
            this.applyPrivacy(world, shouldFilterPois());
        });

        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            if (PlayerConfigManager.getInstance() != null) {
                PlayerConfigManager.getInstance().getPlayerConfig(
                    ((com.hypixel.hytale.server.core.command.system.CommandSender) event.getPlayer()).getUuid()
                );
            }
            World world = event.getPlayer().getWorld();
            this.trackWorld(world);
            this.applyPrivacy(world, shouldFilterPois());
        });

        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            World world = event.getWorld();
            this.trackWorld(world);
            this.applyPrivacy(world, shouldFilterPois());
        });

        if (shouldFilterPois()) {
            updatePrivacyState();
        }

        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                if (!shouldFilterPois()) return;

                for (World world : this.monitoredWorlds) {
                    if (world == null) continue;
                    this.applyPrivacy(world, true);
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
        updatePrivacyState(null);
    }

    /**
     * Updates the POI visibility state, optionally ensuring a specific world is tracked.
     *
     * @param world The world to ensure is tracked and updated, or null to update tracked worlds only.
     */
    public void updatePrivacyState(World world) {
        boolean filter = shouldFilterPois();

        try {
            if (world != null) {
                this.trackWorld(world);
                this.applyPrivacy(world, filter);
            }

            if (this.monitoredWorlds.isEmpty()) {
                Universe universe = Universe.get();
                if (universe != null) {
                    for (World existingWorld : universe.getWorlds().values()) {
                        this.trackWorld(existingWorld);
                    }
                }
            }

            for (World trackedWorld : this.monitoredWorlds) {
                if (trackedWorld == null || trackedWorld == world) {
                    continue;
                }
                this.applyPrivacy(trackedWorld, filter);
            }
        } catch (Exception e) {
            LOGGER.severe("Error updating POI privacy state: " + e.getMessage());
        }
    }

    /**
     * Updates the POI visibility state synchronously for the given world.
     * Use this when already on the world executor to avoid async race conditions.
     *
     * @param world The world to update.
     */
    public void updatePrivacyStateSync(World world) {
        if (world == null) {
            return;
        }

        boolean filter = shouldFilterPois();

        try {
            this.trackWorld(world);
            this.applyPrivacySync(world, filter);
        } catch (Exception e) {
            LOGGER.severe("Error updating POI privacy state synchronously: " + e.getMessage());
        }
    }

    private void trackWorld(World world) {
        if (world != null) {
            this.monitoredWorlds.add(world);
        }
    }

    private void applyPrivacy(World world, boolean filter) {
        if (world == null) {
            return;
        }
        world.execute(() -> {
            applyPrivacySync(world, filter);
        });
    }

    private void applyPrivacySync(World world, boolean filter) {
        if (world == null) {
            return;
        }
        if (filter) {
            this.ensureCompassUpdating(world, true);
            this.replaceProvider(world);
        } else {
            this.restoreProvider(world);
            this.ensureCompassUpdating(world, false);
        }
    }

    private void ensureCompassUpdating(World world, boolean enable) {
        if (world == null) {
            return;
        }

        if (enable) {
            compassUpdatingState.putIfAbsent(world, world.isCompassUpdating());
            if (!world.isCompassUpdating()) {
                world.setCompassUpdating(true);
            }
        } else {
            Boolean previous = compassUpdatingState.remove(world);
            if (previous != null && world.isCompassUpdating() != previous) {
                world.setCompassUpdating(previous);
            }
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
            if (!(existing instanceof PoiPrivacyProvider)) {
                if (existing != null) {
                    backedUpProviders.putIfAbsent(world, existing);
                }
                providers.put(PoiPrivacyProvider.PROVIDER_ID, poiPrivacyProvider);
            }

            WorldMapManager.MarkerProvider playerMarkers = providers.get(PoiPlayerMarkerProvider.PROVIDER_ID);
            if (playerMarkers != null && !(playerMarkers instanceof PoiPlayerMarkerProvider)) {
                backedUpPlayerMarkerProviders.putIfAbsent(world, playerMarkers);
                providers.put(PoiPlayerMarkerProvider.PROVIDER_ID, poiPlayerMarkerProvider);
            }

            WorldMapManager.MarkerProvider spawnMarkers = providers.get(SpawnPrivacyProvider.PROVIDER_ID);
            if (spawnMarkers != null && !(spawnMarkers instanceof SpawnPrivacyProvider)) {
                backedUpSpawnProviders.putIfAbsent(world, spawnMarkers);
                providers.put(SpawnPrivacyProvider.PROVIDER_ID, spawnPrivacyProvider);
            }

            WorldMapManager.MarkerProvider blockMarkers = providers.get(BlockMapMarkerPrivacyProvider.PROVIDER_ID);
            if (blockMarkers != null && !(blockMarkers instanceof BlockMapMarkerPrivacyProvider)) {
                backedUpBlockMarkerProviders.putIfAbsent(world, blockMarkers);
                providers.put(BlockMapMarkerPrivacyProvider.PROVIDER_ID, blockMapMarkerPrivacyProvider);
            }

            WorldMapManager.MarkerProvider deathMarkers = providers.get(DeathPrivacyProvider.PROVIDER_ID);
            if (!(deathMarkers instanceof DeathPrivacyProvider)) {
                if (deathMarkers != null) {
                    backedUpDeathProviders.putIfAbsent(world, deathMarkers);
                }
                providers.put(DeathPrivacyProvider.PROVIDER_ID, deathPrivacyProvider);
            }
        } catch (Exception e) {
            LOGGER.severe("Error replacing POI provider: " + e.getMessage());
        }
    }

    private void restoreProvider(World world) {
        try {
            if (world == null) return;

            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) return;

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            WorldMapManager.MarkerProvider original = backedUpProviders.remove(world);
            if (original != null) {
                providers.put(PoiPrivacyProvider.PROVIDER_ID, original);
            }

            WorldMapManager.MarkerProvider playerOriginal = backedUpPlayerMarkerProviders.remove(world);
            if (playerOriginal != null) {
                providers.put(PoiPlayerMarkerProvider.PROVIDER_ID, playerOriginal);
            }

            WorldMapManager.MarkerProvider spawnOriginal = backedUpSpawnProviders.remove(world);
            if (spawnOriginal != null) {
                providers.put(SpawnPrivacyProvider.PROVIDER_ID, spawnOriginal);
            }

            WorldMapManager.MarkerProvider blockOriginal = backedUpBlockMarkerProviders.remove(world);
            if (blockOriginal != null) {
                providers.put(BlockMapMarkerPrivacyProvider.PROVIDER_ID, blockOriginal);
            }

            WorldMapManager.MarkerProvider deathOriginal = backedUpDeathProviders.remove(world);
            if (deathOriginal != null) {
                providers.put(DeathPrivacyProvider.PROVIDER_ID, deathOriginal);
            }
        } catch (Exception e) {
            LOGGER.severe("Error restoring POI provider: " + e.getMessage());
        }
    }

    private boolean shouldFilterPois() {
        BetterMapConfig config = BetterMapConfig.getInstance();
        PlayerConfigManager playerConfigManager = PlayerConfigManager.getInstance();
        boolean hasPlayerFilters = playerConfigManager != null && playerConfigManager.hasPoiPrivacyOverrides();

        return config.isHideAllPoiOnMap()
            || config.isHideUnexploredPoiOnMap()
            || config.isHideSpawnOnMap()
            || config.isHideDeathMarkerOnMap()
            || (config.getHiddenPoiNames() != null && !config.getHiddenPoiNames().isEmpty())
            || hasPlayerFilters;
    }
}

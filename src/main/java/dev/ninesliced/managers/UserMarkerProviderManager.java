package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.providers.UserMarkerContextMenuProvider;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages the replacement of Hytale's built-in personal and shared marker providers
 * with BetterMap's custom provider that adds context menu options like "Edit".
 */
public class UserMarkerProviderManager {

    private static final Logger LOGGER = Logger.getLogger(UserMarkerProviderManager.class.getName());
    private static final UserMarkerProviderManager INSTANCE = new UserMarkerProviderManager();

    private static final String PERSONAL_PROVIDER_KEY = "personal";
    private static final String SHARED_PROVIDER_KEY = "shared";
    private static final String BETTERMAP_PROVIDER_KEY = "bettermap_usermarkers";

    private final Map<World, WorldMapManager.MarkerProvider> backedUpPersonalProviders = new ConcurrentHashMap<>();
    private final Map<World, WorldMapManager.MarkerProvider> backedUpSharedProviders = new ConcurrentHashMap<>();
    private final Set<World> monitoredWorlds = ConcurrentHashMap.newKeySet();

    private UserMarkerProviderManager() {
    }

    public static UserMarkerProviderManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the manager and sets up event handlers to hook into worlds.
     */
    public void initialize() {
        BetterMap plugin = BetterMap.get();
        if (plugin == null) {
            LOGGER.severe("BetterMap instance is null, cannot initialize UserMarkerProviderManager");
            return;
        }

        plugin.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            World world = event.getWorld();
            if (world != null) {
                monitoredWorlds.add(world);
                replaceProviders(world);
            }
        });

        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            World world = event.getPlayer().getWorld();
            if (world != null) {
                monitoredWorlds.add(world);
                replaceProviders(world);
            }
        });

        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                for (World world : monitoredWorlds) {
                    if (world == null) continue;
                    replaceProviders(world);
                }
            } catch (Exception e) {
                LOGGER.warning("Error in user marker provider poller: " + e.getMessage());
            }
        }, 5L, 30L, TimeUnit.SECONDS);

        try {
            for (World world : Universe.get().getWorlds().values()) {
                monitoredWorlds.add(world);
                replaceProviders(world);
            }
        } catch (Exception e) {
        }

        LOGGER.info("UserMarkerProviderManager initialized - added Edit context menu to markers");
    }

    /**
     * Called when a world is loaded to replace its marker providers.
     */
    public void onWorldLoad(@Nonnull World world) {
        replaceProviders(world);
    }

    /**
     * Called when a world is unloaded to clean up.
     */
    public void onWorldUnload(@Nonnull World world) {
        backedUpPersonalProviders.remove(world);
        backedUpSharedProviders.remove(world);
    }

    private void replaceProviders(@Nonnull World world) {
        try {
            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) return;

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            WorldMapManager.MarkerProvider existingPersonal = providers.remove(PERSONAL_PROVIDER_KEY);
            if (existingPersonal != null && !(existingPersonal instanceof UserMarkerContextMenuProvider)) {
                backedUpPersonalProviders.putIfAbsent(world, existingPersonal);
                LOGGER.info("Backed up personal provider for world " + world.getName());
            }

            WorldMapManager.MarkerProvider existingShared = providers.remove(SHARED_PROVIDER_KEY);
            if (existingShared != null && !(existingShared instanceof UserMarkerContextMenuProvider)) {
                backedUpSharedProviders.putIfAbsent(world, existingShared);
                LOGGER.info("Backed up shared provider for world " + world.getName());
            }

            providers.put(BETTERMAP_PROVIDER_KEY, UserMarkerContextMenuProvider.INSTANCE);

            LOGGER.info("Replaced personal/shared marker providers with BetterMap provider in world " + world.getName());
        } catch (Exception e) {
            LOGGER.severe("Error replacing marker providers in world " + world.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Restores the original providers (for cleanup/shutdown).
     */
    public void restoreProviders(@Nonnull World world) {
        try {
            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) return;

            Map<String, WorldMapManager.MarkerProvider> providers = mapManager.getMarkerProviders();
            if (providers == null) return;

            providers.remove(BETTERMAP_PROVIDER_KEY);

            WorldMapManager.MarkerProvider originalPersonal = backedUpPersonalProviders.remove(world);
            if (originalPersonal != null) {
                providers.put(PERSONAL_PROVIDER_KEY, originalPersonal);
            }

            WorldMapManager.MarkerProvider originalShared = backedUpSharedProviders.remove(world);
            if (originalShared != null) {
                providers.put(SHARED_PROVIDER_KEY, originalShared);
            }

            if (ModConfig.getInstance().isDebug()) {
                LOGGER.info("Restored original marker providers in world " + world.getName());
            }
        } catch (Exception e) {
            LOGGER.severe("Error restoring marker providers: " + e.getMessage());
        }
    }
}

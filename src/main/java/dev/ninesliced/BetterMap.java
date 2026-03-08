package dev.ninesliced;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.ninesliced.commands.bettermap.BetterMapCommand;
import dev.ninesliced.commands.waypoint.WaypointCommand;
import dev.ninesliced.components.ExplorationComponent;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.exploration.ExplorationPlayerSetupSystem;
import dev.ninesliced.exploration.ExplorationTicker;
import dev.ninesliced.handlers.BetterMapPacketHandler;
import dev.ninesliced.hstats.HStats;
import dev.ninesliced.listeners.ExplorationListener;
import dev.ninesliced.managers.ChunkStreamingManager;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.MapAnchorManager;
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PlayerRadarManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.managers.UserMarkerProviderManager;
import dev.ninesliced.managers.WarpPrivacyManager;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.managers.WorldBorderManager;
import dev.ninesliced.providers.LocationHudProvider;
import dev.ninesliced.systems.LocationSystem;
import dev.ninesliced.utils.WaypointLimitUtil;

/**
 * Main class for the BetterMap mod.
 * Handles initialization, component registration, and event setup.
 */
public class BetterMap extends JavaPlugin {

    private static final Logger LOGGER = Logger.getLogger(BetterMap.class.getName());
    private static BetterMap instance;
    private ComponentType<EntityStore, ExplorationComponent> explorationComponentType;
    private LocationHudProvider locationHudProvider;

    /**
     * Constructor for the BetterMap plugin.
     *
     * @param init Plugin initialization context.
     */
    public BetterMap(@Nonnull JavaPluginInit init) {
        super(init);
    }

    /**
     * Gets the singleton instance of the BetterMap plugin.
     *
     * @return The active BetterMap instance.
     */
    public static BetterMap get() {
        return instance;
    }

    /**
     * Gets the LocationHudProvider instance.
     *
     * @return The active LocationHudProvider.
     */
    public LocationHudProvider getLocationHudProvider() {
        return locationHudProvider;
    }

    /**
     * Gets the component type for exploration data.
     *
     * @return The registered ExplorationComponent type.
     */
    public ComponentType<EntityStore, ExplorationComponent> getExplorationComponentType() {
        return explorationComponentType;
    }

    /**
     * Performs the setup logic for the plugin.
     * Registers components, systems, commands, and event listeners.
     */
    @Override
    protected void setup() {
        instance = this;
        new HStats("80d0e1dd-0f46-4c12-8f71-f9cae6e9f0f4", "1.3.3");
        LOGGER.info("========================================");
        LOGGER.info("Setting up Persistent Map Exploration Mod");
        LOGGER.info("========================================");

        try {
            this.explorationComponentType = this.getEntityStoreRegistry()
                    .registerComponent(ExplorationComponent.class, "ExplorationData", ExplorationComponent.CODEC);
            LOGGER.info("Exploration Component: REGISTERED");

            this.getEntityStoreRegistry().registerSystem(new ExplorationPlayerSetupSystem());
            LOGGER.info("Exploration Setup System: REGISTERED");

            Path serverRoot = Paths.get(".").toAbsolutePath().normalize();
            ModConfig.getInstance().initialize(serverRoot);
            WaypointLimitUtil.applyOverridesToAllWorlds(
                ModConfig.getInstance().getMaxPersonalMarkersPerPlayer(),
                ModConfig.getInstance().getMaxSharedMarkersPerPlayer()
            );

            PlayerConfigManager.initialize(serverRoot.resolve("mods").resolve("BetterMap"));
            LOGGER.info("Player Config Manager: INITIALIZED");

            ServerManager.get().registerSubPacketHandlers(BetterMapPacketHandler::new);
            LOGGER.info("BetterMapPacketHandler: REGISTERED");

            MapPrivacyManager.getInstance().initialize();
            LOGGER.info("MapPrivacyManager: INITIALIZED");

            WarpPrivacyManager.getInstance().initialize();
            LOGGER.info("WarpPrivacyManager: INITIALIZED");

            PoiPrivacyManager.getInstance().initialize();
            LOGGER.info("PoiPrivacyManager: INITIALIZED");

            UserMarkerProviderManager.getInstance().initialize();
            LOGGER.info("UserMarkerProviderManager: INITIALIZED");

            Path configDir = ModConfig.getInstance().getConfigDirectory();
            if (configDir == null) {
                configDir = serverRoot.resolve("mods").resolve("BetterMap");
            }
            WaypointManager.initialize(configDir);
            LOGGER.info("Waypoint Persistence: INITIALIZED");

            ExplorationManager.config()
                    .updateRate(0.5f)
                    .enablePersistence("exploration_data")
                    .build();

            LOGGER.info("Exploration Manager: INITIALIZED");

            ExplorationTicker.getInstance().start();
            LOGGER.info("Exploration Ticker: STARTED");

            this.getCommandRegistry().registerCommand(new BetterMapCommand());
            this.getCommandRegistry().registerCommand(new WaypointCommand());
            LOGGER.info("Mod Command: REGISTERED");

            this.locationHudProvider = new LocationHudProvider();
            this.getEntityStoreRegistry().registerSystem(new LocationSystem());
            LOGGER.info("Location Display: INITIALIZED");

            PlayerRadarManager.getInstance();
            LOGGER.info("Player Radar: INITIALIZED");

            WorldBorderManager.getInstance();
            LOGGER.info("World Border Manager: INITIALIZED");

            MapAnchorManager.getInstance().initialize();
            LOGGER.info("Map Anchor Manager: INITIALIZED (Anchor UI on MapServerContent)");

            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ExplorationListener::onPlayerReady);
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, ExplorationListener::onPlayerQuit);

            this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, ExplorationListener::onPlayerJoinWorld);

            this.getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, ExplorationListener::onPlayerLeaveWorld);
            LOGGER.info("Exploration Events: REGISTERED");

            LOGGER.info("========================================");
            LOGGER.info("Plugin Setup Complete!");
            LOGGER.info("Players will now have persistent");
            LOGGER.info("exploration tracking on the world map");
            LOGGER.info("========================================");

        } catch (Exception e) {
            LOGGER.severe("Failed to setup Exploration Plugin: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Plugin initialization failed", e);
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.info("Shutting down BetterMap plugin...");

        ExplorationTicker.getInstance().stop();

        ExplorationManager.getInstance().shutdown();

        if (this.locationHudProvider != null) {
            this.locationHudProvider.cleanup();
        }

        PlayerRadarManager.getInstance().cleanup();
        WorldBorderManager.getInstance().cleanup();
        MapAnchorManager.getInstance().cleanup();
        ChunkStreamingManager.getInstance().cleanup();

        LOGGER.info("BetterMap plugin shutdown complete.");
        super.shutdown();
    }
}

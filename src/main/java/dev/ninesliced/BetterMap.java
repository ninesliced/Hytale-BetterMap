package dev.ninesliced;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.commands.BetterMapCommand;
import dev.ninesliced.components.ExplorationComponent;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.exploration.*;
import dev.ninesliced.listeners.ExplorationEventListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.PlayerConfigManager;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Main class for the BetterMap mod.
 * Handles initialization, component registration, and event setup.
 */
public class BetterMap extends JavaPlugin {

    private static final Logger LOGGER = Logger.getLogger(BetterMap.class.getName());
    private static BetterMap instance;
    private ComponentType<EntityStore, ExplorationComponent> explorationComponentType;

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
            BetterMapConfig.getInstance().initialize(serverRoot);

            PlayerConfigManager.initialize(serverRoot.resolve("mods").resolve("BetterMap"));
            LOGGER.info("Player Config Manager: INITIALIZED");

            ExplorationManager.config()
                    .updateRate(0.5f)
                    .enablePersistence("exploration_data")
                    .build();

            LOGGER.info("Exploration Manager: INITIALIZED");

            ExplorationTicker.getInstance().start();
            LOGGER.info("Exploration Ticker: STARTED");

            this.getCommandRegistry().registerCommand(new BetterMapCommand());
            LOGGER.info("Example Command: REGISTERED");

            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ExplorationEventListener::onPlayerReady);
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, ExplorationEventListener::onPlayerQuit);

            this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, ExplorationEventListener::onPlayerJoinWorld);

            this.getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, ExplorationEventListener::onPlayerLeaveWorld);
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
}

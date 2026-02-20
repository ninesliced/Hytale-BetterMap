package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ninesliced.configs.CavePersistence;
import dev.ninesliced.configs.ExplorationPersistence;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.configs.ModConfig;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Singleton manager responsible for the lifecycle of the exploration system.
 * Handles initialization, configuration, and player data persistence.
 */
public class ExplorationManager {
    private static final Logger LOGGER = Logger.getLogger(ExplorationManager.class.getName());
    private static ExplorationManager INSTANCE;

    private boolean initialized = false;
    private int maxStoredChunksPerPlayer = Integer.MAX_VALUE;
    private float explorationUpdateRate = 0.5f;
    private boolean persistenceEnabled = true;

    private ExplorationPersistence persistence;
    private CavePersistence cavePersistence;

    private String persistencePath = "universe/exploration_data";

    private final ScheduledExecutorService autoSaveScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> autoSaveTask;

    private ExplorationManager() {
    }

    /**
     * Gets the singleton instance.
     *
     * @return The instance.
     */
    @Nonnull
    public static synchronized ExplorationManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ExplorationManager();
        }
        return INSTANCE;
    }

    /**
     * Creates a new config builder.
     *
     * @return A new ConfigBuilder.
     */
    @Nonnull
    public static ConfigBuilder config() {
        return new ConfigBuilder();
    }

    /**
     * Initializes the exploration system.
     */
    public synchronized void initialize() {
        if (initialized) {
            LOGGER.info("Exploration system already initialized");
            return;
        }

        try {
            LOGGER.info("Initializing Exploration System...");

            persistence = new ExplorationPersistence();
            cavePersistence = new CavePersistence();

            LOGGER.info("- Exploration Tracker: " + ExplorationTracker.class.getSimpleName());
            LOGGER.info("- Update Rate: " + explorationUpdateRate + " seconds");
            LOGGER.info("- Persistence: " + (persistenceEnabled ? "ENABLED" : "DISABLED"));
            LOGGER.info("- Cave Persistence: " + (persistenceEnabled ? "ENABLED" : "DISABLED"));

            startAutoSave();

            initialized = true;
            LOGGER.info("Exploration System initialized successfully");
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize exploration system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if the manager is initialized.
     *
     * @return True if initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Loads player data for their current world.
     *
     * @param player The player.
     */
    public void loadPlayerData(@Nonnull Player player) {
        if (player.getWorld() != null) {
            loadPlayerData(player, player.getWorld().getName());
        }
    }

    /**
     * Loads player data for a specific world.
     *
     * @param player    The player.
     * @param worldName The world name.
     */
    public void loadPlayerData(@Nonnull Player player, @Nonnull String worldName) {
        if (persistenceEnabled && persistence != null) {
            persistence.load(player, worldName);
        }
        if (persistenceEnabled && cavePersistence != null && ModConfig.getInstance().isCaveModeEnabled()) {
            cavePersistence.load(player, worldName);
        }
    }

    /**
     * Saves player data for their current world.
     *
     * @param player The player.
     */
    public void savePlayerData(@Nonnull Player player) {
        if (persistenceEnabled && persistence != null) {
            persistence.save(player);
        }
        if (persistenceEnabled && cavePersistence != null && ModConfig.getInstance().isCaveModeEnabled()) {
            cavePersistence.save(player);
        }
    }

    /**
     * Saves player data for a specific world.
     *
     * @param playerName The player's name.
     * @param playerUUID The player's UUID.
     * @param worldName  The world name.
     */
    public void savePlayerData(String playerName, UUID playerUUID, String worldName) {
        if (persistenceEnabled) {
            persistence.save(playerName, playerUUID, worldName);
            if (cavePersistence != null && ModConfig.getInstance().isCaveModeEnabled()) {
                cavePersistence.save(playerName, playerUUID, worldName);
            }
        }
    }

    /**
     * Gets all explored chunks for a given world, combining persistence and active data.
     *
     * @param worldName The world name.
     * @return A set of all explored chunks.
     */
    private final Map<String, Set<Long>> cachedAllExploredChunks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> cachedAllExploredVersion = new java.util.concurrent.ConcurrentHashMap<>();
    
    public java.util.Set<Long> getAllExploredChunks(String worldName) {
        long combinedVersion = 0;
        int playerCount = 0;
        for (ExplorationTracker.PlayerExplorationData data : ExplorationTracker.getInstance().getAllPlayerDataSnapshot().values()) {
            String dataWorld = data.getWorldName();
            if (dataWorld == null || !dataWorld.equals(worldName)) continue;
            combinedVersion += data.getExploredChunks().getVersion();
            playerCount++;
        }
        combinedVersion = combinedVersion * 31 + playerCount;
        
        Long cachedVersion = cachedAllExploredVersion.get(worldName);
        if (cachedVersion != null && cachedVersion == combinedVersion) {
            Set<Long> cached = cachedAllExploredChunks.get(worldName);
            if (cached != null) {
                return cached;
            }
        }
        
        Set<Long> allChunks = new HashSet<>();

        if (persistenceEnabled) {
            allChunks.addAll(persistence.loadAllChunks(worldName));
        }

        Universe universe = Universe.get();
        if (universe != null) {
            for (ExplorationTracker.PlayerExplorationData data : ExplorationTracker.getInstance().getAllPlayerDataSnapshot().values()) {
                String dataWorld = data.getWorldName();
                if (dataWorld == null || !dataWorld.equals(worldName)) {
                    continue;
                }
                allChunks.addAll(data.getExploredChunks().getExploredChunks());
            }
        }

        cachedAllExploredChunks.put(worldName, allChunks);
        cachedAllExploredVersion.put(worldName, combinedVersion);
        return allChunks;
    }

    /**
     * Gets all explored cave chunks for a given world, combining persistence and active data.
     *
     * @param worldName The world name.
     * @return A set of all explored cave chunks.
     */
    public java.util.Set<Long> getAllExploredCaveChunks(String worldName) {
        Set<Long> allChunks = new HashSet<>();

        if (persistenceEnabled && cavePersistence != null) {
            allChunks.addAll(cavePersistence.loadAllChunks(worldName));
        }

        Universe universe = Universe.get();
        if (universe != null && universe.getWorld(worldName) != null) {
            universe.getWorld(worldName).getPlayerRefs().forEach(playerRef -> {
                Player player = playerRef.getComponent(Player.getComponentType());
                if (player != null) {
                    CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
                    if (state != null) {
                        allChunks.addAll(state.getExploredCaveChunks());
                    }
                }
            });
        }

        return allChunks;
    }

    /**
     * Gets the cave persistence instance.
     *
     * @return The cave persistence, or null if not initialized.
     */
    public dev.ninesliced.configs.CavePersistence getCavePersistence() {
        return cavePersistence;
    }

    /**
     * Resets map exploration for all tracked players and clears persisted map exploration files.
     *
     * @return Number of persisted map files deleted.
     */
    public int resetAllMapExploration() {
        int runtimeResetCount = 0;

        for (ExplorationTracker.PlayerExplorationData data : ExplorationTracker.getInstance().getAllPlayerDataSnapshot().values()) {
            data.getExploredChunks().clear();
            data.getMapExpansion().reset();
            data.resetLastChunkPosition();
            runtimeResetCount++;
        }

        cachedAllExploredChunks.clear();
        cachedAllExploredVersion.clear();

        int deletedFiles = 0;
        if (persistenceEnabled && persistence != null) {
            deletedFiles = persistence.clearAllData();
        }

        LOGGER.info("Reset map exploration for " + runtimeResetCount + " tracked player state(s); deleted " + deletedFiles + " persisted file(s)");
        return deletedFiles;
    }

    /**
     * Resets cave exploration for all tracked players and clears persisted cave exploration files.
     *
     * @return Number of persisted cave files deleted.
     */
    public int resetAllCaveExploration() {
        int runtimeResetCount = CaveModeManager.getInstance().clearAllCaveExploration();

        int deletedFiles = 0;
        if (persistenceEnabled && cavePersistence != null) {
            deletedFiles = cavePersistence.clearAllData();
        }

        LOGGER.info("Reset cave exploration for " + runtimeResetCount + " tracked player state(s); deleted " + deletedFiles + " persisted file(s)");
        return deletedFiles;
    }

    /**
     * Gets all player UUIDs with persisted exploration data (map and/or cave).
     */
    @Nonnull
    public Set<UUID> getAllSavedPlayerUuids() {
        Set<UUID> result = new HashSet<>();
        if (!persistenceEnabled) {
            return result;
        }
        if (persistence != null) {
            result.addAll(persistence.listSavedPlayerUuids());
        }
        if (cavePersistence != null) {
            result.addAll(cavePersistence.listSavedPlayerUuids());
        }
        return result;
    }

    /**
     * Resets map exploration for a specific player UUID.
     * Clears persisted files and runtime tracked state if the player is online.
     *
     * @param playerUUID The player UUID.
     * @return Number of persisted map files deleted.
     */
    public int resetMapExplorationForPlayer(@Nonnull UUID playerUUID) {
        int deletedFiles = 0;
        if (persistenceEnabled && persistence != null) {
            deletedFiles = persistence.clearPlayerData(playerUUID);
        }

        Universe universe = Universe.get();
        if (universe != null) {
            PlayerRef playerRef = universe.getPlayer(playerUUID);
            if (playerRef != null) {
                String username = playerRef.getUsername();
                ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(username);
                if (data != null) {
                    data.getExploredChunks().clear();
                    data.getMapExpansion().reset();
                    data.resetLastChunkPosition();
                }
            }
        }

        cachedAllExploredChunks.clear();
        cachedAllExploredVersion.clear();
        return deletedFiles;
    }

    /**
     * Resets cave exploration for a specific player UUID.
     * Clears persisted files and runtime tracked state if the player is online.
     *
     * @param playerUUID The player UUID.
     * @return Number of persisted cave files deleted.
     */
    public int resetCaveExplorationForPlayer(@Nonnull UUID playerUUID) {
        int deletedFiles = 0;
        if (persistenceEnabled && cavePersistence != null) {
            deletedFiles = cavePersistence.clearPlayerData(playerUUID);
        }

        Universe universe = Universe.get();
        if (universe != null) {
            PlayerRef playerRef = universe.getPlayer(playerUUID);
            if (playerRef != null) {
                CaveModeManager.getInstance().clearCaveExploration(playerRef.getUsername());
            }
        }

        return deletedFiles;
    }

    /**
     * Shuts down the system and clears trackers.
     */
    public synchronized void shutdown() {
        try {
            LOGGER.info("Shutting down Exploration System...");
            stopAutoSave();
            autoSaveScheduler.shutdown();
            try {
                if (!autoSaveScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.warning("Auto-save scheduler did not terminate in time, forcing shutdown...");
                    autoSaveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                autoSaveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            ExplorationTracker.getInstance().clear();
            LOGGER.info("Exploration System shutdown complete");
        } catch (Exception e) {
            LOGGER.severe("Error during exploration system shutdown: " + e.getMessage());
        }
    }

    /**
     * Starts the auto-save task.
     */
    public synchronized void startAutoSave() {
        stopAutoSave();
        int interval = ModConfig.getInstance().getAutoSaveInterval();
        if (interval > 0) {
            autoSaveTask = autoSaveScheduler.scheduleAtFixedRate(this::autoSave, interval, interval, TimeUnit.MINUTES);
            LOGGER.info("Auto-save scheduled every " + interval + " minutes.");
        } else {
            LOGGER.info("Auto-save is disabled (interval <= 0).");
        }
    }

    /**
     * Stops the auto-save task.
     */
    public synchronized void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel(false);
            autoSaveTask = null;
        }
    }

    /**
     * Performs the auto-save operation for all players.
     */
    private void autoSave() {
        if (!persistenceEnabled) return;

        persistence.saveAllPlayers();
        if (cavePersistence != null && ModConfig.getInstance().isCaveModeEnabled()) {
            cavePersistence.saveAllPlayers();
        }
        LOGGER.info("Auto-saved exploration" + (ModConfig.getInstance().isCaveModeEnabled() ? " and cave" : "") + " data for all players.");
    }

    /**
     * Registers a player for tracking.
     *
     * @param player The player.
     */
    public void registerPlayer(@Nonnull Player player) {
        try {
            ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            LOGGER.fine("Registered player for exploration tracking: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to register player " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Unregisters a player from tracking.
     *
     * @param player The player.
     */
    public void unregisterPlayer(@Nonnull Player player) {
        try {
            ExplorationTracker.getInstance().removePlayerData(player);
            LOGGER.fine("Unregistered player from exploration tracking: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to unregister player " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Gets the max stored chunks per player.
     *
     * @return The limit.
     */
    public int getMaxStoredChunksPerPlayer() {
        return maxStoredChunksPerPlayer;
    }

    /**
     * Sets the max stored chunks per player.
     *
     * @param max The limit.
     */
    public void setMaxStoredChunksPerPlayer(int max) {
        this.maxStoredChunksPerPlayer = max;
        LOGGER.info("Max stored chunks per player set to: " + max);
    }

    /**
     * Gets the update rate for exploration checks.
     *
     * @return The rate in seconds.
     */
    public float getExplorationUpdateRate() {
        return explorationUpdateRate;
    }

    /**
     * Sets the update rate.
     *
     * @param seconds The rate in seconds.
     */
    public void setExplorationUpdateRate(float seconds) {
        this.explorationUpdateRate = Math.max(0.1f, seconds);
        LOGGER.info("Exploration update rate set to: " + explorationUpdateRate + " seconds");
    }

    /**
     * Checks if persistence is enabled.
     *
     * @return True if enabled.
     */
    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    /**
     * Enables or disables persistence.
     *
     * @param enabled The new state.
     */
    public void setPersistenceEnabled(boolean enabled) {
        this.persistenceEnabled = enabled;
        LOGGER.info("Persistence " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Gets the path for persistence data.
     *
     * @return The path.
     */
    public String getPersistencePath() {
        return persistencePath;
    }

    /**
     * Sets the persistence path.
     *
     * @param path The new path.
     */
    public void setPersistencePath(@Nonnull String path) {
        this.persistencePath = path;
        LOGGER.info("Persistence path set to: " + path);
    }

    @Override
    public String toString() {
        return String.format(
                "ExplorationManager{initialized=%s, maxChunksPerPlayer=%d, updateRate=%.2fs, persistence=%s}",
                initialized, maxStoredChunksPerPlayer, explorationUpdateRate,
                persistenceEnabled ? "enabled@" + persistencePath : "disabled"
        );
    }

    /**
     * Builder for ExplorationManager configuration.
     */
    public static class ConfigBuilder {
        private final ExplorationManager manager = getInstance();

        /**
         * Sets the max chunks limitation.
         *
         * @param max Max chunks.
         * @return The builder.
         */
        public ConfigBuilder maxChunksPerPlayer(int max) {
            manager.setMaxStoredChunksPerPlayer(max);
            return this;
        }

        /**
         * Sets the update rate.
         *
         * @param seconds Rate in seconds.
         * @return The builder.
         */
        public ConfigBuilder updateRate(float seconds) {
            manager.setExplorationUpdateRate(seconds);
            return this;
        }

        /**
         * Enables persistence at the given path.
         *
         * @param path The path.
         * @return The builder.
         */
        public ConfigBuilder enablePersistence(String path) {
            manager.setPersistencePath(path);
            manager.setPersistenceEnabled(true);
            return this;
        }

        /**
         * Disables persistence.
         *
         * @return The builder.
         */
        public ConfigBuilder disablePersistence() {
            manager.setPersistenceEnabled(false);
            return this;
        }

        /**
         * Builds (initializes) the manager.
         *
         * @return The initialized manager.
         */
        public ExplorationManager build() {
            manager.initialize();
            return manager;
        }
    }
}

package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ninesliced.configs.CavePersistence;
import dev.ninesliced.configs.ExplorationPersistence;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Singleton manager responsible for the lifecycle of the exploration system.
 * Handles initialization, configuration, and player data persistence.
 * <p>
 * Memory safety:
 * - getAllExploredChunks() uses version-based caching to avoid repeated disk reads.
 * - getAllExploredCaveChunks() now also uses version-based caching (was uncached before).
 * - Shared caches are periodically trimmed and cleared on shutdown.
 * - maxStoredChunksPerPlayer defaults to 1,000,000 (enforced in ExploredChunksTracker).
 */
public class ExplorationManager {
    private static final Logger LOGGER = Logger.getLogger(ExplorationManager.class.getName());
    private static ExplorationManager INSTANCE;

    private boolean initialized = false;
    private int maxStoredChunksPerPlayer = 1_000_000;
    private float explorationUpdateRate = 0.5f;
    private boolean persistenceEnabled = true;

    private ExplorationPersistence persistence;
    private CavePersistence cavePersistence;

    private String persistencePath = "universe/exploration_data";

    private final ScheduledExecutorService autoSaveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BetterMap-AutoSave");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> autoSaveTask;

    // --- Surface exploration shared cache (version-based, primitive-backed) ---
    private final Map<String, LongOpenHashSet> cachedAllExploredChunks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> cachedAllExploredVersion = new java.util.concurrent.ConcurrentHashMap<>();

    // --- Cave exploration shared cache (version-based, primitive-backed) ---
    private final Map<String, LongOpenHashSet> cachedAllCaveChunks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> cachedAllCaveVersion = new java.util.concurrent.ConcurrentHashMap<>();

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
     * Uses version-based caching backed by a primitive LongOpenHashSet.
     * <p>
     * Returned set is a live unmodifiable view over the cache. The cache itself is rebuilt
     * only when the version changes; it is NOT mutated in place.
     */
    public Set<Long> getAllExploredChunks(String worldName) {
        return new ExploredChunkSetView(getAllExploredChunksLong(worldName));
    }

    /**
     * Primitive variant of {@link #getAllExploredChunks(String)} for hot-path callers
     * that want to avoid boxing.
     */
    @Nonnull
    public LongSet getAllExploredChunksLong(String worldName) {
        // Compute combined version in a single snapshot pass.
        long combinedVersion = 0;
        int playerCount = 0;
        Map<String, ExplorationTracker.PlayerExplorationData> snapshot =
                ExplorationTracker.getInstance().getAllPlayerDataSnapshot();
        for (ExplorationTracker.PlayerExplorationData data : snapshot.values()) {
            String dataWorld = data.getWorldName();
            if (dataWorld == null || !dataWorld.equals(worldName)) continue;
            combinedVersion += data.getExploredChunks().getVersion();
            playerCount++;
        }
        combinedVersion = combinedVersion * 31 + playerCount;

        Long cachedVersion = cachedAllExploredVersion.get(worldName);
        if (cachedVersion != null && cachedVersion == combinedVersion) {
            LongOpenHashSet cached = cachedAllExploredChunks.get(worldName);
            if (cached != null) {
                return cached;
            }
        }

        LongOpenHashSet allChunks = new LongOpenHashSet();

        if (persistenceEnabled && persistence != null) {
            // loadAllChunks still returns Set<Long> for compat — fold it in.
            Set<Long> persisted = persistence.loadAllChunks(worldName);
            for (Long c : persisted) {
                allChunks.add(c.longValue());
            }
        }

        for (ExplorationTracker.PlayerExplorationData data : snapshot.values()) {
            String dataWorld = data.getWorldName();
            if (dataWorld == null || !dataWorld.equals(worldName)) {
                continue;
            }
            LongIterator it = data.getExploredChunks().getRawSet().iterator();
            while (it.hasNext()) {
                allChunks.add(it.nextLong());
            }
        }

        cachedAllExploredChunks.put(worldName, allChunks);
        cachedAllExploredVersion.put(worldName, combinedVersion);
        return allChunks;
    }

    /**
     * Gets all explored cave chunks for a given world, combining persistence and active data.
     * FIX: Now uses version-based caching (was previously uncached, causing disk reads every call).
     *
     * @param worldName The world name.
     * @return A set of all explored cave chunks.
     */
    public Set<Long> getAllExploredCaveChunks(String worldName) {
        // Build version from active player cave state sizes
        long combinedVersion = 0;
        int playerCount = 0;
        Universe universe = Universe.get();
        if (universe != null && universe.getWorld(worldName) != null) {
            for (PlayerRef playerRef : universe.getWorld(worldName).getPlayerRefs()) {
                Player player = playerRef.getComponent(Player.getComponentType());
                if (player != null) {
                    CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
                    if (state != null) {
                        combinedVersion += state.getExploredCaveChunks().size();
                        playerCount++;
                    }
                }
            }
        }
        combinedVersion = combinedVersion * 31 + playerCount;

        Long cachedVersion = cachedAllCaveVersion.get(worldName);
        if (cachedVersion != null && cachedVersion == combinedVersion) {
            LongOpenHashSet cached = cachedAllCaveChunks.get(worldName);
            if (cached != null) {
                return new ExploredChunkSetView(cached);
            }
        }

        LongOpenHashSet allChunks = new LongOpenHashSet();

        if (persistenceEnabled && cavePersistence != null) {
            for (Long c : cavePersistence.loadAllChunks(worldName)) {
                allChunks.add(c.longValue());
            }
        }

        if (universe != null && universe.getWorld(worldName) != null) {
            universe.getWorld(worldName).getPlayerRefs().forEach(playerRef -> {
                Player player = playerRef.getComponent(Player.getComponentType());
                if (player != null) {
                    CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
                    if (state != null) {
                        for (Long c : state.getExploredCaveChunks()) {
                            allChunks.add(c.longValue());
                        }
                    }
                }
            });
        }

        cachedAllCaveChunks.put(worldName, allChunks);
        cachedAllCaveVersion.put(worldName, combinedVersion);
        return new ExploredChunkSetView(allChunks);
    }

    /**
     * Invalidates cave exploration caches. Call when cave exploration is reset.
     */
    public void invalidateCaveCaches() {
        cachedAllCaveChunks.clear();
        cachedAllCaveVersion.clear();
    }

    /**
     * Invalidates all shared exploration caches (surface and cave).
     */
    public void invalidateAllCaches() {
        cachedAllExploredChunks.clear();
        cachedAllExploredVersion.clear();
        cachedAllCaveChunks.clear();
        cachedAllCaveVersion.clear();
    }

    /**
     * Gets the cave persistence instance.
     *
     * @return The cave persistence, or null if not initialized.
     */
    public CavePersistence getCavePersistence() {
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

        invalidateAllCaches();

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

        invalidateCaveCaches();

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

        invalidateAllCaches();
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

        invalidateCaveCaches();
        return deletedFiles;
    }

    /**
     * Shuts down the system and clears trackers.
     * FIX: Also clears all shared caches to release memory immediately.
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

            // Clear all caches to release memory
            invalidateAllCaches();

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

        public ConfigBuilder maxChunksPerPlayer(int max) {
            manager.setMaxStoredChunksPerPlayer(max);
            return this;
        }

        public ConfigBuilder updateRate(float seconds) {
            manager.setExplorationUpdateRate(seconds);
            return this;
        }

        public ConfigBuilder enablePersistence(String path) {
            manager.setPersistencePath(path);
            manager.setPersistenceEnabled(true);
            return this;
        }

        public ConfigBuilder disablePersistence() {
            manager.setPersistenceEnabled(false);
            return this;
        }

        public ExplorationManager build() {
            manager.initialize();
            return manager;
        }
    }

    /**
     * Lightweight unmodifiable Set<Long> view over a primitive LongSet. Avoids the
     * historical pattern of allocating a HashSet<Long> copy on every getAllExploredChunks call.
     */
    static final class ExploredChunkSetView extends java.util.AbstractSet<Long> {
        private final LongSet delegate;

        ExploredChunkSetView(LongSet delegate) {
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof Long) {
                return delegate.contains(((Long) o).longValue());
            }
            return false;
        }

        @Override
        public java.util.Iterator<Long> iterator() {
            final LongIterator it = delegate.iterator();
            return new java.util.Iterator<Long>() {
                @Override
                public boolean hasNext() {return it.hasNext();}

                @Override
                public Long next() {return it.nextLong();}
            };
        }
    }
}

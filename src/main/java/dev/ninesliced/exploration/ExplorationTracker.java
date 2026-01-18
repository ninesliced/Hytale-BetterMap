package dev.ninesliced.exploration;

import com.hypixel.hytale.server.core.entity.entities.Player;
import dev.ninesliced.components.ExplorationComponent;
import dev.ninesliced.managers.MapExpansionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central tracker for all active players' exploration data.
 */
public class ExplorationTracker {
    private static final ExplorationTracker INSTANCE = new ExplorationTracker();

    private final Map<String, PlayerExplorationData> playerExplorationData = new ConcurrentHashMap<>();

    private ExplorationTracker() {
    }

    /**
     * Gets the singleton instance of the tracker.
     *
     * @return The tracker instance.
     */
    @Nonnull
    public static ExplorationTracker getInstance() {
        return INSTANCE;
    }

    /**
     * Gets or creates the exploration data for a player.
     *
     * @param player The player.
     * @return The player's exploration data.
     */
    @Nonnull
    public PlayerExplorationData getOrCreatePlayerData(@Nonnull Player player) {
        return getOrCreatePlayerData(player, null);
    }

    /**
     * Gets or creates the exploration data for a player, optionally providing an existing component.
     *
     * @param player    The player.
     * @param component existing exploration component (optional).
     * @return The player's exploration data.
     */
    @Nonnull
    public PlayerExplorationData getOrCreatePlayerData(@Nonnull Player player, @Nullable ExplorationComponent component) {
        String playerName = player.getDisplayName();
        return playerExplorationData.compute(playerName, (k, v) -> {
            if (v == null) {
                return new PlayerExplorationData(component);
            }
            return v;
        });
    }

    /**
     * Gets existing exploration data for a player.
     *
     * @param player The player.
     * @return The data, or null if not found.
     */
    public PlayerExplorationData getPlayerData(@Nonnull Player player) {
        return getPlayerData(player.getDisplayName());
    }

    /**
     * Gets existing exploration data by player name.
     *
     * @param playerName The player's name.
     * @return The data, or null if not found.
     */
    public PlayerExplorationData getPlayerData(@Nonnull String playerName) {
        return playerExplorationData.get(playerName);
    }

    /**
     * Removes a player's data from the tracker.
     *
     * @param player The player to remove.
     */
    public void removePlayerData(@Nonnull Player player) {
        removePlayerData(player.getDisplayName());
    }

    /**
     * Removes a player's data by name.
     *
     * @param playerName The player name.
     */
    public void removePlayerData(@Nonnull String playerName) {
        playerExplorationData.remove(playerName);
    }

    /**
     * Clears all tracking data.
     */
    public void clear() {
        playerExplorationData.clear();
    }

    /**
     * Gets a snapshot of all player exploration data.
     *
     * @return A copy of the current player data map.
     */
    @Nonnull
    public Map<String, PlayerExplorationData> getAllPlayerDataSnapshot() {
        return new HashMap<>(playerExplorationData);
    }

    /**
     * Holds the runtime exploration state for a single player.
     */
    public static class PlayerExplorationData {
        private final ExploredChunksTracker exploredChunks;
        private final MapExpansionManager mapExpansion;
        private long lastUpdateTime;
        private int lastChunkX = Integer.MAX_VALUE;
        private int lastChunkZ = Integer.MAX_VALUE;
        private volatile String worldName;

        /**
         * Creates new player exploration data.
         *
         * @param component The persistent component backing this data.
         */
        public PlayerExplorationData(@Nullable ExplorationComponent component) {
            this.exploredChunks = new ExploredChunksTracker(component);
            this.mapExpansion = new MapExpansionManager(exploredChunks);
            this.lastUpdateTime = System.currentTimeMillis();
        }

        /**
         * Gets the tracker for explored chunks.
         *
         * @return The explored chunks tracker.
         */
        public ExploredChunksTracker getExploredChunks() {
            return exploredChunks;
        }

        /**
         * Gets the manager for map expansion.
         *
         * @return The map expansion manager.
         */
        public MapExpansionManager getMapExpansion() {
            return mapExpansion;
        }

        /**
         * Gets the last update time in milliseconds.
         *
         * @return The last update time.
         */
        public long getLastUpdateTime() {
            return lastUpdateTime;
        }

        /**
         * Sets the last update time in milliseconds.
         *
         * @param time The new last update time.
         */
        public void setLastUpdateTime(long time) {
            this.lastUpdateTime = time;
        }

        /**
         * Gets the last chunk X coordinate.
         *
         * @return The last chunk X.
         */
        public int getLastChunkX() {
            return lastChunkX;
        }

        /**
         * Gets the last chunk Z coordinate.
         *
         * @return The last chunk Z.
         */
        public int getLastChunkZ() {
            return lastChunkZ;
        }

        /**
         * Sets the last known chunk position.
         *
         * @param chunkX The chunk X coordinate.
         * @param chunkZ The chunk Z coordinate.
         */
        public void setLastChunkPosition(int chunkX, int chunkZ) {
            this.lastChunkX = chunkX;
            this.lastChunkZ = chunkZ;
        }

        /**
         * Resets the last chunk position to an undefined state.
         */
        public void resetLastChunkPosition() {
            this.lastChunkX = Integer.MAX_VALUE;
            this.lastChunkZ = Integer.MAX_VALUE;
        }

        /**
         * Gets the current world name for this player data.
         *
         * @return The world name, or null if unknown.
         */
        @Nullable
        public String getWorldName() {
            return worldName;
        }

        /**
         * Sets the current world name for this player data.
         *
         * @param worldName The world name.
         */
        public void setWorldName(@Nullable String worldName) {
            this.worldName = worldName;
        }

        /**
         * Checks if the player has moved to a new chunk.
         *
         * @param chunkX The current chunk X.
         * @param chunkZ The current chunk Z.
         * @return True if the player has moved to a new chunk, false otherwise.
         */
        public boolean hasMovedToNewChunk(int chunkX, int chunkZ) {
            return lastChunkX != chunkX || lastChunkZ != chunkZ;
        }
    }
}

package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages DYNAMIC cave mode state for players.
 * 
 * The cave mode is now automatic and seamless:
 * - When a player goes underground (below surface), cave view activates in a radius around them
 * - The Y-level is divided into layers (e.g., 90-100, 80-90, 70-80, etc.)
 * - Normal map is always shown for surface/explored areas
 * - Cave view overlay appears around the player when underground
 * - Previously explored underground areas persist
 * - When returning to surface, cave overlay disappears seamlessly
 * 
 * Configuration is loaded from BetterMapConfig (config.json).
 */
public class CaveModeManager {
    private static final Logger LOGGER = Logger.getLogger(CaveModeManager.class.getName());
    private static final CaveModeManager INSTANCE = new CaveModeManager();
    
    /**
     * Minimum consecutive solid blocks above to consider player underground.
     */
    public static final int CEILING_CHECK_BLOCKS = 3;
    
    private final Map<String, DynamicCaveModeState> playerStates = new ConcurrentHashMap<>();
    
    private CaveModeManager() {
    }
    
    @Nonnull
    public static CaveModeManager getInstance() {
        return INSTANCE;
    }
    
    
    /**
     * Gets the configured layer size from config.
     */
    public static int getConfigLayerSize() {
        return ModConfig.getInstance().getCaveModeLayerSize();
    }
    
    /**
     * Gets the configured underground threshold from config.
     */
    public static int getConfigUndergroundThreshold() {
        return ModConfig.getInstance().getCaveModeUndergroundThreshold();
    }
    
    /**
     * Gets the configured cave radius from config.
     */
    public static int getConfigCaveRadius() {
        return ModConfig.getInstance().getCaveModeRadius();
    }
    
    /**
     * Checks if cave mode feature is enabled in server config.
     */
    public static boolean isConfigEnabled() {
        return ModConfig.getInstance().isCaveModeEnabled();
    }
    
    /**
     * Checks if cave mode is effectively enabled for a specific player.
     * Returns true only if BOTH server config AND player config have it enabled.
     * 
     * @param player The player to check
     * @return true if cave mode is enabled for this player
     */
    public static boolean isEffectivelyEnabledForPlayer(@Nonnull Player player) {
        if (!isConfigEnabled()) {
            return false;
        }
        PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());
        if (playerConfig != null) {
            return playerConfig.isCaveModeEnabled();
        }
        return true;
    }
    
    /**
     * Gets the dynamic cave mode state for a player.
     */
    @Nullable
    public DynamicCaveModeState getState(@Nonnull Player player) {
        return playerStates.get(player.getDisplayName());
    }
    
    /**
     * Gets the dynamic cave mode state by player name.
     */
    @Nullable
    public DynamicCaveModeState getStateByName(@Nonnull String playerName) {
        return playerStates.get(playerName);
    }
    
    /**
     * Gets or creates the dynamic cave mode state for a player.
     * Initializes with values from global config and player config.
     */
    @Nonnull
    public DynamicCaveModeState getOrCreateState(@Nonnull Player player) {
        return playerStates.computeIfAbsent(player.getDisplayName(), k -> {
            DynamicCaveModeState state = new DynamicCaveModeState();

            state.setDynamicModeEnabled(isEffectivelyEnabledForPlayer(player));
            state.setLayerSize(getConfigLayerSize());
            state.setUndergroundThreshold(getConfigUndergroundThreshold());
            state.setCaveRadius(getConfigCaveRadius());
            return state;
        });
    }
    
    /**
     * Calculates the Y-level layer for a given Y position.
     * E.g., with layerSize=5: Y=97 -> layer 95, Y=93 -> layer 90
     */
    public static int getLayerForY(int y, int layerSize) {
        return (y / layerSize) * layerSize;
    }
    
    /**
     * Calculates the Y-level layer using default layer size.
     */
    public static int getLayerForY(int y) {
        return getLayerForY(y, getConfigLayerSize());
    }
    
    /**
     * Gets the layer range (min Y, max Y) for a given layer.
     */
    public static int[] getLayerRange(int layer, int layerSize) {
        return new int[] { layer, layer + layerSize };
    }
    
    /**
     * Gets the layer range using default layer size.
     */
    public static int[] getLayerRange(int layer) {
        return getLayerRange(layer, getConfigLayerSize());
    }
    
    /**
     * Updates the player's underground state based on their position.
     * Call this every tick to update the dynamic cave mode.
     * 
     * @param player The player.
     * @param playerY The player's Y position.
     * @param hasCeiling Whether there are solid blocks above the player.
     * @return True if the UNDERGROUND state changed (entered or exited caves).
     *         Layer changes within caves do NOT return true here.
     */
    public boolean updateUndergroundState(@Nonnull Player player, int playerY, boolean hasCeiling) {
        DynamicCaveModeState state = getOrCreateState(player);
        
        boolean wasUnderground = state.isCurrentlyUnderground();
        int oldLayer = state.getCurrentLayer();
        
        int threshold = state.getUndergroundThreshold();
        int layerSize = state.getLayerSize();
        
        boolean isUnderground = state.isDynamicModeEnabled() && playerY < threshold && hasCeiling;
        
        int newLayer = getLayerForY(playerY, layerSize);
        
        state.setCurrentlyUnderground(isUnderground);
        state.setPlayerY(playerY);
        
        if (isUnderground) {
            if (oldLayer != newLayer && wasUnderground) {
                state.setPreviousLayer(oldLayer);
                state.setLayerChanged(true);
                LOGGER.info("[DYNAMIC CAVE] Player " + player.getDisplayName() + 
                           " changed layer from " + oldLayer + " to " + newLayer);
            } else {
                state.setLayerChanged(false);
            }
            state.setCurrentLayer(newLayer);
            state.setLastUndergroundLayer(newLayer);
        } else {
            state.setLayerChanged(false);
        }
        
        boolean undergroundStateChanged = (wasUnderground != isUnderground);
        
        if (undergroundStateChanged) {
            if (isUnderground) {
                LOGGER.info("[DYNAMIC CAVE] Player " + player.getDisplayName() + 
                           " entered underground at Y=" + playerY + " (layer " + newLayer + "-" + (newLayer + layerSize) + ")");
            } else {
                LOGGER.info("[DYNAMIC CAVE] Player " + player.getDisplayName() + 
                           " returned to surface from Y=" + playerY);
            }
        }
        
        return undergroundStateChanged;
    }
    
    /**
     * Checks if the player changed layers while underground.
     */
    public boolean didLayerChange(@Nonnull Player player) {
        DynamicCaveModeState state = getState(player);
        return state != null && state.isLayerChanged();
    }
    
    /**
     * Gets the previous layer before the last change.
     */
    public int getPreviousLayer(@Nonnull Player player) {
        DynamicCaveModeState state = getState(player);
        return state != null ? state.getPreviousLayer() : 0;
    }
    
    /**
     * Checks if dynamic cave mode is enabled for a player.
     * Checks both server config and player config.
     */
    public boolean isDynamicModeEnabled(@Nonnull Player player) {
        if (!isEffectivelyEnabledForPlayer(player)) {
            return false;
        }
        DynamicCaveModeState state = getState(player);
        return state == null || state.isDynamicModeEnabled();
    }
    
    /**
     * Checks if a player is currently underground (needs cave view).
     * Returns false if cave mode is disabled for this player.
     */
    public boolean isPlayerUnderground(@Nonnull Player player) {
        if (!isEffectivelyEnabledForPlayer(player)) {
            return false;
        }
        DynamicCaveModeState state = getState(player);
        return state != null && state.isCurrentlyUnderground();
    }
    
    /**
     * Gets the cave view radius for a player (in chunks).
     */
    public int getCaveRadius(@Nonnull Player player) {
        DynamicCaveModeState state = getState(player);
        return state != null ? state.getCaveRadius() : getConfigCaveRadius();
    }
    
    /**
     * Gets the current layer Y-level for cave rendering.
     */
    public int getCurrentLayerY(@Nonnull Player player) {
        DynamicCaveModeState state = getState(player);
        if (state == null || !state.isCurrentlyUnderground()) {
            return -1;
        }
        return state.getCurrentLayer() + (state.getLayerSize() / 2);
    }
    
    /**
     * Removes a player from the manager (on disconnect).
     */
    public void removePlayer(@Nonnull Player player) {
        playerStates.remove(player.getDisplayName());
    }
    
    /**
     * Removes a player from the manager by name (on disconnect when Player object unavailable).
     */
    public void removePlayerByName(@Nonnull String playerName) {
        playerStates.remove(playerName);
    }

    /**
     * Clears cave exploration/runtime overlay state for a player.
     */
    public void clearCaveExploration(@Nonnull Player player) {
        clearCaveExploration(player.getDisplayName());
    }

    /**
     * Clears cave exploration/runtime overlay state for a player by name.
     */
    public void clearCaveExploration(@Nonnull String playerName) {
        DynamicCaveModeState state = playerStates.get(playerName);
        if (state == null) {
            return;
        }

        state.getExploredCaveChunks().clear();
        state.getLoadedCaveChunks().clear();
        state.getPendingCaveChunks().clear();
        state.invalidateTargetCache();
        state.setLastOverlayMapChunk(Integer.MIN_VALUE, Integer.MIN_VALUE);
        state.setLastOverlayUpdateMs(0L);
        state.setNeedsLayerRefresh(true);
        state.setCaveProcessingInProgress(false);
    }

    /**
     * Clears cave exploration/runtime overlay state for all tracked players.
     *
     * @return Number of states that were reset.
     */
    public int clearAllCaveExploration() {
        int resetCount = 0;
        for (String playerName : playerStates.keySet()) {
            clearCaveExploration(playerName);
            resetCount++;
        }
        return resetCount;
    }
    
    public void enableCaveMode(@Nonnull Player player, int yLevel) {
        DynamicCaveModeState state = getOrCreateState(player);
        state.setCurrentlyUnderground(true);
        state.setCurrentLayer(getLayerForY(yLevel));
        state.setPlayerY(yLevel);
    }
    
    public void enableCaveMode(@Nonnull Player player, int yLevel, int verticalRange) {
        enableCaveMode(player, yLevel);
    }
    
    public void enableAutoCaveMode(@Nonnull Player player) {
        DynamicCaveModeState state = getOrCreateState(player);
        state.setDynamicModeEnabled(true);
    }
    
    public void disableCaveMode(@Nonnull Player player) {
        DynamicCaveModeState state = getState(player);
        if (state != null) {
            state.setCurrentlyUnderground(false);
        }
    }
    
    public boolean toggleCaveMode(@Nonnull Player player, int yLevel) {
        if (isPlayerUnderground(player)) {
            disableCaveMode(player);
            return false;
        } else {
            enableCaveMode(player, yLevel);
            return true;
        }
    }
    
    public void updateYLevel(@Nonnull Player player, int yLevel) {
        DynamicCaveModeState state = getState(player);
        if (state != null && state.isCurrentlyUnderground()) {
            state.setPlayerY(yLevel);
            state.setCurrentLayer(getLayerForY(yLevel, state.getLayerSize()));
        }
    }
    
    public boolean shouldAutoActivate(@Nonnull Player player, int playerY, int skylight) {
        DynamicCaveModeState state = getState(player);
        int threshold = state != null ? state.getUndergroundThreshold() : getConfigUndergroundThreshold();
        return playerY < threshold && skylight < 15;
    }
    
    /**
     * Gets the player's layer size.
     */
    public int getLayerSize(@Nonnull Player player) {
        DynamicCaveModeState state = getState(player);
        return state != null ? state.getLayerSize() : getConfigLayerSize();
    }
    
    /**
     * Gets the player's underground threshold.
     */
    public int getUndergroundThreshold(@Nonnull Player player) {
        DynamicCaveModeState state = getState(player);
        return state != null ? state.getUndergroundThreshold() : getConfigUndergroundThreshold();
    }
    
    /**
     * Configures the player's cave mode settings.
     */
    public void configureSettings(@Nonnull Player player, boolean enabled, int layerSize, int undergroundThreshold) {
        DynamicCaveModeState state = getOrCreateState(player);
        state.setDynamicModeEnabled(enabled);
        state.setLayerSize(layerSize);
        state.setUndergroundThreshold(undergroundThreshold);
    }

    /**
     * Applies a new cave radius to every tracked player state.
     * This also forces cave overlay target recomputation on next update.
     */
    public void updateCaveRadiusForAllStates(int radius) {
        int clampedRadius = Math.max(1, Math.min(radius, 16));
        playerStates.values().forEach(state -> state.setCaveRadius(clampedRadius));
    }
    
    
    /**
     * Holds the dynamic cave mode state for a single player.
     * Tracks underground detection, layer position, and explored cave chunks.
     * Settings are initialized from BetterMapConfig when created via getOrCreateState().
     */
    public static class DynamicCaveModeState {
        private boolean dynamicModeEnabled = true;
        private boolean currentlyUnderground = false;
        private boolean layerChanged = false;
        private int playerY = 100;
        private int currentLayer = 0;
        private int previousLayer = 0;
        private int lastUndergroundLayer = 0;
        private int caveRadius = 8;
        private long lastLayerChangeTime = 0;
        private boolean needsLayerRefresh = false;
        
        private int layerSize = 5;
        private int undergroundThreshold = 100;
        
        private final Set<Long> exploredCaveChunks = ConcurrentHashMap.newKeySet();
        
        private final Set<Long> loadedCaveChunks = ConcurrentHashMap.newKeySet();
        
        private final Set<Long> pendingCaveChunks = ConcurrentHashMap.newKeySet();

        private volatile long lastOverlayUpdateMs = 0L;
        private volatile int lastOverlayMapChunkX = Integer.MIN_VALUE;
        private volatile int lastOverlayMapChunkZ = Integer.MIN_VALUE;
        
        /** Cached target cave chunks - only recomputed when player moves map chunk */
        private volatile Set<Long> cachedTargetChunks = null;
        /** Sorted by distance from player (nearest first) - used for progressive loading */
        private volatile java.util.List<Long> cachedTargetSorted = null;
        private volatile int cachedTargetMapChunkX = Integer.MIN_VALUE;
        private volatile int cachedTargetMapChunkZ = Integer.MIN_VALUE;
        
        /** Flag to prevent concurrent cave overlay processing */
        private volatile boolean caveProcessingInProgress = false;
        
        public boolean isDynamicModeEnabled() {
            return dynamicModeEnabled;
        }
        
        public void setDynamicModeEnabled(boolean enabled) {
            this.dynamicModeEnabled = enabled;
        }
        
        public boolean needsLayerRefresh() {
            return needsLayerRefresh;
        }
        
        public void setNeedsLayerRefresh(boolean needs) {
            this.needsLayerRefresh = needs;
        }
        
        public boolean isLayerChanged() {
            return layerChanged;
        }
        
        public void setLayerChanged(boolean changed) {
            this.layerChanged = changed;
        }
        
        public int getPreviousLayer() {
            return previousLayer;
        }
        
        public void setPreviousLayer(int layer) {
            this.previousLayer = layer;
        }
        
        public boolean isCurrentlyUnderground() {
            return currentlyUnderground;
        }
        
        public void setCurrentlyUnderground(boolean underground) {
            this.currentlyUnderground = underground;
        }
        
        public int getPlayerY() {
            return playerY;
        }
        
        public void setPlayerY(int y) {
            this.playerY = y;
        }
        
        public int getCurrentLayer() {
            return currentLayer;
        }
        
        public void setCurrentLayer(int layer) {
            if (this.currentLayer != layer) {
                this.lastLayerChangeTime = System.currentTimeMillis();
            }
            this.currentLayer = layer;
        }
        
        public int getLastUndergroundLayer() {
            return lastUndergroundLayer;
        }
        
        public void setLastUndergroundLayer(int layer) {
            this.lastUndergroundLayer = layer;
        }
        
        public int getCaveRadius() {
            return caveRadius;
        }
        
        public void setCaveRadius(int radius) {
            int clamped = Math.max(1, Math.min(radius, 16));
            if (this.caveRadius != clamped) {
                this.caveRadius = clamped;
                pendingCaveChunks.clear();
                invalidateTargetCache();
                this.lastOverlayUpdateMs = 0L;
                this.needsLayerRefresh = true;
                this.caveProcessingInProgress = false;
                return;
            }
            this.caveRadius = clamped;
        }
        
        public long getLastLayerChangeTime() {
            return lastLayerChangeTime;
        }
                
        public int getLayerSize() {
            return layerSize;
        }
        
        public void setLayerSize(int size) {
            this.layerSize = Math.max(1, Math.min(size, 20));
        }
        
        public int getUndergroundThreshold() {
            return undergroundThreshold;
        }
        
        public void setUndergroundThreshold(int threshold) {
            this.undergroundThreshold = Math.max(0, Math.min(threshold, 319));
        }
        
        /**
         * Gets all explored cave chunks (flat set, no layer separation).
         */
        public Set<Long> getExploredCaveChunks() {
            return exploredCaveChunks;
        }
        
        /**
         * Loads persisted cave exploration data.
         * @param chunks Set of explored chunk indices
         */
        public void loadExploredChunks(Set<Long> chunks) {
            if (chunks != null) {
                exploredCaveChunks.addAll(chunks);
            }
        }
        
        /**
         * Marks a chunk as explored in caves.
         */
        public void markCaveChunkExplored(long chunkIdx) {
            exploredCaveChunks.add(chunkIdx);
        }
        
        public Set<Long> getLoadedCaveChunks() {
            return loadedCaveChunks;
        }
        
        public Set<Long> getPendingCaveChunks() {
            return pendingCaveChunks;
        }

        public long getLastOverlayUpdateMs() {
            return lastOverlayUpdateMs;
        }

        public void setLastOverlayUpdateMs(long lastOverlayUpdateMs) {
            this.lastOverlayUpdateMs = lastOverlayUpdateMs;
        }

        public int getLastOverlayMapChunkX() {
            return lastOverlayMapChunkX;
        }

        public int getLastOverlayMapChunkZ() {
            return lastOverlayMapChunkZ;
        }

        public void setLastOverlayMapChunk(int mapChunkX, int mapChunkZ) {
            this.lastOverlayMapChunkX = mapChunkX;
            this.lastOverlayMapChunkZ = mapChunkZ;
        }
        
        public Set<Long> getCachedTargetChunks() { return cachedTargetChunks; }
        public void setCachedTargetChunks(Set<Long> targets) { this.cachedTargetChunks = targets; }
        /** Gets target chunks sorted nearest-first for progressive loading */
        public java.util.List<Long> getCachedTargetSorted() { return cachedTargetSorted; }
        public void setCachedTargetSorted(java.util.List<Long> sorted) { this.cachedTargetSorted = sorted; }
        public int getCachedTargetMapChunkX() { return cachedTargetMapChunkX; }
        public int getCachedTargetMapChunkZ() { return cachedTargetMapChunkZ; }
        public void setCachedTargetPosition(int mx, int mz) {
            this.cachedTargetMapChunkX = mx;
            this.cachedTargetMapChunkZ = mz;
        }
        public void invalidateTargetCache() {
            this.cachedTargetChunks = null;
            this.cachedTargetSorted = null;
            this.cachedTargetMapChunkX = Integer.MIN_VALUE;
            this.cachedTargetMapChunkZ = Integer.MIN_VALUE;
        }
        public boolean isCaveProcessingInProgress() { return caveProcessingInProgress; }
        public void setCaveProcessingInProgress(boolean inProgress) { this.caveProcessingInProgress = inProgress; }

        /**
         * Clears loaded cave chunks (when transitioning to surface).
         */
        public void clearLoadedCaveChunks() {
            loadedCaveChunks.clear();
            pendingCaveChunks.clear();
            invalidateTargetCache();
        }
        
        /**
         * Gets the Y level for rendering (middle of layer + offset based on player position).
         */
        public int getRenderYLevel() {
            return playerY;
        }
        
        /**
         * Gets the vertical range for the layer.
         */
        public int getVerticalRange() {
            return layerSize / 2;
        }
    }
}

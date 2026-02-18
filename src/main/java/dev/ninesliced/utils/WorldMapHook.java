package dev.ninesliced.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.iterator.CircleSpiralIterator;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMapSettings;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.managers.CaveModeManager;
import dev.ninesliced.managers.ChunkStreamingManager;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.MapExpansionManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.WorldBorderManager;
import dev.ninesliced.providers.CaveModeImageBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Hooks into the Hytale WorldMap system to provide custom exploration behavior.
 *
 * DYNAMIC CAVE MODE SYSTEM:
 * - Automatically detects when player goes underground (below Y=100 with ceiling)
 * - Shows cave view in a radius (default 8 chunks) around the player
 * - Y-levels are divided into layers (0-10, 10-20, 20-30, etc.)
 * - Normal surface map is always shown for explored areas
 * - Cave overlay appears seamlessly when underground
 * - Previously explored cave chunks persist per layer
 * - When returning to surface, cave overlay disappears automatically
 */
public class WorldMapHook {
    private static final Logger LOGGER = Logger.getLogger(WorldMapHook.class.getName());

    /**
     * Consolidated per-player cave mode state.
     */
    private static final class CaveModePlayerState {
        final Set<Long> loadedChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Set<Long> failedChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Set<Long> targetChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Set<Long> pendingChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Map<Long, CompletableFuture<CaveModeImageBuilder>> pendingFutures = new java.util.concurrent.ConcurrentHashMap<>();
        int retryCounter;
        int lastSharedCount = -1;
        Boolean lastShareEnabled;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, CaveModePlayerState> cavePlayerStates = new java.util.concurrent.ConcurrentHashMap<>();

    private static CaveModePlayerState getCavePlayerState(String playerName) {
        return cavePlayerStates.computeIfAbsent(playerName, k -> new CaveModePlayerState());
    }

    private static final Map<String, Set<Long>> sharedCaveExploredChunks = new java.util.concurrent.ConcurrentHashMap<>();

    private static Set<Long> getSharedCaveExploredChunks(@Nonnull String worldName) {
        return sharedCaveExploredChunks.computeIfAbsent(worldName, k -> java.util.Collections.synchronizedSet(new HashSet<>()));
    }

    /**
     * Gets shared cave explored chunks and lazily hydrates the cache from persisted/active data.
     * This keeps cave sharing working after restarts and for players who are not currently underground.
     */
    private static Set<Long> getHydratedSharedCaveExploredChunks(@Nonnull String worldName) {
        Set<Long> shared = getSharedCaveExploredChunks(worldName);
        if (shared.isEmpty()) {
            Set<Long> allKnown = ExplorationManager.getInstance().getAllExploredCaveChunks(worldName);
            if (!allKnown.isEmpty()) {
                shared.addAll(allKnown);
            }
        }
        return shared;
    }

    /**
     * Clears the cave mode loaded chunks for a player.
     */
    public static void clearCaveModeLoadedChunks(String playerName) {
        CaveModePlayerState ps = cavePlayerStates.get(playerName);
        if (ps != null) {
            ps.loadedChunks.clear();
            ps.failedChunks.clear();
            ps.targetChunks.clear();
            ps.pendingFutures.clear();
            ps.pendingChunks.clear();
            ps.retryCounter = 0;
            ps.lastSharedCount = -1;
            ps.lastShareEnabled = null;
        }
    }

    /**
     * Removes a player from cave mode tracking (on disconnect).
     */
    public static void removeCaveModePlayer(String playerName) {
        cavePlayerStates.remove(playerName);
    }

    /**
     * Injects a custom RestrictedSpiralIterator into the player's world map tracker.
     *
     * @param player  The player.
     * @param tracker The world map tracker.
     */
    public static void hookPlayerMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            setTrackerViewRadiusOverride(tracker, 999);

            World world = player.getWorld();
            if (world != null) {
                sendMapSettingsToPlayer(player);
            }

            ExplorationTracker.PlayerExplorationData explorationData = ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            RestrictedSpiralIterator customIterator = new RestrictedSpiralIterator(explorationData, tracker);

            setTrackerSpiralIterator(tracker, customIterator);

            LOGGER.info("Hooked map tracker for player: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to hook WorldMapTracker for player " + player.getDisplayName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Removes the custom hooks from the player's tracker, attempting to clean up.
     *
     * @param player  The player.
     * @param tracker The tracker.
     */
    public static void unhookPlayerMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            Object spiralIterator = getTrackerSpiralIterator(tracker);
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                ((RestrictedSpiralIterator) spiralIterator).stop();
            }

            int mapChunkX = 0;
            int mapChunkZ = 0;
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    var pos = tc.getPosition();
                    mapChunkX = (int) Math.floor(pos.x) >> 5;
                    mapChunkZ = (int) Math.floor(pos.z) >> 5;
                }
            }

            CircleSpiralIterator vanillaIterator = new CircleSpiralIterator();
            vanillaIterator.init(mapChunkX, mapChunkZ, 0, 999);
            setTrackerSpiralIterator(tracker, vanillaIterator);
            setTrackerViewRadiusOverride(tracker, (Integer) null);

            try {
                Object pendingReloadFutures = getTrackerPendingReloadFutures(tracker);
                if (pendingReloadFutures instanceof Map) {
                    ((Map<?, ?>) pendingReloadFutures).clear();
                }
            } catch (Exception e) {
                LOGGER.fine("Could not clear pendingReloadFutures: " + e.getMessage());
            }

            try {
                Object pendingReloadChunks = getTrackerPendingReloadChunks(tracker);
                if (pendingReloadChunks instanceof Set) {
                    ((Set<?>) pendingReloadChunks).clear();
                }
            } catch (Exception e) {
                LOGGER.fine("Could not clear pendingReloadChunks: " + e.getMessage());
            }

            try {
                setTrackerUpdateTimer(tracker, 999.0f);
            } catch (Exception ignored) {}

            ChunkStreamingManager.getInstance().removeState(player.getDisplayName());

            LOGGER.info("Unhooked map tracker for player: " + player.getDisplayName() + " at map chunk (" + mapChunkX + ", " + mapChunkZ + ")");
        } catch (Exception e) {
            LOGGER.warning("Error unhooking tracker for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Restores the vanilla CircleSpiralIterator to the tracker.
     *
     * @param player  The player.
     * @param tracker The tracker.
     */
    public static void restoreVanillaMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            Object spiralIterator = getTrackerSpiralIterator(tracker);
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                ((RestrictedSpiralIterator) spiralIterator).stop();
            }

            setTrackerViewRadiusOverride(tracker, (Integer) null);

            int mapChunkX = 0;
            int mapChunkZ = 0;
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    var pos = tc.getPosition();
                    mapChunkX = (int) Math.floor(pos.x) >> 5;
                    mapChunkZ = (int) Math.floor(pos.z) >> 5;
                }
            }

            CircleSpiralIterator vanillaIterator = new CircleSpiralIterator();
            vanillaIterator.init(mapChunkX, mapChunkZ, 0, 999);
            setTrackerSpiralIterator(tracker, vanillaIterator);

            setTrackerUpdateTimer(tracker, 0.0f);

            LOGGER.info("Restored vanilla map tracker for player: " + player.getDisplayName() + " at map chunk (" + mapChunkX + ", " + mapChunkZ + ")");
        } catch (Exception e) {
            LOGGER.warning("Failed to restore vanilla tracker for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Adjusts world map settings (resolution/scale) for the given world based on configuration.
     *
     * @param world The world.
     */
    public static void hookWorldMapResolution(@Nonnull World world) {
        try {
            LOGGER.info("Hooking WorldMap resolution for world: " + world.getName());
            WorldMapManager manager = world.getWorldMapManager();

            LOGGER.info("Modifying WorldMapSettings for world: " + world.getName());
            WorldMapSettings settings = manager.getWorldMapSettings();

            ModConfig.MapQuality quality = ModConfig.getInstance().getActiveMapQuality();
            setSettingsImageScale(settings, quality.scale);

            manager.clearImages();

            WorldBorderManager.getInstance().hookWorldMapManager(world);

            LOGGER.info("Modified WorldMapSettings imageScale to " + quality.scale + " (" + quality + " quality) for world: " + world.getName());
        } catch (Exception e) {
            LOGGER.warning("Failed to hook WorldMap resolution: " + e.getMessage());
        }
    }

    /**
     * Updates the exploration state for a player, updating boundaries and forcing a tracker update if moved.
     * Now uses DYNAMIC cave mode - automatically shows cave view when underground.
     *
     * When cave mode is active and player is underground:
     * - Only cave chunks are marked as explored (not normal surface chunks)
     * - Only cave chunks are loaded (not normal chunks)
     *
     * When cave mode is disabled globally:
     * - Normal chunks are always marked as explored (even when underground)
     * - Cave chunks are never saved
     *
     * @param player  The player.
     * @param tracker The tracker.
     * @param x       Player X.
     * @param z       Player Z.
     */
    public static void updateExplorationState(@Nonnull Player player, @Nonnull WorldMapTracker tracker, double x, double z) {
        try {
            ExplorationTracker explorationTracker = ExplorationTracker.getInstance();
            ExplorationTracker.PlayerExplorationData explorationData = explorationTracker.getPlayerData(player);

            if (explorationData == null) {
                explorationData = explorationTracker.getOrCreatePlayerData(player);
                if (explorationData == null) {
                    LOGGER.warning("[DEBUG] Could not create exploration data for " + player.getDisplayName());
                    return;
                }
            }

            World world = player.getWorld();
            if (world != null) {
                explorationData.setWorldName(world.getName());
            }

            int playerChunkX = ChunkUtil.blockToChunkCoord(x);
            int playerChunkZ = ChunkUtil.blockToChunkCoord(z);
            boolean hasMoved = explorationData.hasMovedToNewChunk(playerChunkX, playerChunkZ);

            Ref<EntityStore> playerRef = player.getReference();
            TransformComponent transform = (playerRef != null && playerRef.isValid())
                ? playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType())
                : null;
            int playerY = transform != null ? (int) transform.getPosition().y : 100;
            boolean hasCeiling = checkForCeiling(world, player, x, playerY, z);

            CaveModeManager caveManager = CaveModeManager.getInstance();

            boolean caveModeGloballyEnabled = ModConfig.getInstance().isCaveModeEnabled();

            boolean stateChanged = false;
            boolean isUnderground = false;

            if (caveModeGloballyEnabled) {
                stateChanged = caveManager.updateUndergroundState(player, playerY, hasCeiling);
                isUnderground = caveManager.isPlayerUnderground(player);
            }

            boolean discoverSurfaceUnderground = ModConfig.getInstance().isDiscoverSurfaceUnderground();

            if (hasMoved && (!caveModeGloballyEnabled || !isUnderground || discoverSurfaceUnderground)) {
                int explorationRadius = ModConfig.getInstance().getExplorationRadius();
                int beforeCount = explorationData.getExploredChunks().getExploredCount();
                explorationData.getMapExpansion().updateBoundaries(playerChunkX, playerChunkZ, explorationRadius);
                explorationData.setLastChunkPosition(playerChunkX, playerChunkZ);
                int afterCount = explorationData.getExploredChunks().getExploredCount();

                if (afterCount > beforeCount) {
                    LOGGER.fine("[EXPLORATION] Added " + (afterCount - beforeCount) + " new surface chunks. Total: " + afterCount);
                }
            }

            if (caveModeGloballyEnabled) {
                if (stateChanged && world != null) {
                    CaveModeManager.DynamicCaveModeState state = caveManager.getState(player);
                    boolean fogOfWar = ModConfig.getInstance().isCaveFogOfWar();

                    if (isUnderground) {
                        LOGGER.fine("[DYNAMIC CAVE] Activating cave overlay for " + player.getDisplayName() +
                                   " at layer " + state.getCurrentLayer() + "-" + (state.getCurrentLayer() + state.getLayerSize()));

                        if (fogOfWar) {
                            LOGGER.fine("[DYNAMIC CAVE] Fog of war enabled - refreshing map for cave entry");
                            forceFullMapRefresh(player);
                        }

                    } else {
                        LOGGER.fine("[DYNAMIC CAVE] Deactivating cave overlay for " + player.getDisplayName());

                        if (fogOfWar) {
                            LOGGER.fine("[DYNAMIC CAVE] Fog of war enabled - refreshing map for cave exit");
                            forceFullMapRefresh(player);
                        } else {
                            clearCaveModeOverlay(player, world, tracker);
                        }
                    }
                }

                boolean layerChanged = caveManager.didLayerChange(player);
                if (layerChanged && isUnderground && world != null) {
                    CaveModeManager.DynamicCaveModeState state = caveManager.getState(player);
                    int previousLayer = caveManager.getPreviousLayer(player);
                    int currentLayer = state.getCurrentLayer();

                    LOGGER.fine("[DYNAMIC CAVE] Layer change: " + previousLayer + " -> " + currentLayer +
                               ". Will regenerate cave images for new Y level.");

                    state.setNeedsLayerRefresh(true);

                    String playerName = player.getDisplayName();
                    CaveModePlayerState playerState = getCavePlayerState(playerName);
                    for (Long pendingIdx : new ArrayList<>(state.getPendingCaveChunks())) {
                        playerState.pendingFutures.remove(pendingIdx);
                    }
                    state.getPendingCaveChunks().clear();
                }

                if (isUnderground && world != null) {
                    CaveModeManager.DynamicCaveModeState state = caveManager.getState(player);
                    scheduleCaveOverlayUpdate(player, world, tracker, x, z, state);
                    return;
                }
            }

            if (hasMoved) {
                forceTrackerUpdate(player, tracker, x, z);
                int mapChunkX = playerChunkX >> 1;
                int mapChunkZ = playerChunkZ >> 1;
                manageLoadedChunks(player, tracker, mapChunkX, mapChunkZ);
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Exception in updateExplorationState: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if there's a ceiling (solid blocks) above the player.
     * Used to detect if player is actually underground vs just at low Y.
     *
     * For now, we use a simplified approach: if player is below the threshold,
     * we assume they're in a cave. A more sophisticated check would scan actual blocks.
     */
    private static boolean checkForCeiling(@Nullable World world, @Nullable Player player, double x, int y, double z) {
        if (world == null) return false;

        int threshold = CaveModeManager.getConfigUndergroundThreshold();
        if (player != null) {
            CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
            if (state != null) {
                threshold = state.getUndergroundThreshold();
            }
        }

        return y < threshold;

    }

    /**
     * Schedules cave overlay update to run OFF the world thread.
     * Called from the world thread - reads tracker state (fast), then dispatches heavy work
     * to the ticker thread. This prevents the world thread from being blocked by cave processing.
     *
     * Like Hytale's WorldMapManager which runs on its own 10 TPS thread, cave overlay
     * computation runs on the BetterMap ticker thread, with only packet sending on the world thread.
     */
    private static void scheduleCaveOverlayUpdate(@Nonnull Player player, @Nonnull World world,
                                                    @Nonnull WorldMapTracker tracker,
                                                    double playerX, double playerZ,
                                                    @Nonnull CaveModeManager.DynamicCaveModeState state) {
        if (state.isCaveProcessingInProgress()) {
            return;
        }

        boolean fogOfWar = ModConfig.getInstance().isCaveFogOfWar();

        Object spiralIterator = getTrackerSpiralIterator(tracker);
        if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
            restrictedIterator.setCaveModeActive(fogOfWar);
        }

        WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
        float imageScale = settings.getImageScale();
        int imageSize = MathUtil.fastFloor(32.0F * imageScale);

        Object loadedObj = getTrackerLoaded(tracker);
        @SuppressWarnings("unchecked")
        Set<Long> trackerLoaded = (loadedObj instanceof Set) ? (Set<Long>) loadedObj : null;

        state.setCaveProcessingInProgress(true);
        dev.ninesliced.exploration.ExplorationTicker.getInstance().scheduleUpdate(() -> {
            try {
                processCaveOverlayAsync(player, world, tracker, trackerLoaded, playerX, playerZ, state, imageSize);
            } finally {
                state.setCaveProcessingInProgress(false);
            }
        });
    }

    /**
     * Processes cave overlay computation OFF the world thread (runs on the ticker thread).
     * All heavy work (candidate gathering, sorting, future processing) happens here.
     * Only packet sending and tracker state updates are dispatched to the world thread.
     */
    private static void processCaveOverlayAsync(@Nonnull Player player, @Nonnull World world,
                                                  @Nonnull WorldMapTracker tracker,
                                                  @Nullable Set<Long> trackerLoaded,
                                                  double playerX, double playerZ,
                                                  @Nonnull CaveModeManager.DynamicCaveModeState state,
                                                  int imageSize) {
        try {
            int playerMapChunkX = ((int) Math.floor(playerX)) >> 5;
            int playerMapChunkZ = ((int) Math.floor(playerZ)) >> 5;

            long nowMs = System.currentTimeMillis();
            boolean movedMapChunk = playerMapChunkX != state.getLastOverlayMapChunkX() ||
                                    playerMapChunkZ != state.getLastOverlayMapChunkZ();
            boolean needsRefresh = state.needsLayerRefresh();
            boolean hasPending = !state.getPendingCaveChunks().isEmpty();
            if (!movedMapChunk && !needsRefresh && !hasPending) {
                long lastUpdate = state.getLastOverlayUpdateMs();
                if (nowMs - lastUpdate < 200) {
                    return;
                }
            }
            state.setLastOverlayUpdateMs(nowMs);
            state.setLastOverlayMapChunk(playerMapChunkX, playerMapChunkZ);

            int caveRadius = state.getCaveRadius();
            int yLevel = state.getRenderYLevel();
            int verticalRange = state.getVerticalRange();
            int maxChunks = ModConfig.getInstance().getActiveMapQuality().maxChunks;
            boolean shareCaves = ModConfig.getInstance().isShareAllExploration();

            Set<Long> loadedCaveChunks = state.getLoadedCaveChunks();
            Set<Long> pendingCaveChunks = state.getPendingCaveChunks();
            Set<Long> exploredCaveChunks = state.getExploredCaveChunks();
            Set<Long> sharedExplored = shareCaves ? getHydratedSharedCaveExploredChunks(world.getName()) : null;

            if (shareCaves && sharedExplored != null && !exploredCaveChunks.isEmpty()) {
                sharedExplored.addAll(exploredCaveChunks);
            }

            String playerName = player.getDisplayName();
            CaveModePlayerState playerState = getCavePlayerState(playerName);
            Set<Long> failedChunks = playerState.failedChunks;
            List<MapChunk> chunksToSend = state.borrowChunksToSend();
            Set<Long> trackerToAdd = state.borrowTrackerToAdd();
            Set<Long> trackerToRemove = state.borrowTrackerToRemove();

            boolean shareModeChanged = false;
            Boolean previousShareEnabled = playerState.lastShareEnabled;
            if (previousShareEnabled == null || previousShareEnabled.booleanValue() != shareCaves) {
                shareModeChanged = true;
            }
            playerState.lastShareEnabled = shareCaves;

            boolean sharedChanged = false;
            if (shareCaves && sharedExplored != null) {
                int currentSharedCount = sharedExplored.size();
                int previousSharedCount = playerState.lastSharedCount;
                sharedChanged = previousSharedCount != currentSharedCount;
                playerState.lastSharedCount = currentSharedCount;
            } else {
                playerState.lastSharedCount = -1;
            }

            for (Long pendingIdx : new ArrayList<>(pendingCaveChunks)) {
                CompletableFuture<CaveModeImageBuilder> future = playerState.pendingFutures.get(pendingIdx);
                if (future == null) {
                    pendingCaveChunks.remove(pendingIdx);
                    continue;
                }
                if (future.isDone()) {
                    pendingCaveChunks.remove(pendingIdx);
                    playerState.pendingFutures.remove(pendingIdx);

                    CaveModeImageBuilder builder = future.getNow(null);
                    if (builder != null && builder.getImage() != null && builder.getImage().data != null) {
                        int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(pendingIdx);
                        int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(pendingIdx);
                        chunksToSend.add(new MapChunk(mx, mz, builder.getImage()));
                        loadedCaveChunks.add(pendingIdx);
                        trackerToAdd.add(pendingIdx);
                        if (shareCaves && sharedExplored != null) {
                            sharedExplored.add(pendingIdx);
                        }
                        failedChunks.remove(pendingIdx);
                    } else {
                        failedChunks.add(pendingIdx);
                    }
                } else if (future.isCompletedExceptionally()) {
                    pendingCaveChunks.remove(pendingIdx);
                    playerState.pendingFutures.remove(pendingIdx);
                    failedChunks.add(pendingIdx);
                }
            }

            boolean needsTargetRecompute = movedMapChunk || needsRefresh || shareModeChanged || sharedChanged || state.getCachedTargetChunks() == null;
            Set<Long> targetCaveChunks;

            if (needsTargetRecompute) {
                int caveRadiusSq = caveRadius * caveRadius;
                int scanRadius = caveRadius + 2;
                int scanRadiusSq = scanRadius * scanRadius;

                Set<Long> candidateSet = state.borrowCandidateSet();
                List<long[]> candidateChunksWithDist = state.borrowCandidateChunksWithDist();

                for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                    for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                        int dist2 = dx * dx + dz * dz;
                        if (dist2 > scanRadiusSq) continue;

                        int mx = playerMapChunkX + dx;
                        int mz = playerMapChunkZ + dz;
                        long idx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(mx, mz);

                        boolean inImmediateRadius = dist2 <= caveRadiusSq;
                        boolean explored = inImmediateRadius || exploredCaveChunks.contains(idx) ||
                                (shareCaves && sharedExplored != null && sharedExplored.contains(idx));

                        if (explored) {
                            candidateSet.add(idx);
                            candidateChunksWithDist.add(new long[]{idx, dist2});
                            if (inImmediateRadius) {
                                state.markCaveChunkExplored(idx);
                                if (shareCaves && sharedExplored != null) {
                                    sharedExplored.add(idx);
                                }
                            }
                        }
                    }
                }

                for (Long exploredIdx : exploredCaveChunks) {
                    if (!candidateSet.contains(exploredIdx)) {
                        candidateSet.add(exploredIdx);
                        int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(exploredIdx);
                        int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(exploredIdx);
                        long ddx = (long) mx - playerMapChunkX;
                        long ddz = (long) mz - playerMapChunkZ;
                        candidateChunksWithDist.add(new long[]{exploredIdx, ddx * ddx + ddz * ddz});
                    }
                }
                if (shareCaves && sharedExplored != null) {
                    for (Long sharedIdx : sharedExplored) {
                        if (!candidateSet.contains(sharedIdx)) {
                            candidateSet.add(sharedIdx);
                            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(sharedIdx);
                            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(sharedIdx);
                            long ddx = (long) mx - playerMapChunkX;
                            long ddz = (long) mz - playerMapChunkZ;
                            candidateChunksWithDist.add(new long[]{sharedIdx, ddx * ddx + ddz * ddz});
                        }
                    }
                }

                candidateChunksWithDist.sort(Comparator.comparingLong(a -> a[1]));

                int caveChunksAllowed = maxChunks * 3 / 4;
                int targetCount = Math.min(candidateChunksWithDist.size(), caveChunksAllowed);

                targetCaveChunks = new HashSet<>(targetCount * 2);
                List<Long> sortedTargets = new ArrayList<>(targetCount);
                for (int i = 0; i < targetCount; i++) {
                    long chunkId = candidateChunksWithDist.get(i)[0];
                    targetCaveChunks.add(chunkId);
                    sortedTargets.add(chunkId);
                }

                state.setCachedTargetChunks(targetCaveChunks);
                state.setCachedTargetSorted(sortedTargets);
                state.setCachedTargetPosition(playerMapChunkX, playerMapChunkZ);
            } else {
                targetCaveChunks = state.getCachedTargetChunks();
            }

            List<Long> sortedTargets = state.getCachedTargetSorted();
            if (sortedTargets == null) {
                sortedTargets = new ArrayList<>(targetCaveChunks);
            }

            List<MapChunk> chunksToUnload = state.borrowChunksToUnload();

            for (Long loadedIdx : new ArrayList<>(loadedCaveChunks)) {
                if (!targetCaveChunks.contains(loadedIdx)) {
                    loadedCaveChunks.remove(loadedIdx);
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(loadedIdx);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(loadedIdx);
                    chunksToUnload.add(new MapChunk(mx, mz, null));
                    trackerToRemove.add(loadedIdx);
                }
            }

            if (needsRefresh) {
                LOGGER.fine("[DYNAMIC CAVE] Refreshing chunks for new Y level: " + yLevel);
                for (Long chunkIdx : new ArrayList<>(loadedCaveChunks)) {
                    if (!targetCaveChunks.contains(chunkIdx)) continue;
                    if (pendingCaveChunks.contains(chunkIdx)) continue;

                    CompletableFuture<CaveModeImageBuilder> future = CaveModeImageBuilder.build(
                        chunkIdx, imageSize, imageSize, world, yLevel, verticalRange);

                    if (future.isDone()) {
                        CaveModeImageBuilder builder = future.getNow(null);
                        if (builder != null && builder.getImage() != null && builder.getImage().data != null) {
                            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(chunkIdx);
                            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(chunkIdx);
                            chunksToSend.add(new MapChunk(mx, mz, builder.getImage()));
                        }
                    } else {
                        pendingCaveChunks.add(chunkIdx);
                        playerState.pendingFutures.put(chunkIdx, future);
                    }
                }
                state.setNeedsLayerRefresh(false);
            }

            final int MAX_PENDING_GENERATION = 20;
            int currentPending = pendingCaveChunks.size();
            int availableSlots = MAX_PENDING_GENERATION - currentPending;

            if (availableSlots > 0) {
                int newGenerations = 0;
                int immediateLoads = 0;
                int maxImmediateLoads = 4;

                for (Long chunkIdx : sortedTargets) {
                    if (newGenerations >= availableSlots && immediateLoads >= maxImmediateLoads) break;
                    if (loadedCaveChunks.contains(chunkIdx) || pendingCaveChunks.contains(chunkIdx)) continue;

                    CompletableFuture<CaveModeImageBuilder> future = CaveModeImageBuilder.build(
                        chunkIdx, imageSize, imageSize, world, yLevel, verticalRange);

                    if (future.isDone() && immediateLoads < maxImmediateLoads) {
                        CaveModeImageBuilder builder = future.getNow(null);
                        if (builder != null && builder.getImage() != null && builder.getImage().data != null) {
                            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(chunkIdx);
                            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(chunkIdx);
                            chunksToSend.add(new MapChunk(mx, mz, builder.getImage()));
                            loadedCaveChunks.add(chunkIdx);
                            trackerToAdd.add(chunkIdx);
                            if (shareCaves && sharedExplored != null) {
                                sharedExplored.add(chunkIdx);
                            }
                            immediateLoads++;
                            failedChunks.remove(chunkIdx);
                        } else {
                            failedChunks.add(chunkIdx);
                        }
                    } else if (!future.isDone() && newGenerations < availableSlots) {
                        pendingCaveChunks.add(chunkIdx);
                        playerState.pendingFutures.put(chunkIdx, future);
                        newGenerations++;
                    }
                }
            }

            playerState.retryCounter++;

            if (playerState.retryCounter % 10 == 0 && !failedChunks.isEmpty()) {
                int retryCount = Math.min(3, failedChunks.size());
                Iterator<Long> failedIter = failedChunks.iterator();
                for (int i = 0; i < retryCount && failedIter.hasNext(); i++) {
                    Long failedIdx = failedIter.next();
                    if (targetCaveChunks.contains(failedIdx) && !loadedCaveChunks.contains(failedIdx) && !pendingCaveChunks.contains(failedIdx)) {
                        failedIter.remove();
                    }
                }
            }

            Set<Long> globalTargetChunks = playerState.targetChunks;
            globalTargetChunks.clear();
            globalTargetChunks.addAll(targetCaveChunks);

            final List<MapChunk> finalChunksToSend = chunksToSend;
            final List<MapChunk> finalChunksToUnload = chunksToUnload;
            final Set<Long> finalTrackerToAdd = trackerToAdd;
            final Set<Long> finalTrackerToRemove = trackerToRemove;

            if (!finalChunksToSend.isEmpty() || !finalChunksToUnload.isEmpty()) {
                world.execute(() -> {
                    try {
                        Ref<EntityStore> ref = player.getReference();
                        if (ref == null || !ref.isValid()) return;

                        if (trackerLoaded != null) {
                            for (Long idx : finalTrackerToRemove) {
                                trackerLoaded.remove(idx);
                            }
                            for (Long idx : finalTrackerToAdd) {
                                trackerLoaded.add(idx);
                            }

                            int totalLoaded = trackerLoaded.size();
                            if (totalLoaded > maxChunks) {
                                List<Long> surfaceToEvict = new ArrayList<>();
                                for (Long idx : trackerLoaded) {
                                    if (!loadedCaveChunks.contains(idx)) {
                                        surfaceToEvict.add(idx);
                                    }
                                }
                                surfaceToEvict.sort(Comparator.comparingLong(idx -> {
                                    int emx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                                    int emz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                                    long ddx = (long) emx - playerMapChunkX;
                                    long ddz = (long) emz - playerMapChunkZ;
                                    return -(ddx * ddx + ddz * ddz);
                                }));

                                int toRemove = totalLoaded - maxChunks;
                                for (int i = 0; i < toRemove && i < surfaceToEvict.size(); i++) {
                                    Long idx = surfaceToEvict.get(i);
                                    int emx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                                    int emz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                                    finalChunksToUnload.add(new MapChunk(emx, emz, null));
                                    trackerLoaded.remove(idx);
                                }
                            }
                        }

                        if (!finalChunksToUnload.isEmpty()) {
                            UpdateWorldMap unloadPacket = new UpdateWorldMap(finalChunksToUnload.toArray(new MapChunk[0]), null, null);
                            sendPacket(player, unloadPacket);
                        }

                        if (!finalChunksToSend.isEmpty()) {
                            int batchSize = 15;
                            for (int i = 0; i < finalChunksToSend.size(); i += batchSize) {
                                int end = Math.min(i + batchSize, finalChunksToSend.size());
                                List<MapChunk> batch = finalChunksToSend.subList(i, end);
                                UpdateWorldMap packet = new UpdateWorldMap(batch.toArray(new MapChunk[0]), null, null);
                                sendPacket(player, packet);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.fine("[DYNAMIC CAVE] Error sending cave packets: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.warning("[DYNAMIC CAVE] Error processing overlay async: " + e.getMessage());
        }
    }

    /**
     * Clears the cave mode overlay chunks and restores normal map view.
     * Sends unload packets (null images) for all loaded cave chunks, then forces normal map refresh.
     */
    private static void clearCaveModeOverlay(@Nonnull Player player, @Nonnull World world,
                                              @Nonnull WorldMapTracker tracker) {
        try {
            CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
            if (state == null) return;

            Set<Long> loadedCaveChunks = new HashSet<>(state.getLoadedCaveChunks());
            String playerName = player.getDisplayName();

            CaveModePlayerState playerState = getCavePlayerState(playerName);
            playerState.pendingFutures.clear();
            state.getPendingCaveChunks().clear();

            Object loadedObj = getTrackerLoaded(tracker);
            @SuppressWarnings("unchecked")
            Set<Long> trackerLoaded = (loadedObj instanceof Set) ? (Set<Long>) loadedObj : new HashSet<>();

            List<MapChunk> chunksToUnload = new ArrayList<>();
            for (Long caveChunkIdx : loadedCaveChunks) {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(caveChunkIdx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(caveChunkIdx);
                chunksToUnload.add(new MapChunk(mx, mz, null));
                trackerLoaded.remove(caveChunkIdx);
            }

            if (!chunksToUnload.isEmpty()) {
                int batchSize = 50;
                for (int i = 0; i < chunksToUnload.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, chunksToUnload.size());
                    List<MapChunk> batch = chunksToUnload.subList(i, end);
                    UpdateWorldMap unloadPacket = new UpdateWorldMap(batch.toArray(new MapChunk[0]), null, null);
                    sendPacket(player, unloadPacket);
                }
                LOGGER.fine("[DYNAMIC CAVE] Unloaded " + chunksToUnload.size() + " cave chunks for " + playerName);
            }

            state.clearLoadedCaveChunks();

            Object spiralIterator = getTrackerSpiralIterator(tracker);
            if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                restrictedIterator.setCaveModeActive(false);
                restrictedIterator.resetState();
            }

            Ref<EntityStore> playerRef = player.getReference();
            TransformComponent transform = (playerRef != null && playerRef.isValid())
                ? playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType())
                : null;
            if (transform != null) {
                var pos = transform.getPosition();
                forceTrackerUpdate(player, tracker, pos.x, pos.z);

                int playerChunkX = ChunkUtil.blockToChunkCoord(pos.x);
                int playerChunkZ = ChunkUtil.blockToChunkCoord(pos.z);
                int mapChunkX = playerChunkX >> 1;
                int mapChunkZ = playerChunkZ >> 1;
                manageLoadedChunks(player, tracker, mapChunkX, mapChunkZ);
            }

            setTrackerUpdateTimer(tracker, 0.0f);

            LOGGER.fine("[DYNAMIC CAVE] Cleared cave overlay and triggered normal map refresh for " + playerName);

        } catch (Exception e) {
            LOGGER.warning("[DYNAMIC CAVE] Error clearing overlay: " + e.getMessage());
        }
    }

    private static void manageLoadedChunks(@Nonnull Player player, @Nonnull WorldMapTracker tracker, int cx, int cz) {
        try {
            Object loadedObj = getTrackerLoaded(tracker);
            if (!(loadedObj instanceof Set))
                return;
            
            @SuppressWarnings("unchecked")
            Set<Long> loaded = (Set<Long>) loadedObj;

            Object spiralIterator = getTrackerSpiralIterator(tracker);
            if (!(spiralIterator instanceof RestrictedSpiralIterator))
                return;

            List<Long> targetChunks = ((RestrictedSpiralIterator) spiralIterator).getTargetMapChunks();
            Set<Long> targetSet = new HashSet<>(targetChunks);
            
            String playerName = player.getDisplayName();
            ChunkStreamingManager streamingManager = ChunkStreamingManager.getInstance();

            ChunkStreamingManager.ChunkDelta delta = streamingManager.computeDelta(
                playerName, targetSet, cx, cz
            );

            if (!delta.toLoad.isEmpty()) {
                streamingManager.queueChunksForLoading(playerName, delta.toLoad, cx, cz);
            }
            
            if (!delta.toUnload.isEmpty()) {
                streamingManager.queueChunksForUnloading(playerName, delta.toUnload);
            }

            streamingManager.processLoadQueue(player);

            List<Long> loadedSnapshot = new ArrayList<>(loaded);
            List<Long> toUnload = new ArrayList<>();
            List<MapChunk> unloadPackets = new ArrayList<>();

            for (Long idx : loadedSnapshot) {
                if (!targetSet.contains(idx)) {
                    toUnload.add(idx);
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                    unloadPackets.add(new MapChunk(mx, mz, null));
                }
            }

            if (toUnload.isEmpty()) return;

            toUnload.forEach(loaded::remove);

            streamingManager.markChunksUnloaded(playerName, toUnload);

            UpdateWorldMap packet = new UpdateWorldMap(
                    unloadPackets.toArray(new MapChunk[0]),
                    null,
                    null
            );
            sendPacket(player, packet);

        } catch (Exception e) {
            LOGGER.warning("Failed to manage loaded chunks: " + e.getMessage());
        }
    }

    private static void sendPacket(Player player, ToClientPacket packet) {
        if (player == null || packet == null) {
            return;
        }

        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            com.hypixel.hytale.component.Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;
            playerRef.getPacketHandler().write(packet);
        } catch (Exception e) {
            LOGGER.warning("Failed to send world map packet to " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    private static void forceTrackerUpdate(@Nonnull Player player, @Nonnull WorldMapTracker tracker, double x, double z) {
        try {
            Object spiralIterator = getTrackerSpiralIterator(tracker);
            if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                int chunkX = (int) Math.floor(x) >> 5;
                int chunkZ = (int) Math.floor(z) >> 5;

                restrictedIterator.init(chunkX, chunkZ, 0, 999);
            }

            setTrackerUpdateTimer(tracker, 0.0f);
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Failed to force tracker update: " + e.getMessage());
        }
    }

    /**
     * Forces a full map refresh for a player.
     * This properly clears the client map and server state, then regenerates.
     * Uses native Hytale methods where possible for proper synchronization.
     *
     * @param player The player to refresh.
     */
    public static void forceFullMapRefresh(@Nonnull Player player) {
        try {
            World world = player.getWorld();
            if (world == null) return;

            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker == null) return;

            CaveModeManager caveManager = CaveModeManager.getInstance();
            boolean isUnderground = caveManager.isPlayerUnderground(player);
            boolean fogOfWar = ModConfig.getInstance().isCaveFogOfWar();
            boolean caveModeEnabled = ModConfig.getInstance().isCaveModeEnabled();

            LOGGER.fine("[MAP REFRESH] Starting full map refresh for " + player.getDisplayName() +
                       " (underground: " + isUnderground + ", fogOfWar: " + fogOfWar + ")");

            String playerName = player.getDisplayName();
            clearCaveModeLoadedChunks(playerName);

            CaveModeManager.DynamicCaveModeState state = caveManager.getState(player);
            if (state != null) {
                state.clearLoadedCaveChunks();
                state.getPendingCaveChunks().clear();
            }

            try {
                Object pendingReloadChunks = getTrackerPendingReloadChunks(tracker);
                if (pendingReloadChunks != null) {
                    java.lang.reflect.Method clearMethod = pendingReloadChunks.getClass().getMethod("clear");
                    clearMethod.invoke(pendingReloadChunks);
                    LOGGER.fine("[MAP REFRESH] Cleared pendingReloadChunks");
                }
                Object pendingReloadFutures = getTrackerPendingReloadFutures(tracker);
                if (pendingReloadFutures != null) {
                    java.lang.reflect.Method clearMethod = pendingReloadFutures.getClass().getMethod("clear");
                    clearMethod.invoke(pendingReloadFutures);
                    LOGGER.fine("[MAP REFRESH] Cleared pendingReloadFutures");
                }
            } catch (Exception e) {
                LOGGER.fine("[MAP REFRESH] Could not clear pending reload state: " + e.getMessage());
            }

            Object spiralIterator = getTrackerSpiralIterator(tracker);
            if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                boolean shouldBlockSurface = caveModeEnabled && isUnderground && fogOfWar;
                restrictedIterator.setCaveModeActive(shouldBlockSurface);
                restrictedIterator.resetState();
                LOGGER.fine("[MAP REFRESH] Set RestrictedSpiralIterator cave mode to: " + shouldBlockSurface);
            }

            tracker.clear();
            LOGGER.fine("[MAP REFRESH] Sent ClearWorldMap packet");

            Ref<EntityStore> playerRef = player.getReference();
            TransformComponent transform = (playerRef != null && playerRef.isValid())
                ? playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType())
                : null;
            if (transform != null) {
                var pos = transform.getPosition();
                int chunkX = (int) Math.floor(pos.x) >> 5;
                int chunkZ = (int) Math.floor(pos.z) >> 5;

                if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                    restrictedIterator.init(chunkX, chunkZ, 0, 999);
                }

                setTrackerUpdateTimer(tracker, 0.0f);

                if (caveModeEnabled && isUnderground && state != null) {
                    LOGGER.fine("[MAP REFRESH] Starting cave overlay at layer " + state.getCurrentLayer());
                    scheduleCaveOverlayUpdate(player, world, tracker, pos.x, pos.z, state);
                }

                LOGGER.fine("[MAP REFRESH] Re-initialized map at chunk " + chunkX + ", " + chunkZ);
            }

            LOGGER.fine("[MAP REFRESH] Completed for " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to force full map refresh for " + player.getDisplayName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generates cave mode images and sends them to the player PROGRESSIVELY.
     * Works like the base map: only sends chunks that are ready, skips those that aren't.
     * This prevents freezing by not waiting for all futures to complete.
     *
     * @param player  The player.
     * @param world   The world.
     * @param tracker The world map tracker.
     * @param playerX Player X position.
     * @param playerZ Player Z position.
     * @param yLevel  The Y level for cave mode.
     * @param range   The vertical range.
     * @param maxGeneration Maximum number of pending generations allowed (like base map)
     * @return Number of remaining generation slots
     */
    private static int generateCaveModeImagesProgressive(@Nonnull Player player, @Nonnull World world,
                                                          @Nonnull WorldMapTracker tracker,
                                                          double playerX, double playerZ,
                                                          int yLevel, int range, int maxGeneration) {
        try {
            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            float imageScale = settings.getImageScale();
            int imageSize = MathUtil.fastFloor(32.0F * imageScale);

            int playerMapChunkX = ((int) Math.floor(playerX)) >> 5;
            int playerMapChunkZ = ((int) Math.floor(playerZ)) >> 5;

            Object loadedObj = getTrackerLoaded(tracker);
            @SuppressWarnings("unchecked")
            final Set<Long> loaded = (loadedObj instanceof Set) ? (Set<Long>) loadedObj : new HashSet<>();

            String playerName = player.getDisplayName();
            CaveModePlayerState playerState = getCavePlayerState(playerName);
            Set<Long> caveModeLoaded = playerState.loadedChunks;
            Set<Long> caveModeFailed = playerState.failedChunks;
            Set<Long> caveModeTarget = playerState.targetChunks;
            Set<Long> caveModePending = playerState.pendingChunks;

            ExplorationTracker.PlayerExplorationData explorationData = ExplorationTracker.getInstance().getPlayerData(player);
            if (explorationData == null) {
                return maxGeneration;
            }

            Set<Long> exploredWorldChunks;
            CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
            boolean shareCaves = ModConfig.getInstance().isShareAllExploration();
            if (state != null) {
                exploredWorldChunks = new HashSet<>(state.getExploredCaveChunks());
                if (shareCaves) {
                    exploredWorldChunks.addAll(getHydratedSharedCaveExploredChunks(world.getName()));
                }
            } else if (shareCaves) {
                exploredWorldChunks = getHydratedSharedCaveExploredChunks(world.getName());
            } else {
                exploredWorldChunks = Collections.emptySet();
            }

            if (exploredWorldChunks.isEmpty()) {
                return maxGeneration;
            }

            Set<Long> mapChunksSet = new HashSet<>();
            for (Long chunkIdx : exploredWorldChunks) {
                int wx = ChunkUtil.indexToChunkX(chunkIdx);
                int wz = ChunkUtil.indexToChunkZ(chunkIdx);
                int mx = wx >> 1;
                int mz = wz >> 1;
                long mapChunkIdx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(mx, mz);
                mapChunksSet.add(mapChunkIdx);
            }

            List<Long> sortedChunks = new ArrayList<>(mapChunksSet);
            sortedChunks.sort(Comparator.comparingLong(idx -> {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                long dx = (long) mx - playerMapChunkX;
                long dz = (long) mz - playerMapChunkZ;
                return dx * dx + dz * dz;
            }));

            caveModeTarget.clear();
            caveModeTarget.addAll(mapChunksSet);

            List<Long> completedPending = new ArrayList<>();
            List<MapChunk> chunksToSend = new ArrayList<>();

            for (Long pendingIdx : new ArrayList<>(caveModePending)) {
                CompletableFuture<CaveModeImageBuilder> future = playerState.pendingFutures.get(pendingIdx);
                if (future != null && future.isDone()) {
                    completedPending.add(pendingIdx);
                    caveModePending.remove(pendingIdx);
                    playerState.pendingFutures.remove(pendingIdx);

                    CaveModeImageBuilder builder = future.getNow(null);
                    if (builder != null) {
                        MapImage image = builder.getImage();
                        if (image != null && image.data != null) {
                            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(pendingIdx);
                            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(pendingIdx);
                            chunksToSend.add(new MapChunk(mx, mz, image));
                            loaded.add(pendingIdx);
                            caveModeLoaded.add(pendingIdx);
                            caveModeFailed.remove(pendingIdx);
                        } else {
                            caveModeFailed.add(pendingIdx);
                        }
                    } else {
                        caveModeFailed.add(pendingIdx);
                    }
                }
            }

            for (Long chunkIdx : sortedChunks) {
                if (maxGeneration <= 0) break;

                if (caveModeLoaded.contains(chunkIdx) || caveModePending.contains(chunkIdx)) {
                    continue;
                }

                CompletableFuture<CaveModeImageBuilder> future = CaveModeImageBuilder.build(
                    chunkIdx, imageSize, imageSize, world, yLevel, range);

                if (future.isDone()) {
                    CaveModeImageBuilder builder = future.getNow(null);
                    if (builder != null) {
                        MapImage image = builder.getImage();
                        if (image != null && image.data != null) {
                            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(chunkIdx);
                            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(chunkIdx);
                            chunksToSend.add(new MapChunk(mx, mz, image));
                            loaded.add(chunkIdx);
                            caveModeLoaded.add(chunkIdx);
                            caveModeFailed.remove(chunkIdx);
                        } else {
                            caveModeFailed.add(chunkIdx);
                        }
                    } else {
                        caveModeFailed.add(chunkIdx);
                    }
                } else {
                    caveModePending.add(chunkIdx);
                    playerState.pendingFutures.put(chunkIdx, future);
                    maxGeneration--;
                }
            }

            if (!chunksToSend.isEmpty()) {
                int batchSize = 25;
                for (int i = 0; i < chunksToSend.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, chunksToSend.size());
                    List<MapChunk> batch = chunksToSend.subList(i, end);

                    UpdateWorldMap packet = new UpdateWorldMap(
                            batch.toArray(new MapChunk[0]),
                            null,
                            null
                    );
                    sendPacket(player, packet);
                }

                LOGGER.fine("[CAVE MODE] Sent " + chunksToSend.size() + " chunks (pending: " + caveModePending.size() + ")");
            }

            return maxGeneration;
        } catch (Exception e) {
            LOGGER.warning("[CAVE MODE] Error in progressive generation: " + e.getMessage());
            return maxGeneration;
        }
    }

    /**
     * Updates world map configuration settings on the server side.
     *
     * @param world The world.
     */
    public static void updateWorldMapConfigs(@Nonnull World world) {
        try {
            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            UpdateWorldMapSettings packet = settings.getSettingsPacket();
            ModConfig config = ModConfig.getInstance();

            if (packet != null) {
                packet.minScale = config.getMinScale();
                packet.maxScale = config.getMaxScale();
            }

            setSettingsMinScale(settings, config.getMinScale());
            setSettingsMaxScale(settings, config.getMaxScale());

        } catch (Exception e) {
            LOGGER.warning("Failed to update world map configs: " + e.getMessage());
        }
    }

    /**
     * Triggers the broadcast of map settings to clients in the world.
     *
     * @param world The world.
     */
    public static void broadcastMapSettings(@Nonnull World world) {
        try {
            Object mapManager = world.getWorldMapManager();
            java.lang.reflect.Method sendSettings = mapManager.getClass().getMethod("sendSettings");
            sendSettings.invoke(mapManager);
        } catch (Exception e) {
            LOGGER.fine("Could not invoke mapManager.sendSettings(): " + e.getMessage());
        }
    }

    /**
     * Sends custom map settings packet to a specific player.
     *
     * @param player The player.
     */
    public static void sendMapSettingsToPlayer(@Nonnull Player player) {
        try {
            World world = player.getWorld();
            if (world == null)
                return;

            updateWorldMapConfigs(world);

            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            UpdateWorldMapSettings basePacket = settings.getSettingsPacket();

            if (basePacket == null)
                return;

            UpdateWorldMapSettings packet = basePacket.clone();

            PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());

            if (playerConfig != null) {
                packet.minScale = playerConfig.getMinScale();
                packet.maxScale = playerConfig.getMaxScale();
            }

            WorldMapTracker tracker = player.getWorldMapTracker();
            packet.allowTeleportToCoordinates = tracker.isAllowTeleportToCoordinates();
            packet.allowTeleportToMarkers = tracker.isAllowTeleportToMarkers();

            WorldMapConfig worldMapConfig = world.getGameplayConfig().getWorldMapConfig();
            packet.allowCreatingMapMarkers = worldMapConfig.getUserMapMarkerConfig().isAllowCreatingMarkers();
            packet.allowRemovingOtherPlayersMarkers = worldMapConfig.getUserMapMarkerConfig().isAllowDeleteOtherPlayersSharedMarkers();
            packet.allowShowOnMapToggle = worldMapConfig.canTogglePlayersInMap();
            packet.allowCompassTrackingToggle = worldMapConfig.canTrackPlayersInCompass();

            sendPacket(player, packet);

            LOGGER.fine("Sent custom map settings to " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send map settings to player: " + e.getMessage());
        }
    }

    /**
     * Refreshes the map trackers for all players in the given world.
     * Use this when exploration data sharing settings change.
     *
     * @param world The world.
     */
    public static void refreshTrackers(@Nonnull World world) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Holder<EntityStore> holder = playerRef.getHolder();
            if (holder == null) continue;
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) continue;

            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());

                    if (tc != null) {
                        var pos = tc.getPosition();
                        forceTrackerUpdate(player, player.getWorldMapTracker(), pos.x, pos.z);
                        updateExplorationState(player, player.getWorldMapTracker(), pos.x, pos.z);
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to refresh tracker for " + player.getDisplayName() + ": " + e.getMessage());
            }
        }
    }

    public static void clearMarkerCaches(@Nonnull World world) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Holder<EntityStore> holder = playerRef.getHolder();
            if (holder == null) continue;
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) continue;

            try {
                clearMarkerCaches(player.getWorldMapTracker());
            } catch (Exception e) {
                LOGGER.fine("Failed to clear marker cache for " + player.getDisplayName() + ": " + e.getMessage());
            }
        }
    }

    public static void clearPlayerMarkerCache(@Nonnull Player player) {
        try {
            clearMarkerCaches(player.getWorldMapTracker());
        } catch (Exception e) {
            LOGGER.fine("Failed to clear marker cache for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    private static void clearMarkerCaches(@Nonnull WorldMapTracker tracker) {
        Object markerTracker = findMarkerTracker(tracker);
        if (markerTracker == null) {
            return;
        }

        clearCollections(markerTracker);
        ReflectionHelper.invokeMethod(markerTracker, "clear", new Class<?>[0], new Object[0]);
        ReflectionHelper.invokeMethod(markerTracker, "reset", new Class<?>[0], new Object[0]);
    }

    private static Object findMarkerTracker(@Nonnull WorldMapTracker tracker) {
        Class<?> current = tracker.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Class<?> type = field.getType();
                    String typeName = type.getName();
                    if ("MapMarkerTracker".equals(type.getSimpleName())
                        || typeName.endsWith(".MapMarkerTracker")) {
                        Object value = field.get(tracker);
                        if (value != null) {
                            return value;
                        }
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void clearCollections(@Nonnull Object target) {
        Class<?> current = target.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value instanceof Map<?, ?> map) {
                        map.clear();
                    } else if (value instanceof Collection<?> collection) {
                        collection.clear();
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    /**
     * Pre-resolved MethodHandle constants for known reflection targets.
     */
    private static final class TrackerHandles {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        
        static final MethodHandle LOADED_GETTER;
        static final MethodHandle SPIRAL_ITERATOR_GETTER;
        static final MethodHandle SPIRAL_ITERATOR_SETTER;
        static final MethodHandle UPDATE_TIMER_GETTER;
        static final MethodHandle UPDATE_TIMER_SETTER;
        static final MethodHandle PENDING_RELOAD_CHUNKS_GETTER;
        static final MethodHandle PENDING_RELOAD_FUTURES_GETTER;
        static final MethodHandle VIEW_RADIUS_OVERRIDE_SETTER;
        
        static final MethodHandle IMAGE_SCALE_SETTER;
        static final MethodHandle MIN_SCALE_SETTER;
        static final MethodHandle MAX_SCALE_SETTER;
        
        static {
            MethodHandle loadedGetter = null, spiralIteratorGetter = null, spiralIteratorSetter = null;
            MethodHandle updateTimerGetter = null, updateTimerSetter = null;
            MethodHandle pendingReloadChunksGetter = null, pendingReloadFuturesGetter = null;
            MethodHandle viewRadiusOverrideSetter = null;
            MethodHandle imageScaleSetter = null, minScaleSetter = null, maxScaleSetter = null;
            
            try {
                Class<?> trackerClass = WorldMapTracker.class;
                
                Field loadedField = findFieldRecursive(trackerClass, "loaded");
                if (loadedField != null) { loadedField.setAccessible(true); loadedGetter = LOOKUP.unreflectGetter(loadedField); }
                
                Field spiralField = findFieldRecursive(trackerClass, "spiralIterator");
                if (spiralField != null) { spiralField.setAccessible(true); spiralIteratorGetter = LOOKUP.unreflectGetter(spiralField); spiralIteratorSetter = LOOKUP.unreflectSetter(spiralField); }
                
                Field updateTimerField = findFieldRecursive(trackerClass, "updateTimer");
                if (updateTimerField != null) { updateTimerField.setAccessible(true); updateTimerGetter = LOOKUP.unreflectGetter(updateTimerField); updateTimerSetter = LOOKUP.unreflectSetter(updateTimerField); }
                
                Field pendingReloadChunksField = findFieldRecursive(trackerClass, "pendingReloadChunks");
                if (pendingReloadChunksField != null) { pendingReloadChunksField.setAccessible(true); pendingReloadChunksGetter = LOOKUP.unreflectGetter(pendingReloadChunksField); }
                
                Field pendingReloadFuturesField = findFieldRecursive(trackerClass, "pendingReloadFutures");
                if (pendingReloadFuturesField != null) { pendingReloadFuturesField.setAccessible(true); pendingReloadFuturesGetter = LOOKUP.unreflectGetter(pendingReloadFuturesField); }
                
                Field viewRadiusField = findFieldRecursive(trackerClass, "viewRadiusOverride");
                if (viewRadiusField != null) { viewRadiusField.setAccessible(true); viewRadiusOverrideSetter = LOOKUP.unreflectSetter(viewRadiusField); }
                
                Class<?> settingsClass = WorldMapSettings.class;
                
                Field imageScaleField = findFieldRecursive(settingsClass, "imageScale");
                if (imageScaleField != null) { imageScaleField.setAccessible(true); imageScaleSetter = LOOKUP.unreflectSetter(imageScaleField); }
                
                Field minScaleField = findFieldRecursive(settingsClass, "minScale");
                if (minScaleField != null) { minScaleField.setAccessible(true); minScaleSetter = LOOKUP.unreflectSetter(minScaleField); }
                
                Field maxScaleField = findFieldRecursive(settingsClass, "maxScale");
                if (maxScaleField != null) { maxScaleField.setAccessible(true); maxScaleSetter = LOOKUP.unreflectSetter(maxScaleField); }
                
            } catch (Exception e) {
                Logger.getLogger(WorldMapHook.class.getName()).warning("Failed to resolve some MethodHandles: " + e.getMessage());
            }
            
            LOADED_GETTER = loadedGetter;
            SPIRAL_ITERATOR_GETTER = spiralIteratorGetter;
            SPIRAL_ITERATOR_SETTER = spiralIteratorSetter;
            UPDATE_TIMER_GETTER = updateTimerGetter;
            UPDATE_TIMER_SETTER = updateTimerSetter;
            PENDING_RELOAD_CHUNKS_GETTER = pendingReloadChunksGetter;
            PENDING_RELOAD_FUTURES_GETTER = pendingReloadFuturesGetter;
            VIEW_RADIUS_OVERRIDE_SETTER = viewRadiusOverrideSetter;
            IMAGE_SCALE_SETTER = imageScaleSetter;
            MIN_SCALE_SETTER = minScaleSetter;
            MAX_SCALE_SETTER = maxScaleSetter;
        }
        
        private static Field findFieldRecursive(Class<?> clazz, String fieldName) {
            Class<?> current = clazz;
            while (current != null) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }
    }

    private static Object getTrackerLoaded(WorldMapTracker tracker) {
        try { return TrackerHandles.LOADED_GETTER != null ? TrackerHandles.LOADED_GETTER.invoke(tracker) : ReflectionHelper.getFieldValueRecursive(tracker, "loaded"); }
        catch (Throwable e) { return ReflectionHelper.getFieldValueRecursive(tracker, "loaded"); }
    }

    private static Object getTrackerSpiralIterator(WorldMapTracker tracker) {
        try { return TrackerHandles.SPIRAL_ITERATOR_GETTER != null ? TrackerHandles.SPIRAL_ITERATOR_GETTER.invoke(tracker) : ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator"); }
        catch (Throwable e) { return ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator"); }
    }

    private static void setTrackerSpiralIterator(WorldMapTracker tracker, Object value) {
        try { if (TrackerHandles.SPIRAL_ITERATOR_SETTER != null) TrackerHandles.SPIRAL_ITERATOR_SETTER.invoke(tracker, value); else ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", value); }
        catch (Throwable e) { ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", value); }
    }

    private static void setTrackerUpdateTimer(WorldMapTracker tracker, float value) {
        try { if (TrackerHandles.UPDATE_TIMER_SETTER != null) TrackerHandles.UPDATE_TIMER_SETTER.invoke(tracker, value); else ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", value); }
        catch (Throwable e) { ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", value); }
    }

    private static void setTrackerViewRadiusOverride(WorldMapTracker tracker, Integer value) {
        try { if (TrackerHandles.VIEW_RADIUS_OVERRIDE_SETTER != null) TrackerHandles.VIEW_RADIUS_OVERRIDE_SETTER.invoke(tracker, value); else ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", value); }
        catch (Throwable e) { ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", value); }
    }

    private static Object getTrackerPendingReloadChunks(WorldMapTracker tracker) {
        try { return TrackerHandles.PENDING_RELOAD_CHUNKS_GETTER != null ? TrackerHandles.PENDING_RELOAD_CHUNKS_GETTER.invoke(tracker) : ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadChunks"); }
        catch (Throwable e) { return ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadChunks"); }
    }

    private static Object getTrackerPendingReloadFutures(WorldMapTracker tracker) {
        try { return TrackerHandles.PENDING_RELOAD_FUTURES_GETTER != null ? TrackerHandles.PENDING_RELOAD_FUTURES_GETTER.invoke(tracker) : ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadFutures"); }
        catch (Throwable e) { return ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadFutures"); }
    }

    private static void setSettingsImageScale(WorldMapSettings settings, float value) {
        try { if (TrackerHandles.IMAGE_SCALE_SETTER != null) TrackerHandles.IMAGE_SCALE_SETTER.invoke(settings, value); else ReflectionHelper.setFieldValueRecursive(settings, "imageScale", value); }
        catch (Throwable e) { ReflectionHelper.setFieldValueRecursive(settings, "imageScale", value); }
    }

    private static void setSettingsMinScale(WorldMapSettings settings, float value) {
        try { if (TrackerHandles.MIN_SCALE_SETTER != null) TrackerHandles.MIN_SCALE_SETTER.invoke(settings, value); else ReflectionHelper.setFieldValueRecursive(settings, "minScale", value); }
        catch (Throwable e) { ReflectionHelper.setFieldValueRecursive(settings, "minScale", value); }
    }

    private static void setSettingsMaxScale(WorldMapSettings settings, float value) {
        try { if (TrackerHandles.MAX_SCALE_SETTER != null) TrackerHandles.MAX_SCALE_SETTER.invoke(settings, value); else ReflectionHelper.setFieldValueRecursive(settings, "maxScale", value); }
        catch (Throwable e) { ReflectionHelper.setFieldValueRecursive(settings, "maxScale", value); }
    }

    /**
     * Custom iterator that only returns chunks that have been explored or are within the persistent boundaries.
     * Thread-safe implementation to prevent race conditions with the WorldMap thread.
     */
    public static class RestrictedSpiralIterator extends CircleSpiralIterator {
        private final ExplorationTracker.PlayerExplorationData data;
        private final WorldMapTracker tracker;
        private volatile Iterator<Long> currentIterator;
        private volatile List<Long> targetMapChunks = new ArrayList<>();
        private volatile int currentGoalRadius;
        private volatile boolean stopped = false;
        private volatile boolean initialized = false;
        private volatile boolean caveModeActive = false;
        private volatile int centerX;
        private volatile int centerZ;
        private volatile int currentRadius;
        private int cleanupTimer = 0;
        private final Object lock = new Object();

        private volatile List<Long> cachedRankedChunks = null;
        private volatile int cachedCenterX = Integer.MIN_VALUE;
        private volatile int cachedCenterZ = Integer.MIN_VALUE;
        private volatile long cachedExploredVersion = -1;
        private volatile Set<Long> cachedBoundaryChunks = null;
        
        private volatile Set<Long> cachedMapChunks = null;
        private volatile long cachedMapChunksVersion = -1;

        private static final int RESORT_DISTANCE_THRESHOLD = 4;

        public RestrictedSpiralIterator(ExplorationTracker.PlayerExplorationData data, WorldMapTracker tracker) {
            super();
            super.init(0, 0, 0, 1);
            this.data = data;
            this.tracker = tracker;
            this.currentIterator = Collections.emptyIterator();
            this.initialized = true;
        }

        /**
         * Enables or disables cave mode. When cave mode is active, this iterator
         * will return no chunks, allowing the cave mode system to handle map generation.
         *
         * @param active Whether cave mode should be active.
         */
        public void setCaveModeActive(boolean active) {
            synchronized (lock) {
                this.caveModeActive = active;
                if (active) {
                    this.currentIterator = Collections.emptyIterator();
                }
            }
        }

        /**
         * Checks if cave mode is active.
         *
         * @return true if cave mode is active.
         */
        public boolean isCaveModeActive() {
            return caveModeActive;
        }

        public void stop() {
            synchronized (lock) {
                this.stopped = true;
                this.currentIterator = Collections.emptyIterator();
                this.cachedRankedChunks = null;
                this.cachedCenterX = Integer.MIN_VALUE;
                this.cachedCenterZ = Integer.MIN_VALUE;
                this.cachedExploredVersion = -1;
                this.cachedBoundaryChunks = null;
                this.cachedMapChunks = null;
                this.cachedMapChunksVersion = -1;
                try {
                    super.init(0, 0, 0, 1);
                } catch (Exception ignored) {}
            }
        }

        /**
         * Resets the iterator state to allow fresh chunk loading.
         * Called when switching between cave mode and normal mode.
         */
        public void resetState() {
            synchronized (lock) {
                this.stopped = false;
                this.initialized = true;
                this.currentIterator = Collections.emptyIterator();
                this.targetMapChunks = new ArrayList<>();
                this.currentGoalRadius = 0;
                this.currentRadius = 0;
                this.cleanupTimer = 0;
            }
        }

        /**
         * Gets the list of target chunks being iterated.
         *
         * @return List of chunk indices.
         */
        public List<Long> getTargetMapChunks() {
            return targetMapChunks;
        }
        
        /**
         * Gets or rebuilds the map chunks set from explored world chunks.
         * Uses version counter for O(1) staleness check instead of iterating all chunks.
         */
        private Set<Long> getOrBuildMapChunks() {
            long currentVersion = data.getExploredChunks().getVersion();
            Set<Long> cached = cachedMapChunks;
            if (cached != null && cachedMapChunksVersion == currentVersion) {
                return cached;
            }
            
            Set<Long> mapChunks = new HashSet<>(1024);
            data.getExploredChunks().forEachExploredChunk(chunkIdx -> {
                int wx = ChunkUtil.indexToChunkX(chunkIdx);
                int wz = ChunkUtil.indexToChunkZ(chunkIdx);
                int mx = wx >> 1;
                int mz = wz >> 1;
                long mapChunkIdx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(mx, mz);
                mapChunks.add(mapChunkIdx);
            });
            
            cachedMapChunks = mapChunks;
            cachedMapChunksVersion = currentVersion;
            return mapChunks;
        }

        @Override
        public void init(int cx, int cz, int startRadius, int endRadius) {
            try {
                super.init(cx, cz, startRadius, endRadius);
            } catch (Exception ignored) {}

            synchronized (lock) {
                if (stopped || caveModeActive) {
                    this.currentIterator = Collections.emptyIterator();
                    this.initialized = true;
                    return;
                }

                this.centerX = cx;
                this.centerZ = cz;
                this.currentRadius = startRadius;
                this.currentGoalRadius = endRadius;

                try {
                    Player player = tracker.getPlayer();
                    if (data == null) {
                        this.currentIterator = Collections.emptyIterator();
                        this.initialized = true;
                        return;
                    }

                    long currentExploredVersion;
                    Set<Long> mapChunksSet;
                    
                    if (ModConfig.getInstance().isShareAllExploration()) {
                        World world = player.getWorld();
                        String worldName = world != null ? world.getName() : "world";
                        Set<Long> exploredWorldChunks = ExplorationManager.getInstance().getAllExploredChunks(worldName);
                        currentExploredVersion = ExplorationTracker.getInstance().getGlobalVersion();
                        
                        if (exploredWorldChunks.isEmpty()) {
                            bootstrapExploration(cx, cz);
                            exploredWorldChunks = ExplorationManager.getInstance().getAllExploredChunks(worldName);
                            currentExploredVersion = ExplorationTracker.getInstance().getGlobalVersion();
                        }
                        
                        mapChunksSet = new HashSet<>(exploredWorldChunks.size() / 2);
                        for (Long chunkIdx : exploredWorldChunks) {
                            int wx = ChunkUtil.indexToChunkX(chunkIdx);
                            int wz = ChunkUtil.indexToChunkZ(chunkIdx);
                            long mapChunkIdx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(wx >> 1, wz >> 1);
                            mapChunksSet.add(mapChunkIdx);
                        }
                    } else {
                        currentExploredVersion = data.getExploredChunks().getVersion();
                        
                        if (data.getExploredChunks().getExploredCount() == 0) {
                            bootstrapExploration(cx, cz);
                            currentExploredVersion = data.getExploredChunks().getVersion();
                        }
                        
                        mapChunksSet = getOrBuildMapChunks();
                    }

                    if (mapChunksSet.isEmpty()) {
                        this.currentIterator = Collections.emptyIterator();
                        this.initialized = true;
                        return;
                    }

                    int distanceFromCachedCenter = (cachedCenterX == Integer.MIN_VALUE) ? Integer.MAX_VALUE :
                            Math.abs(cx - cachedCenterX) + Math.abs(cz - cachedCenterZ);

                    MapExpansionManager.MapBoundaries bounds = data.getMapExpansion().getCurrentBoundaries();
                    Set<Long> boundaryChunks = new HashSet<>(4);

                    if (bounds.minX != Integer.MAX_VALUE) {
                        boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.minX >> 1, bounds.minZ >> 1));
                        boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.maxX >> 1, bounds.minZ >> 1));
                        boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.minX >> 1, bounds.maxZ >> 1));
                        boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.maxX >> 1, bounds.maxZ >> 1));
                    }

                    boolean boundaryChunksChanged = cachedBoundaryChunks == null ||
                            !boundaryChunks.equals(cachedBoundaryChunks);

                    boolean needsResort = cachedRankedChunks == null ||
                            distanceFromCachedCenter > RESORT_DISTANCE_THRESHOLD ||
                            currentExploredVersion != cachedExploredVersion ||
                            boundaryChunksChanged;

                    List<Long> rankedChunks;
                    
                    int maxChunks = ModConfig.getInstance().getActiveMaxChunksToLoad();
                    int searchLimit = maxChunks - boundaryChunks.size();
                    if (searchLimit < 0) searchLimit = 0;

                    if (needsResort) {
                        rankedChunks = new ArrayList<>(mapChunksSet.size());
                        
                        for (Long chunk : mapChunksSet) {
                            if (!boundaryChunks.contains(chunk)) {
                                rankedChunks.add(chunk);
                            }
                        }

                        final int sortCenterX = cx;
                        final int sortCenterZ = cz;

                        // Use quickselect + partial sort: O(n) + O(k log k) instead of O(n log n)
                        Comparator<Long> distComparator = (a, b) -> {
                            int ax = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(a);
                            int az = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(a);
                            int bx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(b);
                            int bz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(b);
                            long da = (long)(ax - sortCenterX) * (ax - sortCenterX) + (long)(az - sortCenterZ) * (az - sortCenterZ);
                            long db = (long)(bx - sortCenterX) * (bx - sortCenterX) + (long)(bz - sortCenterZ) * (bz - sortCenterZ);
                            return Long.compare(da, db);
                        };

                        if (rankedChunks.size() > searchLimit && searchLimit > 0) {
                            // Quickselect partitions so elements [0..searchLimit-1] are the closest
                            quickSelect(rankedChunks, 0, rankedChunks.size() - 1, searchLimit, distComparator);
                            // Sort only the top-N partition for proper iteration order
                            rankedChunks.subList(0, searchLimit).sort(distComparator);
                        } else {
                            rankedChunks.sort(distComparator);
                        }

                        this.cachedRankedChunks = rankedChunks;
                        this.cachedCenterX = cx;
                        this.cachedCenterZ = cz;
                        this.cachedExploredVersion = currentExploredVersion;
                        this.cachedBoundaryChunks = new HashSet<>(boundaryChunks);
                    } else {
                        rankedChunks = cachedRankedChunks;
                    }

                    List<Long> limitedRankedChunks;
                    if (rankedChunks.size() > searchLimit) {
                        limitedRankedChunks = new ArrayList<>(searchLimit);
                        for (int i = 0; i < searchLimit; i++) {
                            limitedRankedChunks.add(rankedChunks.get(i));
                        }
                    } else {
                        limitedRankedChunks = rankedChunks;
                    }

                    this.targetMapChunks = new ArrayList<>(boundaryChunks.size() + limitedRankedChunks.size());
                    this.targetMapChunks.addAll(boundaryChunks);
                    this.targetMapChunks.addAll(limitedRankedChunks);

                    this.currentIterator = limitedRankedChunks.iterator();
                    this.initialized = true;

                    if (++cleanupTimer > 100) {
                        cleanupTimer = 0;
                        cleanupFarChunks(limitedRankedChunks);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Error in RestrictedSpiralIterator.init(): " + e.getMessage());
                    this.currentIterator = Collections.emptyIterator();
                    this.initialized = true;
                }
            }
        }
        
        /**
         * Quickselect algorithm (Hoare's selection) to partition a list so that
         * elements [0..k-1] are the k smallest according to the comparator.
         * Average O(n) time complexity vs O(n log n) for full sort.
         */
        private static void quickSelect(List<Long> list, int left, int right, int k, Comparator<Long> cmp) {
            while (left < right) {
                int pivotIdx = left + (right - left) / 2;
                pivotIdx = partition(list, left, right, pivotIdx, cmp);
                if (pivotIdx == k) {
                    return;
                } else if (pivotIdx < k) {
                    left = pivotIdx + 1;
                } else {
                    right = pivotIdx - 1;
                }
            }
        }

        private static int partition(List<Long> list, int left, int right, int pivotIdx, Comparator<Long> cmp) {
            Long pivotValue = list.get(pivotIdx);
            // Move pivot to end
            swap(list, pivotIdx, right);
            int storeIdx = left;
            for (int i = left; i < right; i++) {
                if (cmp.compare(list.get(i), pivotValue) < 0) {
                    swap(list, i, storeIdx);
                    storeIdx++;
                }
            }
            // Move pivot to its final position
            swap(list, storeIdx, right);
            return storeIdx;
        }

        private static void swap(List<Long> list, int a, int b) {
            Long tmp = list.get(a);
            list.set(a, list.get(b));
            list.set(b, tmp);
        }

        /**
         * Bootstraps initial exploration chunks when none exist.
         */
        private void bootstrapExploration(int cx, int cz) {
            int worldChunkX = cx * 2;
            int worldChunkZ = cz * 2;
            int bootstrapRadius = ModConfig.getInstance().getExplorationRadius();

            Set<Long> bootstrapChunks = ChunkUtil.getChunksInCircularArea(worldChunkX, worldChunkZ, bootstrapRadius);
            data.getExploredChunks().markChunksExplored(bootstrapChunks);
            data.getMapExpansion().updateBoundaries(worldChunkX, worldChunkZ, bootstrapRadius);

            LOGGER.info("Bootstrapped " + bootstrapChunks.size() + " exploration chunks around (" + worldChunkX + ", " + worldChunkZ + ")");
        }

        private void cleanupFarChunks(List<Long> keepChunks) {
            try {
                Object loadedObj = getTrackerLoaded(tracker);
                if (loadedObj instanceof Set<?> loadedSet) {
                    if (loadedSet.size() > 20000) {
                        Set<Long> keepSet = new HashSet<>(keepChunks);
                        List<MapChunk> toRemovePackets = new ArrayList<>();

                        Iterator<?> it = loadedSet.iterator();
                        while (it.hasNext()) {
                            Object obj = it.next();
                            if (obj instanceof Long idx) {
                                if (!keepSet.contains(idx)) {
                                    it.remove();
                                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                                    toRemovePackets.add(new MapChunk(mx, mz, null));
                                }
                            }
                        }

                        if (!toRemovePackets.isEmpty()) {
                            UpdateWorldMap packet = new UpdateWorldMap(toRemovePackets.toArray(new MapChunk[0]), null, null);
                            sendPacket(tracker.getPlayer(), packet);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to cleanup far chunks: " + e.getMessage());
            }
        }

        @Override
        public boolean hasNext() {
            if (stopped || caveModeActive) return false;
            Iterator<Long> iter = currentIterator;
            return iter != null && iter.hasNext();
        }

        @Override
        public long next() {
            Iterator<Long> iter = currentIterator;
            if (stopped || iter == null || !iter.hasNext())
                return 0;

            try {
                long next = iter.next();
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(next);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(next);
                long dx = (long) mx - centerX;
                long dz = (long) mz - centerZ;
                long distSquared = dx * dx + dz * dz;
                this.currentRadius = (int) fastSqrt(distSquared);
                return next;
            } catch (java.util.NoSuchElementException e) {
                return 0;
            }
        }
        
        /**
         * Fast integer square root using Newton's method.
         */
        private static int fastSqrt(long n) {
            if (n <= 0) return 0;
            if (n == 1) return 1;
            long x = n;
            long y = (x + 1) >> 1;
            while (y < x) {
                x = y;
                y = (x + n / x) >> 1;
            }
            return (int) x;
        }

        @Override
        public int getCompletedRadius() {
            return (stopped || caveModeActive) ? currentGoalRadius : currentRadius;
        }
    }
}

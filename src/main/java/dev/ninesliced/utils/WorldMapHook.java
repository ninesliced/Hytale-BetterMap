package dev.ninesliced.utils;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.iterator.CircleSpiralIterator;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMapSettings;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.MapExpansionManager;
import dev.ninesliced.managers.PlayerConfigManager;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Logger;

/**
 * Hooks into the Hytale WorldMap system to provide custom exploration behavior.
 */
public class WorldMapHook {
    private static final Logger LOGGER = Logger.getLogger(WorldMapHook.class.getName());

    /**
     * Injects a custom RestrictedSpiralIterator into the player's world map tracker.
     *
     * @param player  The player.
     * @param tracker The world map tracker.
     */
    public static void hookPlayerMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", 999);

            World world = player.getWorld();
            if (world != null) {
                sendMapSettingsToPlayer(player);
            }

            ExplorationTracker.PlayerExplorationData explorationData = ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            RestrictedSpiralIterator customIterator = new RestrictedSpiralIterator(explorationData, tracker);

            ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", customIterator);

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
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                ((RestrictedSpiralIterator) spiralIterator).stop();
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", null);

            try {
                Object pendingReloadFutures = ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadFutures");
                if (pendingReloadFutures instanceof Map) {
                    ((Map<?, ?>) pendingReloadFutures).clear();
                }
            } catch (Exception e) {
                LOGGER.warning("Could not clear pendingReloadFutures: " + e.getMessage());
            }

            try {
                Object pendingReloadChunks = ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadChunks");
                if (pendingReloadChunks instanceof Set) {
                    ((Set<?>) pendingReloadChunks).clear();
                }
            } catch (Exception e) {
                LOGGER.warning("Could not clear pendingReloadChunks: " + e.getMessage());
            }

            try {
                ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 999.0f);
            } catch (Exception _) {}

            LOGGER.info("Unhooked map tracker for player: " + player.getDisplayName());
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
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                ((RestrictedSpiralIterator) spiralIterator).stop();
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", null);
            ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", new CircleSpiralIterator());
            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);

            LOGGER.info("Restored vanilla map tracker for player: " + player.getDisplayName());
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

            BetterMapConfig.MapQuality quality = BetterMapConfig.getInstance().getActiveMapQuality();
            ReflectionHelper.setFieldValueRecursive(settings, "imageScale", quality.scale);

            manager.clearImages();

            LOGGER.info("Modified WorldMapSettings imageScale to " + quality.scale + " (" + quality + " quality) for world: " + world.getName());
        } catch (Exception e) {
            LOGGER.warning("Failed to hook WorldMap resolution: " + e.getMessage());
        }
    }

    /**
     * Updates the exploration state for a player, updating boundaries and forcing a tracker update if moved.
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
                LOGGER.info("[DEBUG] No exploration data for " + player.getDisplayName() + " in updateExplorationState");
                return;
            }

            World world = player.getWorld();
            if (world != null) {
                explorationData.setWorldName(world.getName());
            }

            int playerChunkX = ChunkUtil.blockToChunkCoord(x);
            int playerChunkZ = ChunkUtil.blockToChunkCoord(z);

            boolean hasMoved = explorationData.hasMovedToNewChunk(playerChunkX, playerChunkZ);

            if (hasMoved) {
                int explorationRadius = BetterMapConfig.getInstance().getExplorationRadius();

                explorationData.getMapExpansion().updateBoundaries(playerChunkX, playerChunkZ, explorationRadius);
                explorationData.setLastChunkPosition(playerChunkX, playerChunkZ);

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

    private static void manageLoadedChunks(@Nonnull Player player, @Nonnull WorldMapTracker tracker, int cx, int cz) {
        try {
            Object loadedObj = ReflectionHelper.getFieldValueRecursive(tracker, "loaded");
            if (!(loadedObj instanceof Set))
                return;
            
            @SuppressWarnings("unchecked")
            Set<Long> loaded = (Set<Long>) loadedObj;

            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (!(spiralIterator instanceof RestrictedSpiralIterator))
                return;

            List<Long> targetChunks = ((RestrictedSpiralIterator) spiralIterator).getTargetMapChunks();
            Set<Long> targetSet = new HashSet<>(targetChunks);

            List<Long> toUnload = new ArrayList<>();
            List<Long> loadedSnapshot = new ArrayList<>(loaded);

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

    private static void sendPacket(Player player, Packet packet) {
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                playerRef.getPacketHandler().write(packet);
            }
        }
    }

    private static void forceTrackerUpdate(@Nonnull Player player, @Nonnull WorldMapTracker tracker, double x, double z) {
        try {
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                int chunkX = (int) Math.floor(x) >> 5;
                int chunkZ = (int) Math.floor(z) >> 5;

                restrictedIterator.init(chunkX, chunkZ, 0, 999);
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Failed to force tracker update: " + e.getMessage());
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
            UpdateWorldMapSettings packet = (UpdateWorldMapSettings) ReflectionHelper.getFieldValue(settings, "settingsPacket");
            BetterMapConfig config = BetterMapConfig.getInstance();

            if (packet != null) {
                packet.minScale = config.getMinScale();
                packet.maxScale = config.getMaxScale();
            }

            ReflectionHelper.setFieldValueRecursive(settings, "minScale", config.getMinScale());
            ReflectionHelper.setFieldValueRecursive(settings, "maxScale", config.getMaxScale());

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
            UpdateWorldMapSettings packet = (UpdateWorldMapSettings) ReflectionHelper.getFieldValue(settings, "settingsPacket");

            if (packet == null)
                return;

            synchronized (packet) {
                float originalMin = packet.minScale;
                float originalMax = packet.maxScale;

                PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());

                if (playerConfig != null) {
                    packet.minScale = playerConfig.getMinScale();
                    packet.maxScale = playerConfig.getMaxScale();
                }

                sendPacket(player, packet);

                if (playerConfig != null) {
                    packet.minScale = originalMin;
                    packet.maxScale = originalMax;
                }
            }
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

    /**
     * Custom iterator that only returns chunks that have been explored or are within the persistent boundaries.
     */
    public static class RestrictedSpiralIterator extends CircleSpiralIterator {
        private final ExplorationTracker.PlayerExplorationData data;
        private final WorldMapTracker tracker;
        private Iterator<Long> currentIterator;
        private List<Long> targetMapChunks = new ArrayList<>();
        private int currentGoalRadius;
        private volatile boolean stopped = false;
        private int centerX;
        private int centerZ;
        private int currentRadius;
        private int cleanupTimer = 0;

        public RestrictedSpiralIterator(ExplorationTracker.PlayerExplorationData data, WorldMapTracker tracker) {
            this.data = data;
            this.tracker = tracker;
        }

        /**
         * Stops the iterator.
         */
        public void stop() {
            this.stopped = true;
        }

        /**
         * Gets the list of target chunks being iterated.
         *
         * @return List of chunk indices.
         */
        public List<Long> getTargetMapChunks() {
            return targetMapChunks;
        }

        @Override
        public void init(int cx, int cz, int startRadius, int endRadius) {
            if (stopped) {
                this.currentIterator = Collections.emptyIterator();
                return;
            }

            this.centerX = cx;
            this.centerZ = cz;
            this.currentRadius = startRadius;
            this.currentGoalRadius = endRadius;

            Set<Long> mapChunks = new HashSet<>();
            Set<Long> exploredWorldChunks;

            if (BetterMapConfig.getInstance().isShareAllExploration()) {
                World world = tracker.getPlayer().getWorld();
                String worldName = world != null ? world.getName() : "world";
                exploredWorldChunks = ExplorationManager.getInstance().getAllExploredChunks(worldName);
            } else {
                exploredWorldChunks = data.getExploredChunks().getExploredChunks();
            }

            for (Long chunkIdx : exploredWorldChunks) {
                int wx = ChunkUtil.indexToChunkX(chunkIdx);
                int wz = ChunkUtil.indexToChunkZ(chunkIdx);

                int mx = wx >> 1;
                int mz = wz >> 1;

                long mapChunkIdx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(mx, mz);
                mapChunks.add(mapChunkIdx);
            }

            List<Long> rankedChunks = new ArrayList<>();
            MapExpansionManager.MapBoundaries bounds = data.getMapExpansion().getCurrentBoundaries();
            Set<Long> boundaryChunks = new HashSet<>();

            if (bounds.minX != Integer.MAX_VALUE) {
                boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.minX >> 1, bounds.minZ >> 1));
                boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.maxX >> 1, bounds.minZ >> 1));
                boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.minX >> 1, bounds.maxZ >> 1));
                boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.maxX >> 1, bounds.maxZ >> 1));
            }

            for (Long chunk : mapChunks) {
                if (!boundaryChunks.contains(chunk)) {
                    rankedChunks.add(chunk);
                }
            }

            rankedChunks.sort(Comparator.comparingDouble(idx -> {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                return Math.sqrt(Math.pow(mx - cx, 2) + Math.pow(mz - cz, 2));
            }));

            int maxChunks = BetterMapConfig.getInstance().getActiveMapQuality().maxChunks;
            int searchLimit = maxChunks - boundaryChunks.size();
            if (searchLimit < 0) searchLimit = 0;

            if (rankedChunks.size() > searchLimit) {
                rankedChunks = rankedChunks.subList(0, searchLimit);
            }

            this.targetMapChunks = new ArrayList<>(boundaryChunks);
            this.targetMapChunks.addAll(rankedChunks);

            this.currentIterator = rankedChunks.iterator();

            if (++cleanupTimer > 100) {
                cleanupTimer = 0;
                cleanupFarChunks(rankedChunks);
            }
        }

        private void cleanupFarChunks(List<Long> keepChunks) {
            try {
                Object loadedObj = ReflectionHelper.getFieldValue(tracker, "loaded");
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
                            sendPacket(tracker.getPlayer(), (Packet) packet);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to cleanup far chunks: " + e.getMessage());
            }
        }

        @Override
        public boolean hasNext() {
            if (stopped) {
                return false;
            }
            return currentIterator != null && currentIterator.hasNext();
        }

        @Override
        public long next() {
            if (stopped || currentIterator == null) {
                return 0;
            }
            long next = currentIterator.next();

            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(next);
            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(next);
            double dist = Math.sqrt(Math.pow(mx - centerX, 2) + Math.pow(mz - centerZ, 2));
            this.currentRadius = (int) dist;

            return next;
        }

        @Override
        public int getCompletedRadius() {
            if (stopped) {
                return currentGoalRadius;
            }
            return currentRadius;
        }
    }
}

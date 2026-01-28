package dev.ninesliced.managers;

import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

/**
 * Manages chunk streaming with delta updates and priority-based loading.
 */
public class ChunkStreamingManager {
    private static final Logger LOGGER = Logger.getLogger(ChunkStreamingManager.class.getName());
    private static final ChunkStreamingManager INSTANCE = new ChunkStreamingManager();
    
    /**
     * Maximum chunks to send per tick.
     */
    private static final int MAX_CHUNKS_PER_TICK = 50;
    
    /**
     * Maximum chunks to unload per tick.
     */
    private static final int MAX_UNLOADS_PER_TICK = 100;
    
    /**
     * Batch size for combining chunks into single packets.
     */
    private static final int PACKET_BATCH_SIZE = 25;
    
    /**
     * Per-player streaming state.
     */
    private final Map<String, PlayerStreamingState> playerStates = new ConcurrentHashMap<>();
    
    private ChunkStreamingManager() {}
    
    public static ChunkStreamingManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Gets or creates the streaming state for a player.
     */
    @Nonnull
    public PlayerStreamingState getOrCreateState(@Nonnull String playerName) {
        return playerStates.computeIfAbsent(playerName, k -> new PlayerStreamingState());
    }
    
    /**
     * Removes a player's streaming state.
     */
    public void removeState(@Nonnull String playerName) {
        PlayerStreamingState state = playerStates.remove(playerName);
        if (state != null) {
            state.clear();
            LOGGER.fine("Removed streaming state for player: " + playerName);
        }
    }

    /**
     * Clears all player states. Called on plugin shutdown.
     */
    public void cleanup() {
        for (PlayerStreamingState state : playerStates.values()) {
            state.clear();
        }
        playerStates.clear();
        LOGGER.info("ChunkStreamingManager cleaned up");
    }
    
    /**
     * Computes delta between currently loaded chunks and target chunks.
     * Only returns chunks that need to be loaded or unloaded.
     */

    @Nonnull
    public ChunkDelta computeDelta(@Nonnull String playerName, 
                                    @Nonnull Set<Long> targetChunks,
                                    int playerChunkX, 
                                    int playerChunkZ) {
        PlayerStreamingState state = getOrCreateState(playerName);
        return state.computeDelta(targetChunks, playerChunkX, playerChunkZ);
    }
    
    /**
     * Processes the load queue for a player.
     */
    public int processLoadQueue(@Nonnull Player player) {
        String playerName = player.getDisplayName();
        PlayerStreamingState state = playerStates.get(playerName);
        if (state == null) {
            return 0;
        }
        
        return state.processQueue(player, MAX_CHUNKS_PER_TICK, MAX_UNLOADS_PER_TICK);
    }
    
    /**
     * Queues chunks for loading with priority based on distance.
     */
    public void queueChunksForLoading(@Nonnull String playerName,
                                       @Nonnull Collection<Long> chunksToLoad,
                                       int playerChunkX,
                                       int playerChunkZ) {
        PlayerStreamingState state = getOrCreateState(playerName);
        state.queueForLoading(chunksToLoad, playerChunkX, playerChunkZ);
    }
    
    /**
     * Queues chunks for unloading.
     */
    public void queueChunksForUnloading(@Nonnull String playerName,
                                         @Nonnull Collection<Long> chunksToUnload) {
        PlayerStreamingState state = getOrCreateState(playerName);
        state.queueForUnloading(chunksToUnload);
    }
    
    /**
     * Marks chunks as sent.
     */
    public void markChunksSent(@Nonnull String playerName, @Nonnull Collection<Long> chunks) {
        PlayerStreamingState state = playerStates.get(playerName);
        if (state != null) {
            state.markSent(chunks);
        }
    }
    
    /**
     * Marks chunks as unloaded.
     */
    public void markChunksUnloaded(@Nonnull String playerName, @Nonnull Collection<Long> chunks) {
        PlayerStreamingState state = playerStates.get(playerName);
        if (state != null) {
            state.markUnloaded(chunks);
        }
    }
    
    /**
     * Gets the set of chunks already sent to a player.
     */
    @Nonnull
    public Set<Long> getSentChunks(@Nonnull String playerName) {
        PlayerStreamingState state = playerStates.get(playerName);
        return state != null ? state.getSentChunks() : Collections.emptySet();
    }
    
    /**
     * Represents the delta between current and target chunk sets.
     */
    public static class ChunkDelta {
        public final List<Long> toLoad;
        public final List<Long> toUnload;
        
        public ChunkDelta(List<Long> toLoad, List<Long> toUnload) {
            this.toLoad = toLoad;
            this.toUnload = toUnload;
        }
        
        public boolean isEmpty() {
            return toLoad.isEmpty() && toUnload.isEmpty();
        }
    }
    
    /**
     * Prioritized chunk load request.
     */
    private static class ChunkLoadRequest implements Comparable<ChunkLoadRequest> {
        final long chunkIndex;
        final int distanceSquared;
        
        ChunkLoadRequest(long chunkIndex, int distanceSquared) {
            this.chunkIndex = chunkIndex;
            this.distanceSquared = distanceSquared;
        }
        
        @Override
        public int compareTo(ChunkLoadRequest other) {
            return Integer.compare(this.distanceSquared, other.distanceSquared);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkLoadRequest that = (ChunkLoadRequest) o;
            return chunkIndex == that.chunkIndex;
        }
        
        @Override
        public int hashCode() {
            return Long.hashCode(chunkIndex);
        }
    }
    
    /**
     * Per-player streaming state tracking sent chunks and pending queues.
     */
    public static class PlayerStreamingState {
        private final Set<Long> sentChunks = ConcurrentHashMap.newKeySet();
        private final PriorityBlockingQueue<ChunkLoadRequest> loadQueue = new PriorityBlockingQueue<>();
        private final Set<Long> loadQueueSet = ConcurrentHashMap.newKeySet();
        private final Queue<Long> unloadQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final Set<Long> unloadQueueSet = ConcurrentHashMap.newKeySet();
        @Nonnull
        public ChunkDelta computeDelta(@Nonnull Set<Long> targetChunks, 
                                        int playerChunkX, 
                                        int playerChunkZ) {
            List<Long> toLoad = new ArrayList<>();
            for (Long chunk : targetChunks) {
                if (!sentChunks.contains(chunk) && !loadQueueSet.contains(chunk)) {
                    toLoad.add(chunk);
                }
            }
            
            List<Long> toUnload = new ArrayList<>();
            for (Long chunk : sentChunks) {
                if (!targetChunks.contains(chunk) && !unloadQueueSet.contains(chunk)) {
                    toUnload.add(chunk);
                }
            }
            
            if (!toLoad.isEmpty()) {
                toLoad.sort(Comparator.comparingLong(idx -> {
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                    long dx = mx - playerChunkX;
                    long dz = mz - playerChunkZ;
                    return dx * dx + dz * dz;
                }));
            }
            
            return new ChunkDelta(toLoad, toUnload);
        }

        public void queueForLoading(@Nonnull Collection<Long> chunks, int playerChunkX, int playerChunkZ) {
            for (Long chunkIndex : chunks) {
                if (loadQueueSet.add(chunkIndex)) {
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(chunkIndex);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(chunkIndex);
                    int dx = mx - playerChunkX;
                    int dz = mz - playerChunkZ;
                    int distSq = dx * dx + dz * dz;
                    loadQueue.offer(new ChunkLoadRequest(chunkIndex, distSq));
                }
            }
        }
        
        public void queueForUnloading(@Nonnull Collection<Long> chunks) {
            for (Long chunkIndex : chunks) {
                if (unloadQueueSet.add(chunkIndex)) {
                    unloadQueue.offer(chunkIndex);
                }
            }
        }
        
        public int processQueue(@Nonnull Player player, int maxLoads, int maxUnloads) {
            int processed = 0;
            
            // Process unloads first (they free up client memory)
            List<Long> unloaded = new ArrayList<>();
            for (int i = 0; i < maxUnloads && !unloadQueue.isEmpty(); i++) {
                Long chunk = unloadQueue.poll();
                if (chunk != null) {
                    unloadQueueSet.remove(chunk);
                    if (sentChunks.remove(chunk)) {
                        unloaded.add(chunk);
                        processed++;
                    }
                }
            }
            
            if (!unloaded.isEmpty()) {
                sendUnloadPackets(player, unloaded);
            }
            
            List<Long> loaded = new ArrayList<>();
            for (int i = 0; i < maxLoads && !loadQueue.isEmpty(); i++) {
                ChunkLoadRequest request = loadQueue.poll();
                if (request != null) {
                    loadQueueSet.remove(request.chunkIndex);
                    // Mark as sent - the actual sending happens through the tracker
                    sentChunks.add(request.chunkIndex);
                    loaded.add(request.chunkIndex);
                    processed++;
                }
            }
            
            return processed;
        }
        
        private void sendUnloadPackets(@Nonnull Player player, @Nonnull List<Long> chunks) {
            if (chunks.isEmpty()) return;
            
            List<MapChunk> unloadPackets = new ArrayList<>(chunks.size());
            for (Long idx : chunks) {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                unloadPackets.add(new MapChunk(mx, mz, null));
            }
            
            for (int i = 0; i < unloadPackets.size(); i += PACKET_BATCH_SIZE) {
                int end = Math.min(i + PACKET_BATCH_SIZE, unloadPackets.size());
                List<MapChunk> batch = unloadPackets.subList(i, end);
                
                UpdateWorldMap packet = new UpdateWorldMap(
                    batch.toArray(new MapChunk[0]),
                    null,
                    null
                );
                
                sendPacket(player, packet);
            }
        }
        
        private void sendPacket(@Nonnull Player player, @Nonnull UpdateWorldMap packet) {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref != null && ref.isValid()) {
                    PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef != null) {
                        playerRef.getPacketHandler().write(packet);
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to send packet: " + e.getMessage());
            }
        }
        
        public void markSent(@Nonnull Collection<Long> chunks) {
            sentChunks.addAll(chunks);
            // Remove from load queue if present
            loadQueueSet.removeAll(chunks);
        }
        
        public void markUnloaded(@Nonnull Collection<Long> chunks) {
            sentChunks.removeAll(chunks);
            // Remove from unload queue if present
            unloadQueueSet.removeAll(chunks);
        }
        
        @Nonnull
        public Set<Long> getSentChunks() {
            return new HashSet<>(sentChunks);
        }
        
        public int getPendingLoadCount() {
            return loadQueue.size();
        }
        
        public int getPendingUnloadCount() {
            return unloadQueue.size();
        }
        
        public void clear() {
            sentChunks.clear();
            loadQueue.clear();
            loadQueueSet.clear();
            unloadQueue.clear();
            unloadQueueSet.clear();
        }
    }
}

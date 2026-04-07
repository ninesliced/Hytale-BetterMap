package dev.ninesliced.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages chunk streaming with delta updates for unloading.
 * Note: Load packets are handled by the native WorldMapTracker via RestrictedSpiralIterator.
 * This manager only handles unload packets and tracks sent chunks for delta computation.
 * <p>
 * Memory:
 * - sentChunks is a LongOpenHashSet (~9 bytes/entry vs ~56 for HashSet<Long>).
 * - sentChunks is bounded — entries beyond MAX_SENT_CHUNKS_PER_PLAYER are evicted via the
 * unload queue, prioritising chunks farthest from the last known player position.
 * - All access goes through synchronized blocks on the per-player state — no need for
 * ConcurrentHashMap.newKeySet() overhead.
 */
public class ChunkStreamingManager {
    private static final Logger LOGGER = Logger.getLogger(ChunkStreamingManager.class.getName());
    private static final ChunkStreamingManager INSTANCE = new ChunkStreamingManager();

    private static final int MAX_UNLOADS_PER_TICK = 100;
    private static final int PACKET_BATCH_SIZE = 25;

    /**
     * Maximum number of sent chunks tracked per player.
     * If exceeded, farthest chunks are queued for unloading.
     */
    private static final int MAX_SENT_CHUNKS_PER_PLAYER = 50_000;

    private final Map<String, PlayerStreamingState> playerStates = new ConcurrentHashMap<>();

    private ChunkStreamingManager() {}

    public static ChunkStreamingManager getInstance() {
        return INSTANCE;
    }

    @Nonnull
    public PlayerStreamingState getOrCreateState(@Nonnull String playerName) {
        return playerStates.computeIfAbsent(playerName, k -> new PlayerStreamingState());
    }

    public void removeState(@Nonnull String playerName) {
        PlayerStreamingState state = playerStates.remove(playerName);
        if (state != null) {
            state.clear();
            LOGGER.fine("Removed streaming state for player: " + playerName);
        }
    }

    public void cleanup() {
        for (PlayerStreamingState state : playerStates.values()) {
            state.clear();
        }
        playerStates.clear();
        LOGGER.info("ChunkStreamingManager cleaned up");
    }

    @Nonnull
    public ChunkDelta computeDelta(@Nonnull String playerName,
                                   @Nonnull Set<Long> targetChunks,
                                   int playerChunkX,
                                   int playerChunkZ) {
        PlayerStreamingState state = getOrCreateState(playerName);
        return state.computeDelta(targetChunks, playerChunkX, playerChunkZ);
    }

    public int processLoadQueue(@Nonnull Player player) {
        String playerName = player.getDisplayName();
        PlayerStreamingState state = playerStates.get(playerName);
        if (state == null) {
            return 0;
        }

        return state.processUnloadQueue(player, MAX_UNLOADS_PER_TICK);
    }

    public void queueChunksForLoading(@Nonnull String playerName,
                                      @Nonnull Collection<Long> chunksToLoad,
                                      int playerChunkX,
                                      int playerChunkZ) {
        // No-op: loading is handled by native WorldMapTracker
    }

    public void queueChunksForUnloading(@Nonnull String playerName,
                                        @Nonnull Collection<Long> chunksToUnload) {
        PlayerStreamingState state = getOrCreateState(playerName);
        state.queueForUnloading(chunksToUnload);
    }

    public void markChunksSent(@Nonnull String playerName, @Nonnull Collection<Long> chunks) {
        PlayerStreamingState state = playerStates.get(playerName);
        if (state != null) {
            state.markSent(chunks);
        }
    }

    public void markChunksUnloaded(@Nonnull String playerName, @Nonnull Collection<Long> chunks) {
        PlayerStreamingState state = playerStates.get(playerName);
        if (state != null) {
            state.markUnloaded(chunks);
        }
    }

    @Nonnull
    public Set<Long> getSentChunks(@Nonnull String playerName) {
        PlayerStreamingState state = playerStates.get(playerName);
        return state != null ? state.getSentChunks() : Collections.emptySet();
    }

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
     * Per-player streaming state tracking sent chunks and pending unload queue.
     * sentChunks is bounded — excess entries are auto-queued for unloading,
     * choosing the chunks farthest from the player's last known map position.
     */
    public static class PlayerStreamingState {
        private final LongOpenHashSet sentChunks = new LongOpenHashSet();
        private final ArrayDeque<Long> unloadQueue = new ArrayDeque<>();
        private final LongOpenHashSet unloadQueueSet = new LongOpenHashSet();

        // Last known player map-chunk position, used for far-eviction.
        private int lastPlayerChunkX = 0;
        private int lastPlayerChunkZ = 0;

        @Nonnull
        public synchronized ChunkDelta computeDelta(@Nonnull Set<Long> targetChunks,
                                                    int playerChunkX,
                                                    int playerChunkZ) {
            this.lastPlayerChunkX = playerChunkX;
            this.lastPlayerChunkZ = playerChunkZ;

            List<Long> toLoad = new ArrayList<>();
            for (Long chunk : targetChunks) {
                if (!sentChunks.contains(chunk.longValue())) {
                    toLoad.add(chunk);
                }
            }

            List<Long> toUnload = new ArrayList<>();
            LongIterator it = sentChunks.iterator();
            while (it.hasNext()) {
                long chunk = it.nextLong();
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

        public synchronized void markSent(@Nonnull Collection<Long> chunks) {
            for (Long chunk : chunks) {
                sentChunks.add(chunk.longValue());
            }
            enforceCapacity();
        }

        /**
         * Bounds sentChunks to MAX_SENT_CHUNKS_PER_PLAYER. When over the cap, the chunks
         * farthest from the last known player position are queued for unloading.
         * Caller must hold the monitor.
         */
        private void enforceCapacity() {
            int overflow = sentChunks.size() - MAX_SENT_CHUNKS_PER_PLAYER;
            if (overflow <= 0) return;

            // Build a list of (chunk, distSq) for entries not already queued for unload.
            // We only need to find the worst `overflow` entries — but with up to ~50k entries
            // a full sort is fine and simple. This runs only when over cap.
            int eligible = sentChunks.size() - unloadQueueSet.size();
            if (eligible <= 0) return;

            List<long[]> ranked = new ArrayList<>(eligible);
            LongIterator it = sentChunks.iterator();
            while (it.hasNext()) {
                long idx = it.nextLong();
                if (unloadQueueSet.contains(idx)) continue;
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                long dx = (long) mx - lastPlayerChunkX;
                long dz = (long) mz - lastPlayerChunkZ;
                ranked.add(new long[]{idx, dx * dx + dz * dz});
            }
            // Largest distance first.
            ranked.sort((a, b) -> Long.compare(b[1], a[1]));

            int toEvict = Math.min(overflow, ranked.size());
            for (int i = 0; i < toEvict; i++) {
                long idx = ranked.get(i)[0];
                if (unloadQueueSet.add(idx)) {
                    unloadQueue.add(idx);
                }
            }
            LOGGER.fine("Streaming cap exceeded — queued " + toEvict + " far chunks for eviction");
        }

        public synchronized void queueForUnloading(@Nonnull Collection<Long> chunks) {
            for (Long chunkIndex : chunks) {
                if (unloadQueueSet.add(chunkIndex.longValue())) {
                    unloadQueue.add(chunkIndex);
                }
            }
        }

        public int processUnloadQueue(@Nonnull Player player, int maxUnloads) {
            List<Long> unloaded = new ArrayList<>();
            synchronized (this) {
                for (int i = 0; i < maxUnloads && !unloadQueue.isEmpty(); i++) {
                    Long chunk = unloadQueue.poll();
                    if (chunk != null) {
                        unloadQueueSet.remove(chunk.longValue());
                        if (sentChunks.remove(chunk.longValue())) {
                            unloaded.add(chunk);
                        }
                    }
                }
            }

            if (!unloaded.isEmpty()) {
                sendUnloadPackets(player, unloaded);
            }

            return unloaded.size();
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

        public synchronized void markUnloaded(@Nonnull Collection<Long> chunks) {
            for (Long chunk : chunks) {
                long c = chunk.longValue();
                sentChunks.remove(c);
                unloadQueueSet.remove(c);
            }
        }

        @Nonnull
        public synchronized Set<Long> getSentChunks() {
            // Defensive copy for the rare external caller.
            HashSet<Long> copy = new HashSet<>(sentChunks.size());
            LongIterator it = sentChunks.iterator();
            while (it.hasNext()) {
                copy.add(it.nextLong());
            }
            return copy;
        }

        public synchronized int getSentChunkCount() {
            return sentChunks.size();
        }

        public synchronized int getPendingUnloadCount() {
            return unloadQueue.size();
        }

        public synchronized void clear() {
            sentChunks.clear();
            sentChunks.trim();
            unloadQueue.clear();
            unloadQueueSet.clear();
            unloadQueueSet.trim();
        }
    }
}

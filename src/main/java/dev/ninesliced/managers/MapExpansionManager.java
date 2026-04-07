package dev.ninesliced.managers;

import dev.ninesliced.exploration.ExploredChunksTracker;
import dev.ninesliced.utils.ChunkUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the boundaries of the explored map area to optimize rendering.
 * <p>
 * Memory: uses a per-instance reusable LongOpenHashSet so the per-tick boundary update
 * does not allocate. Old implementation allocated a fresh boxed HashSet of every chunk
 * in the radius on every chunk move per player — that was the worst single allocation
 * source in the plugin.
 */
public class MapExpansionManager {

    private final ExploredChunksTracker exploredChunks;
    private int minChunkX = Integer.MAX_VALUE;
    private int maxChunkX = Integer.MIN_VALUE;
    private int minChunkZ = Integer.MAX_VALUE;
    private int maxChunkZ = Integer.MIN_VALUE;

    /**
     * Last position used for boundary update - avoids re-processing same chunks
     */
    private int lastUpdateChunkX = Integer.MAX_VALUE;
    private int lastUpdateChunkZ = Integer.MAX_VALUE;
    private int lastUpdateRadius = -1;

    /**
     * Reusable scratch set — sized once on first use, then reused forever.
     */
    private final LongOpenHashSet scratchChunks = new LongOpenHashSet(1024);

    public MapExpansionManager(@Nonnull ExploredChunksTracker exploredChunks) {
        this.exploredChunks = exploredChunks;
    }

    /**
     * Updates boundaries based on player position and view radius.
     * Marks new chunks as explored.
     */
    public void updateBoundaries(int playerChunkX, int playerChunkZ, int viewRadius) {
        if (playerChunkX == lastUpdateChunkX && playerChunkZ == lastUpdateChunkZ && viewRadius == lastUpdateRadius) {
            return;
        }

        // Compute the chunks in the new radius into the reusable scratch set (no allocation).
        ChunkUtil.getChunksInCircularAreaLong(playerChunkX, playerChunkZ, viewRadius, scratchChunks);

        int newMinX = playerChunkX - viewRadius;
        int newMaxX = playerChunkX + viewRadius;
        int newMinZ = playerChunkZ - viewRadius;
        int newMaxZ = playerChunkZ + viewRadius;

        minChunkX = Math.min(minChunkX, newMinX);
        maxChunkX = Math.max(maxChunkX, newMaxX);
        minChunkZ = Math.min(minChunkZ, newMinZ);
        maxChunkZ = Math.max(maxChunkZ, newMaxZ);

        // Mark via the primitive overload — no boxing, no extra HashSet copy on the receiver side.
        exploredChunks.markChunksExplored(scratchChunks);

        lastUpdateChunkX = playerChunkX;
        lastUpdateChunkZ = playerChunkZ;
        lastUpdateRadius = viewRadius;
    }

    @Nonnull
    public MapBoundaries getCurrentBoundaries() {
        if (minChunkX == Integer.MAX_VALUE) {
            return new MapBoundaries(0, 0, 0, 0);
        }
        return new MapBoundaries(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    /**
     * Gets all chunks within the current bounding box.
     * Note: This returns the full rectangle, not just explored chunks.
     * Kept on the boxed Set<Long> API for backwards compat — only used by external code.
     */
    @Nonnull
    public Set<Long> getExpandedMapChunks() {
        if (minChunkX == Integer.MAX_VALUE) {
            return new HashSet<>();
        }
        return ChunkUtil.getChunksInRectangularArea(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    public long getTotalExploredArea() {
        if (minChunkX == Integer.MAX_VALUE) {
            return 0;
        }
        long width = ((long) maxChunkX - (long) minChunkX) + 1L;
        long height = ((long) maxChunkZ - (long) minChunkZ) + 1L;
        if (width <= 0L || height <= 0L) {
            return 0L;
        }
        if (width > Long.MAX_VALUE / height) {
            return Long.MAX_VALUE;
        }
        return width * height;
    }

    public void reset() {
        minChunkX = Integer.MAX_VALUE;
        maxChunkX = Integer.MIN_VALUE;
        minChunkZ = Integer.MAX_VALUE;
        maxChunkZ = Integer.MIN_VALUE;
        lastUpdateChunkX = Integer.MAX_VALUE;
        lastUpdateChunkZ = Integer.MAX_VALUE;
        lastUpdateRadius = -1;
        scratchChunks.clear();
        scratchChunks.trim();
        exploredChunks.clear();
    }

    public static class MapBoundaries {
        public final int minX;
        public final int maxX;
        public final int minZ;
        public final int maxZ;

        public MapBoundaries(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public int getWidth() {
            return saturatingLongToInt(getWidthLong());
        }

        public int getHeight() {
            return saturatingLongToInt(getHeightLong());
        }

        public long getArea() {
            long width = getWidthLong();
            long height = getHeightLong();
            if (width <= 0L || height <= 0L) {
                return 0L;
            }
            if (width > Long.MAX_VALUE / height) {
                return Long.MAX_VALUE;
            }
            return width * height;
        }

        public long getWidthLong() {
            return ((long) maxX - (long) minX) + 1L;
        }

        public long getHeightLong() {
            return ((long) maxZ - (long) minZ) + 1L;
        }

        private static int saturatingLongToInt(long value) {
            if (value <= 0L) {
                return 0;
            }
            if (value > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) value;
        }

        @Override
        public String toString() {
            return String.format("MapBoundaries{x:[%d,%d], z:[%d,%d], size:%dx%d}",
                    minX, maxX, minZ, maxZ, getWidth(), getHeight());
        }
    }
}

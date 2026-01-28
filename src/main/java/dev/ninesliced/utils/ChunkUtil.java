package dev.ninesliced.utils;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for chunk coordinate calculations.
 */
public class ChunkUtil {
    private static final int CHUNK_SIZE = 16;
    
    /**
     * Cache of pre-computed circular offsets for each radius.
     */
    private static final Map<Integer, int[][]> CIRCULAR_OFFSETS_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Thread-local reusable set to avoid creating new HashSets.
     */
    private static final ThreadLocal<Set<Long>> REUSABLE_CHUNK_SET = ThreadLocal.withInitial(() -> new HashSet<>(1024));

    /**
     * Packs chunk coordinates into a long index.
     *  
     * @param chunkX Chunk X.
     * @param chunkZ Chunk Z.
     * @return Packed index.
     */
    public static long chunkCoordsToIndex(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Extracts X coordinate from packed index.
     * 
     * @param index Packed index.
     * @return Chunk X.
     */
    public static int indexToChunkX(long index) {
        return (int) (index >> 32);
    }

    /**
     * Extracts Z coordinate from packed index.
     * 
     * @param index Packed index.
     * @return Chunk Z.
     */
    public static int indexToChunkZ(long index) {
        return (int) index;
    }

    /**
     * Converts a block coordinate to a chunk coordinate.
     * @param blockCoord Block coordinate.
     * @return Chunk coordinate.
     */
    public static int blockToChunkCoord(double blockCoord) {
        return (int) Math.floor(blockCoord) >> 4;
    }

    /**
     * Gets a set of chunk indices within a circular radius.
     *
     * @param centerChunkX Center chunk X.
     * @param centerChunkZ Center chunk Z.
     * @param radiusChunks Radius in chunks.
     * @return Set of chunk indices.
     */
    @Nonnull
    public static Set<Long> getChunksInCircularArea(int centerChunkX, int centerChunkZ, int radiusChunks) {
        int[][] offsets = getCircularOffsets(radiusChunks);
        
        // Pre-allocate with expected capacity based on offsets count
        Set<Long> chunks = new HashSet<>(offsets.length + offsets.length / 3);
        
        for (int[] offset : offsets) {
            chunks.add(chunkCoordsToIndex(centerChunkX + offset[0], centerChunkZ + offset[1]));
        }

        return chunks;
    }
    
    /**
     * Gets a set of chunk indices within a circular radius, reusing the provided set.
     */
    public static void getChunksInCircularArea(int centerChunkX, int centerChunkZ, int radiusChunks, @Nonnull Set<Long> targetSet) {
        targetSet.clear();
        int[][] offsets = getCircularOffsets(radiusChunks);
        
        for (int[] offset : offsets) {
            targetSet.add(chunkCoordsToIndex(centerChunkX + offset[0], centerChunkZ + offset[1]));
        }
    }
    
    /**
     * Gets or computes the circular offsets for a given radius.
     */
    @Nonnull
    private static int[][] getCircularOffsets(int radiusChunks) {
        int[][] cached = CIRCULAR_OFFSETS_CACHE.get(radiusChunks);
        if (cached != null) {
            return cached;
        }
        
        int radiusSquared = radiusChunks * radiusChunks;
        
        // First pass: count how many offsets we need
        int count = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                if (dx * dx + dz * dz <= radiusSquared) {
                    count++;
                }
            }
        }
        
        // Second pass: populate the array
        int[][] offsets = new int[count][2];
        int index = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                if (dx * dx + dz * dz <= radiusSquared) {
                    offsets[index][0] = dx;
                    offsets[index][1] = dz;
                    index++;
                }
            }
        }
        
        CIRCULAR_OFFSETS_CACHE.put(radiusChunks, offsets);
        return offsets;
    }
    
    /**
     * Gets a thread-local reusable set for temporary chunk operations.
     */
    @Nonnull
    public static Set<Long> getReusableChunkSet() {
        Set<Long> set = REUSABLE_CHUNK_SET.get();
        set.clear();
        return set;
    }

    /**
     * Gets a set of chunk indices within a rectangular area.
     *
     * @param minChunkX Min X.
     * @param maxChunkX Max X.
     * @param minChunkZ Min Z.
     * @param maxChunkZ Max Z.
     * @return Set of chunk indices.
     */
    @Nonnull
    public static Set<Long> getChunksInRectangularArea(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        Set<Long> chunks = new HashSet<>();

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunks.add(chunkCoordsToIndex(x, z));
            }
        }

        return chunks;
    }

    /**
     * Calculates Euclidean distance between two chunk coordinates.
     *
     * @param x1 First X.
     * @param z1 First Z.
     * @param x2 Second X.
     * @param z2 Second Z.
     * @return Distance.
     */
    public static double getChunkDistance(int x1, int z1, int x2, int z2) {
        long dx = x1 - x2;
        long dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }
}

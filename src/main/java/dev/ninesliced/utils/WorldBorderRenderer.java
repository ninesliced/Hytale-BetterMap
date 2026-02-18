package dev.ninesliced.utils;

import dev.ninesliced.configs.ModConfig;

/**
 * Utility class for rendering the world border circle on map chunk images.
 */
public class WorldBorderRenderer {

    private static final int BORDER_COLOR = 0xFF0000FF;
    private static final int MAP_CHUNK_SIZE = 32;

    /**
     * Checks if a map chunk intersects with the world border circle.
     *
     * @param mapChunkX The map chunk X coordinate.
     * @param mapChunkZ The map chunk Z coordinate.
     * @return true if the chunk intersects the border.
     */
    public static boolean chunkIntersectsBorder(int mapChunkX, int mapChunkZ) {
        ModConfig config = ModConfig.getInstance();
        if (!config.isWorldBorderEnabled()) {
            return false;
        }

        int radius = config.getWorldBorderRadius();
        int offsetX = config.getWorldBorderOffsetX();
        int offsetZ = config.getWorldBorderOffsetZ();

        int chunkMinX = mapChunkX * MAP_CHUNK_SIZE;
        int chunkMaxX = chunkMinX + MAP_CHUNK_SIZE;
        int chunkMinZ = mapChunkZ * MAP_CHUNK_SIZE;
        int chunkMaxZ = chunkMinZ + MAP_CHUNK_SIZE;

        double closestDist = distanceToClosestPoint(chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, offsetX, offsetZ);
        double farthestDist = distanceToFarthestPoint(chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, offsetX, offsetZ);

        return closestDist <= radius && farthestDist >= radius;
    }

    /**
     * Renders the world border circle directly on a pixel data array.
     *
     * @param data      The pixel data array (ABGR format).
     * @param width     The image width.
     * @param height    The image height.
     * @param mapChunkX The map chunk X coordinate.
     * @param mapChunkZ The map chunk Z coordinate.
     */
    public static void renderBorderOnData(int[] data, int width, int height, int mapChunkX, int mapChunkZ) {
        ModConfig config = ModConfig.getInstance();
        if (!config.isWorldBorderEnabled() || data == null || width <= 0 || height <= 0) {
            return;
        }

        int radius = config.getWorldBorderRadius();
        int offsetX = config.getWorldBorderOffsetX();
        int offsetZ = config.getWorldBorderOffsetZ();

        int chunkMinX = mapChunkX * MAP_CHUNK_SIZE;
        int chunkMinZ = mapChunkZ * MAP_CHUNK_SIZE;

        float scaleX = (float) width / MAP_CHUNK_SIZE;
        float scaleZ = (float) height / MAP_CHUNK_SIZE;

        float tolerance = Math.max(1.0f, 1.0f / Math.min(scaleX, scaleZ));
        int innerRadius = (int) Math.max(0, radius - tolerance);
        int outerRadius = (int) (radius + tolerance);
        long innerRadiusSq = (long) innerRadius * innerRadius;
        long outerRadiusSq = (long) outerRadius * outerRadius;

        // Use integer math: pre-compute world coordinates scaled by width/height
        // worldX = chunkMinX + px * MAP_CHUNK_SIZE / width  (integer division per pixel)
        // Pre-compute row's dz² outside the inner loop
        for (int py = 0; py < height; py++) {
            int worldZ = chunkMinZ + py * MAP_CHUNK_SIZE / height;
            long dz = (long) worldZ - offsetZ;
            long dzSq = dz * dz;

            // Early row skip: if dz² alone exceeds outerRadiusSq, no pixel in this row can match
            if (dzSq > outerRadiusSq) {
                continue;
            }

            int rowOffset = py * width;
            for (int px = 0; px < width; px++) {
                int worldX = chunkMinX + px * MAP_CHUNK_SIZE / width;
                long dx = (long) worldX - offsetX;
                long distSq = dx * dx + dzSq;

                if (distSq >= innerRadiusSq && distSq <= outerRadiusSq) {
                    int index = rowOffset + px;
                    if (index >= 0 && index < data.length) {
                        data[index] = BORDER_COLOR;
                    }
                }
            }
        }
    }

    private static double distanceToClosestPoint(int minX, int maxX, int minZ, int maxZ, int centerX, int centerZ) {
        int closestX = Math.max(minX, Math.min(maxX, centerX));
        int closestZ = Math.max(minZ, Math.min(maxZ, centerZ));
        double dx = closestX - centerX;
        double dz = closestZ - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double distanceToFarthestPoint(int minX, int maxX, int minZ, int maxZ, int centerX, int centerZ) {
        int farthestX = (Math.abs(minX - centerX) > Math.abs(maxX - centerX)) ? minX : maxX;
        int farthestZ = (Math.abs(minZ - centerZ) > Math.abs(maxZ - centerZ)) ? minZ : maxZ;

        double dx = farthestX - centerX;
        double dz = farthestZ - centerZ;

        return Math.sqrt(dx * dx + dz * dz);
    }
}

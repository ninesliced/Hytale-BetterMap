package dev.ninesliced.utils;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/** Height lookups through the 0.7.0 chunk components, without blocking the world thread. */
public final class WorldHeightUtil {
    private WorldHeightUtil() {}

    @Nullable
    public static Double getLoadedSurfaceY(World world, double x, double z) {
        var chunkRef = world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunkRef == null || !chunkRef.isValid()) return null;
        var heightmap = chunkRef.getStore().getComponentConcurrent(chunkRef, HeightmapColumn.getComponentType());
        return getSurfaceY(heightmap, MathUtil.floor(x), MathUtil.floor(z));
    }

    public static CompletableFuture<Double> getSurfaceYAsync(World world, double x, double z) {
        return world.getChunkStore().getChunkReferenceAsync(ChunkUtil.indexChunkFromBlock(x, z))
            .thenApplyAsync(chunkRef -> {
                if (chunkRef == null || !chunkRef.isValid()) return null;
                var heightmap = chunkRef.getStore().getComponent(chunkRef, HeightmapColumn.getComponentType());
                return getSurfaceY(heightmap, MathUtil.floor(x), MathUtil.floor(z));
            }, world);
    }

    /** Returns one block above the surface, or null if no surface is known. */
    @Nullable
    public static Double getSurfaceY(@Nullable HeightmapColumn heightmap, int x, int z) {
        if (heightmap == null) return null;
        int height = heightmap.getHeight(x, z);
        return height == HeightmapColumn.NO_HEIGHT ? null : height + 1.0;
    }
}

package dev.ninesliced.utils;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.LegacyHeightmap;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.TreeHeightmap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class WorldHeightUtilTest {
    @Test
    void unknownSurfaceDoesNotBecomeATeleportDestination() {
        assertNull(WorldHeightUtil.getSurfaceY(null, 0, 0));
        assertNull(WorldHeightUtil.getSurfaceY(new HeightmapColumn(new LegacyHeightmap()), 0, 0));
        assertNull(WorldHeightUtil.getSurfaceY(new HeightmapColumn(new TreeHeightmap()), 0, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {-64, 0, 319, 40000})
    void preservesFullHeightRangeAndNegativeWorldCoordinates(int height) {
        var backend = new TreeHeightmap();
        backend.onBlockChanged(ChunkUtil.chunkCoordinate(height), 31, height, 30, true, true, null);
        var column = new HeightmapColumn(backend);
        assertEquals(height + 1.0, WorldHeightUtil.getSurfaceY(column, -1, -2));
        assertNull(WorldHeightUtil.getSurfaceY(column, 0, 0));
    }
}

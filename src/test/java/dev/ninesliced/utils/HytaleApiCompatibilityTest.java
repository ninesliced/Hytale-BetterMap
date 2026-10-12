package dev.ninesliced.utils;

import com.hypixel.hytale.common.fastutil.HLongSet;
import com.hypixel.hytale.math.iterator.CircleSpiralIterator;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.asset.type.gameplay.worldmap.UserMapMarkerConfig;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import static org.junit.jupiter.api.Assertions.*;

/** Checks runtime-only contracts that javac cannot check for BetterMap's reflection hooks. */
class HytaleApiCompatibilityTest {
    @Test
    void trackerAndImageCacheHooksMatchTheServerArtifact() throws Exception {
        field(WorldMapTracker.class, "loadedLock", ReentrantReadWriteLock.class);
        field(WorldMapTracker.class, "loaded", HLongSet.class);
        field(WorldMapTracker.class, "spiralIterator", CircleSpiralIterator.class);
        field(WorldMapTracker.class, "pendingReloadChunks", HLongSet.class);
        field(WorldMapTracker.class, "pendingReloadFutures", Map.class);
        field(WorldMapTracker.class, "viewRadiusOverride", Integer.class);
        field(WorldMapTracker.class, "updateTimer", float.class);
        field(WorldMapTracker.class, "markerTracker", MapMarkerTracker.class);
        field(MapMarkerTracker.class, "smallMovementsTimer", float.class);
        var images = WorldMapManager.class.getDeclaredField("images");
        assertTrue(images.trySetAccessible());
        assertTrue(Iterable.class.isAssignableFrom(images.getType().getMethod("long2ObjectEntrySet").getReturnType()));
        field(WorldMapManager.ImageEntry.class, "image", MapImage.class);
    }

    @Test
    void settingsHooksRemainWritable() {
        var settings = new WorldMapSettings();
        ReflectionHelper.setFieldValueRecursive(settings, "imageScale", 0.25f);
        assertEquals(0.25f, settings.getImageScale());
        var markers = new UserMapMarkerConfig();
        ReflectionHelper.setFieldValueRecursive(markers, "allowCreatingMarkers", false);
        ReflectionHelper.setFieldValueRecursive(markers, "allowDeleteOtherPlayersSharedMarkers", false);
        ReflectionHelper.setFieldValueRecursive(markers, "maxPersonalMarkersPerPlayer", 30);
        ReflectionHelper.setFieldValueRecursive(markers, "maxSharedMarkersPerPlayer", 40);
        assertFalse(markers.isAllowCreatingMarkers());
        assertFalse(markers.isAllowDeleteOtherPlayersSharedMarkers());
        assertEquals(30, markers.getMaxPersonalMarkersPerPlayer());
        assertEquals(40, markers.getMaxSharedMarkersPerPlayer());
    }

    private static void field(Class<?> owner, String name, Class<?> type) throws Exception {
        var field = owner.getDeclaredField(name);
        assertTrue(field.trySetAccessible(), owner.getSimpleName() + "." + name);
        assertTrue(type.isAssignableFrom(field.getType()), owner.getSimpleName() + "." + name);
    }
}

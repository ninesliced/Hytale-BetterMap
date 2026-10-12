package dev.ninesliced.providers;

import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerIconSize;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.universe.world.meta.state.BlockMapMarkersResource.BlockMapMarkerData;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerAlert;
import dev.ninesliced.managers.PlayerRadarManager.RadarData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import static org.junit.jupiter.api.Assertions.*;

class MapMarkerCompatibilityTest {
    @Test
    void blockMarkerPreservesCompassAlertThroughSerialization() {
        var alert = new MapMarkerAlert("Alert.png", 6f, 12f, new Color((byte) 20, (byte) 40, (byte) 60));
        var data = new BlockMapMarkerData("server.marker.test", "Marker.png", "block-marker", false,
            MapMarkerIconSize.Default, "Compass.png", alert);
        var marker = BlockMapMarkerPrivacyProvider.createMarker(-10, 80, 45, data);
        var buffer = MemorySegment.ofArray(new byte[marker.computeSize()]);
        marker.serialize(buffer, 0);
        var decoded = MapMarker.toObject(buffer);
        assertEquals("block-marker", decoded.id);
        assertEquals("Compass.png", decoded.compassImage);
        assertEquals(MapMarkerIconSize.Default, decoded.iconSize);
        assertEquals(1, decoded.components.length);
        assertEquals(alert.toComponent(), decoded.components[0]);
        assertEquals(-9.5, decoded.transform.position.x);
    }

    @Test
    void blockMarkerWithoutAlertKeepsComponentsAbsent() {
        var data = new BlockMapMarkerData("server.marker.test", "Marker.png", "block-marker", false,
            MapMarkerIconSize.Default, null);
        var marker = BlockMapMarkerPrivacyProvider.createMarker(0, 80, 0, data);
        assertNull(marker.components);
        assertNull(marker.compassImage);
    }

    @Test
    void radarMarkerSerializesWithTheNewProtocolFields() throws Exception {
        var create = PlayerRadarProvider.class.getDeclaredMethod("createMarker", String.class, String.class, RadarData.class);
        create.setAccessible(true);
        var data = new RadarData("player-id", "Explorer", new Vector3d(-20, 80, 45), null);
        var marker = (MapMarker) create.invoke(null, "Player-player-id", "Explorer", data);
        var buffer = MemorySegment.ofArray(new byte[4096]);
        assertTrue(marker.serialize(buffer, 0) > 0);
        assertEquals(MapMarkerIconSize.Default, MapMarker.getIconSize(buffer));
        assertFalse(MapMarker.hasCompassImage(buffer, 0));
        assertEquals("Player-player-id", MapMarker.getId(buffer));
        assertEquals("Explorer", marker.name.rawText);
        assertEquals(-20.0, marker.transform.position.x);
    }
}

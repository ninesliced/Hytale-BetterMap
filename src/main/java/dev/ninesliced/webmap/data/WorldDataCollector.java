package dev.ninesliced.webmap.data;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.listeners.ExplorationListener;
import dev.ninesliced.managers.WaypointManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects world, player, and marker payloads for web map API and websocket frames.
 */
public class WorldDataCollector {

    @Nonnull
    public List<Map<String, Object>> getWorlds() {
        List<Map<String, Object>> worlds = new ArrayList<>();
        for (World world : Universe.get().getWorlds().values()) {
            if (!ExplorationListener.isTrackedWorld(world)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", world.getName());
            worlds.add(item);
        }
        worlds.sort(Comparator.comparing(item -> String.valueOf(item.get("name"))));
        return worlds;
    }

    @Nonnull
    public Map<String, Object> buildSnapshot(@Nonnull String worldName) {
        return buildSnapshot(worldName, WebViewFilter.global());
    }

    @Nonnull
    public Map<String, Object> buildSnapshot(@Nonnull String worldName, @Nonnull WebViewFilter filter) {
        return buildSnapshot(worldName, filter, true);
    }

    @Nonnull
    public Map<String, Object> buildSnapshot(@Nonnull String worldName,
                                             @Nonnull WebViewFilter filter,
                                             boolean allowGlobalMode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("world", worldName);
        payload.put("players", getPlayers(worldName, filter));
        payload.put("markers", getMarkers(worldName, filter));
        payload.put("defaultMode", allowGlobalMode ? "global" : "player");
        payload.put("allowGlobalMode", allowGlobalMode);
        payload.put("filterMode", filter.modeId());
        payload.put("filterPlayerUuid", filter.playerUuid() == null ? "" : filter.playerUuid().toString());
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    @Nonnull
    public List<Map<String, Object>> getPlayers(@Nonnull String worldName) {
        return getPlayers(worldName, WebViewFilter.global());
    }

    @Nonnull
    public List<Map<String, Object>> getPlayers(@Nonnull String worldName, @Nonnull WebViewFilter filter) {
        World world = Universe.get().getWorld(worldName);
        if (!ExplorationListener.isTrackedWorld(world) || world == null) {
            return List.of();
        }

        List<Map<String, Object>> players = new ArrayList<>();

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            try {
                if (!filter.includesPlayer(playerRef)) {
                    continue;
                }

                Transform transform = playerRef.getTransform();
                if (transform == null) {
                    continue;
                }
                Vector3d position = transform.getPosition();
                Vector3f rotation = transform.getRotation();

                Map<String, Object> player = new LinkedHashMap<>();
                player.put("uuid", playerRef.getUuid().toString());
                player.put("name", playerRef.getUsername());
                player.put("x", position.x);
                player.put("y", position.y);
                player.put("z", position.z);
                player.put("yaw", rotation != null ? rotation.y : 0.0f);
                player.put("icon", playerIconFor(playerRef.getUuid().toString()));
                players.add(player);
            } catch (Exception ignored) {
            }
        }
        return players;
    }

    @Nonnull
    public List<Map<String, Object>> getMarkers(@Nonnull String worldName) {
        return getMarkers(worldName, WebViewFilter.global());
    }

    @Nonnull
    public List<Map<String, Object>> getMarkers(@Nonnull String worldName, @Nonnull WebViewFilter filter) {
        World world = Universe.get().getWorld(worldName);
        if (!ExplorationListener.isTrackedWorld(world) || world == null) {
            return List.of();
        }

        Map<String, Map<String, Object>> markersById = new LinkedHashMap<>();

        for (UserMapMarker marker : WaypointManager.getMarkersForWebMap(world)) {
            if (marker == null || marker.getId() == null) {
                continue;
            }

            boolean shared = WaypointManager.isSharedId(marker.getId());
            String ownerUuid = marker.getCreatedByUuid() == null ? "" : marker.getCreatedByUuid().toString();
            if (filter.mode() == WebViewFilter.Mode.PLAYER && filter.playerUuid() != null && !shared) {
                if (!filter.playerUuid().toString().equals(ownerUuid)) {
                    continue;
                }
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", marker.getId());
            payload.put("name", marker.getName());
            payload.put("icon", marker.getIcon());
            payload.put("x", marker.getX());
            payload.put("z", marker.getZ());
            payload.put("shared", shared);
            payload.put("owner", marker.getCreatedByName());
            payload.put("ownerUuid", ownerUuid);
            markersById.put(marker.getId(), payload);
        }

        return new ArrayList<>(markersById.values());
    }

    private String playerIconFor(String uuid) {
        int index = Math.floorMod(uuid.hashCode(), 6);
        return switch (index) {
            case 0 -> "UserA.png";
            case 1 -> "UserB.png";
            case 2 -> "UserC.png";
            case 3 -> "UserD.png";
            case 4 -> "UserE.png";
            default -> "UserF.png";
        };
    }

}

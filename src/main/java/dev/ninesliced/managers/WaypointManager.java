package dev.ninesliced.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.listeners.ExplorationEventListener;
import dev.ninesliced.utils.PermissionsUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WaypointManager {
    private static final Logger LOGGER = Logger.getLogger(WaypointManager.class.getName());
    private static final String GLOBAL_ID_PREFIX = "global_waypoint_";

    private static WaypointPersistence persistence;
    private static final Set<String> loadedPlayers = ConcurrentHashMap.newKeySet();

    private WaypointManager() {
    }

    public static void initialize(@Nonnull Path configDir) {
        persistence = new WaypointPersistence(configDir);
    }

    @Nullable
    public static MapMarker[] getWaypoints(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null || !ExplorationEventListener.isTrackedWorld(world)) return new MapMarker[0];

        ensureLoaded(player, world);

        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] markers = perWorldData.getWorldMapMarkers();
        return markers != null ? markers : new MapMarker[0];
    }

    public static void addWaypoint(@Nonnull Player player, @Nonnull String name, @Nonnull String icon, @Nonnull Transform transform, boolean global) {
        World world = player.getWorld();
        if (world == null || !ExplorationEventListener.isTrackedWorld(world)) return;

        ensureLoaded(player, world);

        String markerId = (global ? GLOBAL_ID_PREFIX : "waypoint_") + UUID.randomUUID();
        MapMarker marker = new MapMarker(markerId, name, normalizeIcon(icon), transform, buildContextMenu(player, markerId));

        if (global) {
            saveGlobalMarker(marker, world, player);
        } else {
            savePersonalMarker(player, world, marker);
        }

        invalidatePlayerCache(player, world);
    }

    public static boolean removeWaypoint(@Nonnull Player player, @Nonnull String idOrName) {
        World world = player.getWorld();
        if (world == null || !ExplorationEventListener.isTrackedWorld(world)) return false;

        ensureLoaded(player, world);

        MapMarker target = findWaypoint(player, idOrName);
        if (target == null || target.id == null) {
            return false;
        }

        if (isGlobalId(target.id)) {
            boolean result = removeGlobalMarker(target.id, world.getName(), player);
            if (result) {
                invalidatePlayerCache(player, world);
                ensureLoaded(player, world);
            }
            return result;
        }

        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] currentMarkers = perWorldData.getWorldMapMarkers();
        if (currentMarkers == null) return false;

        List<MapMarker> newMarkerList = new ArrayList<>();
        boolean found = false;

        for (MapMarker marker : currentMarkers) {
            boolean match = marker != null && marker.id != null && marker.id.equalsIgnoreCase(target.id);
            if (!match && marker != null && marker.name != null) {
                match = stripColorTags(marker.name).equalsIgnoreCase(idOrName);
            }

            if (match) {
                found = true;
                continue;
            }
            newMarkerList.add(marker);
        }

        if (found) {
            persistPersonal(player, world.getName(), newMarkerList);
            invalidatePlayerCache(player, world);
            ensureLoaded(player, world);
        }
        return found;
    }

    public static boolean updateWaypoint(@Nonnull Player player, @Nonnull String id, @Nullable String newName, @Nullable String newIcon, @Nullable Transform newTransform) {
        World world = player.getWorld();
        if (world == null || !ExplorationEventListener.isTrackedWorld(world)) return false;

        ensureLoaded(player, world);

        if (isGlobalId(id)) {
            boolean result = updateGlobalMarker(id, newName, newIcon, newTransform, world.getName(), player);
            if (result) {
                invalidatePlayerCache(player, world);
            }
            return result;
        }

        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] currentMarkers = perWorldData.getWorldMapMarkers();
        if (currentMarkers == null) return false;

        List<MapMarker> rebuilt = new ArrayList<>();
        boolean found = false;

        for (MapMarker m : currentMarkers) {
            if (m != null && m.id != null && m.id.equals(id)) {
                found = true;
                String nameToUse = newName != null ? newName : m.name;
                String iconToUse = newIcon != null ? normalizeIcon(newIcon) : m.markerImage;
                Transform transformToUse = newTransform != null ? newTransform : m.transform;
                String newId = (iconToUse != null && !iconToUse.equals(m.markerImage)) ? ("waypoint_" + UUID.randomUUID()) : m.id;
                rebuilt.add(new MapMarker(newId, nameToUse, iconToUse, transformToUse, buildContextMenu(player, newId)));
                continue;
            }
            rebuilt.add(m);
        }

        if (!found) {
            return false;
        }

        persistPersonal(player, world.getName(), rebuilt);
        invalidatePlayerCache(player, world);
        ensureLoaded(player, world);
        return true;
    }

    @Nullable
    private static ContextMenuItem[] buildContextMenu(@Nonnull Player player, @Nonnull String markerId) {
        List<ContextMenuItem> menuItems = new ArrayList<>();
        boolean isGlobal = isGlobalId(markerId);
        menuItems.add(new ContextMenuItem(isGlobal ? "Global Waypoint" : "Personal Waypoint", ""));
        if (PermissionsUtil.canTeleport(player)
            && BetterMapConfig.getInstance().isAllowWaypointTeleports()) {
            menuItems.add(new ContextMenuItem("Teleport To", "bm waypoint teleport " + markerId));
        }
        if (isGlobal) {
            menuItems.add(new ContextMenuItem("Delete", "bm waypoint removeglobal " + markerId));
        } else {
            menuItems.add(new ContextMenuItem("Delete", "bm waypoint remove " + markerId));
        }
        return menuItems.isEmpty() ? null : menuItems.toArray(new ContextMenuItem[0]);
    }

    public static MapMarker getWaypoint(@Nonnull Player player, @Nonnull String id) {
        World world = player.getWorld();
        if (world == null || !ExplorationEventListener.isTrackedWorld(world)) return null;

        ensureLoaded(player, world);

        MapMarker[] all = getWaypoints(player);
        for (MapMarker m : all) {
            if (m != null && m.id != null && m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public static MapMarker findWaypoint(@Nonnull Player player, @Nonnull String nameOrId) {
        World world = player.getWorld();
        if (world == null || !ExplorationEventListener.isTrackedWorld(world)) return null;

        ensureLoaded(player, world);

        MapMarker[] all = getWaypoints(player);
        for (MapMarker m : all) {
            if (m == null) continue;
            if (m.id != null && m.id.equalsIgnoreCase(nameOrId)) return m;
            if (m.name != null) {
                if (m.name.equalsIgnoreCase(nameOrId)) return m;
                if (stripColorTags(m.name).equalsIgnoreCase(nameOrId)) return m;
            }
        }
        return null;
    }

    private static void refreshPlayerMarkers(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null || !ExplorationEventListener.isTrackedWorld(world)) return;

        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] currentMarkers = perWorldData.getWorldMapMarkers();

        List<String> oldMarkerIds = new ArrayList<>();
        if (currentMarkers != null) {
            for (MapMarker m : currentMarkers) {
                if (m != null && m.id != null) {
                    oldMarkerIds.add(m.id);
                }
            }
        }

        List<MapMarker> personal = new ArrayList<>();
        if (currentMarkers != null) {
            for (MapMarker m : currentMarkers) {
                if (m != null && m.id != null && !isGlobalId(m.id)) {
                    personal.add(m);
                }
            }
        }

        List<MapMarker> globals = getGlobalMarkers(world.getName(), player);
        List<MapMarker> combined = new ArrayList<>(personal);
        combined.addAll(globals);

        MapMarker[] newMarkers = combined.toArray(new MapMarker[0]);
        perWorldData.setWorldMapMarkers(newMarkers);

        sendMarkersToClient(player, newMarkers, oldMarkerIds);
    }

    private static void ensureLoaded(@Nonnull Player player, @Nonnull World world) {
        if (persistence == null) {
            return;
        }

        if (!isTrackedWorld(world)) {
            return;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        String worldName = world.getName();
        String worldCacheKey = cacheKey(uuid, worldName);
        if (!loadedPlayers.add(worldCacheKey)) {
            return;
        }

        List<StoredWaypoint> stored = persistence.loadPlayer(uuid, player.getDisplayName(), worldName);
        List<MapMarker> markers = new ArrayList<>();
        if (!stored.isEmpty()) {
            for (StoredWaypoint waypoint : stored) {
                MapMarker marker = toMarker(waypoint, player);
                if (marker != null) {
                    markers.add(marker);
                }
            }
        }
        markers.addAll(getGlobalMarkers(worldName, player));

        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(worldName);
        perWorldData.setWorldMapMarkers(markers.toArray(new MapMarker[0]));
    }

    private static List<MapMarker> getGlobalMarkers(@Nonnull String worldName, @Nonnull Player player) {
        if (persistence == null) {
            return Collections.emptyList();
        }
        List<StoredWaypoint> stored = persistence.loadGlobal();
        if (stored.isEmpty()) {
            return Collections.emptyList();
        }
        List<MapMarker> markers = new ArrayList<>();
        for (StoredWaypoint waypoint : stored) {
            if (waypoint.world == null || !worldName.equalsIgnoreCase(waypoint.world)) {
                continue;
            }
            MapMarker marker = toMarker(waypoint, player);
            if (marker != null) {
                markers.add(marker);
            }
        }
        return markers;
    }

    private static void savePersonalMarker(@Nonnull Player player, @Nonnull World world, @Nonnull MapMarker marker) {
        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] currentMarkers = perWorldData.getWorldMapMarkers();
        List<MapMarker> newMarkerList = new ArrayList<>();
        if (currentMarkers != null) {
            newMarkerList.addAll(Arrays.asList(currentMarkers));
        }
        newMarkerList.add(marker);
        perWorldData.setWorldMapMarkers(newMarkerList.toArray(new MapMarker[0]));

        persistPersonal(player, world.getName(), newMarkerList);
    }

    private static void persistPersonal(@Nonnull Player player, @Nonnull String worldName, @Nonnull List<MapMarker> markers) {
        if (persistence == null || !ExplorationEventListener.isTrackedWorld(player.getWorld())) {
            return;
        }
        List<StoredWaypoint> stored = new ArrayList<>();
        for (MapMarker marker : markers) {
            if (marker == null || marker.id == null) continue;
            if (isGlobalId(marker.id)) continue;
            StoredWaypoint waypoint = fromMarker(marker, worldName, player.getDisplayName(), ((CommandSender) player).getUuid(), false);
            if (waypoint != null) {
                stored.add(waypoint);
            }
        }
        persistence.savePlayer(((CommandSender) player).getUuid(), player.getDisplayName(), worldName, stored);
    }

    private static void saveGlobalMarker(@Nonnull MapMarker marker, @Nonnull World world, @Nonnull Player player) {
        if (persistence == null || !ExplorationEventListener.isTrackedWorld(world)) {
            return;
        }
        List<StoredWaypoint> existing = persistence.loadGlobal();
        List<StoredWaypoint> mutable = new ArrayList<>(existing);
        StoredWaypoint converted = fromMarker(marker, world.getName(), player.getDisplayName(), ((CommandSender) player).getUuid(), true);
        if (converted == null) {
            return;
        }
        mutable.add(converted);
        persistence.saveGlobal(mutable);
        world.execute(() -> refreshAllPlayersMarkers(world));
    }

    public static void refreshAllPlayersMarkers(@Nonnull World world) {
        if (world == null || !ExplorationEventListener.isTrackedWorld(world)) {
            return;
        }

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;
            
            Player p = ref.getStore().getComponent(ref, Player.getComponentType());
            if (p == null) continue;

            invalidatePlayerCache(p, world);
            refreshPlayerMarkers(p);
        }
    }

    private static boolean removeGlobalMarker(@Nonnull String markerId, @Nonnull String worldName, @Nonnull Player player) {
        if (persistence == null) {
            return false;
        }
        List<StoredWaypoint> existing = persistence.loadGlobal();
        List<StoredWaypoint> updated = new ArrayList<>();
        boolean found = false;
        for (StoredWaypoint waypoint : existing) {
            if (waypoint.id != null && waypoint.id.equals(markerId)) {
                found = true;
                continue;
            }
            updated.add(waypoint);
        }
        if (found) {
            persistence.saveGlobal(updated);
            World world = Universe.get().getWorld(worldName);
            if (world != null) {
                world.execute(() -> refreshAllPlayersMarkers(world));
            }
        }
        return found;
    }

    private static boolean updateGlobalMarker(@Nonnull String markerId, @Nullable String newName, @Nullable String newIcon, @Nullable Transform newTransform, @Nonnull String worldName, @Nonnull Player actor) {
        if (persistence == null) {
            return false;
        }
        List<StoredWaypoint> existing = persistence.loadGlobal();
        boolean found = false;
        List<StoredWaypoint> rebuilt = new ArrayList<>();
        for (StoredWaypoint waypoint : existing) {
            if (waypoint.id != null && waypoint.id.equals(markerId)) {
                found = true;
                String iconToUse = newIcon != null ? normalizeIcon(newIcon) : waypoint.icon;
                
                String newId = waypoint.id;
                if (iconToUse != null && !iconToUse.equals(waypoint.icon)) {
                    newId = GLOBAL_ID_PREFIX + UUID.randomUUID();
                }

                double x = waypoint.x;
                double y = waypoint.y;
                double z = waypoint.z;
                if (newTransform != null && newTransform.position != null) {
                    x = newTransform.position.x;
                    y = newTransform.position.y;
                    z = newTransform.position.z;
                }
                StoredWaypoint updated = new StoredWaypoint(
                    newId,
                    newName != null ? newName : waypoint.name,
                    iconToUse,
                    x,
                    y,
                    z,
                    worldName,
                    true,
                    waypoint.ownerUuid,
                    waypoint.ownerName
                );
                rebuilt.add(updated);
            } else {
                rebuilt.add(waypoint);
            }
        }
        if (found) {
            persistence.saveGlobal(rebuilt);
            World world = Universe.get().getWorld(worldName);
            if (world != null) {
                world.execute(() -> refreshAllPlayersMarkers(world));
            }
        }
        return found;
    }

    private static MapMarker toMarker(@Nonnull StoredWaypoint waypoint, @Nonnull Player player) {
        Transform transform = PositionUtil.toTransformPacket(new com.hypixel.hytale.math.vector.Transform(waypoint.x, waypoint.y, waypoint.z));
        ContextMenuItem[] menu = buildContextMenu(player, waypoint.id);
        return new MapMarker(waypoint.id, waypoint.name, normalizeIcon(waypoint.icon), transform, menu);
    }

    private static StoredWaypoint fromMarker(@Nonnull MapMarker marker, @Nonnull String worldName, @Nonnull String ownerName, @Nonnull UUID ownerUuid, boolean shared) {
        if (marker.transform == null || marker.transform.position == null) {
            return null;
        }
        return new StoredWaypoint(
            marker.id,
            marker.name != null ? marker.name : "Waypoint",
            normalizeIcon(marker.markerImage),
            marker.transform.position.x,
            marker.transform.position.y,
            marker.transform.position.z,
            worldName,
            shared,
            ownerUuid.toString(),
            ownerName
        );
    }

    private static String normalizeIcon(@Nullable String icon) {
        if (icon == null || icon.isEmpty()) {
            return "Coordinate.png";
        }
        if (icon.endsWith(".png")) {
            return icon;
        }
        return icon + ".png";
    }

    public static boolean isGlobalId(@Nonnull String id) {
        return id.startsWith(GLOBAL_ID_PREFIX);
    }

    private static String stripColorTags(String input) {
        if (input == null) return "";
        return input.replaceAll("<[^>]*>", "");
    }

    public static boolean isTrackedWorld(@Nullable World world) {
        return ExplorationEventListener.isTrackedWorld(world);
    }

    private static String cacheKey(@Nonnull UUID uuid, @Nonnull String worldName) {
        return uuid + "|" + worldName.toLowerCase(Locale.ROOT);
    }

    private static void invalidatePlayerCache(@Nonnull Player player, @Nonnull World world) {
        UUID uuid = ((CommandSender) player).getUuid();
        loadedPlayers.remove(cacheKey(uuid, world.getName()));
    }

    /**
     * Sends a full marker update to the client using the UpdateWorldMap packet.
     * Removes markers that no longer exist and adds/updates the current markers.
     */
    private static void sendMarkersToClient(@Nonnull Player player, @Nonnull MapMarker[] markers, @Nonnull List<String> oldMarkerIds) {
        World world = player.getWorld();
        if (world == null) return;
        
        Runnable sendTask = () -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }

                Set<String> newMarkerIds = new java.util.HashSet<>();
                for (MapMarker m : markers) {
                    if (m != null && m.id != null) {
                        newMarkerIds.add(m.id);
                    }
                }

                List<String> idsToRemove = new ArrayList<>();
                for (String oldId : oldMarkerIds) {
                    if (!newMarkerIds.contains(oldId)) {
                        idsToRemove.add(oldId);
                    }
                }

                UpdateWorldMap packet = new UpdateWorldMap(
                    null,
                    markers,
                    idsToRemove.isEmpty() ? null : idsToRemove.toArray(new String[0])
                );
                playerRef.getPacketHandler().write(packet);
            } catch (Exception e) {
                LOGGER.warning("Failed to send markers to client for " + player.getDisplayName() + ": " + e.getMessage());
            }
        };
        
        if (world.isInThread()) {
            sendTask.run();
        } else {
            world.execute(sendTask);
        }
    }

    private static final class WaypointPersistence {
        private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        private final Path dataRoot;
        private final Path globalFile;

        WaypointPersistence(@Nonnull Path baseDir) {
            this.dataRoot = baseDir.resolve("data");
            this.globalFile = this.dataRoot.resolve("global-pings.json");
        }

        List<StoredWaypoint> loadPlayer(@Nonnull UUID playerUuid, @Nonnull String playerName, @Nonnull String worldName) {
            try {
                Path dir = dataRoot.resolve(worldName);
                if (!Files.exists(dir)) {
                    return Collections.emptyList();
                }
                Path file = dir.resolve(playerUuid + "-pings.json");
                if (!Files.exists(file)) {
                    return Collections.emptyList();
                }
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    PlayerWaypointFile data = gson.fromJson(reader, PlayerWaypointFile.class);
                    if (data == null || data.waypoints == null) {
                        return Collections.emptyList();
                    }
                    return new ArrayList<>(Arrays.asList(data.waypoints));
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to load waypoints for " + playerName + ": " + e.getMessage());
                return Collections.emptyList();
            }
        }

        void savePlayer(@Nonnull UUID playerUuid, @Nonnull String playerName, @Nonnull String worldName, @Nonnull List<StoredWaypoint> waypoints) {
            try {
                Path dir = dataRoot.resolve(worldName);
                Files.createDirectories(dir);
                Path file = dir.resolve(playerUuid + "-pings.json");
                PlayerWaypointFile data = new PlayerWaypointFile(playerUuid.toString(), playerName, waypoints.toArray(new StoredWaypoint[0]));
                try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                    gson.toJson(data, writer);
                }
            } catch (IOException e) {
                LOGGER.warning("Failed to save waypoints for " + playerName + ": " + e.getMessage());
            }
        }

        List<StoredWaypoint> loadGlobal() {
            try {
                if (!Files.exists(globalFile)) {
                    return new ArrayList<>();
                }
                try (BufferedReader reader = Files.newBufferedReader(globalFile)) {
                    GlobalWaypointFile data = gson.fromJson(reader, GlobalWaypointFile.class);
                    if (data == null || data.waypoints == null) {
                        return new ArrayList<>();
                    }
                    return new ArrayList<>(Arrays.asList(data.waypoints));
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to load global waypoints: " + e.getMessage());
                return new ArrayList<>();
            }
        }

        void saveGlobal(@Nonnull List<StoredWaypoint> waypoints) {
            try {
                Files.createDirectories(dataRoot);
                GlobalWaypointFile data = new GlobalWaypointFile(waypoints.toArray(new StoredWaypoint[0]));
                try (BufferedWriter writer = Files.newBufferedWriter(globalFile)) {
                    gson.toJson(data, writer);
                }
            } catch (IOException e) {
                LOGGER.warning("Failed to save global waypoints: " + e.getMessage());
            }
        }
    }

    private static final class StoredWaypoint {
        @SerializedName("Id")
        private final String id;

        @SerializedName("Name")
        private final String name;

        @SerializedName("Icon")
        private final String icon;

        @SerializedName("X")
        private final double x;

        @SerializedName("Y")
        private final double y;

        @SerializedName("Z")
        private final double z;

        @SerializedName("World")
        private final String world;

        @SerializedName("Shared")
        private final boolean shared;

        @SerializedName("OwnerUuid")
        private final String ownerUuid;

        @SerializedName("OwnerName")
        private final String ownerName;

        StoredWaypoint(String id, String name, String icon, double x, double y, double z, String world, boolean shared, String ownerUuid, String ownerName) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
            this.shared = shared;
            this.ownerUuid = ownerUuid != null ? ownerUuid.toString() : null;
            this.ownerName = ownerName;
        }
    }

    private static final class PlayerWaypointFile {
        @SerializedName("PlayerUuid")
        private final String playerUuid;
        @SerializedName("PlayerName")
        private final String playerName;
        @SerializedName("Waypoints")
        private final StoredWaypoint[] waypoints;

        PlayerWaypointFile(String playerUuid, String playerName, StoredWaypoint[] waypoints) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.waypoints = waypoints;
        }
    }

    private static final class GlobalWaypointFile {
        @SerializedName("Waypoints")
        private final StoredWaypoint[] waypoints;

        GlobalWaypointFile(StoredWaypoint[] waypoints) {
            this.waypoints = waypoints;
        }
    }
}

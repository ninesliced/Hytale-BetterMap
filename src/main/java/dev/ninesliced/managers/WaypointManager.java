package dev.ninesliced.managers;

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
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarkersStore;

import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.listeners.ExplorationListener;
import dev.ninesliced.utils.ReflectionHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Manages user map markers through Hytale's built-in UserMapMarkersStore system.
 * BetterMap uses this for CRUD operations on markers; Hytale's PersonalMarkersProvider
 * and SharedMarkersProvider handle the actual rendering on the map.
 */
public class WaypointManager {
    private static final Logger LOGGER = Logger.getLogger(WaypointManager.class.getName());
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final String PERSONAL_ID_PREFIX = "user_personal_";
    private static final String SHARED_ID_PREFIX = "user_shared_";

    private WaypointManager() {
    }

    /**
     * Kept for backwards compatibility with the old initialization flow.
     * No-op now that we rely on Hytale's built-in marker storage.
     */
    public static void initialize(@Nonnull Path configDir) {
        WaypointYPersistence.getInstance().initialize(configDir);
    }

    /**
     * Gets all user markers (personal and shared) for a player.
     */
    @Nonnull
    public static List<UserMapMarker> getUserMarkers(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) {
            return List.of();
        }

        List<UserMapMarker> result = new ArrayList<>();

        UserMapMarkersStore personalStore = resolveStore(world, player, false);
        if (personalStore != null) {
            result.addAll(personalStore.getUserMapMarkers());
        }

        UserMapMarkersStore sharedStore = resolveStore(world, player, true);
        if (sharedStore != null) {
            result.addAll(sharedStore.getUserMapMarkers());
        }

        return result;
    }

    /**
     * Creates a new marker with the given parameters.
     */
    public static void addMarker(@Nonnull Player player,
                                 @Nonnull String name,
                                 @Nonnull String icon,
                                 float x,
                                 float z,
                                 @Nullable Color tint,
                                 boolean shared) {
        addMarker(player, name, icon, x, z, null, tint, shared);
    }

    /**
     * Creates a new marker with the given parameters and optional Y coordinate.
     */
    public static void addMarker(@Nonnull Player player,
                                 @Nonnull String name,
                                 @Nonnull String icon,
                                 float x,
                                 float z,
                                 @Nullable Double y,
                                 @Nullable Color tint,
                                 boolean shared) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return;

        UserMapMarkersStore store = resolveStore(world, player, shared);
        if (store == null) return;

        UserMapMarker marker = new UserMapMarker();
        marker.setId((shared ? SHARED_ID_PREFIX : PERSONAL_ID_PREFIX) + UUID.randomUUID());
        marker.setName(name);
        marker.setIcon(normalizeIcon(icon));
        marker.setPosition(x, z);
        marker.setColorTint(tint != null ? tint : new Color((byte) 0, (byte) 0, (byte) 0));
        marker.withCreatedByName(player.getDisplayName());
        UUID ownerUuid = ((CommandSender) player).getUuid();
        marker.withCreatedByUuid(ownerUuid);

        store.addUserMapMarker(marker);

        double markerY = y != null ? y : getCurrentPlayerY(player, 100.0);
        if (shared) {
            WaypointYPersistence.getInstance().setGlobalY(world.getName(), marker.getId(), markerY);
        } else if (ownerUuid != null) {
            WaypointYPersistence.getInstance().setPersonalY(world.getName(), ownerUuid, marker.getId(), markerY);
        }

        MapAnchorManager.getInstance().refreshAnchor(player);
    }

    /**
     * Removes a marker by ID or name.
     */
    public static boolean removeMarker(@Nonnull Player player, @Nonnull String idOrName) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return false;

        MarkerEntry entry = findMarkerEntry(player, world, idOrName);
        if (entry == null) {
            return false;
        }

        entry.store.removeUserMapMarker(entry.marker.getId());
        UUID ownerUuid = resolveOwnerUuid(player, entry.marker);
        if (entry.shared) {
            WaypointYPersistence.getInstance().removeGlobal(world.getName(), entry.marker.getId());
        } else if (ownerUuid != null) {
            WaypointYPersistence.getInstance().removePersonal(world.getName(), ownerUuid, entry.marker.getId());
        }

        MapAnchorManager.getInstance().refreshAnchor(player);

        return true;
    }

    /**
     * Updates a marker's properties.
     * When position changes, the marker is deleted and recreated with a new ID
     * to ensure both map and compass update correctly.
     */
    public static boolean updateMarker(@Nonnull Player player,
                                       @Nonnull String id,
                                       @Nullable String newName,
                                       @Nullable String newIcon,
                                       @Nullable Float newX,
                                       @Nullable Float newZ,
                                       @Nullable Color newTint) {
        return updateMarker(player, id, newName, newIcon, newX, newZ, null, newTint);
    }

    public static boolean updateMarker(@Nonnull Player player,
                                       @Nonnull String id,
                                       @Nullable String newName,
                                       @Nullable String newIcon,
                                       @Nullable Float newX,
                                       @Nullable Float newZ,
                                       @Nullable Double newY,
                                       @Nullable Color newTint) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return false;

        MarkerEntry entry = findMarkerEntry(player, world, id);
        if (entry == null) {
            return false;
        }

        UserMapMarker existing = entry.marker;

        boolean positionChanging = newX != null && newZ != null &&
            (Math.abs(existing.getX() - newX) > 0.01f || Math.abs(existing.getZ() - newZ) > 0.01f);

        if (positionChanging) {
            String finalName = (newName != null && !newName.trim().isEmpty()) ? newName.trim() : existing.getName();
            String finalIcon = (newIcon != null && !newIcon.trim().isEmpty()) ? normalizeIcon(newIcon.trim()) : existing.getIcon();
            Color finalTint = newTint != null ? newTint : existing.getColorTint();
            UUID ownerUuid = resolveOwnerUuid(player, existing);
            Double existingY = getMarkerY(world, player, id);

            entry.store.removeUserMapMarker(id);
            if (entry.shared) {
                WaypointYPersistence.getInstance().removeGlobal(world.getName(), id);
            } else if (ownerUuid != null) {
                WaypointYPersistence.getInstance().removePersonal(world.getName(), ownerUuid, id);
            }

            UserMapMarker newMarker = new UserMapMarker();
            newMarker.setId((entry.shared ? SHARED_ID_PREFIX : PERSONAL_ID_PREFIX) + UUID.randomUUID());
            newMarker.setName(finalName);
            newMarker.setIcon(finalIcon);
            newMarker.setPosition(newX, newZ);
            newMarker.setColorTint(finalTint);
            newMarker.withCreatedByName(existing.getCreatedByName());
            newMarker.withCreatedByUuid(existing.getCreatedByUuid());

            entry.store.addUserMapMarker(newMarker);

            double markerY = newY != null ? newY : (existingY != null ? existingY : getCurrentPlayerY(player, 100.0));
            if (entry.shared) {
                WaypointYPersistence.getInstance().setGlobalY(world.getName(), newMarker.getId(), markerY);
            } else if (ownerUuid != null) {
                WaypointYPersistence.getInstance().setPersonalY(world.getName(), ownerUuid, newMarker.getId(), markerY);
            }

            MapAnchorManager.getInstance().refreshAnchor(player);

            return true;
        }

        List<UserMapMarker> markers = new ArrayList<>(entry.store.getUserMapMarkers());
        boolean updated = false;
        for (int i = 0; i < markers.size(); i++) {
            UserMapMarker m = markers.get(i);
            if (!id.equals(m.getId())) continue;

            if (newName != null && !newName.trim().isEmpty()) {
                m.setName(newName.trim());
            }
            if (newIcon != null && !newIcon.trim().isEmpty()) {
                m.setIcon(normalizeIcon(newIcon.trim()));
            }
            if (newTint != null) {
                m.setColorTint(newTint);
            }

            updated = true;
            break;
        }

        if (updated) {
            entry.store.setUserMapMarkers(markers);

            if (newY != null) {
                UUID ownerUuid = resolveOwnerUuid(player, existing);
                if (entry.shared) {
                    WaypointYPersistence.getInstance().setGlobalY(world.getName(), id, newY);
                } else if (ownerUuid != null) {
                    WaypointYPersistence.getInstance().setPersonalY(world.getName(), ownerUuid, id, newY);
                }
            }

            if (entry.shared) {
                forceRemoveAndResyncMarkerForAllClients(world, id);
            } else {
                forceRemoveAndResyncMarker(player, id);
            }

            MapAnchorManager.getInstance().refreshAnchor(player);
        }
        return updated;
    }

    /**
     * Forces a marker to be removed from client and server caches, then immediately re-synced.
     * This is the key to updating both map AND compass - we fully remove the old marker,
     * then the provider will re-add it fresh on the next tick.
     */
    private static void forceRemoveAndResyncMarker(@Nonnull Player player, @Nonnull String markerId) {
        try {
            var tracker = player.getWorldMapTracker();
            if (tracker == null) return;

            Object markerTrackerObj = ReflectionHelper.getFieldValueRecursive(tracker, "markerTracker");
            if (!(markerTrackerObj instanceof MapMarkerTracker markerTracker)) return;

            var sentMarkers = markerTracker.getSentMarkers();
            if (sentMarkers != null) {
                sentMarkers.remove(markerId);
            }

            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            playerRef.getPacketHandler().writeNoCache(new UpdateWorldMap(
                null,
                null,
                new String[]{markerId}
            ));

            ReflectionHelper.setFieldValueRecursive(markerTracker, "smallMovementsTimer", 0.0f);
        } catch (Exception e) {
            LOGGER.warning("Failed to force marker resync: " + e.getMessage());
        }
    }

    /**
     * Forces a shared marker to be removed and re-synced for ALL clients in the world.
     */
    private static void forceRemoveAndResyncMarkerForAllClients(@Nonnull World world, @Nonnull String markerId) {
        try {
            for (PlayerRef worldPlayer : world.getPlayerRefs()) {
                Holder<EntityStore> holder = worldPlayer.getHolder();
                if (holder == null) continue;
                Player player = holder.getComponent(Player.getComponentType());
                if (player == null) continue;
                forceRemoveAndResyncMarker(player, markerId);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to force marker resync for all clients: " + e.getMessage());
        }
    }

    /**
     * Gets a marker by ID.
     */
    @Nullable
    public static UserMapMarker getMarker(@Nonnull Player player, @Nonnull String id) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return null;

        MarkerEntry entry = findMarkerEntry(player, world, id);
        return entry != null ? entry.marker : null;
    }

    /**
     * Finds a marker by name or ID.
     */
    @Nullable
    public static UserMapMarker findMarker(@Nonnull Player player, @Nonnull String nameOrId) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return null;

        MarkerEntry entry = findMarkerEntry(player, world, nameOrId);
        return entry != null ? entry.marker : null;
    }

    /**
     * Checks if marker ID is a shared marker.
     */
    public static boolean isSharedId(@Nonnull String id) {
        return id.startsWith(SHARED_ID_PREFIX);
    }

    @Nullable
    public static Double getMarkerY(@Nonnull World world, @Nonnull Player player, @Nonnull String markerId) {
        if (isSharedId(markerId)) {
            return WaypointYPersistence.getInstance().getGlobalY(world.getName(), markerId);
        }

        UUID playerUuid = ((CommandSender) player).getUuid();
        if (playerUuid == null) {
            return null;
        }
        return WaypointYPersistence.getInstance().getPersonalY(world.getName(), playerUuid, markerId);
    }

    public static double getMarkerYOrDefault(@Nonnull World world, @Nonnull Player player, @Nonnull String markerId, double fallback) {
        Double y = getMarkerY(world, player, markerId);
        if (y != null) {
            return y;
        }

        MarkerEntry entry = findMarkerEntry(player, world, markerId);
        if (entry == null || entry.marker == null) {
            return fallback;
        }

        double resolvedY = resolveHighestBlockY(world, entry.marker.getX(), entry.marker.getZ(), fallback);

        UUID ownerUuid = resolveOwnerUuid(player, entry.marker);
        if (entry.shared) {
            WaypointYPersistence.getInstance().setGlobalY(world.getName(), markerId, resolvedY);
        } else if (ownerUuid != null) {
            WaypointYPersistence.getInstance().setPersonalY(world.getName(), ownerUuid, markerId, resolvedY);
        }

        return resolvedY;
    }

    @Nullable
    private static UUID resolveOwnerUuid(@Nonnull Player actingPlayer, @Nonnull UserMapMarker marker) {
        UUID owner = marker.getCreatedByUuid();
        if (owner != null) {
            return owner;
        }
        return ((CommandSender) actingPlayer).getUuid();
    }

    private static double getCurrentPlayerY(@Nonnull Player player, double fallback) {
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                TransformComponent transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    return transform.getPosition().y;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static double resolveHighestBlockY(@Nonnull World world, float x, float z, double fallback) {
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk chunk = world.getChunk(chunkIndex);
            if (chunk == null) {
                return fallback;
            }

            int blockX = MathUtil.floor(x);
            int blockZ = MathUtil.floor(z);
            int localX = blockX & 31;
            int localZ = blockZ & 31;
            short surfaceHeight = chunk.getHeight(localX, localZ);
            return surfaceHeight + 1.0;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static UserMapMarkersStore resolveStore(@Nonnull World world, @Nonnull Player player, boolean shared) {
        if (shared) {
            return world.getChunkStore().getStore().getResource(WorldMarkersResource.getResourceType());
        }
        return player.getPlayerConfigData().getPerWorldData(world.getName());
    }

    @Nullable
    private static MarkerEntry findMarkerEntry(@Nonnull Player player, @Nonnull World world, @Nonnull String nameOrId) {
        UserMapMarkersStore personal = resolveStore(world, player, false);
        MarkerEntry personalEntry = locateInStore(personal, nameOrId, false);
        if (personalEntry != null) {
            return personalEntry;
        }
        UserMapMarkersStore shared = resolveStore(world, player, true);
        return locateInStore(shared, nameOrId, true);
    }

    @Nullable
    private static MarkerEntry locateInStore(@Nullable UserMapMarkersStore store, @Nonnull String nameOrId, boolean shared) {
        if (store == null) return null;
        for (UserMapMarker marker : store.getUserMapMarkers()) {
            if (marker == null || marker.getId() == null) continue;
            String markerName = marker.getName();
            boolean matchId = marker.getId().equalsIgnoreCase(nameOrId);
            boolean matchName = markerName != null && markerName.equalsIgnoreCase(nameOrId);
            if (matchId || matchName) {
                return new MarkerEntry(marker, store, shared);
            }
        }
        return null;
    }

    public static boolean isTrackedWorld(@Nullable World world) {
        return ExplorationListener.isTrackedWorld(world);
    }

    private static String normalizeIcon(@Nullable String icon) {
        if (icon == null || icon.isEmpty()) {
            return "UserA.png";
        }
        if (icon.endsWith(".png")) {
            return icon;
        }
        return icon + ".png";
    }

    /**
     * Called when a player joins. No-op now since Hytale handles marker sync.
     */
    public static void onPlayerJoin(@Nonnull Player player) {
    }

    private record MarkerEntry(UserMapMarker marker, UserMapMarkersStore store, boolean shared) {
    }
}

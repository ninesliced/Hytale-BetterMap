package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.PlacedByMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.ReflectionHelper;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Custom marker provider that adds "Edit" context menu option to user map markers.
 * This replaces the built-in PersonalMarkersProvider and SharedMarkersProvider
 * to add BetterMap-specific context menu options.
 */
public class UserMarkerContextMenuProvider implements WorldMapManager.MarkerProvider {
    
    public static final UserMarkerContextMenuProvider INSTANCE = new UserMarkerContextMenuProvider();
    private static final Logger LOGGER = Logger.getLogger(UserMarkerContextMenuProvider.class.getName());
    private static final Map<UUID, Boolean> TELEPORT_MENU_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SHARED_EDIT_MENU_STATE = new ConcurrentHashMap<>();
    
    private UserMarkerContextMenuProvider() {
    }
    
    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        boolean allowWaypointTeleport = ModConfig.getInstance().isAllowWaypointTeleports();
        boolean hasTeleportPermission = PermissionsUtil.canTeleport(player);
        boolean showTeleport = allowWaypointTeleport || hasTeleportPermission;
        boolean canEditAnyShared = PermissionsUtil.canEditSharedWaypointByPermission(player)
            || ModConfig.getInstance().isAllowGlobalWaypointEditsForEveryone();

        UUID playerId = ((CommandSender) player).getUuid();
        PlayerConfig playerConfig = playerId != null ? PlayerConfigManager.getInstance().getPlayerConfig(playerId) : null;

        boolean hidePersonalWaypoints = playerConfig != null && playerConfig.isHidePersonalWaypointsOnMap();

        boolean hideGlobalWaypoints = ModConfig.getInstance().isHideGlobalWaypointsOnMap();
        if (playerConfig != null) {
            if (playerConfig.isOverrideGlobalWaypointHide()) {
                hideGlobalWaypoints = playerConfig.isHideGlobalWaypointsOnMap();
            } else if (playerConfig.isHideGlobalWaypointsOnMap()) {
                hideGlobalWaypoints = true;
            }
        }

        if (playerId != null) {
            Boolean previous = TELEPORT_MENU_STATE.put(playerId, showTeleport);
            if (previous != null && previous != showTeleport) {
                scheduleResyncAllMarkers(world, player);
            }

            Boolean previousSharedEdit = SHARED_EDIT_MENU_STATE.put(playerId, canEditAnyShared);
            if (previousSharedEdit != null && previousSharedEdit != canEditAnyShared) {
                WorldMapHook.sendMapSettingsToPlayer(player);
                scheduleResyncAllMarkers(world, player);
            }
        }

        if (!hidePersonalWaypoints) {
            PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
            for (UserMapMarker marker : perWorldData.getUserMapMarkers()) {
                collector.add(buildMarkerWithContextMenu(world, player, marker, showTeleport));
            }
        }
        
        if (!hideGlobalWaypoints) {
            WorldMarkersResource worldMarkersResource = world.getChunkStore().getStore().getResource(WorldMarkersResource.getResourceType());
            for (UserMapMarker marker : worldMarkersResource.getUserMapMarkers()) {
                collector.add(buildMarkerWithContextMenu(world, player, marker, showTeleport));
            }
        }
    }
    
    /**
     * Builds a MapMarker with context menu options (Edit).
     */
    private MapMarker buildMarkerWithContextMenu(@Nonnull World world, @Nonnull Player player, @Nonnull UserMapMarker marker, boolean showTeleport) {
        double markerY = WaypointManager.getMarkerYOrDefault(world, player, marker.getId(), 100.0);
        MapMarkerBuilder builder = new MapMarkerBuilder(
            marker.getId(),
            marker.getIcon(),
            new Transform(marker.getX(), markerY, marker.getZ())
        );
        
        if (marker.getName() != null) {
            builder.withCustomName(marker.getName());
        }
        
        if (marker.getColorTint() != null) {
            builder.withComponent(new TintComponent(marker.getColorTint()));
        }
        
        if (marker.getCreatedByName() != null) {
            builder.withComponent(new PlacedByMarkerComponent(
                Message.raw(marker.getCreatedByName()).getFormattedMessage(),
                marker.getCreatedByUuid()
            ));
        }
        
        if (showTeleport) {
            builder.withContextMenuItem(new ContextMenuItem("Teleport", "bettermap waypoint teleport " + marker.getId()));
        }

        String markerId = marker.getId();
        boolean isShared = markerId != null && markerId.startsWith("user_shared_");
        boolean canEditShared = PermissionsUtil.canEditSharedWaypoint(player, marker);
        boolean isOwner = marker.getCreatedByUuid() != null && marker.getCreatedByUuid().equals(player.getUuid());

        if (!isShared || canEditShared) {
            builder.withContextMenuItem(new ContextMenuItem("Edit", "bettermap waypoint edit " + marker.getId()));
        }

        if ((isShared && canEditShared) && !isOwner) {
            builder.withContextMenuItem(new ContextMenuItem("Remove Marker", "bettermap waypoint delete " + marker.getId()));
        }
        
        return builder.build();
    }

    private void forceResyncAllMarkers(@Nonnull Player player) {
        try {
            var tracker = player.getWorldMapTracker();
            if (tracker == null) return;

            Object markerTrackerObj = ReflectionHelper.getFieldValueRecursive(tracker, "markerTracker");
            if (!(markerTrackerObj instanceof MapMarkerTracker markerTracker)) return;

            var sentMarkers = markerTracker.getSentMarkers();
            if (sentMarkers == null || sentMarkers.isEmpty()) return;

            String[] ids = sentMarkers.keySet().toArray(String[]::new);
            sentMarkers.clear();

            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            playerRef.getPacketHandler().writeNoCache(new UpdateWorldMap(
                null,
                null,
                ids
            ));

            ReflectionHelper.setFieldValueRecursive(markerTracker, "smallMovementsTimer", 0.0f);
        } catch (Exception e) {
            LOGGER.warning("Failed to refresh marker context menu: " + e.getMessage());
        }
    }

    private void scheduleResyncAllMarkers(@Nonnull World world, @Nonnull Player player) {
        try {
            if (!world.isAlive()) {
                return;
            }
            world.execute(() -> forceResyncAllMarkers(player));
        } catch (Exception e) {
            LOGGER.warning("Failed to schedule marker context menu refresh: " + e.getMessage());
        }
    }
}

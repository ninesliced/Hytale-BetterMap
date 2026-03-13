package dev.ninesliced.utils;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.gameplay.worldmap.UserMapMarkerConfig;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.configs.ModConfig;

public final class PermissionsUtil {
    private static final String ADMIN_PERMISSION = "bettermap.admin";
    private static final String ADMIN_COMMAND_PERMISSION = "bettermap.command.admin";
    private static final String TELEPORT_PERMISSION = "bettermap.command.teleport";
    private static final String TELEPORT_WAYPOINT_PERMISSION = "bettermap.command.teleport.waypoint";
    private static final String TELEPORT_MARKER_PERMISSION = "bettermap.command.teleport.marker";
    private static final String TELEPORT_MARKER_POI_PERMISSION = "bettermap.command.teleport.marker.poi";
    private static final String TELEPORT_MARKER_WARP_PERMISSION = "bettermap.command.teleport.marker.warp";
    private static final String TELEPORT_MARKER_DEATH_PERMISSION = "bettermap.command.teleport.marker.death";
    private static final String TELEPORT_MARKER_SPAWN_PERMISSION = "bettermap.command.teleport.marker.spawn";
    private static final String TELEPORT_MARKER_PLAYER_PERMISSION = "bettermap.command.teleport.marker.player";
    private static final String TELEPORT_COORDINATE_PERMISSION = "bettermap.command.teleport.coordinate";
    private static final String GLOBAL_WAYPOINT_PERMISSION = "bettermap.command.waypoint.global";
    private static final String EDIT_GLOBAL_WAYPOINT_PERMISSION = "bettermap.command.waypoint.editglobal";
    private static final String OVERRIDE_PLAYERS_PERMISSION = "bettermap.command.override.players";
    private static final String OVERRIDE_WARPS_PERMISSION = "bettermap.command.override.warps";
    private static final String OVERRIDE_UNEXPLORED_WARPS_PERMISSION = "bettermap.command.override.unexploredwarps";
    private static final String OVERRIDE_POI_PERMISSION = "bettermap.command.override.poi";
    private static final String OVERRIDE_UNEXPLORED_POI_PERMISSION = "bettermap.command.override.unexploredpoi";
    private static final String OVERRIDE_SPAWN_PERMISSION = "bettermap.command.override.spawn";
    private static final String OVERRIDE_DEATH_PERMISSION = "bettermap.command.override.death";
    private static final String OVERRIDE_WAYPOINTS_PERMISSION = "bettermap.command.override.waypoints";
    private static final String CONFIG_PERMISSION = "bettermap.command.config";
    private static final String CREATE_MARKER_PERMISSION = "bettermap.command.createmarker";

    private PermissionsUtil() {
    }

    public static boolean isAdmin(@Nonnull Player player) {
        UUID uuid = ((CommandSender) player).getUuid();
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }
        return perms.hasPermission(uuid, ADMIN_PERMISSION) || perms.hasPermission(uuid, ADMIN_COMMAND_PERMISSION);
    }

    public static boolean canTeleport(@Nonnull Player player) {
        return canTeleportToWaypoints(player)
            || canTeleportToMarkers(player)
            || canTeleportToCoordinates(player);
    }

    public static boolean canTeleportToWaypoints(@Nonnull Player player) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, TELEPORT_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_WAYPOINT_PERMISSION);
    }

    public static boolean canTeleportToMarkers(@Nonnull Player player) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, TELEPORT_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_MARKER_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_MARKER_POI_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_MARKER_WARP_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_MARKER_DEATH_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_MARKER_SPAWN_PERMISSION);
    }

    public static boolean canTeleportToMarkerType(@Nonnull Player player, @Nonnull MarkerType type) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        if (perms.hasPermission(uuid, TELEPORT_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_MARKER_PERMISSION)) {
            return true;
        }

        return switch (type) {
            case POI -> perms.hasPermission(uuid, TELEPORT_MARKER_POI_PERMISSION);
            case WARP -> perms.hasPermission(uuid, TELEPORT_MARKER_WARP_PERMISSION);
            case DEATH -> perms.hasPermission(uuid, TELEPORT_MARKER_DEATH_PERMISSION);
            case SPAWN -> perms.hasPermission(uuid, TELEPORT_MARKER_SPAWN_PERMISSION);
            case PLAYER -> perms.hasPermission(uuid, TELEPORT_MARKER_PLAYER_PERMISSION);
        };
    }

    public static boolean canTeleportToPlayers(@Nonnull Player player) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, TELEPORT_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_MARKER_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_MARKER_PLAYER_PERMISSION);
    }

    public enum MarkerType {
        POI, WARP, DEATH, SPAWN, PLAYER
    }

    public static boolean canTeleportToCoordinates(@Nonnull Player player) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, TELEPORT_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_COORDINATE_PERMISSION);
    }

    public static boolean hasNativeCreativeOpMarkerTeleport(@Nonnull Player player) {
        if (player.getGameMode() != GameMode.Creative) {
            return false;
        }

        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        return groups != null && groups.contains("OP");
    }

    /**
     * Checks if shared waypoints are enabled for BetterMap.
     *
     * This intentionally ignores Hytale's native marker-creation setting because
     * BetterMap can manage waypoint creation independently of the default map UI.
     */
    public static boolean canUseGlobalWaypoints(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null) {
            return false;
        }

        try {
            int maxShared = ModConfig.getInstance().getMaxSharedMarkersPerPlayer();
            return maxShared != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether a player may edit/delete a shared waypoint marker.
     *
     * Rules:
     * - Marker creator can always edit/delete their own shared marker.
     * - Players with {@code bettermap.command.waypoint.editglobal} can edit/delete any shared marker.
     * - If enabled in config, everyone can edit/delete shared markers.
     */
    public static boolean canEditSharedWaypoint(@Nonnull Player player, @Nonnull UserMapMarker marker) {
        if (!isSharedWaypointMarker(marker)) {
            return true;
        }

        UUID playerUuid = ((CommandSender) player).getUuid();
        UUID creatorUuid = marker.getCreatedByUuid();
        if (creatorUuid != null && creatorUuid.equals(playerUuid)) {
            return true;
        }

        if (hasPermission(player, EDIT_GLOBAL_WAYPOINT_PERMISSION)) {
            return true;
        }

        return ModConfig.getInstance().isAllowGlobalWaypointEditsForEveryone();
    }

    public static boolean canEditSharedWaypointByPermission(@Nonnull Player player) {
        return hasPermission(player, EDIT_GLOBAL_WAYPOINT_PERMISSION);
    }

    public static boolean canCreateMapMarkers(@Nonnull Player player) {
        if (ModConfig.getInstance().isAllowNativeMapMarkerCreation()) {
            return true;
        }
        return isAdmin(player) || hasPermission(player, CREATE_MARKER_PERMISSION);
    }

    public static boolean canAccessConfig(@Nonnull Player player) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, CONFIG_PERMISSION);
    }

    public static boolean canOverridePlayers(@Nonnull Player player) {
        return hasOverridePermission(player, OVERRIDE_PLAYERS_PERMISSION);
    }

    public static boolean canOverrideWarps(@Nonnull Player player) {
        return hasOverridePermission(player, OVERRIDE_WARPS_PERMISSION);
    }

    public static boolean canOverrideUnexploredWarps(@Nonnull Player player) {
        return hasOverridePermission(player, OVERRIDE_UNEXPLORED_WARPS_PERMISSION);
    }

    public static boolean canOverridePoi(@Nonnull Player player) {
        return hasOverridePermission(player, OVERRIDE_POI_PERMISSION);
    }

    public static boolean canOverrideUnexploredPoi(@Nonnull Player player) {
        return hasOverridePermission(player, OVERRIDE_UNEXPLORED_POI_PERMISSION);
    }

    public static boolean canOverrideSpawn(@Nonnull Player player) {
        return hasOverridePermission(player, OVERRIDE_SPAWN_PERMISSION);
    }

    public static boolean canOverrideDeath(@Nonnull Player player) {
        return hasOverridePermission(player, OVERRIDE_DEATH_PERMISSION);
    }

    public static boolean canOverrideWaypoints(@Nonnull Player player) {
        return hasOverridePermission(player, OVERRIDE_WAYPOINTS_PERMISSION);
    }

    private static boolean hasOverridePermission(@Nonnull Player player, String permission) {
        return hasPermission(player, permission);
    }

    private static boolean hasPermission(@Nonnull Player player, String permission) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }
        UUID uuid = ((CommandSender) player).getUuid();
        return perms.hasPermission(uuid, permission);
    }

    private static boolean isSharedWaypointMarker(@Nonnull UserMapMarker marker) {
        String id = marker.getId();
        return id != null && id.startsWith("user_shared_");
    }
}

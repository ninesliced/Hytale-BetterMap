package dev.ninesliced.utils;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

public final class PermissionsUtil {
    private static final String ADMIN_PERMISSION = "bettermap.admin";
    private static final String ADMIN_COMMAND_PERMISSION = "command.bettermap.admin";
    private static final String TELEPORT_PERMISSION = "dev.ninesliced.bettermap.command.teleport";
    private static final String WARP_GO_PERMISSION = "hytale.command.warp.go";
    private static final String GLOBAL_WAYPOINT_PERMISSION = "dev.ninesliced.bettermap.command.waypoint.global";
    private static final String OVERRIDE_PLAYERS_PERMISSION = "bettermap.override.players";
    private static final String OVERRIDE_WARPS_PERMISSION = "bettermap.override.warps";
    private static final String OVERRIDE_UNEXPLORED_WARPS_PERMISSION = "bettermap.override.unexploredwarps";
    private static final String OVERRIDE_POI_PERMISSION = "bettermap.override.poi";
    private static final String OVERRIDE_UNEXPLORED_POI_PERMISSION = "bettermap.override.unexploredpoi";
    private static final String OVERRIDE_SPAWN_PERMISSION = "bettermap.override.spawn";
    private static final String OVERRIDE_DEATH_PERMISSION = "bettermap.override.death";

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
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, WARP_GO_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_PERMISSION);
    }

    public static boolean canUseGlobalWaypoints(@Nonnull Player player) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, GLOBAL_WAYPOINT_PERMISSION);
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

    private static boolean hasOverridePermission(@Nonnull Player player, String permission) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        return perms.hasPermission(uuid, permission);
    }
}

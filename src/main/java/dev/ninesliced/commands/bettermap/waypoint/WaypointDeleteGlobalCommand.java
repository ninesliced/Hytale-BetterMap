package dev.ninesliced.commands.bettermap.waypoint;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;

import javax.annotation.Nonnull;

public class WaypointDeleteGlobalCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg = this.withRequiredArg("target", "Shared waypoint id", ArgTypes.STRING);

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    public WaypointDeleteGlobalCommand() {
        super("removeglobal", "Remove a global map waypoint");
        this.addAliases("deleteglobal", "delglobal");
        this.requirePermission("bettermap.command.waypoint.global");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        String target = this.targetArg.get(context);
        
        UserMapMarker marker = WaypointManager.findMarker(player, target);

        if (marker == null) {
            if (!WaypointManager.isSharedId(target)) {
                context.sendMessage(Message.raw("Could not find shared waypoint with that name or id."));
                return;
            }

            boolean canDeleteUnknownShared = PermissionsUtil.canEditSharedWaypointByPermission(player)
                || ModConfig.getInstance().isAllowGlobalWaypointEditsForEveryone();
            if (!canDeleteUnknownShared) {
                context.sendMessage(Message.raw("You do not have permission to delete shared waypoints."));
                return;
            }

            boolean deletedFallback = WaypointManager.removeMarker(player, target);
            if (deletedFallback) {
                context.sendMessage(Message.raw("Shared waypoint has been removed."));
            } else {
                context.sendMessage(Message.raw("Could not find shared waypoint with that name or id."));
            }
            return;
        }

        if (!WaypointManager.isSharedId(marker.getId())) {
            context.sendMessage(Message.raw("That is a personal waypoint. Use 'remove' instead of 'removeglobal'."));
            return;
        }

        if (!PermissionsUtil.canEditSharedWaypoint(player, marker)) {
            context.sendMessage(Message.raw("You do not have permission to delete shared waypoints."));
            return;
        }

        boolean deleted = WaypointManager.removeMarker(player, marker.getId());

        if (deleted) {
            String name = marker.getName() != null ? marker.getName() : marker.getId();
            context.sendMessage(Message.raw("Shared waypoint '" + name + "' has been removed."));
        } else {
            context.sendMessage(Message.raw("Failed to remove shared waypoint."));
        }
    }
}

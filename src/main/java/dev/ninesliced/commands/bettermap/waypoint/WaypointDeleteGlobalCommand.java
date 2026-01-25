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
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;

import javax.annotation.Nonnull;

public class WaypointDeleteGlobalCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg = this.withRequiredArg("target", "Global waypoint id", ArgTypes.STRING);

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    public WaypointDeleteGlobalCommand() {
        super("removeglobal", "Remove a global map waypoint");
        this.addAliases("deleteglobal", "delglobal");
        this.requirePermission("dev.ninesliced.bettermap.command.waypoint.global");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (!PermissionsUtil.canUseGlobalWaypoints(player)) {
            context.sendMessage(Message.raw("You do not have permission to delete global waypoints."));
            return;
        }

        String target = this.targetArg.get(context);
        
        MapMarker marker = WaypointManager.findWaypoint(player, target);

        if (marker == null) {
            if (!WaypointManager.isGlobalId(target)) {
                context.sendMessage(Message.raw("Could not find global waypoint with that name or id."));
                return;
            }
            boolean deletedFallback = WaypointManager.removeWaypoint(player, target);
            if (deletedFallback) {
                context.sendMessage(Message.raw("Global waypoint has been removed."));
            } else {
                context.sendMessage(Message.raw("Could not find global waypoint with that name or id."));
            }
            return;
        }

        if (!WaypointManager.isGlobalId(marker.id)) {
            context.sendMessage(Message.raw("That is a personal waypoint. Use 'remove' instead of 'removeglobal'."));
            return;
        }

        boolean deleted = WaypointManager.removeWaypoint(player, marker.id);

        if (deleted) {
            context.sendMessage(Message.raw("Global waypoint '" + marker.name + "' has been removed."));
        } else {
            context.sendMessage(Message.raw("Failed to remove global waypoint."));
        }
    }
}

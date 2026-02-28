package dev.ninesliced.commands.bettermap.waypoint;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;

import javax.annotation.Nonnull;

public class WaypointUpdateCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg = this.withRequiredArg("target", "Waypoint name or marker id", ArgTypes.STRING);
    private final OptionalArg<String> newNameArg = this.withOptionalArg("newName", "New name for the waypoint", ArgTypes.STRING);

    public WaypointUpdateCommand() {
        super("update", "Update a map waypoint");
        this.addAliases("rename");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        String target = this.targetArg.get(context);
        String newNameRaw = this.newNameArg.get(context);
        if (newNameRaw == null || newNameRaw.trim().isEmpty()) {
            context.sendMessage(Message.raw("You must provide a new name."));
            return;
        }

        UserMapMarker marker = WaypointManager.findMarker(player, target);
        
        if (marker != null) {
            if (WaypointManager.isSharedId(marker.getId()) && !PermissionsUtil.canEditSharedWaypoint(player, marker)) {
                context.sendMessage(Message.raw("You do not have permission to edit shared waypoints."));
                return;
            }
            Double markerY = WaypointManager.getMarkerY(world, player, marker.getId());
            WaypointManager.updateMarker(player, marker.getId(), newNameRaw, marker.getIcon(), marker.getX(), marker.getZ(), markerY, marker.getColorTint());
            String oldName = marker.getName() != null ? marker.getName() : "Unnamed";
            context.sendMessage(Message.raw("Updated waypoint: " + oldName + " -> " + newNameRaw));
        } else {
            context.sendMessage(Message.raw("Could not find waypoint with that name or id."));
        }
    }
}

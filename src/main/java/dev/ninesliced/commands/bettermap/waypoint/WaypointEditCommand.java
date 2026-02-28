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
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.ui.WaypointEditPage;
import dev.ninesliced.utils.PermissionsUtil;

import javax.annotation.Nonnull;

/**
 * Command to open the waypoint edit page for a specific marker.
 * Used by the context menu "Edit" option on map markers.
 */
public class WaypointEditCommand extends AbstractPlayerCommand {
    
    private final RequiredArg<String> idArg = this.withRequiredArg("id", "The waypoint ID to edit", ArgTypes.STRING);
    
    public WaypointEditCommand() {
        super("edit", "Edit a waypoint");
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
        
        String id = this.idArg.get(context);
        
        UserMapMarker marker = WaypointManager.getMarker(player, id);
        if (marker == null) {
            context.sendMessage(Message.raw("Waypoint not found: " + id).color("#FF4444"));
            return;
        }

        if (WaypointManager.isSharedId(marker.getId()) && !PermissionsUtil.canEditSharedWaypoint(player, marker)) {
            context.sendMessage(Message.raw("You do not have permission to edit shared waypoints.").color("#FF4444"));
            return;
        }
        
        WaypointEditPage editPage = new WaypointEditPage(playerRef, id);
        player.getPageManager().openCustomPage(ref, store, editPage);
    }
}

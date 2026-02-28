package dev.ninesliced.commands.bettermap.waypoint;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.managers.WaypointManager;
import java.util.List;
import javax.annotation.Nonnull;

public class WaypointListCommand extends AbstractPlayerCommand {

    public WaypointListCommand() {
        super("list", "List all your map waypoints");
        this.addAliases("markers");
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

        List<UserMapMarker> markers = WaypointManager.getUserMarkers(player);

        if (markers.isEmpty()) {
            context.sendMessage(Message.raw("You have no active waypoints."));
            return;
        }

        context.sendMessage(Message.raw("Active Waypoints:"));
        for (UserMapMarker marker : markers) {
            double markerY = WaypointManager.getMarkerYOrDefault(world, player, marker.getId(), 100.0);
            String positionStr = String.format("%.0f, %.0f, %.0f", marker.getX(), markerY, marker.getZ());
            String markerName = marker.getName() != null ? marker.getName() : "Unnamed";
            String sharedStatus = WaypointManager.isSharedId(marker.getId()) ? " [Shared]" : "";
            context.sendMessage(Message.raw("- " + markerName + " @ " + positionStr + sharedStatus));
        }
    }
}

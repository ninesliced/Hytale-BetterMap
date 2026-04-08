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

import javax.annotation.Nonnull;
import java.util.List;

public class WaypointIdCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> nameArg = this.withRequiredArg("name", "Waypoint name", ArgTypes.STRING);

    public WaypointIdCommand() {
        super("id", "Get the marker id for a waypoint by name");
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

        String targetName = this.nameArg.get(context);
        List<UserMapMarker> markers = WaypointManager.getUserMarkers(player);

        if (markers.isEmpty()) {
            context.sendMessage(Message.raw("You have no waypoints."));
            return;
        }

        for (UserMapMarker marker : markers) {
            if (marker == null) continue;
            String markerName = marker.getName();
            if (markerName != null && markerName.equalsIgnoreCase(targetName)) {
                context.sendMessage(Message.raw("Waypoint '" + targetName + "' id: " + marker.getId()));
                return;
            }
        }

        context.sendMessage(Message.raw("Waypoint not found: " + targetName));
    }
}

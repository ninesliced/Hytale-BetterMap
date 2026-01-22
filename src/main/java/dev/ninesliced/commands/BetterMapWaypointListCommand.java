package dev.ninesliced.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class BetterMapWaypointListCommand extends AbstractPlayerCommand {

    public BetterMapWaypointListCommand() {
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

        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] markers = perWorldData.getWorldMapMarkers();

        if (markers == null || markers.length == 0) {
            context.sendMessage(Message.raw("You have no active waypoints."));
            return;
        }

        context.sendMessage(Message.raw("Active Waypoints:"));
        for (MapMarker marker : markers) {
            String positionStr = "N/A";
            if (marker.transform != null && marker.transform.position != null) {
                double x = marker.transform.position.x;
                double y = marker.transform.position.y;
                double z = marker.transform.position.z;
                positionStr = String.format("%.0f, %.0f, %.0f", x, y, z);
            }
            context.sendMessage(Message.raw("- " + marker.name + " @ " + positionStr));
        }
    }
}

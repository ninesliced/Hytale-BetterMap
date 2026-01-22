package dev.ninesliced.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class BetterMapWaypointIdCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> nameArg = this.withRequiredArg("name", "Waypoint name", ArgTypes.STRING);

    public BetterMapWaypointIdCommand() {
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
        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] markers = perWorldData.getWorldMapMarkers();

        if (markers == null || markers.length == 0) {
            context.sendMessage(Message.raw("You have no waypoints."));
            return;
        }

        for (MapMarker marker : markers) {
            if (marker == null) continue;
            if (marker.name != null && marker.name.equalsIgnoreCase(targetName)) {
                context.sendMessage(Message.raw("Waypoint '" + targetName + "' id: " + marker.id));
                return;
            }
        }

        context.sendMessage(Message.raw("Waypoint not found: " + targetName));
    }
}

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
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.managers.WaypointManager;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public class BetterMapWaypointDeleteCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg = this.withRequiredArg("target", "Waypoint name or marker id", ArgTypes.STRING);

    public BetterMapWaypointDeleteCommand() {
        super("remove", "Remove a map waypoint by name");
        this.addAliases("delete", "del");
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
        
        MapMarker marker = WaypointManager.findWaypoint(player, target);
        if (marker != null && WaypointManager.isGlobalId(marker.id)) {
            if (!PermissionsUtil.canUseGlobalWaypoints(player)) {
                context.sendMessage(Message.raw("You do not have permission to delete global waypoints."));
                return;
            }
        }

        boolean deleted = WaypointManager.removeWaypoint(player, target);

        if (deleted) {
            context.sendMessage(Message.raw("Waypoint has been removed."));
        } else {
            context.sendMessage(Message.raw("Could not find waypoint with that name or id."));
        }
    }
}

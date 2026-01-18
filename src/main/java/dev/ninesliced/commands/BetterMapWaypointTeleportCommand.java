package dev.ninesliced.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import javax.annotation.Nonnull;

public class BetterMapWaypointTeleportCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg = this.withRequiredArg("target", "Waypoint name or marker id", ArgTypes.STRING);

    @Override
    protected String generatePermissionNode() {
        return "teleport";
    }

    public BetterMapWaypointTeleportCommand() {
        super("teleport", "Teleport to a map waypoint");
        this.addAliases("tp");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (!PermissionsUtil.canTeleport(player)) {
            context.sendMessage(Message.raw("You don't have permission to teleport to waypoints."));
            return;
        }
        if (!BetterMapConfig.getInstance().isAllowWaypointTeleports()) {
            context.sendMessage(Message.raw("Waypoint teleports are currently disabled."));
            return;
        }

        String target = this.targetArg.get(context);
        MapMarker marker = WaypointManager.findWaypoint(player, target);
        if (marker == null || marker.transform == null || marker.transform.position == null) {
            context.sendMessage(Message.raw("Could not find waypoint with that name or id."));
            return;
        }

        Vector3d destination = new Vector3d(
            marker.transform.position.x,
            marker.transform.position.y,
            marker.transform.position.z
        );
        TransformComponent transform = player.getTransformComponent();
        Vector3f currentRotation = transform != null ? transform.getRotation() : Vector3f.ZERO;
        Teleport teleport = new Teleport(destination, currentRotation);

        world.execute(() -> store.addComponent(ref, Teleport.getComponentType(), teleport));
        context.sendMessage(Message.raw("Teleported to waypoint: " + (marker.name != null ? marker.name : target)));
    }
}

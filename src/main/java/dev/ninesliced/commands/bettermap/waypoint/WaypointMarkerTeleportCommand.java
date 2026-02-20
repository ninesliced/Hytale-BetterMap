package dev.ninesliced.commands.bettermap.waypoint;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
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
import dev.ninesliced.configs.ModConfig;

import javax.annotation.Nonnull;

public class WaypointMarkerTeleportCommand extends AbstractPlayerCommand {
    private final RequiredArg<Integer> xArg = this.withRequiredArg("x", "Marker X coordinate", ArgTypes.INTEGER);
    private final RequiredArg<Integer> yArg = this.withRequiredArg("y", "Marker Y coordinate", ArgTypes.INTEGER);
    private final RequiredArg<Integer> zArg = this.withRequiredArg("z", "Marker Z coordinate", ArgTypes.INTEGER);

    public WaypointMarkerTeleportCommand() {
        super("markertp", "Teleport to a map marker position");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (!ModConfig.getInstance().isAllowWaypointTeleports()) {
            context.sendMessage(Message.raw("You don't have permission to teleport to map markers."));
            return;
        }

        int x = this.xArg.get(context);
        int y = this.yArg.get(context);
        int z = this.zArg.get(context);

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3f currentRotation = transform != null ? transform.getRotation() : Vector3f.ZERO;

        Vector3d destination = new Vector3d(x + 0.5d, y, z + 0.5d);
        Teleport teleport = new Teleport(destination, currentRotation);
        store.addComponent(ref, Teleport.getComponentType(), teleport);
    }
}
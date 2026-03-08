package dev.ninesliced.commands.bettermap.waypoint;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
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
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WorldMapHook;

public class WaypointTeleportCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg = this.withRequiredArg("target", "Waypoint name or marker id", ArgTypes.STRING);

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    public WaypointTeleportCommand() {
        super("teleport", "Teleport to a map waypoint");
        this.addAliases("tp");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (!ModConfig.getInstance().isAllowWaypointTeleports() && !PermissionsUtil.canTeleportToWaypoints(player)) {
            context.sendMessage(Message.raw("You don't have permission to teleport to waypoints."));
            return;
        }

        String target = this.targetArg.get(context);
        UserMapMarker marker = WaypointManager.findMarker(player, target);
        if (marker == null) {
            context.sendMessage(Message.raw("Could not find waypoint with that name or id."));
            return;
        }

        Double storedY = WaypointManager.getMarkerY(world, player, marker.getId());

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        double fallbackY = transform != null ? transform.getPosition().y : 64.0;
        Vector3f currentRotation = transform != null ? transform.getRotation() : Vector3f.ZERO;

        int blockX = MathUtil.floor(marker.getX());
        int blockZ = MathUtil.floor(marker.getZ());
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);

        world.getChunkStore().getChunkReferenceAsync(chunkIndex).thenAcceptAsync(chunkRef -> {
            double destinationY = storedY != null ? storedY : fallbackY;
            try {
                if (storedY == null) {
                    BlockChunk blockChunk = world.getChunkStore().getStore()
                        .getComponent((Ref<ChunkStore>) chunkRef, BlockChunk.getComponentType());
                    if (blockChunk != null) {
                        destinationY = blockChunk.getHeight(blockX, blockZ) + 1.0;
                    }
                }
            } catch (Exception ignored) {
            }

            Vector3d destination = new Vector3d(marker.getX(), destinationY, marker.getZ());
            Teleport teleport = new Teleport(destination, currentRotation);
            store.addComponent(ref, Teleport.getComponentType(), teleport);

            String markerName = marker.getName() != null ? marker.getName() : target;
            context.sendMessage(Message.raw("Teleported to waypoint: " + markerName));
        }, world);
    }
}

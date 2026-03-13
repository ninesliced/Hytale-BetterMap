package dev.ninesliced.commands.bettermap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;
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

import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.utils.PermissionsUtil;

public class MarkerTeleportContextCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> markerIdArg = this.withRequiredArg("markerId", "Marker ID", ArgTypes.STRING);

    public MarkerTeleportContextCommand() {
        super("mtp", "Teleport to a map marker");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ModConfig config = ModConfig.getInstance();
        boolean configAllows = config.isAllowMapMarkerTeleports()
                && (config.isAnyMarkerTeleportEnabled() || config.isAllowPlayerTeleports());
        if (!configAllows
                && !PermissionsUtil.canTeleportToMarkers(player)
                && !PermissionsUtil.canTeleportToPlayers(player)) {
            context.sendMessage(Message.raw("You don't have permission to teleport to markers."));
            return;
        }

        String markerId = this.markerIdArg.get(context);
        MapMarker marker = player.getWorldMapTracker().getSentMarkers().get(markerId);
        if (marker == null) {
            context.sendMessage(Message.raw("Marker not found."));
            return;
        }

        if (isPlayerMarker(marker)) {
            if (!ModConfig.getInstance().isAllowPlayerTeleports() && !PermissionsUtil.canTeleportToPlayers(player)) {
                context.sendMessage(Message.raw("You don't have permission to teleport to players."));
                return;
            }
        }

        double markerX = marker.transform.position.x;
        double markerZ = marker.transform.position.z;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        double fallbackY = transform != null ? transform.getPosition().y : 64.0;
        Vector3f currentRotation = transform != null ? transform.getRotation() : Vector3f.ZERO;

        int blockX = MathUtil.floor(markerX);
        int blockZ = MathUtil.floor(markerZ);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);

        world.getChunkStore().getChunkReferenceAsync(chunkIndex).thenAcceptAsync(chunkRef -> {
            double destinationY = fallbackY;
            try {
                BlockChunk blockChunk = world.getChunkStore().getStore()
                    .getComponent((Ref<ChunkStore>) chunkRef, BlockChunk.getComponentType());
                if (blockChunk != null) {
                    destinationY = blockChunk.getHeight(blockX, blockZ) + 1.0;
                }
            } catch (Exception ignored) {
            }

            Vector3d destination = new Vector3d(markerX, destinationY, markerZ);
            Teleport teleport = new Teleport(destination, currentRotation);
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        }, world);
    }

    private static boolean isPlayerMarker(MapMarker marker) {
        if (marker.id != null && marker.id.startsWith("PlayerRadar-")) return true;
        if (marker.components == null) return false;
        for (MapMarkerComponent component : marker.components) {
            if (component instanceof PlayerMarkerComponent) return true;
        }
        return false;
    }
}

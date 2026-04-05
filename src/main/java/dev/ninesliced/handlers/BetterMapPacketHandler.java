package dev.ninesliced.handlers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.player.RemoveMapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.io.handlers.IPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.IWorldPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SubPacketHandler;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarkersStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.utils.MapMarkerUtils;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WaypointLimitUtil;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Overrides native packet handlers that have hardcoded Adventure mode checks.
 * <p>
 * Packet 119 (RemoveMapMarker): skips native distance check, keeps ownership +
 * BetterMap permission check.
 * Packet 244 (TeleportToWorldMapMarker): uses PermissionsUtil instead of native
 * permission + game mode check.
 * Packet 245 (TeleportToWorldMapPosition): same as 244.
 * Packet 246 (CreateUserMarker): same as 244.
 * Packet 243 (UpdateWorldMapVisible): sends map settings when map is opened.
 */
public class BetterMapPacketHandler implements SubPacketHandler {
    private final IPacketHandler packetHandler;

    public BetterMapPacketHandler(IPacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    @Override
    public void registerHandlers() {
        if (ModConfig.getInstance().isDisableDistanceRestrictionsForMarkerDeletion()) {
            IWorldPacketHandler.registerHandler(this.packetHandler, 119, this::handleRemoveMapMarker);
        }
        IWorldPacketHandler.registerHandler(this.packetHandler, 244, this::handleTeleportToWorldMapMarker);
        IWorldPacketHandler.registerHandler(this.packetHandler, 245, this::handleTeleportToWorldMapPosition);
        if (ModConfig.getInstance().isDisableDistanceRestrictionsForMarkerCreation()) {
            IWorldPacketHandler.registerHandler(this.packetHandler, 246, this::handleCreateUserMarker);
        }
        IWorldPacketHandler.registerHandler(this.packetHandler, 243, this::handleUpdateWorldMapVisible);
    }

    private void handleRemoveMapMarker(
            @Nonnull RemoveMapMarker packet,
            @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        assert player != null;

        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());

        if (perWorldData.removeLastDeath(packet.markerId)) {
            return;
        }

        UserMapMarker marker;
        UserMapMarkersStore markerStore;

        UserMapMarker personal = perWorldData.getUserMapMarker(packet.markerId);
        if (personal != null) {
            marker = personal;
            markerStore = perWorldData;
        } else {
            WorldMarkersResource shared = world.getChunkStore()
                    .getStore().getResource(WorldMarkersResource.getResourceType());
            UserMapMarker sharedMarker = shared.getUserMapMarker(packet.markerId);
            if (sharedMarker == null) {
                playerRef.sendMessage(Message.translation(
                        "server.worldmap.markers.edit.notFound").color("#ffc800"));
                return;
            }
            marker = sharedMarker;
            markerStore = shared;
        }

        UUID playerUuid = playerRef.getUuid();
        UUID createdBy = marker.getCreatedByUuid();
        boolean isOwner = playerUuid.equals(createdBy) || createdBy == null;
        boolean canDelete = isOwner
                || PermissionsUtil.canEditSharedWaypointByPermission(player)
                || ModConfig.getInstance().isAllowGlobalWaypointEditsForEveryone();

        if (!canDelete) {
            playerRef.sendMessage(Message.translation(
                    "server.worldmap.markers.edit.notOwner").color("#ffc800"));
            return;
        }

        markerStore.removeUserMapMarker(marker.getId());
    }

    private void handleTeleportToWorldMapMarker(
            @Nonnull TeleportToWorldMapMarker packet,
            @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        assert playerComponent != null;

        if (!ModConfig.getInstance().isAllowMapMarkerTeleports()
                && !PermissionsUtil.isAdmin(playerComponent)
                && !PermissionsUtil.canTeleportToMarkers(playerComponent)) {
            return;
        }

        WorldMapTracker worldMapTracker = playerComponent.getWorldMapTracker();
        MapMarker marker = worldMapTracker.getSentMarkers().get(packet.id);
        if (marker != null) {
            Transform transform = PositionUtil.toTransform(marker.transform);
            if (MapMarkerUtils.isUserMarker(marker)) {
                int blockX = (int) transform.getPosition().getX();
                int blockZ = (int) transform.getPosition().getZ();
                WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(blockX, blockZ));
                int height = chunk == null ? 319 : chunk.getHeight(blockX, blockZ);
                transform.getPosition().setY(height);
            }
            Teleport teleportComponent = Teleport.createForPlayer(transform);
            world.getEntityStore().getStore().addComponent(
                    playerRef.getReference(), Teleport.getComponentType(), teleportComponent);
        }
    }

    private void handleTeleportToWorldMapPosition(
            @Nonnull TeleportToWorldMapPosition packet,
            @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        assert playerComponent != null;

        if (!ModConfig.getInstance().isAllowCoordinateTeleports()
                && !PermissionsUtil.isAdmin(playerComponent)
                && !PermissionsUtil.canTeleportToCoordinates(playerComponent)) {
            return;
        }

        world.getChunkStore().getChunkReferenceAsync(
                ChunkUtil.indexChunkFromBlock(packet.x, packet.y)
        ).thenAcceptAsync((chunkRef) -> {
            BlockChunk blockChunkComponent = world.getChunkStore()
                    .getStore().getComponent(chunkRef, BlockChunk.getComponentType());
            assert blockChunkComponent != null;

            Vector3d position = new Vector3d(
                    packet.x,
                    blockChunkComponent.getHeight(packet.x, packet.y) + 2,
                    packet.y);
            Teleport teleportComponent = Teleport.createForPlayer(
                    null, position, new Vector3f(0.0F, 0.0F, 0.0F));
            world.getEntityStore().getStore().addComponent(
                    playerRef.getReference(), Teleport.getComponentType(), teleportComponent);
        }, world);
    }

    private void handleCreateUserMarker(
            @Nonnull CreateUserMarker packet,
            @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        assert playerComponent != null;

        if (!PermissionsUtil.canCreateMapMarkers(playerComponent)) {
            return;
        }

        if (packet.name != null && packet.name.length() > 24) {
            playerRef.sendMessage(Message.translation(
                    "server.worldmap.markers.create.nameTooLong").color("#ffc800"));
            return;
        }

        String limitError = WaypointLimitUtil.getCreationError(playerComponent, packet.shared);
        if (limitError != null) {
            playerRef.sendMessage(Message.raw(limitError).color("#ffc800"));
            return;
        }

        UserMapMarkersStore markerStore;
        if (packet.shared) {
            markerStore = world.getChunkStore().getStore()
                    .getResource(WorldMarkersResource.getResourceType());
        } else {
            markerStore = playerComponent.getPlayerConfigData().getPerWorldData(world.getName());
        }

        UserMapMarker marker = new UserMapMarker();
        marker.setId("user_" + (packet.shared ? "shared" : "personal") + "_" + UUID.randomUUID());
        marker.setPosition(packet.x, packet.z);
        marker.setName(packet.name);
        marker.setIcon(packet.markerImage == null ? "User1.png" : packet.markerImage);
        marker.setColorTint(packet.tintColor == null
                ? new Color((byte) 0, (byte) 0, (byte) 0) : packet.tintColor);
        marker.withCreatedByName(playerRef.getUsername());
        marker.withCreatedByUuid(playerRef.getUuid());
        markerStore.addUserMapMarker(marker);
    }

    private void handleUpdateWorldMapVisible(
            @Nonnull UpdateWorldMapVisible packet,
            @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        assert playerComponent != null;

        playerComponent.getWorldMapTracker().setClientHasWorldMapVisible(packet.visible);

        if (packet.visible) {
            WorldMapHook.sendMapSettingsToPlayer(playerComponent);
        }
    }
}

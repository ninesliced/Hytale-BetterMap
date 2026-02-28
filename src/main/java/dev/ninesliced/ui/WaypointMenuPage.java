package dev.ninesliced.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

public class WaypointMenuPage extends InteractiveCustomUIPage<WaypointMenuPage.WaypointGuiData> {

    private static final String WAYPOINT_LIST_PATH = "#WaypointListContainer";

    public WaypointMenuPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, WaypointGuiData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append("Pages/BetterMap/WaypointMenu.ui");

        Player player = store.getComponent(ref, Player.getComponentType());
        boolean isAdmin = player != null && PermissionsUtil.isAdmin(player);
        ui.set("#ConfigButton.Visible", true);
        ui.set("#AdminConfigSpacer.Visible", isAdmin);
        ui.set("#AdminConfigButton.Visible", isAdmin);

        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CreateButton",
            new EventData().put(WaypointGuiData.KEY_ACTION, Action.CREATE.name()),
            false
        );
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            new EventData().put(WaypointGuiData.KEY_ACTION, Action.CLOSE.name()),
            false
        );
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ConfigButton",
            new EventData().put(WaypointGuiData.KEY_ACTION, Action.CONFIG.name()),
            false
        );
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#AdminConfigButton",
            new EventData().put(WaypointGuiData.KEY_ACTION, Action.ADMIN_CONFIG.name()),
            false
        );

        buildWaypointList(ref, store, ui, events);
    }

    private void buildWaypointList(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder ui,
        @Nonnull UIEventBuilder events
    ) {
        ui.clear(WAYPOINT_LIST_PATH);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        List<UserMapMarker> markers = WaypointManager.getUserMarkers(player);
        if (markers == null || markers.isEmpty()) {
            return;
        }

        List<UserMapMarker> sortedMarkers = new ArrayList<>(markers);
        sortedMarkers.sort(
            Comparator.comparing(
                marker -> {
                    String name = marker.getName();
                    if (name == null || name.isBlank()) {
                        return "~";
                    }
                    return name.toLowerCase(Locale.ROOT);
                }
            )
        );

        boolean canTeleport = PermissionsUtil.canTeleport(player)
            && ModConfig.getInstance().isAllowWaypointTeleports();

        int index = 0;
        for (UserMapMarker marker : sortedMarkers) {
            if (marker == null || marker.getId() == null) {
                continue;
            }

            String itemPath = WAYPOINT_LIST_PATH + "[" + index + "]";

            ui.append(WAYPOINT_LIST_PATH, "Pages/BetterMap/WaypointItem.ui");

            String name = marker.getName();
            ui.set(itemPath + " #NameLabel.Text", name != null && !name.isEmpty() ? name : "Unnamed");
            
            String icon = marker.getIcon();

            boolean isShared = WaypointManager.isSharedId(marker.getId());
            ui.set(itemPath + " #SharedLabel.Text", isShared ? "(Shared)" : "(Personal)");

            String worldName = player.getWorld() != null ? player.getWorld().getName() : "-";
            if (worldName == null || worldName.isEmpty()) {
                worldName = "-";
            }
            ui.set(itemPath + " #WorldValue.Text", worldName);

            ui.set(itemPath + " #XValue.Text", String.format(Locale.ROOT, "%.1f", marker.getX()));
            double markerY = player.getWorld() != null
                ? WaypointManager.getMarkerYOrDefault(player.getWorld(), player, marker.getId(), 100.0)
                : 100.0;
            ui.set(itemPath + " #YValue.Text", String.format(Locale.ROOT, "%.1f", markerY));
            ui.set(itemPath + " #ZValue.Text", String.format(Locale.ROOT, "%.1f", marker.getZ()));

            String createdBy = marker.getCreatedByName();
            ui.set(itemPath + " #CreatorValue.Text", createdBy != null && !createdBy.isEmpty() ? createdBy : "-");

            String iconPath = icon != null && !icon.isEmpty() ? icon : "UserA.png";
            ui.set(itemPath + " #IconPreview.Background", "Common/" + iconPath);
            Color tint = marker.getColorTint();
            if (tint != null) {
                ui.set(itemPath + " #IconPreview.Background.Color", String.format("#%02X%02X%02X", tint.red & 0xFF, tint.green & 0xFF, tint.blue & 0xFF));
            }

            ui.set(itemPath + " #TeleportButton.Visible", canTeleport);
            if (canTeleport) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #TeleportButton",
                    new EventData()
                        .put(WaypointGuiData.KEY_TARGET_ID, marker.getId())
                        .put(WaypointGuiData.KEY_ACTION, Action.TELEPORT.name()),
                    false
                );
            }

            boolean canEdit = !isShared || PermissionsUtil.canEditSharedWaypoint(player, marker);
            ui.set(itemPath + " #EditButton.Visible", canEdit);
            if (canEdit) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #EditButton",
                    new EventData()
                        .put(WaypointGuiData.KEY_TARGET_ID, marker.getId())
                        .put(WaypointGuiData.KEY_ACTION, Action.EDIT.name()),
                    false
                );
            }

            ui.set(itemPath + " #DeleteButton.Visible", canEdit);
            if (canEdit) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #DeleteButton",
                    new EventData()
                        .put(WaypointGuiData.KEY_TARGET_ID, marker.getId())
                        .put(WaypointGuiData.KEY_ACTION, Action.DELETE.name()),
                    false
                );
            }

            index++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull WaypointGuiData data) {
        super.handleDataEvent(ref, store, data);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        Action action = Action.from(data.action);
        if (action == null) {
            return;
        }

        if (action == Action.CLOSE) {
            player.getPageManager().setPage(ref, store, com.hypixel.hytale.protocol.packets.interface_.Page.None);
            return;
        }

        if (action == Action.CONFIG) {
            player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(this.playerRef));
            return;
        }

        if (action == Action.ADMIN_CONFIG) {
            if (PermissionsUtil.isAdmin(player)) {
                player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(this.playerRef, true));
            } else {
                player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(this.playerRef));
            }
            return;
        }

        switch (action) {
            case CREATE -> {
                player.getPageManager().openCustomPage(ref, store, new WaypointEditPage(this.playerRef, null));
            }
            case DELETE -> {
                if (data.targetId != null && !data.targetId.isEmpty()) {
                    if (WaypointManager.isSharedId(data.targetId)) {
                        UserMapMarker marker = WaypointManager.getMarker(player, data.targetId);
                        if (marker == null || !PermissionsUtil.canEditSharedWaypoint(player, marker)) {
                            player.sendMessage(Message.raw("You do not have permission to delete shared waypoints."));
                            return;
                        }
                    }
                    boolean removed = WaypointManager.removeMarker(player, data.targetId);
                    if (removed) {
                        refreshWaypoints(ref, store);
                    }
                }
            }
            case EDIT -> {
                if (data.targetId != null && !data.targetId.isEmpty()) {
                    if (WaypointManager.isSharedId(data.targetId)) {
                        UserMapMarker marker = WaypointManager.getMarker(player, data.targetId);
                        if (marker == null || !PermissionsUtil.canEditSharedWaypoint(player, marker)) {
                            player.sendMessage(Message.raw("You do not have permission to edit shared waypoints."));
                            return;
                        }
                    }
                     player.getPageManager().openCustomPage(ref, store, new WaypointEditPage(this.playerRef, data.targetId));
                }
            }
            case TELEPORT -> {
                if (!PermissionsUtil.canTeleport(player)
                    || !ModConfig.getInstance().isAllowWaypointTeleports()) {
                    return;
                }
                if (data.targetId != null && !data.targetId.isEmpty()) {
                    UserMapMarker marker = WaypointManager.getMarker(player, data.targetId);
                    if (marker != null) {
                        World world = ((EntityStore) store.getExternalData()).getWorld();
                        if (world == null) {
                            return;
                        }

                        float markerX = marker.getX();
                        float markerZ = marker.getZ();
                        Double storedY = WaypointManager.getMarkerY(world, player, marker.getId());

                        double destinationY = storedY != null ? storedY : 64.0;
                        try {
                            if (storedY == null) {
                                long chunkIndex = ChunkUtil.indexChunkFromBlock(markerX, markerZ);
                                WorldChunk chunk = world.getChunk(chunkIndex);
                                if (chunk != null) {
                                    int blockX = MathUtil.floor(markerX);
                                    int blockZ = MathUtil.floor(markerZ);
                                    int localX = blockX & 31;
                                    int localZ = blockZ & 31;
                                    short surfaceHeight = chunk.getHeight(localX, localZ);
                                    destinationY = surfaceHeight + 1.0;
                                }
                            }
                        } catch (Exception e) {
                            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                            if (transform != null) {
                                destinationY = transform.getPosition().y;
                            }
                        }

                        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                        Vector3f currentRotation = transform != null ? transform.getRotation() : Vector3f.ZERO;
                        Vector3d destination = new Vector3d(markerX, destinationY, markerZ);
                        Teleport teleport = new Teleport(destination, currentRotation);

                        world.execute(() -> store.addComponent(ref, Teleport.getComponentType(), teleport));
                    }
                }
            }
        }
    }

    private void refreshWaypoints(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        buildWaypointList(ref, store, ui, events);
        sendUpdate(ui, events, false);
    }

    private enum Action {
        CREATE,
        EDIT,
        DELETE,
        TELEPORT,
        CLOSE,
        CONFIG,
        ADMIN_CONFIG;

        static Action from(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return valueOf(raw.toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public static class WaypointGuiData {
        static final String KEY_ACTION = "Action";
        static final String KEY_TARGET_ID = "TargetId";

        public static final BuilderCodec<WaypointGuiData> CODEC = BuilderCodec.<WaypointGuiData>builder(
                WaypointGuiData.class,
                WaypointGuiData::new
            )
            .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
            .append(new KeyedCodec<>(KEY_TARGET_ID, Codec.STRING), (data, value) -> data.targetId = value, data -> data.targetId).add()
            .build();

        private String action;
        private String targetId;

        public WaypointGuiData() {
        }
    }
}


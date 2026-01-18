package dev.ninesliced.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
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
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;

public class WaypointMenuPage extends InteractiveCustomUIPage<WaypointMenuPage.WaypointGuiData> {

    private static final String WAYPOINT_LIST_PATH = "#WaypointListContainer";

    public WaypointMenuPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, WaypointGuiData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append("Pages/BetterMap/WaypointMenu.ui");

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

        MapMarker[] markers = WaypointManager.getWaypoints(player);
        if (markers == null) {
            return;
        }

        boolean canTeleport = PermissionsUtil.canTeleport(player)
            && BetterMapConfig.getInstance().isAllowWaypointTeleports();

        int index = 0;
        for (MapMarker marker : markers) {
            if (marker == null) {
                continue;
            }

            String itemPath = WAYPOINT_LIST_PATH + "[" + index + "]";

            ui.append(WAYPOINT_LIST_PATH, "Pages/BetterMap/WaypointItem.ui");
            ui.set(itemPath + " #NameLabel.Text", marker.name != null ? marker.name : "Unnamed");
            ui.set(itemPath + " #IconLabel.Text", "[" + colorLabel(marker.markerImage) + "]");
            
            boolean isGlobal = WaypointManager.isGlobalId(marker.id);
            ui.set(itemPath + " #SharedLabel.Text", isGlobal ? "(Global)" : "(Local)");

            String worldName = player.getWorld() != null ? player.getWorld().getName() : "-";
            if (worldName == null || worldName.isEmpty()) {
                worldName = "-";
            }
            ui.set(itemPath + " #WorldValue.Text", worldName);

            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            if (marker.transform != null && marker.transform.position != null) {
                x = marker.transform.position.x;
                y = marker.transform.position.y;
                z = marker.transform.position.z;
            }
            ui.set(itemPath + " #XValue.Text", String.format(Locale.ROOT, "%.1f", x));
            ui.set(itemPath + " #YValue.Text", String.format(Locale.ROOT, "%.1f", y));
            ui.set(itemPath + " #ZValue.Text", String.format(Locale.ROOT, "%.1f", z));

            ui.set(itemPath + " #TeleportButton.Visible", canTeleport);
            if (canTeleport) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #TeleportButton",
                    new EventData()
                        .put(WaypointGuiData.KEY_TARGET_ID, marker.id)
                        .put(WaypointGuiData.KEY_ACTION, Action.TELEPORT.name()),
                    false
                );
            }

            boolean canDelete = !isGlobal || PermissionsUtil.canUseGlobalWaypoints(player);
            ui.set(itemPath + " #EditButton.Visible", canDelete);
            if (canDelete) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #EditButton",
                    new EventData()
                        .put(WaypointGuiData.KEY_TARGET_ID, marker.id)
                        .put(WaypointGuiData.KEY_ACTION, Action.EDIT.name()),
                    false
                );
            }

            ui.set(itemPath + " #DeleteButton.Visible", canDelete);
            if (canDelete) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #DeleteButton",
                    new EventData()
                        .put(WaypointGuiData.KEY_TARGET_ID, marker.id)
                        .put(WaypointGuiData.KEY_ACTION, Action.DELETE.name()),
                    false
                );
            }

            index++;
        }
    }

    private static String colorLabel(String markerImage) {
        if (markerImage == null) {
            return "White";
        }
        switch (markerImage) {
            case "RedMarker.png":
                return "Red";
            case "GreenMarker.png":
                return "Green";
            case "BlueMarker.png":
                return "Blue";
            case "Coordinate.png":
                return "White";
            default:
                return markerImage;
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

        switch (action) {
            case CREATE -> {
                player.getPageManager().openCustomPage(ref, store, new WaypointEditPage(this.playerRef, null));
            }
            case DELETE -> {
                if (data.targetId != null && !data.targetId.isEmpty()) {
                    if (WaypointManager.isGlobalId(data.targetId) && !PermissionsUtil.canUseGlobalWaypoints(player)) {
                        player.sendMessage(Message.raw("You do not have permission to delete global waypoints."));
                        return;
                    }
                    boolean removed = WaypointManager.removeWaypoint(player, data.targetId);
                    if (removed) {
                        refreshWaypoints(ref, store);
                    }
                }
            }
            case EDIT -> {
                if (data.targetId != null && !data.targetId.isEmpty()) {
                    if (WaypointManager.isGlobalId(data.targetId) && !PermissionsUtil.canUseGlobalWaypoints(player)) {
                        player.sendMessage(Message.raw("You do not have permission to delete global waypoints."));
                        return;
                    }
                     player.getPageManager().openCustomPage(ref, store, new WaypointEditPage(this.playerRef, data.targetId));
                }
            }
            case TELEPORT -> {
                if (!PermissionsUtil.canTeleport(player)
                    || !BetterMapConfig.getInstance().isAllowWaypointTeleports()) {
                    return;
                }
                if (data.targetId != null && !data.targetId.isEmpty()) {
                    MapMarker marker = WaypointManager.getWaypoint(player, data.targetId);
                    if (marker != null && marker.transform != null && marker.transform.position != null) {
                        Vector3d destination = new Vector3d(
                            marker.transform.position.x,
                            marker.transform.position.y,
                            marker.transform.position.z
                        );
                        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                        Vector3f currentRotation = transform != null ? transform.getRotation() : Vector3f.ZERO;
                        Teleport teleport = new Teleport(destination, currentRotation);
                        World world = ((EntityStore) store.getExternalData()).getWorld();
                        if (world != null) {
                            world.execute(() -> store.addComponent(ref, Teleport.getComponentType(), teleport));
                        } else {
                            store.addComponent(ref, Teleport.getComponentType(), teleport);
                        }
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
        CLOSE;

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
            .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action)
            .addField(new KeyedCodec<>(KEY_TARGET_ID, Codec.STRING), (data, value) -> data.targetId = value, data -> data.targetId)
            .build();

        private String action;
        private String targetId;

        public WaypointGuiData() {
        }
    }
}


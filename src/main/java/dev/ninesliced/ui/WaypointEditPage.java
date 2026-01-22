package dev.ninesliced.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.annotation.Nonnull;

public class WaypointEditPage extends InteractiveCustomUIPage<WaypointEditPage.EditData> {

    @Nullable
    private final String targetId;
    private String selectedColor = "Coordinate.png";
    private boolean global = false;

    private String nameInput = "";
    private String inputX = "0.00";
    private String inputY = "64.00";
    private String inputZ = "0.00";
    
    private boolean initialized = false;

    public WaypointEditPage(@Nonnull PlayerRef playerRef, @Nullable String targetId) {
        super(playerRef, CustomPageLifetime.CanDismiss, EditData.CODEC);
        this.targetId = targetId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append("Pages/BetterMap/WaypointEdit.ui");
        
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        
        if (!initialized) {
            if (targetId != null) {
                MapMarker marker = WaypointManager.getWaypoint(player, targetId);
                if (marker != null) {
                    this.nameInput = marker.name != null ? marker.name : "";
                    this.selectedColor = marker.markerImage;
                    var pos = marker.transform.position;
                    this.inputX = String.format(Locale.ROOT, "%.2f", pos.x);
                    this.inputY = String.format(Locale.ROOT, "%.2f", pos.y);
                    this.inputZ = String.format(Locale.ROOT, "%.2f", pos.z);
                    this.global = marker.id != null && marker.id.startsWith("global_waypoint_");
                }
            } else {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    var pos = transform.getPosition();
                    this.inputX = String.format(Locale.ROOT, "%.2f", pos.x);
                    this.inputY = String.format(Locale.ROOT, "%.2f", pos.y);
                    this.inputZ = String.format(Locale.ROOT, "%.2f", pos.z);
                }
            }
            initialized = true;
        }

        ui.set("#NameInput.Value", this.nameInput);
        ui.set("#InputX.Value", this.inputX);
        ui.set("#InputY.Value", this.inputY);
        ui.set("#InputZ.Value", this.inputZ);

        ui.set("#SelRed.Visible", "RedMarker.png".equals(this.selectedColor));
        ui.set("#SelGreen.Visible", "GreenMarker.png".equals(this.selectedColor));
        ui.set("#SelBlue.Visible", "BlueMarker.png".equals(this.selectedColor));
        ui.set("#SelWhite.Visible", "Coordinate.png".equals(this.selectedColor));

        boolean canGlobal = PermissionsUtil.canUseGlobalWaypoints(player);
        ui.set("#GlobalRow.Visible", canGlobal);
        if (canGlobal) {
            ui.set("#GlobalCheckbox.Value", this.global);
        }

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NameInput",  
            new EventData().put(EditData.KEY_NAME_INPUT, "#NameInput.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputX", 
            new EventData().put(EditData.KEY_INPUT_X, "#InputX.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputY", 
            new EventData().put(EditData.KEY_INPUT_Y, "#InputY.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputZ", 
            new EventData().put(EditData.KEY_INPUT_Z, "#InputZ.Value"), false);
        
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", 
            new EventData().put(EditData.KEY_ACTION, Action.SAVE.name()), false);
            
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", 
            new EventData().put(EditData.KEY_ACTION, Action.CANCEL.name()), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ColorRed", 
            new EventData().put(EditData.KEY_ACTION, Action.SET_RED.name()), false);
            
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ColorGreen", 
            new EventData().put(EditData.KEY_ACTION, Action.SET_GREEN.name()), false);
            
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ColorBlue", 
            new EventData().put(EditData.KEY_ACTION, Action.SET_BLUE.name()), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ColorWhite", 
            new EventData().put(EditData.KEY_ACTION, Action.SET_WHITE.name()), false);

        if (canGlobal) {
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#GlobalCheckbox",
                new EventData().put(EditData.KEY_GLOBAL, "#GlobalCheckbox.Value"), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EditData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        
        if (data.nameInput != null) this.nameInput = data.nameInput;
        if (data.inputX != null) this.inputX = data.inputX;
        if (data.inputY != null) this.inputY = data.inputY;
        if (data.inputZ != null) this.inputZ = data.inputZ;
        if (data.global != null) this.global = data.global;

        if (data.action == null) {
            return; 
        }

        Action action;
        try {
            action = Action.valueOf(data.action);
        } catch (Exception e) {
            return;
        }

        boolean canGlobal = PermissionsUtil.canUseGlobalWaypoints(player);
        if (targetId != null && targetId.startsWith("global_waypoint_") && !canGlobal) {
            return;
        }

        switch (action) {
            case SET_RED:
                this.selectedColor = "RedMarker.png";
                player.getPageManager().openCustomPage(ref, store, this);
                break;
            case SET_GREEN:
                this.selectedColor = "GreenMarker.png";
                player.getPageManager().openCustomPage(ref, store, this);
                break;
            case SET_BLUE:
                this.selectedColor = "BlueMarker.png";
                player.getPageManager().openCustomPage(ref, store, this);
                break;
            case SET_WHITE:
                this.selectedColor = "Coordinate.png";
                player.getPageManager().openCustomPage(ref, store, this);
                break;
            case CANCEL:
                player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(this.playerRef));
                break;
            case SAVE:
                String newName = this.nameInput.trim();
                if (newName.isEmpty()) newName = generateDefaultName(player, targetId == null);
                
                double x = 0, y = 0, z = 0;
                try {
                    x = Double.parseDouble(this.inputX);
                    y = Double.parseDouble(this.inputY);
                    z = Double.parseDouble(this.inputZ);
                } catch (NumberFormatException ignored) {}

                com.hypixel.hytale.math.vector.Transform newVecTransform = new com.hypixel.hytale.math.vector.Transform(x, y, z);
                com.hypixel.hytale.protocol.Transform packetTransform = PositionUtil.toTransformPacket(newVecTransform);

                boolean wantsGlobal = this.global && canGlobal;
                if (targetId != null) {
                    MapMarker old = WaypointManager.getWaypoint(player, targetId);
                    boolean wasGlobal = old != null && old.id != null && old.id.startsWith("global_waypoint_");
                    if (wantsGlobal != wasGlobal && old != null) {
                        WaypointManager.removeWaypoint(player, targetId);
                        WaypointManager.addWaypoint(player, newName, selectedColor, packetTransform, wantsGlobal);
                    } else if (old != null) {
                        WaypointManager.updateWaypoint(player, targetId, newName, selectedColor, packetTransform);
                    }
                } else {
                    WaypointManager.addWaypoint(player, newName, selectedColor, packetTransform, wantsGlobal);
                }
                
                WaypointMenuPage menuPage = new WaypointMenuPage(this.playerRef);
                player.getPageManager().openCustomPage(ref, store, menuPage);
                break;
        }
    }

    private String generateDefaultName(@Nonnull Player player, boolean isNew) {
        MapMarker[] markers = WaypointManager.getWaypoints(player);
        int count = markers != null ? markers.length : 0;
        int suffix = isNew ? count + 1 : Math.max(count, 1);
        return "Waypoint" + suffix;
    }

    enum Action {
        SAVE, CANCEL, SET_RED, SET_GREEN, SET_BLUE, SET_WHITE
    }

    public static class EditData {
        public static final String KEY_ACTION = "Action";
        public static final String KEY_NAME_INPUT = "@NameInput";
        public static final String KEY_INPUT_X = "@InputX";
        public static final String KEY_INPUT_Y = "@InputY";
        public static final String KEY_INPUT_Z = "@InputZ";
        public static final String KEY_GLOBAL = "@Global";
        
        public String action;
        public String nameInput;
        public String inputX;
        public String inputY;
        public String inputZ;
        public Boolean global;

        public static final BuilderCodec<EditData> CODEC = BuilderCodec.<EditData>builder(EditData.class, EditData::new)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .addField(new KeyedCodec<>(KEY_NAME_INPUT, Codec.STRING), (data, value) -> data.nameInput = value, data -> data.nameInput)
                .addField(new KeyedCodec<>(KEY_INPUT_X, Codec.STRING), (data, value) -> data.inputX = value, data -> data.inputX)
                .addField(new KeyedCodec<>(KEY_INPUT_Y, Codec.STRING), (data, value) -> data.inputY = value, data -> data.inputY)
                .addField(new KeyedCodec<>(KEY_INPUT_Z, Codec.STRING), (data, value) -> data.inputZ = value, data -> data.inputZ)
                .addField(new KeyedCodec<>(KEY_GLOBAL, Codec.BOOLEAN), (data, value) -> data.global = value, data -> data.global)
                .build();
    }
}

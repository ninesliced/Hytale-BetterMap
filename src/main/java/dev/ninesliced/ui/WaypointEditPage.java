package dev.ninesliced.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WaypointLimitUtil;
import dev.ninesliced.BetterMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.annotation.Nonnull;

public class WaypointEditPage extends InteractiveCustomUIPage<WaypointEditPage.EditData> {

    private static final String[] AVAILABLE_ICONS = {
        "UserA.png", "UserB.png", "UserC.png", "UserD.png", "UserE.png", "UserF.png"
    };

    private static final Color[] AVAILABLE_TINTS = {
        new Color((byte) -1, (byte) -1, (byte) -1),   // White #FFFFFF
        new Color((byte) -1, (byte) 68, (byte) 68),   // Red #FF4444
        new Color((byte) 1, (byte) -20, (byte) 126),   // Green #01EC7E
        new Color((byte) 68, (byte) 68, (byte) -1),   // Blue #4444FF
        new Color((byte) -1, (byte) -1, (byte) 68),   // Yellow #FFFF44
        new Color((byte) -1, (byte) 68, (byte) -1),   // Magenta #FF44FF
        new Color((byte) 68, (byte) -1, (byte) -1),   // Cyan #44FFFF
        new Color((byte) -1, (byte) -120, (byte) 0),  // Orange #FF8800
    };

    @Nullable
    private final String targetId;
    private boolean shared = false;

    private String nameInput = "";
    private String inputX = "0.00";
    private String inputZ = "0.00";
    private double inputY = 100.0;
    private int selectedIconIndex = 0;
    private int selectedTintIndex = 0;
    private String errorMessage = null;

    private boolean initialized = false;

    private static final Logger LOGGER = Logger.getLogger("WaypointEditPage");

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
                UserMapMarker marker = WaypointManager.getMarker(player, targetId);
                if (marker != null) {
                    this.nameInput = marker.getName() != null ? marker.getName() : "";
                    this.inputX = String.format(Locale.ROOT, "%.2f", marker.getX());
                    this.inputZ = String.format(Locale.ROOT, "%.2f", marker.getZ());
                    World world = player.getWorld();
                    this.inputY = world != null
                        ? WaypointManager.getMarkerYOrDefault(world, player, marker.getId(), this.inputY)
                        : this.inputY;
                    this.shared = WaypointManager.isSharedId(targetId);

                    String markerIcon = marker.getIcon();
                    Color markerTint = marker.getColorTint();
                    this.selectedIconIndex = getIconIndex(markerIcon);
                    this.selectedTintIndex = getTintIndex(markerTint);

                    LOGGER.info("[WaypointEdit] Loading marker: id=" + targetId +
                        ", icon=" + markerIcon + ", iconIndex=" + this.selectedIconIndex +
                        ", tint=" + (markerTint != null ? String.format("#%02X%02X%02X", markerTint.red & 0xFF, markerTint.green & 0xFF, markerTint.blue & 0xFF) : "null") +
                        ", tintIndex=" + this.selectedTintIndex);
                } else {
                    LOGGER.warning("[WaypointEdit] Marker not found for id: " + targetId);
                }
            } else {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    var pos = transform.getPosition();
                    this.inputX = String.format(Locale.ROOT, "%.2f", pos.x);
                    this.inputZ = String.format(Locale.ROOT, "%.2f", pos.z);
                    this.inputY = pos.y;
                }
            }
            initialized = true;
        }

        if (targetId != null && WaypointManager.isSharedId(targetId)) {
            UserMapMarker marker = WaypointManager.getMarker(player, targetId);
            if (marker == null || !PermissionsUtil.canEditSharedWaypoint(player, marker)) {
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You do not have permission to edit shared waypoints."));
                player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(this.playerRef));
                return;
            }
        }

        ui.set("#NameInput.Value", this.nameInput);
        ui.set("#InputX.Value", this.inputX);
        ui.set("#InputY.Value", String.format(Locale.ROOT, "%.2f", this.inputY));
        ui.set("#InputZ.Value", this.inputZ);

        Color tintColor = AVAILABLE_TINTS[selectedTintIndex];
        String tintHex = String.format("#%02X%02X%02X", tintColor.red & 0xFF, tintColor.green & 0xFF, tintColor.blue & 0xFF);
        for (int i = 0; i < AVAILABLE_ICONS.length; i++) {
            ui.set("#Icon" + i + "Preview.Background", "Common/" + AVAILABLE_ICONS[i]);
            ui.set("#Icon" + i + "Preview.Background.Color", tintHex);
            ui.set("#Icon" + i + "Selected.Visible", i == selectedIconIndex);
        }

        for (int i = 0; i < AVAILABLE_TINTS.length; i++) {
            ui.set("#Color" + i + "Selected.Visible", i == selectedTintIndex);
        }

        String iconName = AVAILABLE_ICONS[selectedIconIndex];
        ui.set("#SelectedIconPreview.Background", "Common/" + iconName);
        ui.set("#SelectedIconPreview.Background.Color", tintHex);

        boolean canShared = PermissionsUtil.canUseGlobalWaypoints(player);
        ui.set("#GlobalRow.Visible", canShared);
        if (canShared) {
            ui.set("#GlobalCheckbox.Value", this.shared);
        }

        ui.set("#ErrorLabel.Text", errorMessage != null ? errorMessage : "");
        ui.set("#ErrorLabel.Visible", errorMessage != null && !errorMessage.isBlank());

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NameInput",  
            new EventData().put(EditData.KEY_NAME_INPUT, "#NameInput.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputX", 
            new EventData().put(EditData.KEY_INPUT_X, "#InputX.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputY", 
            new EventData().put(EditData.KEY_INPUT_Y, "#InputY.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputZ", 
            new EventData().put(EditData.KEY_INPUT_Z, "#InputZ.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#CurrentLocationBtn",
            new EventData().put(EditData.KEY_ACTION, Action.CURRENT_LOCATION.name()), false);

        for (int i = 0; i < AVAILABLE_ICONS.length; i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#Icon" + i,
                new EventData().put(EditData.KEY_ACTION, "SELECT_ICON_" + i), false);
        }

        for (int i = 0; i < AVAILABLE_TINTS.length; i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#Color" + i,
                new EventData().put(EditData.KEY_ACTION, "SELECT_COLOR_" + i), false);
        }

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().put(EditData.KEY_ACTION, Action.BACK.name()), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton",
            new EventData().put(EditData.KEY_ACTION, Action.SAVE.name()), false);
            
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", 
            new EventData().put(EditData.KEY_ACTION, Action.CANCEL.name()), false);

        if (canShared) {
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
        if (data.inputY != null) {
            try {
                this.inputY = Double.parseDouble(data.inputY);
            } catch (NumberFormatException ignored) {
            }
        }
        if (data.inputZ != null) this.inputZ = data.inputZ;
        if (data.global != null) this.shared = data.global;

        if (data.action == null) {
            return; 
        }

        Action action;
        try {
            action = Action.valueOf(data.action);
        } catch (Exception e) {
            return;
        }

        boolean canShared = PermissionsUtil.canUseGlobalWaypoints(player);
        UserMapMarker targetMarker = null;
        if (targetId != null) {
            targetMarker = WaypointManager.getMarker(player, targetId);
        }
        if (targetMarker != null && WaypointManager.isSharedId(targetMarker.getId())
            && !PermissionsUtil.canEditSharedWaypoint(player, targetMarker)) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("You do not have permission to edit shared waypoints."));
            player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(this.playerRef));
            return;
        }

        switch (action) {
            case SELECT_ICON_0:
            case SELECT_ICON_1:
            case SELECT_ICON_2:
            case SELECT_ICON_3:
            case SELECT_ICON_4:
            case SELECT_ICON_5:
                int iconIndex = action.ordinal() - Action.SELECT_ICON_0.ordinal();
                if (iconIndex >= 0 && iconIndex < AVAILABLE_ICONS.length) {
                    selectedIconIndex = iconIndex;
                    refreshIconAndTint(ref, store);
                }
                break;
            case SELECT_COLOR_0:
            case SELECT_COLOR_1:
            case SELECT_COLOR_2:
            case SELECT_COLOR_3:
            case SELECT_COLOR_4:
            case SELECT_COLOR_5:
            case SELECT_COLOR_6:
            case SELECT_COLOR_7:
                int colorIndex = action.ordinal() - Action.SELECT_COLOR_0.ordinal();
                if (colorIndex >= 0 && colorIndex < AVAILABLE_TINTS.length) {
                    selectedTintIndex = colorIndex;
                    refreshIconAndTint(ref, store);
                }
                break;
            case CANCEL:
                player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(this.playerRef));
                break;
            case BACK:
                player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(this.playerRef));
                break;
            case CURRENT_LOCATION:
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    var pos = transform.getPosition();
                    this.inputX = String.format(Locale.ROOT, "%.2f", pos.x);
                    this.inputZ = String.format(Locale.ROOT, "%.2f", pos.z);
                    this.inputY = pos.y;
                    refreshCoordinates(ref, store);
                }
                break;
            case SAVE:
                String newName = this.nameInput.trim();
                if (newName.isEmpty()) newName = generateDefaultName(player, targetId == null);
                
                float x = 0, z = 0;
                try {
                    x = Float.parseFloat(this.inputX);
                    z = Float.parseFloat(this.inputZ);
                } catch (NumberFormatException ignored) {}

                String selectedIcon = AVAILABLE_ICONS[selectedIconIndex];
                Color selectedTint = AVAILABLE_TINTS[selectedTintIndex];
                boolean wantsShared = this.shared && canShared;
                double y = this.inputY;

                if (targetId != null) {
                    UserMapMarker existing = WaypointManager.getMarker(player, targetId);
                    boolean wasShared = WaypointManager.isSharedId(targetId);

                    if (wasShared && !canShared) {
                        wantsShared = true;
                    }

                    if (existing != null && wantsShared != wasShared) {
                        String limitError = WaypointLimitUtil.getCreationError(player, wantsShared);
                        if (limitError != null) {
                            showError(ref, store, limitError);
                            return;
                        }
                    }

                    if (wantsShared != wasShared && existing != null) {
                        WaypointManager.removeMarker(player, targetId);
                        WaypointManager.addMarker(player, newName, selectedIcon, x, z, y, selectedTint, wantsShared);
                    } else if (existing != null) {
                        WaypointManager.updateMarker(player, targetId, newName, selectedIcon, x, z, y, selectedTint);
                    }
                } else {
                    String limitError = WaypointLimitUtil.getCreationError(player, wantsShared);
                    if (limitError != null) {
                        showError(ref, store, limitError);
                        return;
                    }
                    WaypointManager.addMarker(player, newName, selectedIcon, x, z, y, selectedTint, wantsShared);
                }
                
                WaypointMenuPage menuPage = new WaypointMenuPage(this.playerRef);
                player.getPageManager().openCustomPage(ref, store, menuPage);
                break;
        }
    }

    private int getIconIndex(@Nullable String icon) {
        if (icon == null) return 0;
        for (int i = 0; i < AVAILABLE_ICONS.length; i++) {
            if (AVAILABLE_ICONS[i].equalsIgnoreCase(icon)) return i;
        }
        return 0;
    }

    private int getTintIndex(@Nullable Color tint) {
        if (tint == null) return 0;

        for (int i = 0; i < AVAILABLE_TINTS.length; i++) {
            Color t = AVAILABLE_TINTS[i];
            if (t.red == tint.red && t.green == tint.green && t.blue == tint.blue) return i;
        }

        int r = tint.red & 0xFF;
        int g = tint.green & 0xFF;
        int b = tint.blue & 0xFF;

        int closestIndex = 0;
        int closestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < AVAILABLE_TINTS.length; i++) {
            Color t = AVAILABLE_TINTS[i];
            int tr = t.red & 0xFF;
            int tg = t.green & 0xFF;
            int tb = t.blue & 0xFF;

            int dr = r - tr;
            int dg = g - tg;
            int db = b - tb;
            int distance = dr * dr + dg * dg + db * db;

            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    /**
     * Convert RGB to HSL color space.
     * @return float array [hue (0-1), saturation (0-1), lightness (0-1)]
     */
    private float[] rgbToHsl(int r, int g, int b) {
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h, s, l;

        l = (max + min) / 2.0f;

        if (max == min) {
            h = s = 0.0f; // achromatic (gray)
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2.0f - max - min) : d / (max + min);

            if (max == rf) {
                h = (gf - bf) / d + (gf < bf ? 6.0f : 0.0f);
            } else if (max == gf) {
                h = (bf - rf) / d + 2.0f;
            } else {
                h = (rf - gf) / d + 4.0f;
            }
            h /= 6.0f;
        }

        return new float[] { h, s, l };
    }

    private void refreshIconAndTint(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        Color tintColor = AVAILABLE_TINTS[selectedTintIndex];
        String tintHex = String.format("#%02X%02X%02X", tintColor.red & 0xFF, tintColor.green & 0xFF, tintColor.blue & 0xFF);

        for (int i = 0; i < AVAILABLE_ICONS.length; i++) {
            ui.set("#Icon" + i + "Preview.Background", "Common/" + AVAILABLE_ICONS[i]);
            ui.set("#Icon" + i + "Preview.Background.Color", tintHex);
            ui.set("#Icon" + i + "Selected.Visible", i == selectedIconIndex);
        }

        for (int i = 0; i < AVAILABLE_TINTS.length; i++) {
            ui.set("#Color" + i + "Selected.Visible", i == selectedTintIndex);
        }

        String iconName = AVAILABLE_ICONS[selectedIconIndex];
        ui.set("#SelectedIconPreview.Background", "Common/" + iconName);
        ui.set("#SelectedIconPreview.Background.Color", tintHex);

        sendUpdate(ui, events, false);
    }

    private void refreshCoordinates(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        ui.set("#InputX.Value", this.inputX);
        ui.set("#InputY.Value", String.format(Locale.ROOT, "%.2f", this.inputY));
        ui.set("#InputZ.Value", this.inputZ);

        sendUpdate(ui, events, false);
    }

    private void showError(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String message) {
        this.errorMessage = message;
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        ui.set("#ErrorLabel.Text", message);
        ui.set("#ErrorLabel.Visible", true);
        sendUpdate(ui, events, false);
    }

    private String generateDefaultName(@Nonnull Player player, boolean isNew) {
        List<UserMapMarker> markers = WaypointManager.getUserMarkers(player);
        int count = markers.size();
        int suffix = isNew ? count + 1 : Math.max(count, 1);
        return "Waypoint" + suffix;
    }

    enum Action {
        SAVE, CANCEL, BACK, CURRENT_LOCATION,
        SELECT_ICON_0, SELECT_ICON_1, SELECT_ICON_2, SELECT_ICON_3, SELECT_ICON_4, SELECT_ICON_5,
        SELECT_COLOR_0, SELECT_COLOR_1, SELECT_COLOR_2, SELECT_COLOR_3, SELECT_COLOR_4, SELECT_COLOR_5, SELECT_COLOR_6, SELECT_COLOR_7
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

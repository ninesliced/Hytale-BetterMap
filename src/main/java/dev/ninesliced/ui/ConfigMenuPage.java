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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.BetterMapConfig.MapQuality;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WorldMapHook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public class ConfigMenuPage extends InteractiveCustomUIPage<ConfigMenuPage.ConfigEventData> {

    private enum BindingType { STRING, NUMBER, BOOLEAN }

    private static final String LAYOUT_PATH = "Pages/BetterMap/ConfigMenu.ui";

    public ConfigMenuPage(PlayerRef player) {
        super(player, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append(LAYOUT_PATH);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        boolean isAdmin = PermissionsUtil.isAdmin(player);

        PlayerConfig pConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());
        ui.set("#PlayerMinScale.Value", pConfig.getMinScale());
        ui.set("#PlayerMaxScale.Value", pConfig.getMaxScale());

        bindChange(events, "#PlayerMinScale", "player_min_scale", BindingType.NUMBER);
        bindChange(events, "#PlayerMaxScale", "player_max_scale", BindingType.NUMBER);
        bindClick(events, "#PlayerViewBtn", "view_player");
        bindClick(events, "#AdminViewBtn", "view_admin");

        if (isAdmin) {
             ui.set("#NavBar.Visible", true);

             BetterMapConfig gConfig = BetterMapConfig.getInstance();

             ui.set("#AdminExplorationRadius.Value", gConfig.getExplorationRadius());
             ui.set("#AdminMapQualityInfo.Text", gConfig.getMapQuality().name());

             ui.set("#AdminMinScale.Value", (int) gConfig.getMinScale());
             ui.set("#AdminMaxScale.Value", (int) gConfig.getMaxScale());

             ui.set("#AllowWaypointTeleport.Value", gConfig.isAllowWaypointTeleports());
             ui.set("#ShareAllExploration.Value", gConfig.isShareAllExploration());
             ui.set("#DebugMode.Value", gConfig.isDebug());
             ui.set("#RadarEnabled.Value", gConfig.isRadarEnabled());
             ui.set("#HidePlayers.Value", gConfig.isHidePlayersOnMap());
             ui.set("#HideOtherWarps.Value", gConfig.isHideOtherWarpsOnMap());
             ui.set("#HideUnexploredWarps.Value", gConfig.isHideUnexploredWarpsOnMap());
             ui.set("#HideAllPois.Value", gConfig.isHideAllPoiOnMap());
             ui.set("#HideUnexploredPois.Value", gConfig.isHideUnexploredPoiOnMap());

             ui.set("#RadarRange.Value", gConfig.getRadarRange());


             ui.set("#HiddenPoisList.Value", String.join(", ", gConfig.getHiddenPoiNames()));
             ui.set("#AllowedWorldList.Value", String.join(", ", gConfig.getAllowedWorlds()));
             ui.set("#AutoSaveInterval.Value", gConfig.getAutoSaveInterval());

             bindChange(events, "#AdminExplorationRadius", "admin_exp_radius", BindingType.NUMBER);
             bindClick(events, "#AdminMapQualityInfo", "admin_map_quality");
             bindChange(events, "#AdminMinScale", "admin_min_scale", BindingType.NUMBER);
             bindChange(events, "#AdminMaxScale", "admin_max_scale", BindingType.NUMBER);

             bindChange(events, "#AllowWaypointTeleport", "admin_wp_teleport", BindingType.BOOLEAN);
             bindChange(events, "#ShareAllExploration", "admin_share_exp", BindingType.BOOLEAN);
             bindChange(events, "#DebugMode", "admin_debug", BindingType.BOOLEAN);

             bindChange(events, "#RadarEnabled", "admin_radar_enabled", BindingType.BOOLEAN);
             bindChange(events, "#RadarRange", "admin_radar_range", BindingType.NUMBER);

             bindChange(events, "#HidePlayers", "admin_hide_players", BindingType.BOOLEAN);
             bindChange(events, "#HideOtherWarps", "admin_hide_other_warps", BindingType.BOOLEAN);
             bindChange(events, "#HideUnexploredWarps", "admin_hide_unex_warps", BindingType.BOOLEAN);
             bindChange(events, "#HideAllPois", "admin_hide_all_pois", BindingType.BOOLEAN);
             bindChange(events, "#HideUnexploredPois", "admin_hide_unex_pois", BindingType.BOOLEAN);

             bindChange(events, "#HiddenPoisList", "admin_hidden_pois", BindingType.STRING);
             bindChange(events, "#AllowedWorldList", "admin_allowed_worlds", BindingType.STRING);
             bindClick(events, "#AddCurrentWorldBtn", "admin_add_current_world");
             bindChange(events, "#AutoSaveInterval", "admin_autosave", BindingType.NUMBER);
        }
    }

    private void bindChange(UIEventBuilder events, String elementId, String action, BindingType type) {
        EventData data = new EventData().put("Action", action);
        if (type == BindingType.NUMBER) {
            data.put("@ValueNum", elementId + ".Value");
        } else if (type == BindingType.BOOLEAN) {
            data.put("@ValueBool", elementId + ".Value");
        } else {
            data.put("@Value", elementId + ".Value");
        }
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, elementId, data, false);
    }

    private void bindClick(UIEventBuilder events, String elementId, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, elementId,
            new EventData()
                .put("Action", action),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ConfigEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        if (data.action.equals("view_player")) {
            ui.set("#PlayerView.Visible", true);
            ui.set("#AdminView.Visible", false);
            sendUpdate(ui, events, false);
            return;
        } else if (data.action.equals("view_admin")) {
            if (PermissionsUtil.isAdmin(player)) {
                ui.set("#PlayerView.Visible", false);
                ui.set("#AdminView.Visible", true);
                sendUpdate(ui, events, false);
            }
            return;
        }

        if (data.action.startsWith("player_")) {
            handlePlayerUpdate(data, player);
        } else if (data.action.startsWith("admin_")) {
             if (PermissionsUtil.isAdmin(player)) {
                handleAdminUpdate(data, ui, player);
             }
        }
    }

    private void handlePlayerUpdate(ConfigEventData data, Player player) {
        PlayerConfig pConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());
        String val = data.getEffectiveValue();
        World world = player.getWorld();
        try {
            if (val == null) return;
            switch (data.action) {
                case "player_min_scale":
                    pConfig.setMinScale(Float.parseFloat(val));
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    if (world != null)
                        world.execute(() -> WorldMapHook.sendMapSettingsToPlayer(player));
                    break;
                case "player_max_scale":
                    pConfig.setMaxScale(Float.parseFloat(val));
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    if (world != null)
                        world.execute(() -> WorldMapHook.sendMapSettingsToPlayer(player));
                    break;
            }
            PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
        } catch (NumberFormatException _) {}
    }

    private void handleAdminUpdate(ConfigEventData data, UICommandBuilder ui, Player player) {
        BetterMapConfig gConfig = BetterMapConfig.getInstance();
        String val = data.getEffectiveValue();
        try {
            switch (data.action) {
                case "admin_exp_radius":
                     if (val != null) gConfig.setExplorationRadius(Integer.parseInt(val));
                    break;
                case "admin_map_quality":
                    MapQuality current = gConfig.getMapQuality();
                    MapQuality next = MapQuality.values()[(current.ordinal() + 1) % MapQuality.values().length];
                    gConfig.setQuality(next);
                    ui.set("#AdminMapQualityInfo.Text", next.name());
                    sendUpdate(ui, new UIEventBuilder(), false);
                    break;
                case "admin_min_scale":
                     if (val != null) {
                         float f = Float.parseFloat(val);
                         gConfig.setMinScale(f);
                     }
                    break;
                case "admin_max_scale":
                     if (val != null) {
                         float f2 = Float.parseFloat(val);
                         gConfig.setMaxScale(f2);
                     }
                    break;
                case "admin_wp_teleport":
                    if (val != null) gConfig.setAllowWaypointTeleports(Boolean.parseBoolean(val));
                    break;
                case "admin_share_exp":
                    if (val != null) {
                        gConfig.setShareAllExploration(Boolean.parseBoolean(val));

                        Universe universe = Universe.get();
                        if (universe != null) {
                            universe.getWorlds().values().forEach(WorldMapHook::refreshTrackers);
                        }
                    }
                    break;
                case "admin_debug":
                     if (val != null) gConfig.setDebug(Boolean.parseBoolean(val));
                    break;
                case "admin_radar_enabled":
                     if (val != null) gConfig.setRadarEnabled(Boolean.parseBoolean(val));
                    break;
                case "admin_radar_range":
                     if (val != null) gConfig.setRadarRange(Integer.parseInt(val));
                    break;
                case "admin_hide_players":
                     if (val != null) {
                         gConfig.setHidePlayersOnMap(Boolean.parseBoolean(val));
                         MapPrivacyManager.getInstance().updatePrivacyState();
                     }
                    break;
                case "admin_hide_other_warps":
                     if (val != null) {
                         gConfig.setHideOtherWarpsOnMap(Boolean.parseBoolean(val));
                         MapPrivacyManager.getInstance().updatePrivacyState();
                     }
                    break;
                case "admin_hide_unex_warps":
                     if (val != null) {
                         gConfig.setHideUnexploredWarpsOnMap(Boolean.parseBoolean(val));
                         MapPrivacyManager.getInstance().updatePrivacyState();
                     }
                    break;
                case "admin_hide_all_pois":
                     if (val != null) {
                         gConfig.setHideAllPoiOnMap(Boolean.parseBoolean(val));
                         MapPrivacyManager.getInstance().updatePrivacyState();
                     }
                    break;
                case "admin_hide_unex_pois":
                    if (val != null) {
                        gConfig.setHideUnexploredPoiOnMap(Boolean.parseBoolean(val));
                        MapPrivacyManager.getInstance().updatePrivacyState();
                    }
                    break;
                case "admin_hidden_pois":
                    if (val != null) {
                        List<String> pois = Arrays.stream(val.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                        gConfig.setHiddenPoiNames(pois);
                        MapPrivacyManager.getInstance().updatePrivacyState();
                    }
                    break;
                case "admin_allowed_worlds":
                    if (val != null) {
                        List<String> worlds = Arrays.stream(val.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                        gConfig.setAllowedWorlds(worlds);
                    }
                    break;
                case "admin_add_current_world":
                    World world = player.getWorld();
                    if (world == null) break;
                    String worldName = world.getName();

                    List<String> allowed = new ArrayList<>(gConfig.getAllowedWorlds());
                    if (!allowed.contains(worldName)) {
                        allowed.add(worldName);
                        gConfig.setAllowedWorlds(allowed);
                        ui.set("#AllowedWorldList.Value", String.join(", ", allowed));
                        sendUpdate(ui, new UIEventBuilder(), false);
                    }
                    break;
                case "admin_autosave":
                    if (val != null) gConfig.setAutoSaveInterval(Integer.parseInt(val));
                    break;
            }
            gConfig.save();
        } catch (NumberFormatException _) {}
    }

    public static class ConfigEventData {
        public String action;
        public String value;
        public Double valueNum;
        public Boolean valueBool;
        public String checked;

        public ConfigEventData() {}

        public String getEffectiveValue() {
            if (value != null) return value;
            if (valueNum != null) {
                 if (valueNum % 1 == 0 && !Double.isInfinite(valueNum)) {
                     return String.valueOf(valueNum.longValue());
                 }
                 return String.valueOf(valueNum);
            }
            if (valueBool != null) return String.valueOf(valueBool);
            if (checked != null) return checked;
            return null;
        }

        @SuppressWarnings("deprecation")
        public static final BuilderCodec<ConfigEventData> CODEC = BuilderCodec.builder(ConfigEventData.class, ConfigEventData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action)
            .addField(new KeyedCodec<>("@Value", Codec.STRING), (o, v) -> o.value = v, o -> o.value)
            .addField(new KeyedCodec<>("@ValueNum", Codec.DOUBLE), (o, v) -> o.valueNum = v, o -> o.valueNum)
            .addField(new KeyedCodec<>("@ValueBool", Codec.BOOLEAN), (o, v) -> o.valueBool = v, o -> o.valueBool)
            .addField(new KeyedCodec<>("@Checked", Codec.STRING), (o, v) -> o.checked = v, o -> o.checked)
            .build();
    }
}

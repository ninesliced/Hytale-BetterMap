package dev.ninesliced.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerDeathPositionData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.ModConfig.MapQuality;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.hud.HudPosition;
import dev.ninesliced.integration.ExtendedTeleportIntegration;
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.UserMarkerProviderManager;
import dev.ninesliced.managers.WarpPrivacyManager;
import dev.ninesliced.managers.WorldBorderManager;
import dev.ninesliced.managers.CaveModeManager;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WorldMapHook;
import dev.ninesliced.utils.WaypointLimitUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class ConfigMenuPage extends InteractiveCustomUIPage<ConfigMenuPage.ConfigEventData> {

    private static final Logger LOGGER = Logger.getLogger(ConfigMenuPage.class.getName());
    private enum BindingType { STRING, NUMBER, BOOLEAN }
    private enum ExplorationResetType { MAP, CAVE, PLAYER_SURFACE, PLAYER_CAVE }

    private static class SavedPlayerEntry {
        private final UUID uuid;
        private final String label;

        private SavedPlayerEntry(UUID uuid, String label) {
            this.uuid = uuid;
            this.label = label;
        }
    }

    private static final String LAYOUT_PATH = "Pages/BetterMap/ConfigMenu.ui";

    private boolean restartRequired = false;
    private ExplorationResetType pendingExplorationResetType = null;
    private String pendingExplorationResetPlayerUuid = null;
    private String pendingExplorationResetPlayerLabel = null;
    private String selectedExplorationPlayerUuid = null;
    private String selectedExplorationPlayerLabel = null;

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
        
        boolean serverLocationEnabled = ModConfig.getInstance().isLocationEnabled();
        boolean serverCaveModeEnabled = ModConfig.getInstance().isCaveModeEnabled();

        if (serverLocationEnabled) {
            ui.set("#PlayerLocationEnabled.Value", pConfig.isLocationEnabled());
            applyLocationPositionDropdown(ui, pConfig.getEffectiveLocationHudPosition(), "#PlayerLocationPosition");
        } else {
            ui.set("#PlayerLocationHeader.Visible", false);
            ui.set("#PlayerLocationCard.Visible", false);
        }

        if (serverCaveModeEnabled) {
            ui.set("#PlayerCaveModeEnabled.Value", pConfig.isCaveModeEnabled());
        } else {
            ui.set("#PlayerCaveModeHeader.Visible", false);
            ui.set("#PlayerCaveModeCard.Visible", false);
        }

        ui.set("#PlayerHidePlayers.Value", pConfig.isHidePlayersOnMap());
        ui.set("#PlayerHideAllWarps.Value", pConfig.isHideAllWarpsOnMap());
        ui.set("#PlayerHideOtherWarps.Value", pConfig.isHideOtherWarpsOnMap());
        ui.set("#PlayerHideAllPois.Value", pConfig.isHideAllPoiOnMap());
        ui.set("#PlayerHideSpawn.Value", pConfig.isHideSpawnOnMap());
        ui.set("#PlayerHideDeath.Value", pConfig.isHideDeathMarkerOnMap());
        ui.set("#PlayerHideGlobalWaypoints.Value", pConfig.isHideGlobalWaypointsOnMap());
        ui.set("#PlayerHidePersonalWaypoints.Value", pConfig.isHidePersonalWaypointsOnMap());

        bindChange(events, "#PlayerMinScale", "player_min_scale", BindingType.NUMBER);
        bindChange(events, "#PlayerMaxScale", "player_max_scale", BindingType.NUMBER);
        bindChange(events, "#PlayerLocationEnabled", "player_location", BindingType.BOOLEAN);
        bindChange(events, "#PlayerLocationPosition", "player_location_pos", BindingType.STRING);
        bindChange(events, "#PlayerCaveModeEnabled", "player_cavemode", BindingType.BOOLEAN);
        bindChange(events, "#PlayerHidePlayers", "player_hide_players", BindingType.BOOLEAN);
        bindChange(events, "#PlayerHideAllWarps", "player_hide_all_warps", BindingType.BOOLEAN);
        bindChange(events, "#PlayerHideOtherWarps", "player_hide_other_warps", BindingType.BOOLEAN);
        bindChange(events, "#PlayerHideAllPois", "player_hide_all_pois", BindingType.BOOLEAN);
        bindChange(events, "#PlayerHideSpawn", "player_hide_spawn", BindingType.BOOLEAN);
        bindChange(events, "#PlayerHideDeath", "player_hide_death", BindingType.BOOLEAN);
        bindChange(events, "#PlayerHideGlobalWaypoints", "player_hide_global_waypoints", BindingType.BOOLEAN);
        bindChange(events, "#PlayerHidePersonalWaypoints", "player_hide_personal_waypoints", BindingType.BOOLEAN);
        bindClick(events, "#PlayerViewBtn", "view_player");
        bindClick(events, "#PlayerViewBtnSelected", "view_player");
        bindClick(events, "#AdminViewBtn", "view_admin");
        bindClick(events, "#AdminViewBtnSelected", "view_admin");
        bindClick(events, "#OpenWaypointsBtn", "open_waypoints");
        bindClick(events, "#PlayerResetDefaultsBtn", "player_reset_defaults");
        bindClick(events, "#HelpViewBtn", "open_help");
        bindClick(events, "#HelpViewBtnSelected", "open_help");
        bindClick(events, "#CloseBtn", "close_menu");

        if (isAdmin) {
             ui.set("#AdminViewContainer.Visible", true);
             ui.set("#AdminViewSpacer.Visible", true);

             ModConfig gConfig = ModConfig.getInstance();

             ui.set("#AdminExplorationRadius.Value", gConfig.getExplorationRadius());
             applyMapQualityDropdown(ui, gConfig.getMapQuality(), "#AdminMapQuality");
             ui.set("#AdminMaxChunksToLoad.Value", gConfig.getMaxChunksToLoad());

             ui.set("#AdminMinScale.Value", (int) gConfig.getMinScale());
             ui.set("#AdminMaxScale.Value", (int) gConfig.getMaxScale());

             ui.set("#AllowWaypointTeleport.Value", gConfig.isAllowWaypointTeleports());
             ui.set("#ShareAllExploration.Value", gConfig.isShareAllExploration());
             ui.set("#DebugMode.Value", gConfig.isDebug());
             ui.set("#LocationHudEnabled.Value", gConfig.isLocationEnabled());
             applyLocationPositionDropdown(ui, gConfig.getLocationHudPosition(), "#AdminLocationPosition");
             ui.set("#RadarEnabled.Value", gConfig.isRadarEnabled());
             ui.set("#HidePlayers.Value", gConfig.isHidePlayersOnMap());
             ui.set("#HideOtherWarps.Value", gConfig.isHideOtherWarpsOnMap());
             ui.set("#HideUnexploredWarps.Value", gConfig.isHideUnexploredWarpsOnMap());
             ui.set("#HideAllPois.Value", gConfig.isHideAllPoiOnMap());
             ui.set("#HideUnexploredPois.Value", gConfig.isHideUnexploredPoiOnMap());

             ui.set("#RadarRange.Value", gConfig.getRadarRange());

             int personalLimit = gConfig.getMaxPersonalMarkersPerPlayer();
             int sharedLimit = gConfig.getMaxSharedMarkersPerPlayer();
             ui.set("#AdminMaxPersonalMarkers.Value", personalLimit);
             ui.set("#AdminMaxSharedMarkers.Value", sharedLimit);


             ui.set("#HiddenPoisList.Value", String.join(", ", gConfig.getHiddenPoiNames()));
             ui.set("#AllowedWorldList.Value", String.join(", ", gConfig.getAllowedWorlds()));
             ui.set("#AutoSaveInterval.Value", gConfig.getAutoSaveInterval());

             ui.set("#WorldBorderEnabled.Value", gConfig.isWorldBorderEnabled());
             ui.set("#WorldBorderRadius.Value", gConfig.getWorldBorderRadius());
             ui.set("#WorldBorderOffsetX.Value", gConfig.getWorldBorderOffsetX());
             ui.set("#WorldBorderOffsetZ.Value", gConfig.getWorldBorderOffsetZ());

             ui.set("#CaveModeEnabled.Value", gConfig.isCaveModeEnabled());
             ui.set("#DiscoverSurfaceUnderground.Value", gConfig.isDiscoverSurfaceUnderground());
             ui.set("#CaveFogOfWar.Value", gConfig.isCaveFogOfWar());
             ui.set("#CaveModeLayerSize.Value", gConfig.getCaveModeLayerSize());
             ui.set("#CaveModeThreshold.Value", gConfig.getCaveModeUndergroundThreshold());
             ui.set("#CaveModeRadius.Value", gConfig.getCaveModeRadius());

             applySavedPlayersDropdown(ui);

             bindChange(events, "#AdminExplorationRadius", "admin_exp_radius", BindingType.NUMBER);
             bindChange(events, "#AdminMapQuality", "admin_map_quality", BindingType.STRING);
             bindChange(events, "#AdminMaxChunksToLoad", "admin_max_chunks", BindingType.NUMBER);
             bindChange(events, "#AdminMinScale", "admin_min_scale", BindingType.NUMBER);
             bindChange(events, "#AdminMaxScale", "admin_max_scale", BindingType.NUMBER);

             bindChange(events, "#AllowWaypointTeleport", "admin_wp_teleport", BindingType.BOOLEAN);
             bindChange(events, "#ShareAllExploration", "admin_share_exp", BindingType.BOOLEAN);
             bindChange(events, "#DebugMode", "admin_debug", BindingType.BOOLEAN);
             bindChange(events, "#LocationHudEnabled", "admin_location_enabled", BindingType.BOOLEAN);
             bindChange(events, "#AdminLocationPosition", "admin_location_pos", BindingType.STRING);

             bindChange(events, "#RadarEnabled", "admin_radar_enabled", BindingType.BOOLEAN);
             bindChange(events, "#RadarRange", "admin_radar_range", BindingType.NUMBER);
             bindChange(events, "#AdminMaxPersonalMarkers", "admin_marker_personal_limit", BindingType.NUMBER);
             bindChange(events, "#AdminMaxSharedMarkers", "admin_marker_shared_limit", BindingType.NUMBER);

             bindChange(events, "#HidePlayers", "admin_hide_players", BindingType.BOOLEAN);
             bindChange(events, "#HideOtherWarps", "admin_hide_other_warps", BindingType.BOOLEAN);
             bindChange(events, "#HideUnexploredWarps", "admin_hide_unex_warps", BindingType.BOOLEAN);
             bindChange(events, "#HideAllPois", "admin_hide_all_pois", BindingType.BOOLEAN);
             bindChange(events, "#HideUnexploredPois", "admin_hide_unex_pois", BindingType.BOOLEAN);

             bindChange(events, "#HiddenPoisList", "admin_hidden_pois", BindingType.STRING);
             bindChange(events, "#AllowedWorldList", "admin_allowed_worlds", BindingType.STRING);
             bindClick(events, "#AddCurrentWorldBtn", "admin_add_current_world");
             bindChange(events, "#AutoSaveInterval", "admin_autosave", BindingType.NUMBER);

             bindChange(events, "#WorldBorderEnabled", "admin_world_border_enabled", BindingType.BOOLEAN);
             bindChange(events, "#WorldBorderRadius", "admin_world_border_radius", BindingType.NUMBER);
             bindChange(events, "#WorldBorderOffsetX", "admin_world_border_offset_x", BindingType.NUMBER);
             bindChange(events, "#WorldBorderOffsetZ", "admin_world_border_offset_z", BindingType.NUMBER);

             bindChange(events, "#CaveModeEnabled", "admin_cavemode_enabled", BindingType.BOOLEAN);
             bindChange(events, "#DiscoverSurfaceUnderground", "admin_discover_surface", BindingType.BOOLEAN);
             bindChange(events, "#CaveFogOfWar", "admin_cave_fog_of_war", BindingType.BOOLEAN);
             bindChange(events, "#CaveModeLayerSize", "admin_cavemode_layer", BindingType.NUMBER);
             bindChange(events, "#CaveModeThreshold", "admin_cavemode_threshold", BindingType.NUMBER);
             bindChange(events, "#CaveModeRadius", "admin_cavemode_radius", BindingType.NUMBER);

             bindClick(events, "#AdminResetDefaultsBtn", "admin_reset_defaults");
             bindClick(events, "#AdminResetConfirmAllBtn", "admin_reset_confirm_all");
             bindClick(events, "#AdminResetConfirmKeepWorldsBtn", "admin_reset_confirm_keep_worlds");
             bindClick(events, "#AdminResetConfirmCancelBtn", "admin_reset_cancel");
             bindClick(events, "#AdminResetMapExplorationBtn", "admin_reset_map_exploration");
             bindClick(events, "#AdminResetCaveExplorationBtn", "admin_reset_cave_exploration");
             bindChange(events, "#AdminExplorationPlayerSelect", "admin_exploration_player_select", BindingType.STRING);
             bindClick(events, "#AdminResetSelectedPlayerSurfaceExplorationBtn", "admin_reset_selected_player_surface_exploration");
             bindClick(events, "#AdminResetSelectedPlayerCaveExplorationBtn", "admin_reset_selected_player_cave_exploration");
             bindClick(events, "#AdminExplorationResetConfirmBtn", "admin_exploration_reset_confirm");
             bindClick(events, "#AdminExplorationResetCancelBtn", "admin_exploration_reset_cancel");

             bindClick(events, "#HostingBannerBtn", "open_hosting_link");
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

    private void applyLocationPositionDropdown(UICommandBuilder ui, String currentPosition, String elementId) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        for (HudPosition pos : HudPosition.values()) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(pos.getDisplayName()), pos.getId()));
        }
        ui.set(elementId + ".Entries", entries);
        ui.set(elementId + ".Value", currentPosition != null ? currentPosition : HudPosition.TOP_LEFT.getId());
    }

    private void applyMapQualityDropdown(UICommandBuilder ui, MapQuality currentQuality, String elementId) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        for (MapQuality quality : MapQuality.values()) {
            String displayName = quality.name() + " (max " + quality.maxChunks + " chunks)";
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(displayName), quality.name()));
        }
        ui.set(elementId + ".Entries", entries);
        ui.set(elementId + ".Value", currentQuality != null ? currentQuality.name() : MapQuality.MEDIUM.name());
    }

    private void applyPlayerSettingsToUi(UICommandBuilder ui, PlayerConfig pConfig) {
        ui.set("#PlayerMinScale.Value", pConfig.getMinScale());
        ui.set("#PlayerMaxScale.Value", pConfig.getMaxScale());

        if (ModConfig.getInstance().isLocationEnabled()) {
            ui.set("#PlayerLocationEnabled.Value", pConfig.isLocationEnabled());
            applyLocationPositionDropdown(ui, pConfig.getEffectiveLocationHudPosition(), "#PlayerLocationPosition");
        }

        if (ModConfig.getInstance().isCaveModeEnabled()) {
            ui.set("#PlayerCaveModeEnabled.Value", pConfig.isCaveModeEnabled());
        }

        ui.set("#PlayerHidePlayers.Value", pConfig.isHidePlayersOnMap());
        ui.set("#PlayerHideAllWarps.Value", pConfig.isHideAllWarpsOnMap());
        ui.set("#PlayerHideOtherWarps.Value", pConfig.isHideOtherWarpsOnMap());
        ui.set("#PlayerHideAllPois.Value", pConfig.isHideAllPoiOnMap());
        ui.set("#PlayerHideSpawn.Value", pConfig.isHideSpawnOnMap());
        ui.set("#PlayerHideDeath.Value", pConfig.isHideDeathMarkerOnMap());
        ui.set("#PlayerHideGlobalWaypoints.Value", pConfig.isHideGlobalWaypointsOnMap());
        ui.set("#PlayerHidePersonalWaypoints.Value", pConfig.isHidePersonalWaypointsOnMap());
    }

    private static boolean determineNewVisibilityState(boolean currentlyWantsVisible, boolean currentlyWantsHidden) {
        if (currentlyWantsVisible) {
            return false;
        } else if (currentlyWantsHidden) {
            return true;
        }
        return false;
    }

    private void refreshHideState(World world) {
        if (world == null) return;
        WorldMapHook.clearMarkerCaches(world);
        WorldMapHook.refreshTrackers(world);
    }

    private void removeDeathMarkersFromClient(Player player, World world) {
        try {
            PlayerWorldData worldData = player.getPlayerConfigData().getPerWorldData(world.getName());
            if (worldData == null) return;

            List<PlayerDeathPositionData> deathPositions = worldData.getDeathPositions();
            if (deathPositions == null || deathPositions.isEmpty()) return;

            List<String> markerIdsToRemove = new ArrayList<>();
            for (PlayerDeathPositionData deathPosition : deathPositions) {
                if (deathPosition != null && deathPosition.getMarkerId() != null) {
                    markerIdsToRemove.add(deathPosition.getMarkerId());
                }
            }

            if (markerIdsToRemove.isEmpty()) return;

            playerRef.getPacketHandler().write(new UpdateWorldMap(
                null,
                null,
                markerIdsToRemove.toArray(new String[0])
            ));
        } catch (Exception ignored) {
        }
    }

    private void applyAdminSettingsToUi(UICommandBuilder ui, ModConfig gConfig) {
        ui.set("#AdminExplorationRadius.Value", gConfig.getExplorationRadius());
        applyMapQualityDropdown(ui, gConfig.getMapQuality(), "#AdminMapQuality");
        ui.set("#AdminMaxChunksToLoad.Value", gConfig.getMaxChunksToLoad());

        ui.set("#AdminMinScale.Value", (int) gConfig.getMinScale());
        ui.set("#AdminMaxScale.Value", (int) gConfig.getMaxScale());

        ui.set("#AllowWaypointTeleport.Value", gConfig.isAllowWaypointTeleports());
        ui.set("#ShareAllExploration.Value", gConfig.isShareAllExploration());
        ui.set("#DebugMode.Value", gConfig.isDebug());
        ui.set("#LocationHudEnabled.Value", gConfig.isLocationEnabled());
        applyLocationPositionDropdown(ui, gConfig.getLocationHudPosition(), "#AdminLocationPosition");
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

        ui.set("#WorldBorderEnabled.Value", gConfig.isWorldBorderEnabled());
        ui.set("#WorldBorderRadius.Value", gConfig.getWorldBorderRadius());
        ui.set("#WorldBorderOffsetX.Value", gConfig.getWorldBorderOffsetX());
        ui.set("#WorldBorderOffsetZ.Value", gConfig.getWorldBorderOffsetZ());

        ui.set("#CaveModeEnabled.Value", gConfig.isCaveModeEnabled());
        ui.set("#DiscoverSurfaceUnderground.Value", gConfig.isDiscoverSurfaceUnderground());
        ui.set("#CaveFogOfWar.Value", gConfig.isCaveFogOfWar());
        ui.set("#CaveModeLayerSize.Value", gConfig.getCaveModeLayerSize());
        ui.set("#CaveModeThreshold.Value", gConfig.getCaveModeUndergroundThreshold());
        ui.set("#CaveModeRadius.Value", gConfig.getCaveModeRadius());
    }

    private void updatePlayerCaveState(Player player, boolean enabled) {
        CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
        if (state != null) {
            state.setDynamicModeEnabled(enabled);
            if (!enabled) {
                state.setCurrentlyUnderground(false);
            }
        }
    }

    private void refreshWorldPlayersMap(World world) {
        if (world == null) return;
        world.execute(() -> {
            for (PlayerRef pRef : world.getPlayerRefs()) {
                Ref<EntityStore> ref = pRef.getReference();
                if (ref == null || !ref.isValid()) continue;
                Player p = ref.getStore().getComponent(ref, Player.getComponentType());
                if (p == null) continue;
                try {
                    WorldMapHook.forceFullMapRefresh(p);
                } catch (Exception e) {
                    LOGGER.warning("Failed to refresh map after reset: " + e.getMessage());
                }
            }
        });
    }

    private void resetAllMapExploration(Player actor) {
        int deletedFiles = ExplorationManager.getInstance().resetAllMapExploration();

        Universe universe = Universe.get();
        if (universe != null) {
            universe.getWorlds().values().forEach(world -> {
                if (world == null) return;
                world.execute(() -> {
                    for (PlayerRef pRef : world.getPlayerRefs()) {
                        Ref<EntityStore> pStoreRef = pRef.getReference();
                        if (pStoreRef == null || !pStoreRef.isValid()) continue;
                        Player p = pStoreRef.getStore().getComponent(pStoreRef, Player.getComponentType());
                        if (p == null) continue;

                        try {
                            WorldMapHook.clearCaveModeLoadedChunks(p.getDisplayName());
                            WorldMapHook.forceFullMapRefresh(p);
                        } catch (Exception e) {
                            LOGGER.warning("Failed to refresh player map after exploration reset: " + e.getMessage());
                        }
                    }
                });
            });
        }

        actor.sendMessage(
            Message.raw("[BetterMap] ").color("#93844c").bold(true)
                .insert(Message.raw("Reset map exploration for all players. Cleared " + deletedFiles + " persisted file(s).")
                    .color("#bfcdd5"))
        );
    }

    private void resetAllCaveExploration(Player actor) {
        int deletedFiles = ExplorationManager.getInstance().resetAllCaveExploration();
        WorldMapHook.clearSharedCaveExplorationCache();

        Universe universe = Universe.get();
        if (universe != null) {
            universe.getWorlds().values().forEach(world -> {
                if (world == null) return;
                world.execute(() -> {
                    for (PlayerRef pRef : world.getPlayerRefs()) {
                        Ref<EntityStore> pStoreRef = pRef.getReference();
                        if (pStoreRef == null || !pStoreRef.isValid()) continue;
                        Player p = pStoreRef.getStore().getComponent(pStoreRef, Player.getComponentType());
                        if (p == null) continue;

                        try {
                            CaveModeManager.getInstance().clearCaveExploration(p);
                            WorldMapHook.clearCaveModeLoadedChunks(p.getDisplayName());
                            WorldMapHook.forceFullMapRefresh(p);
                        } catch (Exception e) {
                            LOGGER.warning("Failed to refresh player map after cave reset: " + e.getMessage());
                        }
                    }
                });
            });
        }

        actor.sendMessage(
            Message.raw("[BetterMap] ").color("#93844c").bold(true)
                .insert(Message.raw("Reset cave exploration for all players. Cleared " + deletedFiles + " persisted file(s).")
                    .color("#bfcdd5"))
        );
    }

    private void applySavedPlayersDropdown(UICommandBuilder ui) {
        List<SavedPlayerEntry> savedPlayers = collectSavedPlayersWithData();
        List<DropdownEntryInfo> entries = new ArrayList<>();
        Map<String, String> labelsByUuid = new HashMap<>();

        for (SavedPlayerEntry entry : savedPlayers) {
            String uuid = entry.uuid.toString();
            labelsByUuid.put(uuid, entry.label);
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(entry.label), uuid));
        }

        if (entries.isEmpty()) {
            selectedExplorationPlayerUuid = null;
            selectedExplorationPlayerLabel = null;
            entries.add(new DropdownEntryInfo(LocalizableString.fromString("No saved players found"), ""));
            ui.set("#AdminExplorationPlayerSelect.Entries", entries);
            ui.set("#AdminExplorationPlayerSelect.Value", "");
            return;
        }

        String currentSelection = selectedExplorationPlayerUuid;
        if (currentSelection == null || !labelsByUuid.containsKey(currentSelection)) {
            currentSelection = savedPlayers.get(0).uuid.toString();
        }

        selectedExplorationPlayerUuid = currentSelection;
        selectedExplorationPlayerLabel = labelsByUuid.get(currentSelection);

        ui.set("#AdminExplorationPlayerSelect.Entries", entries);
        ui.set("#AdminExplorationPlayerSelect.Value", currentSelection);
    }

    @Nonnull
    private List<SavedPlayerEntry> collectSavedPlayersWithData() {
        Set<UUID> savedUuids = ExplorationManager.getInstance().getAllSavedPlayerUuids();
        List<SavedPlayerEntry> result = new ArrayList<>(savedUuids.size());
        Map<UUID, String> onlineNames = new HashMap<>();

        Universe universe = Universe.get();
        if (universe != null) {
            for (PlayerRef playerRef : universe.getPlayers()) {
                if (playerRef != null) {
                    onlineNames.put(playerRef.getUuid(), playerRef.getUsername());
                }
            }
        }

        List<UUID> sortedUuids = new ArrayList<>(savedUuids);
        sortedUuids.sort(Comparator.comparing(UUID::toString));

        for (UUID uuid : sortedUuids) {
            String username = onlineNames.get(uuid);
            if (username != null && !username.isBlank()) {
                result.add(new SavedPlayerEntry(uuid, username + " (" + uuid + ")"));
            } else {
                result.add(new SavedPlayerEntry(uuid, "UUID: " + uuid));
            }
        }

        return result;
    }

    private void resetSelectedPlayerSurfaceExploration(Player actor, @Nonnull UUID playerUuid, @Nonnull String label) {
        int deletedMapFiles = ExplorationManager.getInstance().resetMapExplorationForPlayer(playerUuid);

        Universe universe = Universe.get();
        if (universe != null) {
            PlayerRef playerRef = universe.getPlayer(playerUuid);
            if (playerRef != null) {
                Ref<EntityStore> pStoreRef = playerRef.getReference();
                if (pStoreRef != null && pStoreRef.isValid()) {
                    Player target = pStoreRef.getStore().getComponent(pStoreRef, Player.getComponentType());
                    if (target != null) {
                        WorldMapHook.clearCaveModeLoadedChunks(target.getDisplayName());
                        WorldMapHook.forceFullMapRefresh(target);
                    }
                }
            }
        }

        actor.sendMessage(
            Message.raw("[BetterMap] ").color("#93844c").bold(true)
                .insert(Message.raw("Reset surface exploration for " + label + ". Cleared " + deletedMapFiles + " map file(s).")
                    .color("#bfcdd5"))
        );
    }

    private void resetSelectedPlayerCaveExploration(Player actor, @Nonnull UUID playerUuid, @Nonnull String label) {
        int deletedCaveFiles = ExplorationManager.getInstance().resetCaveExplorationForPlayer(playerUuid);
        WorldMapHook.clearSharedCaveExplorationCache();

        Universe universe = Universe.get();
        if (universe != null) {
            PlayerRef playerRef = universe.getPlayer(playerUuid);
            if (playerRef != null) {
                Ref<EntityStore> pStoreRef = playerRef.getReference();
                if (pStoreRef != null && pStoreRef.isValid()) {
                    Player target = pStoreRef.getStore().getComponent(pStoreRef, Player.getComponentType());
                    if (target != null) {
                        CaveModeManager.getInstance().clearCaveExploration(target);
                        WorldMapHook.clearCaveModeLoadedChunks(target.getDisplayName());
                        WorldMapHook.forceFullMapRefresh(target);
                    }
                }
            }
        }

        actor.sendMessage(
            Message.raw("[BetterMap] ").color("#93844c").bold(true)
                .insert(Message.raw("Reset cave exploration for " + label + ". Cleared " + deletedCaveFiles + " cave file(s).")
                    .color("#bfcdd5"))
        );
    }

    private void showExplorationResetConfirm(UICommandBuilder ui, UIEventBuilder events, ExplorationResetType type) {
        pendingExplorationResetType = type;
        pendingExplorationResetPlayerUuid = null;
        pendingExplorationResetPlayerLabel = null;

        if (type == ExplorationResetType.PLAYER_SURFACE || type == ExplorationResetType.PLAYER_CAVE) {
            pendingExplorationResetPlayerUuid = selectedExplorationPlayerUuid;
            pendingExplorationResetPlayerLabel = selectedExplorationPlayerLabel;
        }

        ui.set("#AdminExplorationResetConfirm.Visible", true);
        if (type == ExplorationResetType.CAVE) {
            ui.set("#AdminExplorationResetConfirmMessage.Text",
                "Are you sure you want to delete all cave exploration data for all players? This action is irreversible.");
        } else if (type == ExplorationResetType.PLAYER_SURFACE) {
            String label = pendingExplorationResetPlayerLabel != null ? pendingExplorationResetPlayerLabel : "selected player";
            ui.set("#AdminExplorationResetConfirmMessage.Text",
                "Are you sure you want to delete all surface exploration data for " + label + "? This action is irreversible.");
        } else if (type == ExplorationResetType.PLAYER_CAVE) {
            String label = pendingExplorationResetPlayerLabel != null ? pendingExplorationResetPlayerLabel : "selected player";
            ui.set("#AdminExplorationResetConfirmMessage.Text",
                "Are you sure you want to delete all cave exploration data for " + label + "? This action is irreversible.");
        } else {
            ui.set("#AdminExplorationResetConfirmMessage.Text",
                "Are you sure you want to delete all map exploration data for all players? This action is irreversible.");
        }
        sendUpdate(ui, events, false);
    }

    private void cancelExplorationReset(UICommandBuilder ui, UIEventBuilder events) {
        pendingExplorationResetType = null;
        pendingExplorationResetPlayerUuid = null;
        pendingExplorationResetPlayerLabel = null;
        ui.set("#AdminExplorationResetConfirm.Visible", false);
        sendUpdate(ui, events, false);
    }

    private void confirmExplorationReset(UICommandBuilder ui, UIEventBuilder events, Player player) {
        ExplorationResetType action = pendingExplorationResetType;
        String pendingUuid = pendingExplorationResetPlayerUuid;
        String pendingLabel = pendingExplorationResetPlayerLabel;
        pendingExplorationResetType = null;
        pendingExplorationResetPlayerUuid = null;
        pendingExplorationResetPlayerLabel = null;
        ui.set("#AdminExplorationResetConfirm.Visible", false);
        sendUpdate(ui, events, false);

        if (action == ExplorationResetType.CAVE) {
            resetAllCaveExploration(player);
        } else if (action == ExplorationResetType.MAP) {
            resetAllMapExploration(player);
        } else if (action == ExplorationResetType.PLAYER_SURFACE || action == ExplorationResetType.PLAYER_CAVE) {
            if (pendingUuid == null || pendingUuid.isBlank()) {
                player.sendMessage(Message.raw("No saved player selected.").color("#ff4a4a"));
                return;
            }

            try {
                UUID playerUuid = UUID.fromString(pendingUuid);
                String label = pendingLabel != null ? pendingLabel : playerUuid.toString();
                if (action == ExplorationResetType.PLAYER_SURFACE) {
                    resetSelectedPlayerSurfaceExploration(player, playerUuid, label);
                } else {
                    resetSelectedPlayerCaveExploration(player, playerUuid, label);
                }
            } catch (IllegalArgumentException e) {
                player.sendMessage(Message.raw("Invalid player selection.").color("#ff4a4a"));
            }
        }
    }

    private void updateCaveRadiusForAllPlayers(int radius) {
        int clampedRadius = Math.max(1, Math.min(radius, 16));
        CaveModeManager caveModeManager = CaveModeManager.getInstance();
        caveModeManager.updateCaveRadiusForAllStates(clampedRadius);

        Universe universe = Universe.get();
        if (universe == null) return;

        for (World world : universe.getWorlds().values()) {
            if (world == null) continue;

            world.execute(() -> {
                for (PlayerRef pRef : world.getPlayerRefs()) {
                    Ref<EntityStore> pStoreRef = pRef.getReference();
                    if (pStoreRef == null || !pStoreRef.isValid()) continue;

                    Player p = pStoreRef.getStore().getComponent(pStoreRef, Player.getComponentType());
                    if (p == null) continue;

                    caveModeManager.getOrCreateState(p).setCaveRadius(clampedRadius);
                }
            });
        }
    }

    private void handlePlayerReset(UICommandBuilder ui, UIEventBuilder events, Player player) {
        PlayerConfig pConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());
        pConfig.resetToDefaults();
        PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());

        applyPlayerSettingsToUi(ui, pConfig);
        sendUpdate(ui, events, false);

        updatePlayerCaveState(player, pConfig.isCaveModeEnabled());
        World world = player.getWorld();
        if (world != null) {
            world.execute(() -> WorldMapHook.sendMapSettingsToPlayer(player));
            world.execute(() -> WorldMapHook.forceFullMapRefresh(player));
        }
    }

    private void handleAdminResetRequest(UICommandBuilder ui, UIEventBuilder events, Player player) {
        ModConfig gConfig = ModConfig.getInstance();
        if (!gConfig.isAllowedWorldsDefault()) {
            ui.set("#AdminResetConfirm.Visible", true);
            sendUpdate(ui, events, false);
            return;
        }
        handleAdminResetConfirm(ui, player, true);
    }

    private void handleAdminResetConfirm(UICommandBuilder ui, Player player, boolean resetWorlds) {
        ModConfig gConfig = ModConfig.getInstance();
        gConfig.resetToDefaults(resetWorlds);
        restartRequired = true;

        applyAdminSettingsToUi(ui, gConfig);
        ui.set("#AdminResetConfirm.Visible", false);
        sendUpdate(ui, new UIEventBuilder(), false);

        MapPrivacyManager.getInstance().updatePrivacyState();
        WorldBorderManager.getInstance().clearAllCaches();

        Universe universe = Universe.get();
        if (universe != null) {
            universe.getWorlds().values().forEach(WorldMapHook::refreshTrackers);
        }

        updatePlayerCaveState(player, gConfig.isCaveModeEnabled());
        refreshWorldPlayersMap(player.getWorld());
    }

    private void sendHostingLink(Player player) {
        String url = "https://zap-hosting.com/ninesliced?voucher=ninesliced";

        var packetHandler = playerRef.getPacketHandler();
        var primaryMessage = Message.raw("ZAP-Hosting Partner").color("#00aa00").bold(true);
        var secondaryMessage = Message.raw("Click the link below to get a discount!").color("#bfcdd5");
        var icon = new ItemStack("Deco_Trophy_Harvest", 1).toPacket();

        NotificationUtil.sendNotification(
            packetHandler,
            primaryMessage,
            secondaryMessage,
            icon
        );

        Message linkMessage = Message.raw("")
            .insert(Message.raw("[BetterMap] ").color("#93844c").bold(true))
            .insert(Message.raw("Click here to get a discount on your game server: ").color("#bfcdd5"))
            .insert(Message.raw(url).color("#4c9cff").link(url));
        player.sendMessage(linkMessage);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ConfigEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        switch (data.action) {
            case "view_player" -> {
                ui.set("#PlayerView.Visible", true);
                ui.set("#AdminView.Visible", false);
                ui.set("#PlayerViewBtnContainer.Visible", false);
                ui.set("#PlayerViewBtnSelectedContainer.Visible", true);
                ui.set("#AdminViewBtnContainer.Visible", true);
                ui.set("#AdminViewBtnSelectedContainer.Visible", false);
                ui.set("#HelpViewBtnContainer.Visible", true);
                ui.set("#HelpViewBtnSelectedContainer.Visible", false);
                sendUpdate(ui, events, false);
                return;
            }
            case "view_admin" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    ui.set("#PlayerView.Visible", false);
                    ui.set("#AdminView.Visible", true);
                    ui.set("#PlayerViewBtnContainer.Visible", true);
                    ui.set("#PlayerViewBtnSelectedContainer.Visible", false);
                    ui.set("#AdminViewBtnContainer.Visible", false);
                    ui.set("#AdminViewBtnSelectedContainer.Visible", true);
                    ui.set("#HelpViewBtnContainer.Visible", true);
                    ui.set("#HelpViewBtnSelectedContainer.Visible", false);
                    sendUpdate(ui, events, false);
                }
                return;
            }
            case "open_waypoints" -> {
                player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(playerRef));
                return;
            }
            case "open_help" -> {
                player.getPageManager().openCustomPage(ref, store, new HelpMenuPage(playerRef));
                return;
            }
            case "player_reset_defaults" -> {
                handlePlayerReset(ui, events, player);
                return;
            }
            case "admin_reset_defaults" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    handleAdminResetRequest(ui, events, player);
                }
                return;
            }
            case "admin_reset_confirm_all" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    handleAdminResetConfirm(ui, player, true);
                }
                return;
            }
            case "admin_reset_confirm_keep_worlds" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    handleAdminResetConfirm(ui, player, false);
                }
                return;
            }
            case "admin_reset_cancel" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    ui.set("#AdminResetConfirm.Visible", false);
                    sendUpdate(ui, events, false);
                }
                return;
            }
            case "admin_reset_map_exploration" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    showExplorationResetConfirm(ui, events, ExplorationResetType.MAP);
                }
                return;
            }
            case "admin_reset_cave_exploration" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    showExplorationResetConfirm(ui, events, ExplorationResetType.CAVE);
                }
                return;
            }
            case "admin_exploration_player_select" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    String value = data.getEffectiveValue();
                    if (value == null || value.isBlank()) {
                        selectedExplorationPlayerUuid = null;
                        selectedExplorationPlayerLabel = null;
                    } else {
                        selectedExplorationPlayerUuid = value;
                        selectedExplorationPlayerLabel = collectSavedPlayersWithData().stream()
                            .filter(entry -> entry.uuid.toString().equals(value))
                            .map(entry -> entry.label)
                            .findFirst()
                            .orElse(value);
                    }
                }
                return;
            }
            case "admin_reset_selected_player_surface_exploration" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    if (selectedExplorationPlayerUuid == null || selectedExplorationPlayerUuid.isBlank()) {
                        player.sendMessage(Message.raw("No saved player selected.").color("#ff4a4a"));
                        return;
                    }
                    showExplorationResetConfirm(ui, events, ExplorationResetType.PLAYER_SURFACE);
                }
                return;
            }
            case "admin_reset_selected_player_cave_exploration" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    if (selectedExplorationPlayerUuid == null || selectedExplorationPlayerUuid.isBlank()) {
                        player.sendMessage(Message.raw("No saved player selected.").color("#ff4a4a"));
                        return;
                    }
                    showExplorationResetConfirm(ui, events, ExplorationResetType.PLAYER_CAVE);
                }
                return;
            }
            case "admin_exploration_reset_confirm" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    confirmExplorationReset(ui, events, player);
                }
                return;
            }
            case "admin_exploration_reset_cancel" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    cancelExplorationReset(ui, events);
                }
                return;
            }
            case "close_menu" -> {
                if (restartRequired) {
                    var packetHandler = playerRef.getPacketHandler();

                    var primaryMessage = Message.raw("Restart Required").color("#FF0000");
                    var secondaryMessage = Message.raw("Map settings changed. Restart server to apply.").color("#FFAA00");
                    var icon = new ItemStack("Weapon_Spellbook_Demon", 1).toPacket();

                    NotificationUtil.sendNotification(
                        packetHandler,
                        primaryMessage,
                        secondaryMessage,
                        icon
                    );
                }
                player.getPageManager().setPage(ref, store, Page.None);
                return;
            }
            case "open_hosting_link" -> {
                sendHostingLink(player);
                return;
            }
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
                case "player_location":
                    if (!ModConfig.getInstance().isLocationEnabled()) {
                        break;
                    }
                    boolean locationEnabled = Boolean.parseBoolean(val);
                    pConfig.setLocationEnabled(locationEnabled);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    break;
                case "player_location_pos":
                    if (!ModConfig.getInstance().isLocationEnabled()) {
                        break;
                    }
                    pConfig.setLocationHudPosition(val.trim().toLowerCase());
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    break;
                case "player_cavemode":
                    if (!ModConfig.getInstance().isCaveModeEnabled()) {
                        break;
                    }
                    boolean enabled = Boolean.parseBoolean(val);
                    pConfig.setCaveModeEnabled(enabled);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
                    if (state != null) {
                        state.setDynamicModeEnabled(enabled);
                        if (!enabled) {
                            state.setCurrentlyUnderground(false);
                        }
                    }
                    if (world != null) {
                        world.execute(() -> WorldMapHook.forceFullMapRefresh(player));
                    }
                    break;
                case "player_hide_players":
                    if (world == null) break;
                    boolean newPlayersVisible = determineNewVisibilityState(
                        pConfig.isOverrideGlobalPlayersHide() && !pConfig.isHidePlayersOnMap(),
                        pConfig.isHidePlayersOnMap()
                    );
                    pConfig.setOverrideGlobalPlayersHide(newPlayersVisible);
                    pConfig.setHidePlayersOnMap(!newPlayersVisible);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    MapPrivacyManager.getInstance().updatePrivacyState();
                    refreshHideState(world);
                    break;
                case "player_hide_all_warps":
                    if (world == null) break;
                    boolean newAllWarpsVisible = determineNewVisibilityState(
                        pConfig.isOverrideGlobalAllWarpsHide() && !pConfig.isHideAllWarpsOnMap(),
                        pConfig.isHideAllWarpsOnMap()
                    );
                    pConfig.setOverrideGlobalAllWarpsHide(newAllWarpsVisible);
                    pConfig.setHideAllWarpsOnMap(!newAllWarpsVisible);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    WarpPrivacyManager.getInstance().updatePrivacyState();
                    refreshHideState(world);
                    break;
                case "player_hide_other_warps":
                    if (world == null) break;
                    if (!ExtendedTeleportIntegration.getInstance().isAvailable()) {
                        playerRef.sendMessage(Message.raw("This feature requires ExtendedTeleport to be installed."));
                        break;
                    }
                    boolean newOtherWarpsVisible = determineNewVisibilityState(
                        pConfig.isOverrideGlobalOtherWarpsHide() && !pConfig.isHideOtherWarpsOnMap(),
                        pConfig.isHideOtherWarpsOnMap()
                    );
                    pConfig.setOverrideGlobalOtherWarpsHide(newOtherWarpsVisible);
                    pConfig.setHideOtherWarpsOnMap(!newOtherWarpsVisible);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    WarpPrivacyManager.getInstance().updatePrivacyState();
                    refreshHideState(world);
                    break;
                case "player_hide_all_pois":
                    if (world == null) break;
                    boolean newPoisVisible = determineNewVisibilityState(
                        pConfig.isOverrideGlobalPoiHide() && !pConfig.isHideAllPoiOnMap(),
                        pConfig.isHideAllPoiOnMap()
                    );
                    pConfig.setOverrideGlobalPoiHide(newPoisVisible);
                    pConfig.setHideAllPoiOnMap(!newPoisVisible);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
                    refreshHideState(world);
                    break;
                case "player_hide_spawn":
                    if (world == null) break;
                    boolean newSpawnVisible = determineNewVisibilityState(
                        pConfig.isOverrideGlobalSpawnHide() && !pConfig.isHideSpawnOnMap(),
                        pConfig.isHideSpawnOnMap()
                    );
                    pConfig.setOverrideGlobalSpawnHide(newSpawnVisible);
                    pConfig.setHideSpawnOnMap(!newSpawnVisible);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
                    refreshHideState(world);
                    break;
                case "player_hide_death":
                    if (world == null) break;
                    boolean newDeathVisible = determineNewVisibilityState(
                        pConfig.isOverrideGlobalDeathHide() && !pConfig.isHideDeathMarkerOnMap(),
                        pConfig.isHideDeathMarkerOnMap()
                    );
                    pConfig.setOverrideGlobalDeathHide(newDeathVisible);
                    pConfig.setHideDeathMarkerOnMap(!newDeathVisible);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    if (!newDeathVisible) {
                        removeDeathMarkersFromClient(player, world);
                    }
                    PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
                    refreshHideState(world);
                    break;
                case "player_hide_global_waypoints":
                    if (world == null) break;
                    boolean newGlobalWaypointsVisible = determineNewVisibilityState(
                        pConfig.isOverrideGlobalWaypointHide() && !pConfig.isHideGlobalWaypointsOnMap(),
                        pConfig.isHideGlobalWaypointsOnMap()
                    );
                    pConfig.setOverrideGlobalWaypointHide(newGlobalWaypointsVisible);
                    pConfig.setHideGlobalWaypointsOnMap(!newGlobalWaypointsVisible);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    UserMarkerProviderManager.getInstance().onWorldLoad(world);
                    MapPrivacyManager.getInstance().updatePrivacyState();
                    refreshHideState(world);
                    break;
                case "player_hide_personal_waypoints":
                    if (world == null) break;
                    pConfig.setHidePersonalWaypointsOnMap(!pConfig.isHidePersonalWaypointsOnMap());
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    UserMarkerProviderManager.getInstance().onWorldLoad(world);
                    MapPrivacyManager.getInstance().updatePrivacyState();
                    refreshHideState(world);
                    break;
            }
            PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
        } catch (NumberFormatException _) {}
    }

    private void handleAdminUpdate(ConfigEventData data, UICommandBuilder ui, Player player) {
        ModConfig gConfig = ModConfig.getInstance();
        String val = data.getEffectiveValue();
        try {
            switch (data.action) {
                case "admin_exp_radius":
                     if (val != null) gConfig.setExplorationRadius(Integer.parseInt(val));
                     break;
                case "admin_map_quality":
                    if (val != null && !val.isBlank()) {
                        try {
                            MapQuality newQuality = MapQuality.valueOf(val.trim().toUpperCase());
                            gConfig.setQuality(newQuality);
                            ui.set("#AdminMaxChunksToLoad.Value", gConfig.getMaxChunksToLoad());
                            sendUpdate(ui, new UIEventBuilder(), false);

                            var packetHandler = playerRef.getPacketHandler();
                            var primaryMessage = Message.raw("Restart Required").color("#FF0000");
                            var secondaryMessage = Message.raw("Map quality changed. Restart server to apply.").color("#FFAA00");
                            var icon = new ItemStack("Weapon_Spellbook_Demon", 1).toPacket();

                            NotificationUtil.sendNotification(
                                    packetHandler,
                                    primaryMessage,
                                    secondaryMessage,
                                    icon
                            );
                        } catch (IllegalArgumentException ignored) {}
                    }
                    break;
                case "admin_max_chunks":
                    if (val != null) {
                        try {
                            int inputValue = Integer.parseInt(val);
                            int maxAllowed = gConfig.getMapQuality().maxChunks;

                            if (inputValue > maxAllowed) {
                                gConfig.setMaxChunksToLoad(maxAllowed);
                                ui.set("#AdminMaxChunksToLoad.Value", maxAllowed);
                                sendUpdate(ui, new UIEventBuilder(), false);

                                var packetHandler = playerRef.getPacketHandler();

                                var primaryMessage = Message.raw("Limit Exceeded").color("#FF0000");
                                var secondaryMessage = Message.raw("Max for " + gConfig.getMapQuality().name() + " quality is " + maxAllowed).color("#FFAA00");
                                var icon = new ItemStack("Recipe_Book_Magic_Void", 1).toPacket();

                                NotificationUtil.sendNotification(
                                        packetHandler,
                                        primaryMessage,
                                        secondaryMessage,
                                        icon
                                );
                            } else {
                                gConfig.setMaxChunksToLoad(inputValue);
                            }
                            restartRequired = true;
                        } catch (NumberFormatException ignored) {}
                    }
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
                case "admin_location_enabled":
                    if (val != null) gConfig.setLocationEnabled(Boolean.parseBoolean(val));
                    break;
                case "admin_location_pos":
                    if (val != null && !val.isBlank()) {
                        gConfig.setLocationHudPosition(val.trim().toLowerCase());
                    }
                    break;
                case "admin_radar_enabled":
                     if (val != null) gConfig.setRadarEnabled(Boolean.parseBoolean(val));
                    break;
                case "admin_radar_range":
                     if (val != null) gConfig.setRadarRange(Integer.parseInt(val));
                    break;
                case "admin_marker_personal_limit":
                    if (val != null) {
                        int limit = Integer.parseInt(val);
                        gConfig.setMaxPersonalMarkersPerPlayer(limit);
                        ui.set("#AdminMaxPersonalMarkers.Value", gConfig.getMaxPersonalMarkersPerPlayer());
                        sendUpdate(ui, new UIEventBuilder(), false);
                        WaypointLimitUtil.applyOverridesToAllWorlds(
                            gConfig.getMaxPersonalMarkersPerPlayer(),
                            gConfig.getMaxSharedMarkersPerPlayer()
                        );
                    }
                    break;
                case "admin_marker_shared_limit":
                    if (val != null) {
                        int limit = Integer.parseInt(val);
                        gConfig.setMaxSharedMarkersPerPlayer(limit);
                        ui.set("#AdminMaxSharedMarkers.Value", gConfig.getMaxSharedMarkersPerPlayer());
                        sendUpdate(ui, new UIEventBuilder(), false);
                        WaypointLimitUtil.applyOverridesToAllWorlds(
                            gConfig.getMaxPersonalMarkersPerPlayer(),
                            gConfig.getMaxSharedMarkersPerPlayer()
                        );
                    }
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
                case "admin_world_border_enabled":
                    if (val != null) {
                        gConfig.setWorldBorderEnabled(Boolean.parseBoolean(val));
                        WorldBorderManager.getInstance().clearAllCaches();
                    }
                    break;
                case "admin_world_border_radius":
                    if (val != null) {
                        gConfig.setWorldBorderRadius(Integer.parseInt(val));
                        WorldBorderManager.getInstance().clearAllCaches();
                    }
                    break;
                case "admin_world_border_offset_x":
                    if (val != null) {
                        gConfig.setWorldBorderOffsetX(Integer.parseInt(val));
                        WorldBorderManager.getInstance().clearAllCaches();
                    }
                    break;
                case "admin_world_border_offset_z":
                    if (val != null) {
                        gConfig.setWorldBorderOffsetZ(Integer.parseInt(val));
                        WorldBorderManager.getInstance().clearAllCaches();
                    }
                    break;
                case "admin_cavemode_enabled":
                    if (val != null) {
                        boolean caveEnabled = Boolean.parseBoolean(val);
                        gConfig.setCaveModeEnabled(caveEnabled);
                        CaveModeManager.DynamicCaveModeState caveState = CaveModeManager.getInstance().getState(player);
                        if (caveState != null) {
                            caveState.setDynamicModeEnabled(caveEnabled);
                            if (!caveEnabled) {
                                caveState.setCurrentlyUnderground(false);
                            }
                        }
                        World caveWorld = player.getWorld();
                        if (caveWorld != null) {
                            caveWorld.execute(() -> {
                                for (PlayerRef pRef : caveWorld.getPlayerRefs()) {
                                    Ref<EntityStore> ref = pRef.getReference();
                                    if (ref == null || !ref.isValid()) continue;
                                    Player p = ref.getStore().getComponent(ref, Player.getComponentType());
                                    if (p == null) continue;

                                    try {
                                        WorldMapHook.forceFullMapRefresh(p);
                                    } catch (Exception e) {
                                        LOGGER.warning("Failed to refresh map for fog of war: " + e.getMessage());
                                    }
                                }
                            });
                        }
                    }
                    break;
                case "admin_discover_surface":
                    if (val != null) {
                        boolean discoverSurface = Boolean.parseBoolean(val);
                        gConfig.setDiscoverSurfaceUnderground(discoverSurface);
                    }
                    break;
                case "admin_cave_fog_of_war":
                    if (val != null) {
                        boolean fogOfWar = Boolean.parseBoolean(val);
                        gConfig.setCaveFogOfWar(fogOfWar);
                        World fogWorld = player.getWorld();
                        if (fogWorld != null) {
                            fogWorld.execute(() -> {
                                for (PlayerRef pRef : fogWorld.getPlayerRefs()) {
                                    Ref<EntityStore> ref = pRef.getReference();
                                    if (ref == null || !ref.isValid()) continue;
                                    Player p = ref.getStore().getComponent(ref, Player.getComponentType());
                                    if (p == null) continue;

                                    try {
                                        WorldMapHook.forceFullMapRefresh(p);
                                    } catch (Exception e) {
                                        LOGGER.warning("Failed to refresh map for fog of war: " + e.getMessage());
                                    }
                                }
                            });
                        }
                    }
                    break;
                case "admin_cavemode_layer":
                    if (val != null) {
                        int layerSize = Integer.parseInt(val);
                        layerSize = Math.max(1, Math.min(layerSize, 20));
                        gConfig.setCaveModeLayerSize(layerSize);
                        final int finalLayerSize = layerSize;
                        World layerWorld = player.getWorld();
                        if (layerWorld != null) {
                            for (PlayerRef pRef : layerWorld.getPlayerRefs()) {
                                var pHolder = pRef.getHolder();
                                if (pHolder != null) {
                                    Player p = pHolder.getComponent(Player.getComponentType());
                                    if (p != null) {
                                        CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(p);
                                        if (state != null) {
                                            state.setLayerSize(finalLayerSize);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                case "admin_cavemode_threshold":
                    if (val != null) {
                        int threshold = Integer.parseInt(val);
                        threshold = Math.max(0, Math.min(threshold, 319));
                        gConfig.setCaveModeUndergroundThreshold(threshold);
                        final int finalThreshold = threshold;
                        World threshWorld = player.getWorld();
                        if (threshWorld != null) {
                            for (PlayerRef pRef : threshWorld.getPlayerRefs()) {
                                var pHolder = pRef.getHolder();
                                if (pHolder != null) {
                                    Player p = pHolder.getComponent(Player.getComponentType());
                                    if (p != null) {
                                        CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(p);
                                        if (state != null) {
                                            state.setUndergroundThreshold(finalThreshold);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                case "admin_cavemode_radius":
                    if (val != null) {
                        int radius = Integer.parseInt(val);
                        radius = Math.max(1, Math.min(radius, 16));
                        gConfig.setCaveModeRadius(radius);
                        updateCaveRadiusForAllPlayers(radius);
                    }
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

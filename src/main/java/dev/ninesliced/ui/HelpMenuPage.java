package dev.ninesliced.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.Message;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.PlayerRefUtil;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

import dev.ninesliced.utils.PlayerRefUtil;
public class HelpMenuPage extends InteractiveCustomUIPage<HelpMenuPage.HelpEventData> {

    private static final Logger LOGGER = Logger.getLogger(HelpMenuPage.class.getName());
    private static final String LAYOUT_PATH = "Pages/BetterMap/HelpMenu.ui";

    private static final String[] ALL_DETAIL_PANELS = {
        "#DetailMapSettings",
        "#DetailLocationHud",
        "#DetailPlayerCaveMode",
        "#DetailGlobalMap",
        "#DetailFeatureToggles",
        "#DetailRadar",
        "#DetailHideMarkers",
        "#DetailWorldSettings",
        "#DetailCaveMode",
        "#DetailWorldBorder"
    };

    private String openPanel = null;

    public HelpMenuPage(PlayerRef player) {
        super(player, CustomPageLifetime.CanDismiss, HelpEventData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append(LAYOUT_PATH);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        boolean isAdmin = PermissionsUtil.isAdmin(player);

        if (isAdmin) {
            ui.set("#AdminCategoriesSection.Visible", true);
        }

        // Back button -> return to config
        bindClick(events, "#BackBtn", "back_to_config");

        // Discord help button
        bindClick(events, "#NeedHelpBtn", "need_more_help");

        // Player category buttons
        bindClick(events, "#CatMapSettings", "toggle_map_settings");
        bindClick(events, "#CatLocationHud", "toggle_location_hud");
        bindClick(events, "#CatPlayerCaveMode", "toggle_player_cavemode");

        // Admin category buttons
        if (isAdmin) {
            bindClick(events, "#CatGlobalMap", "toggle_global_map");
            bindClick(events, "#CatFeatureToggles", "toggle_feature_toggles");
            bindClick(events, "#CatRadar", "toggle_radar");
            bindClick(events, "#CatHideMarkers", "toggle_hide_markers");
            bindClick(events, "#CatWorldSettings", "toggle_world_settings");
            bindClick(events, "#CatCaveMode", "toggle_cavemode");
            bindClick(events, "#CatWorldBorder", "toggle_world_border");
        }
    }

    private void bindClick(UIEventBuilder events, String elementId, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, elementId,
            new EventData().put("Action", action),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull HelpEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        switch (data.action) {
            case "back_to_config" -> {
                player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(playerRef));
                return;
            }
            case "need_more_help" -> {
                String url = "https://discord.gg/8q7NCUm4xw";
                Message helpMessage = Message.raw("")
                    .insert(Message.raw("[BetterMap] ").color("#F38043").bold(true))
                    .insert(Message.raw("Need help? Join our Discord and open a ticket:\n").color("#ffffff"))
                    .insert(Message.raw(url).color("#4c9cff").link(url));
                PlayerRefUtil.resolve(player).sendMessage(helpMessage);
                player.getPageManager().setPage(ref, store, Page.None);
                return;
            }
            case "toggle_map_settings" -> togglePanel(ui, "#DetailMapSettings");
            case "toggle_location_hud" -> togglePanel(ui, "#DetailLocationHud");
            case "toggle_player_cavemode" -> togglePanel(ui, "#DetailPlayerCaveMode");
            case "toggle_global_map" -> {
                if (!PermissionsUtil.isAdmin(player)) return;
                togglePanel(ui, "#DetailGlobalMap");
            }
            case "toggle_feature_toggles" -> {
                if (!PermissionsUtil.isAdmin(player)) return;
                togglePanel(ui, "#DetailFeatureToggles");
            }
            case "toggle_radar" -> {
                if (!PermissionsUtil.isAdmin(player)) return;
                togglePanel(ui, "#DetailRadar");
            }
            case "toggle_hide_markers" -> {
                if (!PermissionsUtil.isAdmin(player)) return;
                togglePanel(ui, "#DetailHideMarkers");
            }
            case "toggle_world_settings" -> {
                if (!PermissionsUtil.isAdmin(player)) return;
                togglePanel(ui, "#DetailWorldSettings");
            }
            case "toggle_cavemode" -> {
                if (!PermissionsUtil.isAdmin(player)) return;
                togglePanel(ui, "#DetailCaveMode");
            }
            case "toggle_world_border" -> {
                if (!PermissionsUtil.isAdmin(player)) return;
                togglePanel(ui, "#DetailWorldBorder");
            }
            default -> { return; }
        }

        sendUpdate(ui, events, false);
    }

    private void togglePanel(UICommandBuilder ui, String panelId) {
        if (panelId.equals(openPanel)) {
            // Clicking the same category again -> collapse it
            ui.set(panelId + ".Visible", false);
            openPanel = null;
        } else {
            // Close any currently open panel
            if (openPanel != null) {
                ui.set(openPanel + ".Visible", false);
            }
            // Open the new panel
            ui.set(panelId + ".Visible", true);
            openPanel = panelId;
        }
    }

    public static class HelpEventData {
        public String action;

        public HelpEventData() {}

        @SuppressWarnings("deprecation")
        public static final BuilderCodec<HelpEventData> CODEC = BuilderCodec.builder(HelpEventData.class, HelpEventData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action)
            .build();
    }
}

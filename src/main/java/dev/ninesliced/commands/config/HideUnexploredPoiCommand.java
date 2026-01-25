package dev.ninesliced.commands.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle hiding POI markers in unexplored regions on the world map.
 */
public class HideUnexploredPoiCommand extends AbstractCommand {

    public HideUnexploredPoiCommand() {
        super("hideunexploredpoi", "Toggle hiding POIs in unexplored regions");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
        this.addAliases("hideunexploredpois");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        if (!commandContext.isPlayer()) {
            commandContext.sendMessage(Message.raw("This command can only be used by a player.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
        if (ref == null) {
            return CompletableFuture.completedFuture(null);
        }

        var store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            BetterMapConfig config = BetterMapConfig.getInstance();
            boolean newState = !config.isHideUnexploredPoiOnMap();
            config.setHideUnexploredPoiOnMap(newState);

            // Reset player overrides BEFORE updating privacy state so the state is consistent
            PlayerConfig playerConfig = playerRef.getUuid() != null
                ? PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid())
                : null;
            if (playerConfig != null) {
                playerConfig.setOverrideGlobalPoiHide(false);
                PlayerConfigManager.getInstance().savePlayerConfig(playerRef.getUuid());
            }

            PoiPrivacyManager.getInstance().updatePrivacyState(world);
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            boolean visible = !newState;
            Color color = visible ? Color.GREEN : Color.RED;
            String status = visible ? "VISIBLE" : "HIDDEN";

            playerRef.sendMessage(Message.raw("Unexplored POIs are now " + status + " on the map.").color(color));
        }, world);
    }
}

package dev.ninesliced.commands.config;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

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

/**
 * Command to toggle hiding the spawn marker on the world map.
 */
public class HideSpawnCommand extends AbstractCommand {

    public HideSpawnCommand() {
        super("hidespawn", "Toggle hiding the spawn marker");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
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
            boolean newState = !config.isHideSpawnOnMap();
            config.setHideSpawnOnMap(newState);

            // Reset player overrides BEFORE updating privacy state so the state is consistent
            PlayerConfig playerConfig = playerRef.getUuid() != null
                ? PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid())
                : null;
            if (playerConfig != null) {
                playerConfig.setOverrideGlobalSpawnHide(false);
                PlayerConfigManager.getInstance().savePlayerConfig(playerRef.getUuid());
            }

            PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            boolean visible = !newState;
            Color color = visible ? Color.GREEN : Color.RED;
            String status = visible ? "VISIBLE" : "HIDDEN";

            playerRef.sendMessage(Message.raw("Spawn markers are now " + status + " on the map.").color(color));
        }, world);
    }
}

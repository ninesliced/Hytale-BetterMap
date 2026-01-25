package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.managers.WarpPrivacyManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle hiding warps in unexplored regions on the world map.
 */
public class HideUnexploredWarpsCommand extends AbstractCommand {

    public HideUnexploredWarpsCommand() {
        super("hideunexploredwarps", "Toggle hiding warps in unexplored regions");
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
            boolean newState = !config.isHideUnexploredWarpsOnMap();
            config.setHideUnexploredWarpsOnMap(newState);

            WarpPrivacyManager.getInstance().updatePrivacyState();

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            playerRef.sendMessage(Message.raw("Hide Unexplored Warps " + status).color(color));
            if (newState) {
                playerRef.sendMessage(Message.raw("Warps in unexplored regions are hidden on the world map.").color(Color.GRAY));
            } else {
                playerRef.sendMessage(Message.raw("Warps in unexplored regions are visible on the world map.").color(Color.GRAY));
            }
            playerRef.sendMessage(Message.raw("NOTE: It may take a few seconds for markers to refresh.").color(Color.GRAY));
        }, world);
    }
}

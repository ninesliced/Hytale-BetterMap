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
import dev.ninesliced.managers.MapPrivacyManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the 'hide players on map' privacy feature.
 * <p>
 * When enabled, all players are hidden from the world map (similar to NoPlayersOnMap).
 * </p>
 */
public class HidePlayersCommand extends AbstractCommand {

    public HidePlayersCommand() {
        super("hideplayers", "Toggle global map privacy (Hides players from map)");
    }

    /**
     * Executes the hide players toggle command.
     *
     * @param commandContext The command execution context.
     * @return A CompletableFuture representing the asynchronous execution.
     */
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
            boolean newState = !config.isHidePlayersOnMap();
            config.setHidePlayersOnMap(newState);

            MapPrivacyManager.getInstance().updatePrivacyState();

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            playerRef.sendMessage(Message.raw("Global Map Privacy " + status).color(color));
            if (newState) {
                playerRef.sendMessage(Message.raw("Players are now hidden from the world map.").color(Color.GRAY));
            } else {
                playerRef.sendMessage(Message.raw("Players are now visible on the world map.").color(Color.GRAY));
            }
            playerRef.sendMessage(Message.raw("NOTE: You'll need to restart the server for this change to take effect.").color(Color.RED));
        }, world);
    }
}

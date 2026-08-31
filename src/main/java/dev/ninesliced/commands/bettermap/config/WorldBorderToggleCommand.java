package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.managers.WorldBorderManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the world border visualization on the map.
 */
public class WorldBorderToggleCommand extends AbstractCommand {

    public WorldBorderToggleCommand() {
        super("worldborder", "Toggle world border visualization on map");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    /**
     * Executes the world border toggle command.
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

            ModConfig config = ModConfig.getInstance();
            boolean newState = !config.isWorldBorderEnabled();
            config.setWorldBorderEnabled(newState);
            WorldBorderManager.getInstance().clearAllCaches();

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            playerRef.sendMessage(Message.raw("World Border Visualization " + status).color(color));
            if (newState) {
                playerRef.sendMessage(Message.raw("World border markers are now visible on the map.").color(Color.GRAY));
            } else {
                playerRef.sendMessage(Message.raw("World border markers are now hidden from the map.").color(Color.GRAY));
            }
        }, world);
    }
}

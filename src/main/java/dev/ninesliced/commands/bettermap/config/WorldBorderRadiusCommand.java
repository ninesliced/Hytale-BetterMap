package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
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
 * Command to set the world border radius.
 */
public class WorldBorderRadiusCommand extends AbstractCommand {

    private final RequiredArg<Integer> radiusArg = this.withRequiredArg("radius", "The border radius in blocks", ArgTypes.INTEGER);

    public WorldBorderRadiusCommand() {
        super("worldborderradius", "Set world border radius (usage: /bm config worldborderradius <radius>)");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    /**
     * Executes the world border radius command.
     *
     * @param commandContext The command execution context.
     * @return A CompletableFuture representing the asynchronous execution.
     */
    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        if (!commandContext.isPlayer()) {
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
        if (ref == null) {
            return CompletableFuture.completedFuture(null);
        }

        var store = ref.getStore();
        World world = store.getExternalData().getWorld();

        Integer radiusValue = commandContext.get(radiusArg);

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            ModConfig config = ModConfig.getInstance();

            if (radiusValue < 1) {
                playerRef.sendMessage(Message.raw("Radius must be at least 1 block.").color(Color.RED));
                return;
            }

            config.setWorldBorderRadius(radiusValue);
            WorldBorderManager.getInstance().clearAllCaches();
            playerRef.sendMessage(Message.raw("World border radius set to " + radiusValue + " blocks").color(Color.GREEN));
        }, world);
    }
}

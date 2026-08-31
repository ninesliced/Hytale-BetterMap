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
 * Command to set the world border offset (center point).
 */
public class WorldBorderOffsetCommand extends AbstractCommand {

    private final RequiredArg<Integer> offsetXArg = this.withRequiredArg("offsetX", "The X offset in blocks", ArgTypes.INTEGER);
    private final RequiredArg<Integer> offsetZArg = this.withRequiredArg("offsetZ", "The Z offset in blocks", ArgTypes.INTEGER);

    public WorldBorderOffsetCommand() {
        super("worldborderoffset", "Set world border offset (usage: /bm config worldborderoffset <x> <z>)");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    /**
     * Executes the world border offset command.
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

        Integer offsetX = commandContext.get(offsetXArg);
        Integer offsetZ = commandContext.get(offsetZArg);

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            ModConfig config = ModConfig.getInstance();

            config.setWorldBorderOffsetX(offsetX);
            config.setWorldBorderOffsetZ(offsetZ);
            WorldBorderManager.getInstance().clearAllCaches();
            playerRef.sendMessage(Message.raw("World border offset set to X: " + offsetX + ", Z: " + offsetZ).color(Color.GREEN));
        }, world);
    }
}

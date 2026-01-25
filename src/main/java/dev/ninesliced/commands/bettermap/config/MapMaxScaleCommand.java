package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ninesliced.configs.ModConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to set the maximum map scale (zoom in level).
 */
public class MapMaxScaleCommand extends AbstractCommand {
    private final RequiredArg<Float> zoomValueArg = this.withRequiredArg("value", "Max zoom value", ArgTypes.FLOAT);

    /**
     * Constructs the MapMaxScale command.
     */
    public MapMaxScaleCommand() {
        super("maxscale", "Set max map zoom scale (higher = zoom in closer)");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Executes the max scale command, validating and updating the configuration.
     *
     * @param context The command execution context.
     * @return A future that completes when execution is finished.
     */
    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        return CompletableFuture.runAsync(() -> {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
                return;
            }

            Float newMax = context.get(this.zoomValueArg);
            if (newMax <= 0.0f) {
                context.sendMessage(Message.raw("Max scale must be greater than 0").color(Color.RED));
                return;
            }

            ModConfig config = ModConfig.getInstance();
            if (newMax <= config.getMinScale()) {
                context.sendMessage(Message.raw("Max scale must be greater than min scale (" + config.getMinScale() + ")").color(Color.RED));
                return;
            }

            config.setMaxScale(newMax);

            context.sendMessage(Message.raw("Map max scale set to: ").color(Color.GREEN).insert(Message.raw(String.valueOf(newMax)).color(Color.YELLOW)));
        });
    }
}

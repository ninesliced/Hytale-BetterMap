package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ninesliced.configs.BetterMapConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to set the exploration radius (in chunks).
 */
public class MapExplorationRadiusCommand extends AbstractCommand {
    private final RequiredArg<Integer> radiusArg = this.withRequiredArg("radius", "Exploration radius in chunks", ArgTypes.INTEGER);

    /**
     * Constructs the MapExplorationRadiusCommand.
     */
    public MapExplorationRadiusCommand() {
        super("radius", "Set exploration radius in chunks");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Executes the radius command, validating and updating the configuration.
     *
     * @param context The command execution context.
     * @return A future that completes when execution is finished.
     */
    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        try {
            Integer newRadius = context.get(this.radiusArg);
            if (newRadius <= 0) {
                context.sendMessage(Message.raw("Radius must be greater than 0").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            BetterMapConfig config = BetterMapConfig.getInstance();
            config.setExplorationRadius(newRadius);

            context.sendMessage(Message.raw("Exploration radius set to: ").color(Color.GREEN).insert(Message.raw(String.valueOf(newRadius)).color(Color.YELLOW)));
            context.sendMessage(Message.raw("NOTE: Updates will apply as players move to new chunks.").color(Color.YELLOW));

        } catch (Exception e) {
            context.sendMessage(Message.raw("Error: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}


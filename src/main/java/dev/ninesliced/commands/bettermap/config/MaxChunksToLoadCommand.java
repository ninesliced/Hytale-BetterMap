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
 * Command to set the maximum chunks to load.
 */
public class MaxChunksToLoadCommand extends AbstractCommand {
    private final RequiredArg<Integer> chunksArg = this.withRequiredArg("value", "Max chunks to load", ArgTypes.INTEGER);

    /**
     * Constructs the MaxChunksToLoadCommand.
     */
    public MaxChunksToLoadCommand() {
        super("maxchunks", "Set max chunks to load (limit depends on quality)");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Executes the command.
     *
     * @param context The command execution context.
     * @return A future that completes when execution is finished.
     */
    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        ModConfig config = ModConfig.getInstance();
        Integer requestedChunks = context.get(this.chunksArg);

        if (requestedChunks == null) {
            context.sendMessage(Message.raw("Please specify a value.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        ModConfig.MapQuality currentQuality = config.getMapQuality();
        int limit = currentQuality.maxChunks;

        if (requestedChunks > limit) {
             config.setMaxChunksToLoad(limit);
             context.sendMessage(Message.raw("Requested " + requestedChunks + " exceeds limit for " + currentQuality.name() + " quality (" + limit + ").")
                     .color(Color.YELLOW));
             context.sendMessage(Message.raw("Set max chunks to " + limit + ".").color(Color.YELLOW));
        } else {
            config.setMaxChunksToLoad(requestedChunks);
            context.sendMessage(Message.raw("Set max chunks to " + requestedChunks + ".").color(Color.GREEN));
        }
        
        context.sendMessage(Message.raw("NOTE: The server must be restarted for this change to take effect.").color(Color.GRAY));

        return CompletableFuture.completedFuture(null);
    }
}

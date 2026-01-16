package dev.ninesliced.commands.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import dev.ninesliced.configs.BetterMapConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle debug logging for the BetterMap mod.
 */
public class DebugCommand extends AbstractCommand {
    private final RequiredArg<Boolean> debugValueArg = (RequiredArg<Boolean>) this.withRequiredArg("value", "Enable/Disable debug logs", (ArgumentType) ArgTypes.BOOLEAN);

    /**
     * Constructs the Debug command.
     */
    public DebugCommand() {
        super("debug", "Toggle debug logging");
    }

    @Override
    protected String generatePermissionNode() {
        return "command.bettermap.debug";
    }

    /**
     * Executes the debug command, updating the configuration.
     *
     * @param context The command execution context.
     * @return A future that completes when execution is finished.
     */
    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        try {
            Boolean newDebug = (Boolean) context.get(this.debugValueArg);

            BetterMapConfig config = BetterMapConfig.getInstance();
            config.setDebug(newDebug);

            context.sendMessage(Message.raw("BetterMap debug mode set to: " + newDebug).color(Color.GREEN));

        } catch (Exception e) {
            context.sendMessage(Message.raw("Error setting debug mode: " + e.getMessage()).color(Color.RED));
        }

        return CompletableFuture.completedFuture(null);
    }
}

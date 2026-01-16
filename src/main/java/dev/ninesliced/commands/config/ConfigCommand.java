package dev.ninesliced.commands.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.ninesliced.commands.ReloadCommand;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command for managing global BetterMap configuration.
 */
public class ConfigCommand extends AbstractCommand {

    /**
     * Constructs the ConfigCommand and registers subcommands.
     */
    public ConfigCommand() {
        super("config", "Manage global BetterMap configuration");

        this.addSubCommand(new MapMinScaleCommand());
        this.addSubCommand(new MapMaxScaleCommand());
        this.addSubCommand(new MapExplorationRadiusCommand());
        this.addSubCommand(new DebugCommand());
        this.addSubCommand(new MapQualityCommand());
    }

    @Override
    protected String generatePermissionNode() {
        return "command.bettermap.config";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         context.sendMessage(Message.raw("Usage: /bettermap config <subcommand>").color(Color.RED));
         return CompletableFuture.completedFuture(null);
    }
}


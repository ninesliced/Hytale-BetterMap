package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.ninesliced.commands.config.ConfigCommand;
import dev.ninesliced.configs.BetterMapConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Main command for the BetterMap mod.
 * Provides subcommands for managing map settings.
 */
public class BetterMapCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(BetterMapCommand.class.getName());

    /**
     * Constructs the BetterMap command and registers subcommands.
     */
    public BetterMapCommand() {
        super("bettermap", "Manage BetterMap plugin");
        this.addAliases("bm", "map");

        this.addSubCommand(new ConfigCommand());
        this.addSubCommand(new ReloadCommand());
        this.addSubCommand(new PlayerMinScaleCommand());
        this.addSubCommand(new PlayerMaxScaleCommand());
    }

    @Override
    protected String generatePermissionNode() {
        return "command.bettermap";
    }

    /**
     * Executes the command logic, displaying current settings.
     *
     * @param context The command execution context.
     * @return A future that completes when execution is finished.
     */
    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        BetterMapConfig config = BetterMapConfig.getInstance();

        context.sendMessage(Message.raw("=== BetterMap Settings ===").color(Color.ORANGE));
        context.sendMessage(Message.raw("Exploration Radius: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.getExplorationRadius())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Min Scale: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.getMinScale())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Max Scale: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.getMaxScale())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Map Quality: ").color(Color.YELLOW).insert(Message.raw(config.getMapQuality().name()).color(Color.WHITE)));
        context.sendMessage(Message.raw("Debug Mode: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.isDebug())).color(Color.WHITE)));
        context.sendMessage(Message.raw("NOTE: The server must be restarted for map quality changes to take effect."));

        return CompletableFuture.completedFuture(null);
    }
}

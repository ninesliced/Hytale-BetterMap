package dev.ninesliced.commands.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import dev.ninesliced.configs.BetterMapConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to add a world to the allowed/tracked list.
 */
public class TrackWorldCommand extends AbstractCommand {
    private final OptionalArg<String> worldArg = this.withOptionalArg("world", "World name to track", ArgTypes.STRING);

    /**
     * Constructs the TrackWorldCommand.
     */
    public TrackWorldCommand() {
        super("track", "Add a world to the exploration tracking list");
    }

    @Override
    protected String generatePermissionNode() {
        return "track";
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
        String worldName = context.get(this.worldArg);

        if (worldName == null || worldName.isEmpty()) {
            if (context.isPlayer()) {
                CommandSender sender = context.sender();
                if (sender instanceof Player) {
                    assert ((Player) sender).getWorld() != null;
                    worldName = ((Player) sender).getWorld().getName();
                } else {
                    context.sendMessage(Message.raw("This command must be run by a player or specify a world name.").color(Color.RED));
                    return CompletableFuture.completedFuture(null);
                }
            } else {
                context.sendMessage(Message.raw("You must specify a world name when running from console.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
        }

        boolean added = BetterMapConfig.getInstance().addAllowedWorld(worldName);

        if (added) {
            context.sendMessage(Message.raw("World '" + worldName + "' added to tracked worlds.").color(Color.GREEN));
            context.sendMessage(Message.raw("Changes saved to config.").color(Color.GRAY));
        } else {
            context.sendMessage(Message.raw("World '" + worldName + "' is already tracked.").color(Color.YELLOW));
        }

        return CompletableFuture.completedFuture(null);
    }
}

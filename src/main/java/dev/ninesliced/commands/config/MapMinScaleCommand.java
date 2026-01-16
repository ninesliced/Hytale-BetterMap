package dev.ninesliced.commands.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.BetterMapConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to set the minimum map scale (zoom out level).
 */
public class MapMinScaleCommand extends AbstractCommand {
    private final RequiredArg<Float> zoomValueArg = this.withRequiredArg("value", "Min zoom value", ArgTypes.FLOAT);

    /**
     * Constructs the MapMinScale command.
     */
    public MapMinScaleCommand() {
        super("min", "Set min map zoom scale (lower = zoom out further)");
    }

    @Override
    protected String generatePermissionNode() {
        return "command.bettermap.min";
    }

    /**
     * Executes the min scale command, validating and updating the configuration.
     *
     * @param context The command execution context.
     * @return A future that completes when execution is finished.
     */
    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        try {
            Float newMin = context.get(this.zoomValueArg);
            if (newMin < 2.0f) {
                context.sendMessage(Message.raw("Min scale must be greater or equals to 2").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            World world = this.findWorld(context);
            if (world == null) {
                context.sendMessage(Message.raw("Could not access world").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            BetterMapConfig config = BetterMapConfig.getInstance();
            if (newMin >= config.getMaxScale()) {
                context.sendMessage(Message.raw("Min scale must be less than max scale (" + config.getMaxScale() + ")").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            config.setMinScale(newMin);

            context.sendMessage(Message.raw("Map min scale set to: ").color(Color.GREEN).insert(Message.raw(String.valueOf(newMin)).color(Color.YELLOW)));

        } catch (Exception e) {
            context.sendMessage(Message.raw("Error: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Helper method to find the world associated with the command sender.
     *
     * @param context The command context.
     * @return The world the sender is in, or null if not applicable.
     */
    private World findWorld(CommandContext context) {
        try {
            CommandSender sender = context.sender();
            if (sender instanceof Player) {
                return ((Player) sender).getWorld();
            }
        } catch (Exception exception) {
        }
        return null;
    }
}

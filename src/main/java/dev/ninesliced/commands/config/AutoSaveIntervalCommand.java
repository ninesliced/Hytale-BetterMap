package dev.ninesliced.commands.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.managers.ExplorationManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to get or set the auto-save interval.
 */
public class AutoSaveIntervalCommand extends AbstractCommand {
    private final OptionalArg<Integer> intervalArg = this.withOptionalArg("interval", "Auto-save interval in minutes", ArgTypes.INTEGER);

    /**
     * Constructs the AutoSaveIntervalCommand.
     */
    public AutoSaveIntervalCommand() {
        super("autosave", "Get or set the exploration auto-save interval");
    }

    @Override
    protected String generatePermissionNode() {
        return "autosave";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        Integer interval = context.get(this.intervalArg);

        if (interval == null) {
            int current = BetterMapConfig.getInstance().getAutoSaveInterval();
            context.sendMessage(Message.raw("Current auto-save interval: " + current + " minutes.").color(Color.YELLOW));
        } else {
            if (interval < 0) {
                context.sendMessage(Message.raw("Interval cannot be negative.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            BetterMapConfig.getInstance().setAutoSaveInterval(interval);
            ExplorationManager.getInstance().startAutoSave();

            context.sendMessage(Message.raw("Auto-save interval set to " + interval + " minutes.").color(Color.GREEN));
            if (interval == 0) {
                context.sendMessage(Message.raw("Auto-save is now DISABLED.").color(Color.YELLOW));
            }
        }

        return CompletableFuture.completedFuture(null);
    }
}

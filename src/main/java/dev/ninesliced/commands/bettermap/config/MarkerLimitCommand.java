package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.utils.WaypointLimitUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to set per-player waypoint limits.
 */
public class MarkerLimitCommand extends AbstractCommand {
    private final RequiredArg<Integer> personalArg = this.withRequiredArg("personal", "Max personal markers per player (-1 for unlimited)", ArgTypes.INTEGER);
    private final RequiredArg<Integer> sharedArg = this.withRequiredArg("shared", "Max shared markers per player (-1 for unlimited)", ArgTypes.INTEGER);

    public MarkerLimitCommand() {
        super("markerlimit", "Set max personal/shared waypoint limits per player");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        Integer personal = context.get(this.personalArg);
        Integer shared = context.get(this.sharedArg);

        if (personal == null || shared == null) {
            context.sendMessage(Message.raw("Please specify both personal and shared limits.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        if (personal < -1 || shared < -1) {
            context.sendMessage(Message.raw("Limits must be -1 or higher.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        ModConfig config = ModConfig.getInstance();
        config.setMaxPersonalMarkersPerPlayer(personal);
        config.setMaxSharedMarkersPerPlayer(shared);

        WaypointLimitUtil.applyOverridesToAllWorlds(personal, shared);

        context.sendMessage(Message.raw("Waypoint limits updated:").color(Color.GREEN));
        context.sendMessage(Message.raw("Personal: " + personal + " | Shared: " + shared).color(Color.YELLOW));
        context.sendMessage(Message.raw("Use -1 for unlimited.").color(Color.GRAY));

        return CompletableFuture.completedFuture(null);
    }
}

package dev.ninesliced.commands.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.ninesliced.configs.BetterMapConfig;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle waypoint teleports.
 */
public class WaypointTeleportCommand extends AbstractCommand {

    public WaypointTeleportCommand() {
        super("waypointteleport", "Toggle waypoint teleports");
    }

    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        BetterMapConfig config = BetterMapConfig.getInstance();
        boolean newState = !config.isAllowWaypointTeleports();
        config.setAllowWaypointTeleports(newState);

        String status = newState ? "ENABLED" : "DISABLED";
        Color color = newState ? Color.GREEN : Color.RED;
        commandContext.sendMessage(Message.raw("Waypoint teleports " + status).color(color));

        return CompletableFuture.completedFuture(null);
    }
}

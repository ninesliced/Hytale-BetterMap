package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ninesliced.configs.ModConfig;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Command to set the map quality level (LOW, MEDIUM, HIGH).
 * Changes made via this command require a server restart to take full effect.
 */
public class MapQualityCommand extends AbstractCommand {
    private final RequiredArg<String> qualityValueArg = this.withRequiredArg("value", "Quality map value", ArgTypes.STRING);

    /**
     * Constructs a new MapQualityCommand.
     */
    protected MapQualityCommand() {
        super("quality", "Set the map quality (low, medium, high)");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Executes the map quality command.
     * Updates the map quality configuration and notifies the user that a restart is required.
     *
     * @param context global command context
     * @return a future that completes when the command execution is finished
     */
    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext context) {
        String quality = context.get(this.qualityValueArg);
        ModConfig.MapQuality mapQuality;

        try {
            mapQuality = ModConfig.MapQuality.valueOf(quality.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Invalid quality value: " + quality).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        ModConfig.getInstance().setQuality(mapQuality);
        context.sendMessage(Message.raw("Map quality set to: " + mapQuality.name()).color(Color.GREEN));
        context.sendMessage(Message.raw("WARNING: Map Quality change pending restart (Active: " + ModConfig.getInstance().getActiveMapQuality().name() + ")").color(Color.RED));
        return CompletableFuture.completedFuture(null);
    }
}

package dev.ninesliced.commands.config;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.ninesliced.configs.BetterMapConfig;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;


/**
 * Handles the "location" command which allows players to toggle the visibility of the location HUD in the config.
 */
public class LocationCommand extends AbstractCommand {

    public LocationCommand() {
        super("Location", "Toggle the mini-map HUD display");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Executes the toggle command logic.
     * <p>
     * Verifies that the sender is a player, retrieves the player's context, and asynchronously
     * toggles the HUD visibility state on the world thread.
     * </p>
     *
     * @param commandContext The context of the executed command.
     * @return A CompletableFuture representing the asynchronous execution of the command.
     */
    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        BetterMapConfig config = BetterMapConfig.getInstance();
        config.setLocationEnabled(!config.isLocationEnabled());

        commandContext.sendMessage(Message.raw("Location HUD is now " + (config.isLocationEnabled() ? "enabled" : "disabled") + " by default in the config!").color(config.isLocationEnabled() ? Color.GREEN : Color.RED));
        return CompletableFuture.completedFuture(null);
    }
}

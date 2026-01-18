package dev.ninesliced.commands.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.ninesliced.commands.ReloadCommand;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command for managing global BetterMap configuration.
 */
public class ConfigCommand extends AbstractCommandCollection {
    public static final String CONFIG_PERMISSION = "dev.ninesliced.bettermap.command.config";

    /**
     * Constructs the ConfigCommand and registers subcommands.
     */
    public ConfigCommand() {
        super("config", "Manage global BetterMap configuration");
        this.requirePermission(CONFIG_PERMISSION);

        this.addSubCommand(new MapMinScaleCommand());
        this.addSubCommand(new MapMaxScaleCommand());
        this.addSubCommand(new MapExplorationRadiusCommand());
        this.addSubCommand(new DebugCommand());
        this.addSubCommand(new MapQualityCommand());
        this.addSubCommand(new LocationCommand());
        this.addSubCommand(new ShareAllExplorationCommand());
        this.addSubCommand(new MaxChunksToLoadCommand());
        this.addSubCommand(new RadarToggleCommand());
        this.addSubCommand(new RadarRangeCommand());
        this.addSubCommand(new HidePlayersCommand());
        this.addSubCommand(new HideOtherWarpsCommand());
        this.addSubCommand(new HideUnexploredWarpsCommand());
        this.addSubCommand(new HideAllPoiCommand());
        this.addSubCommand(new HideUnexploredPoiCommand());
        this.addSubCommand(new WaypointTeleportCommand());
        this.addSubCommand(new MarkerTeleportCommand());
        this.addSubCommand(new TrackWorldCommand());
        this.addSubCommand(new UntrackWorldCommand());
        this.addSubCommand(new AutoSaveIntervalCommand());
    }

    @Override
    protected String generatePermissionNode() {
        return "config";
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
}

package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Command for managing global BetterMap configuration.
 */
public class ConfigCommand extends AbstractCommandCollection {
    public static final String CONFIG_PERMISSION = "bettermap.command.config";

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
        this.addSubCommand(new HideAllWarpsCommand());
        this.addSubCommand(new HideOtherWarpsCommand());
        this.addSubCommand(new HideUnexploredWarpsCommand());
        this.addSubCommand(new HideAllPoiCommand());
        this.addSubCommand(new HideUnexploredPoiCommand());
        this.addSubCommand(new HideSpawnCommand());
        this.addSubCommand(new HideDeathMarkerCommand());
        this.addSubCommand(new HideGlobalWaypointsCommand());
        this.addSubCommand(new GlobalWaypointEditCommand());
        this.addSubCommand(new HiddenPoiCommand());
        this.addSubCommand(new WaypointTeleportCommand());
        this.addSubCommand(new MarkerTeleportCommand());
        this.addSubCommand(new PlayerTeleportCommand());
        this.addSubCommand(new CoordinateTeleportCommand());
        this.addSubCommand(new MapMarkerCreationCommand());
        this.addSubCommand(new TrackWorldCommand());
        this.addSubCommand(new UntrackWorldCommand());
        this.addSubCommand(new AutoSaveIntervalCommand());
        this.addSubCommand(new MarkerLimitCommand());
        this.addSubCommand(new WorldBorderToggleCommand());
        this.addSubCommand(new WorldBorderRadiusCommand());
        this.addSubCommand(new WorldBorderOffsetCommand());
        this.addSubCommand(new CaveModeToggleCommand());
        this.addSubCommand(new CaveModeLayerCommand());
        this.addSubCommand(new CaveModeThresholdCommand());
        this.addSubCommand(new CaveModeRadiusCommand());
        this.addSubCommand(new DiscoverSurfaceCommand());
        this.addSubCommand(new CaveFogOfWarCommand());
        this.addSubCommand(new DisableMarkerCreationDistanceCommand());
        this.addSubCommand(new DisableMarkerDeletionDistanceCommand());
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

package dev.ninesliced.commands;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import dev.ninesliced.commands.config.ConfigCommand;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;

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
        this.addSubCommand(new PlayerLocationCommand());
        this.addSubCommand(new PlayerHideAllPoiCommand());
        this.addSubCommand(new PlayerHideSpawnCommand());
        this.addSubCommand(new PlayerHideDeathCommand());
        this.addSubCommand(new PlayerHiddenPoiCommand());
        this.addSubCommand(new PlayerHidePlayersCommand());
        this.addSubCommand(new PlayerHideAllWarpsCommand());
        this.addSubCommand(new PlayerHideOtherWarpsCommand());
        this.addSubCommand(new PlayerResetPrivacyCommand());
        this.addSubCommand(new BetterMapWaypointCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
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
        context.sendMessage(Message.raw("Max Chunks: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.getMaxChunksToLoad())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Map Quality: ").color(Color.YELLOW).insert(Message.raw(config.getMapQuality().name()).color(Color.WHITE)));
        context.sendMessage(Message.raw("Debug Mode: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.isDebug())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Player Radar: ").color(Color.YELLOW).insert(Message.raw(config.isRadarEnabled() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide Players: ").color(Color.YELLOW).insert(Message.raw(config.isHidePlayersOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide All Warps: ").color(Color.YELLOW).insert(Message.raw(config.isHideAllWarpsOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide Other Warps: ").color(Color.YELLOW).insert(Message.raw(config.isHideOtherWarpsOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide Unexplored Warps: ").color(Color.YELLOW).insert(Message.raw(config.isHideUnexploredWarpsOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Waypoint Teleport: ").color(Color.YELLOW).insert(Message.raw(config.isAllowWaypointTeleports() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Marker Teleport: ").color(Color.YELLOW).insert(Message.raw(config.isAllowMapMarkerTeleports() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide All POIs: ").color(Color.YELLOW).insert(Message.raw(config.isHideAllPoiOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide Unexplored POIs: ").color(Color.YELLOW).insert(Message.raw(config.isHideUnexploredPoiOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide Spawn: ").color(Color.YELLOW).insert(Message.raw(config.isHideSpawnOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide Death Marker: ").color(Color.YELLOW).insert(Message.raw(config.isHideDeathMarkerOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        int hiddenCount = config.getHiddenPoiNames() != null ? config.getHiddenPoiNames().size() : 0;
        context.sendMessage(Message.raw("Hidden POI Names: ").color(Color.YELLOW).insert(Message.raw(hiddenCount + " entries").color(Color.WHITE)));
        String radarRange = config.getRadarRange() == -1 ? "Infinite" : config.getRadarRange() + " blocks";
        context.sendMessage(Message.raw("Radar Range: ").color(Color.YELLOW).insert(Message.raw(radarRange).color(Color.WHITE)));
        context.sendMessage(Message.raw("NOTE: The server must be restarted for map quality/max chunks changes to take effect."));

        // Show player-specific override settings if the sender is a player
        if (context.isPlayer()) {
            Player player = (Player) context.sender();
            PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());
            if (playerConfig != null) {
                context.sendMessage(Message.raw("=== Your Override Settings ===").color(Color.CYAN));
                
                String poiOverride = playerConfig.isOverrideGlobalPoiHide() ? "[OVERRIDE]" : "";
                String spawnOverride = playerConfig.isOverrideGlobalSpawnHide() ? "[OVERRIDE]" : "";
                String deathOverride = playerConfig.isOverrideGlobalDeathHide() ? "[OVERRIDE]" : "";
                String playersOverride = playerConfig.isOverrideGlobalPlayersHide() ? "[OVERRIDE]" : "";
                String allWarpsOverride = playerConfig.isOverrideGlobalAllWarpsHide() ? "[OVERRIDE]" : "";
                String otherWarpsOverride = playerConfig.isOverrideGlobalOtherWarpsHide() ? "[OVERRIDE]" : "";
                
                context.sendMessage(Message.raw("Hide All POI: ").color(Color.YELLOW)
                    .insert(Message.raw(playerConfig.isHideAllPoiOnMap() ? "Yes" : "No").color(Color.WHITE))
                    .insert(Message.raw(" " + poiOverride).color(Color.GREEN)));
                context.sendMessage(Message.raw("Hide Spawn: ").color(Color.YELLOW)
                    .insert(Message.raw(playerConfig.isHideSpawnOnMap() ? "Yes" : "No").color(Color.WHITE))
                    .insert(Message.raw(" " + spawnOverride).color(Color.GREEN)));
                context.sendMessage(Message.raw("Hide Death: ").color(Color.YELLOW)
                    .insert(Message.raw(playerConfig.isHideDeathMarkerOnMap() ? "Yes" : "No").color(Color.WHITE))
                    .insert(Message.raw(" " + deathOverride).color(Color.GREEN)));
                context.sendMessage(Message.raw("Hide Players: ").color(Color.YELLOW)
                    .insert(Message.raw(playerConfig.isHidePlayersOnMap() ? "Yes" : "No").color(Color.WHITE))
                    .insert(Message.raw(" " + playersOverride).color(Color.GREEN)));
                context.sendMessage(Message.raw("Hide All Warps: ").color(Color.YELLOW)
                    .insert(Message.raw(playerConfig.isHideAllWarpsOnMap() ? "Yes" : "No").color(Color.WHITE))
                    .insert(Message.raw(" " + allWarpsOverride).color(Color.GREEN)));
                context.sendMessage(Message.raw("Hide Other Warps: ").color(Color.YELLOW)
                    .insert(Message.raw(playerConfig.isHideOtherWarpsOnMap() ? "Yes" : "No").color(Color.WHITE))
                    .insert(Message.raw(" " + otherWarpsOverride).color(Color.GREEN)));
            }
        }

        return CompletableFuture.completedFuture(null);
    }
}

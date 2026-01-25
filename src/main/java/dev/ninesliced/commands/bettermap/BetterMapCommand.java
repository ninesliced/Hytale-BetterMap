package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ninesliced.commands.bettermap.config.ConfigCommand;
import dev.ninesliced.commands.bettermap.waypoint.WaypointCommand;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.ui.ConfigMenuPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

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
        this.addSubCommand(new WaypointCommand());
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
        if (context.isPlayer()) {
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref = context.senderAsPlayerRef();
            if (ref != null && ref.isValid()) {
                com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = ref.getStore();
                return CompletableFuture.runAsync(() -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

                    if (player != null && playerRef != null) {
                        player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(playerRef));
                    }
                }, store.getExternalData().getWorld());
            }
        }

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
        context.sendMessage(Message.raw("Hide Other Warps: ").color(Color.YELLOW).insert(Message.raw(config.isHideOtherWarpsOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide Unexplored Warps: ").color(Color.YELLOW).insert(Message.raw(config.isHideUnexploredWarpsOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Waypoint Teleport: ").color(Color.YELLOW).insert(Message.raw(config.isAllowWaypointTeleports() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Marker Teleport: ").color(Color.YELLOW).insert(Message.raw(config.isAllowMapMarkerTeleports() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide All POIs: ").color(Color.YELLOW).insert(Message.raw(config.isHideAllPoiOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        context.sendMessage(Message.raw("Hide Unexplored POIs: ").color(Color.YELLOW).insert(Message.raw(config.isHideUnexploredPoiOnMap() ? "Enabled" : "Disabled").color(Color.WHITE)));
        String radarRange = config.getRadarRange() == -1 ? "Infinite" : config.getRadarRange() + " blocks";
        context.sendMessage(Message.raw("Radar Range: ").color(Color.YELLOW).insert(Message.raw(radarRange).color(Color.WHITE)));
        context.sendMessage(Message.raw("NOTE: The server must be restarted for map quality/max chunks changes to take effect."));

        return CompletableFuture.completedFuture(null);
    }
}

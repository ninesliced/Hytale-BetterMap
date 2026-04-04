package dev.ninesliced.commands.bettermap;
import dev.ninesliced.utils.PlayerRefUtil;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.CaveModeManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Command to toggle the player's personal cave mode preference.
 * Note: This only works if the server has cave mode enabled globally.
 * If the server disables cave mode, players cannot enable it for themselves.
 */
public class PlayerCaveModeCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(PlayerCaveModeCommand.class.getName());

    public PlayerCaveModeCommand() {
        super("cavemode", "Toggle your personal cave mode preference");
    }

    @Override
    protected String generatePermissionNode() {
        return "cavemode";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (!ModConfig.getInstance().isCaveModeEnabled()) {
                context.sendMessage(Message.raw("Cave mode is disabled by the server.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            Player player = PlayerRefUtil.fromContext(context);
            World world = player.getWorld();
            UUID uuid = player.getUuid();
            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

            if (config == null) {
                context.sendMessage(Message.raw("Could not load player config.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            if (world == null) {
                context.sendMessage(Message.raw("Could not access world.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            boolean newValue = !config.isCaveModeEnabled();
            config.setCaveModeEnabled(newValue);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
            if (state != null) {
                state.setDynamicModeEnabled(newValue);
                if (!newValue) {
                    state.setCurrentlyUnderground(false);
                }
            }

            if (newValue) {
                context.sendMessage(Message.raw("Cave mode enabled for you.").color(Color.GREEN));
            } else {
                context.sendMessage(Message.raw("Cave mode disabled for you.").color(Color.YELLOW));
            }

            world.execute(() -> {
                try {
                    WorldMapHook.forceFullMapRefresh(player);
                } catch (Exception e) {
                    LOGGER.warning("Failed to trigger map refresh for player cave mode: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            context.sendMessage(Message.raw("Error toggling cave mode: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(null);
    }
}

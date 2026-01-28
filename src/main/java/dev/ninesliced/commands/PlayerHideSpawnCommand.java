package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;

import com.hypixel.hytale.server.core.command.system.CommandSender;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the player's personal spawn marker visibility preference.
 * Saves the player's desired state (visible/hidden) which is applied
 * based on permissions and global settings by the privacy provider.
 */
public class PlayerHideSpawnCommand extends AbstractCommand {

    public PlayerHideSpawnCommand() {
        super("hidespawn", "Toggle hiding spawn marker for yourself");
    }

    @Override
    protected String generatePermissionNode() {
        return "hidespawn";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) context.sender();
        UUID uuid = ((CommandSender) player).getUuid();
        World world = player.getWorld();
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

        if (world == null || config == null) {
            context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            boolean newWantsVisible = determineNewVisibilityState(
                config.isOverrideGlobalSpawnHide() && !config.isHideSpawnOnMap(),
                config.isHideSpawnOnMap()
            );

            config.setOverrideGlobalSpawnHide(newWantsVisible);
            config.setHideSpawnOnMap(!newWantsVisible);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            Color color = newWantsVisible ? Color.GREEN : Color.RED;
            String status = newWantsVisible ? "VISIBLE" : "HIDDEN";
            context.sendMessage(Message.raw("Spawn markers are now " + status + " for you.").color(color));
        }, world);
    }

    private boolean determineNewVisibilityState(boolean currentlyWantsVisible, boolean currentlyWantsHidden) {
        if (currentlyWantsVisible) {
            return false;
        } else if (currentlyWantsHidden) {
            return true;
        }
        return false;
    }
}

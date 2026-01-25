package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the player's personal "hide players" preference.
 * Saves the player's desired state (visible/hidden) which is applied
 * based on permissions and global settings by the privacy provider.
 */
public class PlayerHidePlayersCommand extends AbstractCommand {

    public PlayerHidePlayersCommand() {
        super("hideplayers", "Toggle hiding other players on map for yourself");
    }

    @Override
    protected String generatePermissionNode() {
        return "hideplayers";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        return CompletableFuture.runAsync(() -> {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
                return;
            }

            Player player = (Player) context.sender();
            UUID uuid = player.getUuid();
            World world = player.getWorld();
            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

            if (world == null || config == null) {
                context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
                return;
            }

            // Determine current desired state and toggle it
            boolean currentlyWantsVisible = config.isOverrideGlobalPlayersHide() && !config.isHidePlayersOnMap();
            boolean currentlyWantsHidden = config.isHidePlayersOnMap();
            
            boolean newWantsVisible;
            if (currentlyWantsVisible) {
                // Currently wants visible -> switch to hidden
                newWantsVisible = false;
            } else if (currentlyWantsHidden) {
                // Currently wants hidden -> switch to visible
                newWantsVisible = true;
            } else {
                // Default state -> switch to hidden
                newWantsVisible = false;
            }

            // Save the new desired state
            config.setOverrideGlobalPlayersHide(newWantsVisible);
            config.setHidePlayersOnMap(!newWantsVisible);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            MapPrivacyManager.getInstance().updatePrivacyState();
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            Color color = newWantsVisible ? Color.GREEN : Color.RED;
            String status = newWantsVisible ? "VISIBLE" : "HIDDEN";
            context.sendMessage(Message.raw("Players are now " + status + " for you.").color(color));
        });
    }
}

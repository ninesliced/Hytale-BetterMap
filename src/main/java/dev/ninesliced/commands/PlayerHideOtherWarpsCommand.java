package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.integration.ExtendedTeleportIntegration;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.WarpPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the player's personal "hide other warps" preference.
 * Saves the player's desired state (visible/hidden) which is applied
 * based on permissions and global settings by the privacy provider.
 */
public class PlayerHideOtherWarpsCommand extends AbstractCommand {

    public PlayerHideOtherWarpsCommand() {
        super("hideotherwarps", "Toggle hiding other players' warps for yourself");
        this.addAliases("hideotherwarp", "hidewarps");
    }

    @Override
    protected String generatePermissionNode() {
        return "hideotherwarps";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        return CompletableFuture.runAsync(() -> {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
                return;
            }

            // Check if ExtendedTeleport is available - required for ownership-based filtering
            if (!ExtendedTeleportIntegration.getInstance().isAvailable()) {
                context.sendMessage(Message.raw("This feature requires ExtendedTeleport to be installed.").color(Color.YELLOW));
                context.sendMessage(Message.raw("Without it, warp ownership cannot be determined.").color(Color.GRAY));
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
            boolean currentlyWantsVisible = config.isOverrideGlobalOtherWarpsHide() && !config.isHideOtherWarpsOnMap();
            boolean currentlyWantsHidden = config.isHideOtherWarpsOnMap();
            
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
            config.setOverrideGlobalOtherWarpsHide(newWantsVisible);
            config.setHideOtherWarpsOnMap(!newWantsVisible);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            WarpPrivacyManager.getInstance().updatePrivacyState();
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            Color color = newWantsVisible ? Color.GREEN : Color.RED;
            String status = newWantsVisible ? "VISIBLE" : "HIDDEN";
            context.sendMessage(Message.raw("Other players' warps are now " + status + " for you.").color(color));
        });
    }
}

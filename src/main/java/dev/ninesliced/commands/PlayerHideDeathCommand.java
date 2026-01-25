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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the player's personal death marker visibility preference.
 * Saves the player's desired state (visible/hidden) which is applied
 * based on permissions and global settings by the privacy provider.
 * 
 * Note: Uses the game's native death storage. If the game's displayDeathMarker 
 * config is disabled, deaths won't be recorded and this toggle has no effect.
 */
public class PlayerHideDeathCommand extends AbstractCommand {

    public PlayerHideDeathCommand() {
        super("hidedeath", "Toggle hiding death marker for yourself");
    }

    @Override
    protected String generatePermissionNode() {
        return "hidedeath";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) context.sender();
        UUID uuid = player.getUuid();
        World world = player.getWorld();
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

        if (world == null || config == null) {
            context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        // Run on world executor to ensure proper ordering
        return CompletableFuture.runAsync(() -> {
            // Determine current desired state and toggle it
            boolean currentlyWantsVisible = config.isOverrideGlobalDeathHide() && !config.isHideDeathMarkerOnMap();
            boolean currentlyWantsHidden = config.isHideDeathMarkerOnMap();
            
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
            config.setOverrideGlobalDeathHide(newWantsVisible);
            config.setHideDeathMarkerOnMap(!newWantsVisible);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            Color color = newWantsVisible ? Color.GREEN : Color.RED;
            String status = newWantsVisible ? "VISIBLE" : "HIDDEN";
            context.sendMessage(Message.raw("Death markers are now " + status + " for you.").color(color));
        }, world);
    }
}

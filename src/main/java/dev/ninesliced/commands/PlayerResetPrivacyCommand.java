package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.managers.WarpPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;

import com.hypixel.hytale.server.core.command.system.CommandSender;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to reset a player's privacy overrides back to defaults.
 */
public class PlayerResetPrivacyCommand extends AbstractCommand {

    public PlayerResetPrivacyCommand() {
        super("resetprivacy", "Reset your map privacy overrides");
    }

    @Override
    protected String generatePermissionNode() {
        return "resetprivacy";
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
            config.setHideAllPoiOnMap(false);
            config.setHideSpawnOnMap(false);
            config.setHideDeathMarkerOnMap(false);
            config.setHiddenPoiNames(new ArrayList<>());
            config.setHidePlayersOnMap(false);
            config.setHideAllWarpsOnMap(false);
            config.setHideOtherWarpsOnMap(false);
            config.setSavedDeathMarkers(new ArrayList<>());
            config.setOverrideGlobalPoiHide(false);
            config.setOverrideGlobalSpawnHide(false);
            config.setOverrideGlobalDeathHide(false);
            config.setOverrideGlobalPlayersHide(false);
            config.setOverrideGlobalAllWarpsHide(false);
            config.setOverrideGlobalOtherWarpsHide(false);

            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
            WarpPrivacyManager.getInstance().updatePrivacyState();
            MapPrivacyManager.getInstance().updatePrivacyState();
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            context.sendMessage(Message.raw("Your map privacy settings have been reset.").color(Color.GREEN));
            context.sendMessage(Message.raw("You now follow the global map settings.").color(Color.GRAY));
        }, world);
    }
}

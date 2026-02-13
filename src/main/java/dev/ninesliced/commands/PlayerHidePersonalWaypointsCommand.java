package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.WaypointManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerHidePersonalWaypointsCommand extends AbstractCommand {

    public PlayerHidePersonalWaypointsCommand() {
        super("hidepersonalwaypoints", "Toggle hiding your personal waypoints");
        this.addAliases("hidemywaypoints");
    }

    @Override
    protected String generatePermissionNode() {
        return "hidepersonalwaypoints";
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
            boolean newState = !config.isHidePersonalWaypointsOnMap();
            config.setHidePersonalWaypointsOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            WaypointManager.refreshAllPlayersMarkers(world);

            boolean visible = !newState;
            Color color = visible ? Color.GREEN : Color.RED;
            String status = visible ? "VISIBLE" : "HIDDEN";
            context.sendMessage(Message.raw("Personal waypoints are now " + status + " for you.").color(color));
        }, world);
    }
}

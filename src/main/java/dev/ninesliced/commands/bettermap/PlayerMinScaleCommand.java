package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to set the player's minimum map scale.
 */
public class PlayerMinScaleCommand extends AbstractCommand {
    private final RequiredArg<Float> scaleArg = this.withRequiredArg("scale", "Min scale value", ArgTypes.FLOAT);

    public PlayerMinScaleCommand() {
        super("minscale", "Set player min map scale");
    }

    @Override
    protected String generatePermissionNode() {
        return "minscale";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        return CompletableFuture.runAsync(() -> {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
                return;
            }

            Float scale = context.get(this.scaleArg);
            if (scale <= 0) {
                context.sendMessage(Message.raw("Scale must be greater than 0").color(Color.RED));
                return;
            }

            UUID uuid = context.sender().getUuid();
            Player player = (Player) context.sender();
            World world = player.getWorld();
            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

            if (world == null) {
                context.sendMessage(Message.raw("Could not access world").color(Color.RED));
                return;
            }

            if (config != null) {
                config.setMinScale(scale);
                PlayerConfigManager.getInstance().savePlayerConfig(uuid);
                world.execute(() -> WorldMapHook.sendMapSettingsToPlayer(player));
                context.sendMessage(Message.raw("Set player min scale to " + scale).color(Color.GREEN));
            } else {
                 context.sendMessage(Message.raw("Could not load player config.").color(Color.RED));
            }
        });
    }
}

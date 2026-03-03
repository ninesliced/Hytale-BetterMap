package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.ninesliced.BetterMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Creates a short-lived one-time web map login code for the requesting player.
 */
public class WebMapCodeCreateCommand extends AbstractCommand {

    public WebMapCodeCreateCommand() {
        super("create", "Create a short-lived login code for web map access");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command can only be used by players.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String code = BetterMap.get().getWebMapService().createLoginCode(context.sender().getUuid(), context.sender().getDisplayName());
        context.sendMessage(Message.raw("Your BetterMap web login code is: " + code).color(Color.GREEN));
        context.sendMessage(Message.raw("Use it on /login and do not share it with others.").color(Color.YELLOW));
        return CompletableFuture.completedFuture(null);
    }
}

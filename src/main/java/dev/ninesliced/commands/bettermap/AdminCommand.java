package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.ui.ConfigMenuPage;
import dev.ninesliced.utils.PermissionsUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to open the BetterMap admin config page directly.
 */
public class AdminCommand extends AbstractCommand {

    public AdminCommand() {
        super("admin", "Open BetterMap admin config");
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

        Ref<EntityStore> ref = context.senderAsPlayerRef();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        return CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (player == null || playerRef == null) {
                return;
            }

            if (!PermissionsUtil.isAdmin(player)) {
                player.sendMessage(Message.raw("You must be an admin to use this command.").color(Color.RED));
                return;
            }

            player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(playerRef, true));
        }, store.getExternalData().getWorld());
    }
}

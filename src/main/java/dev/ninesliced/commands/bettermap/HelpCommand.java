package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.ui.HelpMenuPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to open the BetterMap help menu.
 */
public class HelpCommand extends AbstractCommand {

    /**
     * Constructs the Help command.
     */
    public HelpCommand() {
        super("help", "Open BetterMap help menu");
        this.requireNoPermission();
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }

    /**
     * Executes the help command, opening the help menu for players.
     *
     * @param context The command execution context.
     * @return A future that completes when execution is finished.
     */
    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (context.isPlayer()) {
            Ref<EntityStore> ref = context.senderAsPlayerRef();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                return CompletableFuture.runAsync(() -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

                    if (player != null && playerRef != null) {
                        player.getPageManager().openCustomPage(ref, store, new HelpMenuPage(playerRef));
                    }
                }, store.getExternalData().getWorld());
            }
        } else {
            context.sendMessage(Message.raw("This command can only be used by players.").color(Color.RED));
        }

        return CompletableFuture.completedFuture(null);
    }
}

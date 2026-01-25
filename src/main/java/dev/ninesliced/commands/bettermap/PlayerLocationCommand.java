package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;


/**
 * Handles the "location" command which allows players to toggle the visibility of the location HUD.
 */
public class PlayerLocationCommand extends AbstractCommand {

    public PlayerLocationCommand() {
        super("location", "Toggle the location HUD display");
        this.setPermissionGroup(GameMode.Adventure);
        this.setPermissionGroup(GameMode.Creative);
    }

    /**
     * Executes the toggle command logic.
     * <p>
     * Verifies that the sender is a player, retrieves the player's context, and asynchronously
     * toggles the HUD visibility state on the world thread.
     * </p>
     *
     * @param commandContext The context of the executed command.
     * @return A CompletableFuture representing the asynchronous execution of the command.
     */
    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        /**
         * if (!commandContext.isPlayer()) {
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
        if (ref == null) {
            return CompletableFuture.completedFuture(null);
        }

        var store = ref.getStore();

        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            LocationHudProvider provider = BetterMap.get().getLocationHudProvider();
            if (provider == null) {
                return;
            }

            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid());
            if (config.isLocationEnabled()) {
                provider.disableHudForPlayer(playerRef);
                config.setLocationEnabled(false);
            } else {
                provider.enableHudForPlayer(playerComponent, playerRef);
                config.setLocationEnabled(true);
            }
        }, world);
        */

       commandContext.sendMessage(Message.raw("This feature is currently disabled. We are working on a fix.").color(Color.RED));
       return CompletableFuture.completedFuture(null);
    }
}

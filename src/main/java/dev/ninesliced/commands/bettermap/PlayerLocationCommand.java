package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.providers.LocationHudProvider;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import dev.ninesliced.utils.PlayerRefUtil;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

import dev.ninesliced.utils.PlayerRefUtil;

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
        if (!ModConfig.getInstance().isLocationEnabled()) {
            commandContext.sendMessage(Message.raw("Location HUD is disabled on this server.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        if (!commandContext.isPlayer()) {
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
        if (ref == null) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (player == null || playerRef == null) {
                return;
            }

            LocationHudProvider provider = BetterMap.get().getLocationHudProvider();
            if (provider == null) {
                return;
            }

            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid());
            if (config.isLocationEnabled()) {
                provider.disableHudForPlayer(player, playerRef);
                config.setLocationEnabled(false);
                PlayerRefUtil.resolve(player).sendMessage(Message.raw("Location HUD disabled.").color(Color.YELLOW));
            } else {
                provider.enableHudForPlayer(player, playerRef);
                config.setLocationEnabled(true);
                PlayerRefUtil.resolve(player).sendMessage(Message.raw("Location HUD enabled.").color(Color.GREEN));
            }
            PlayerConfigManager.getInstance().savePlayerConfig(playerRef.getUuid());
        }, world);
    }
}

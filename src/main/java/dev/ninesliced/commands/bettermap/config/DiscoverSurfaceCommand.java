package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Command to toggle whether surface areas are discovered while underground.
 * <p>
 * This only works if the server has cave mode enabled globally.
 * </p>
 */
public class DiscoverSurfaceCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(DiscoverSurfaceCommand.class.getName());

    public DiscoverSurfaceCommand() {
        super("discoversurface", "Toggle discovering surface areas while underground");
        this.addAliases("discoversrf", "dsurface");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        if (!commandContext.isPlayer()) {
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

            ModConfig config = ModConfig.getInstance();

            if (!config.isCaveModeEnabled()) {
                playerRef.sendMessage(Message.raw("Cave mode is disabled by the server. This option requires cave mode.").color(Color.RED));
                return;
            }

            boolean newValue = !config.isDiscoverSurfaceUnderground();
            config.setDiscoverSurfaceUnderground(newValue);

            if (newValue) {
                playerRef.sendMessage(Message.raw("Surface discovery while underground enabled globally.").color(Color.GREEN));
            } else {
                playerRef.sendMessage(Message.raw("Surface discovery while underground disabled globally.").color(Color.YELLOW));
            }

        }, world);
    }
}

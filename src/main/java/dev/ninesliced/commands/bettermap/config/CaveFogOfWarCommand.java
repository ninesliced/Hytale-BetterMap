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
import dev.ninesliced.utils.WorldMapHook;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Command to toggle cave fog of war mode.
 * <p>
 * When enabled, only discovered cave chunks are shown while underground.
 * Normal surface chunks are hidden, creating a true fog of war experience.
 * This only works if cave mode is enabled globally.
 * </p>
 */
public class CaveFogOfWarCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(CaveFogOfWarCommand.class.getName());

    public CaveFogOfWarCommand() {
        super("cavefogofwar", "Toggle cave fog of war (hide surface chunks when underground)");
        this.addAliases("cfog", "cavefog");
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
                playerRef.sendMessage(Message.raw("Cave mode is disabled by the server. Fog of war requires cave mode.").color(Color.RED));
                return;
            }

            boolean newValue = !config.isCaveFogOfWar();
            config.setCaveFogOfWar(newValue);

            if (newValue) {
                playerRef.sendMessage(Message.raw("Cave fog of war enabled - surface chunks will be hidden underground.").color(Color.GREEN));
                playerRef.sendMessage(Message.raw("Only discovered cave chunks will be visible.").color(Color.GRAY));
            } else {
                playerRef.sendMessage(Message.raw("Cave fog of war disabled - surface chunks visible underground.").color(Color.YELLOW));
            }

            try {
                world.execute(() -> {
                    for (PlayerRef pRef : world.getPlayerRefs()) {
                        var pHolder = pRef.getHolder();
                        if (pHolder != null) {
                            Player p = pHolder.getComponent(Player.getComponentType());
                            if (p != null) {
                                WorldMapHook.forceFullMapRefresh(p);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.warning("Failed to refresh maps for fog of war change: " + e.getMessage());
            }

        }, world);
    }
}

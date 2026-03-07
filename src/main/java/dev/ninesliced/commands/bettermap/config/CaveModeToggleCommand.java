package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.managers.CaveModeManager;
import dev.ninesliced.utils.WorldMapHook;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Command to toggle the cave mode feature on and off.
 * <p>
 * When enabled, the map will show a cave view when the player is underground.
 * </p>
 */
public class CaveModeToggleCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(CaveModeToggleCommand.class.getName());

    public CaveModeToggleCommand() {
        super("cavemode", "Toggle cave mode feature on/off");
        this.addAliases("cave");
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
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            ModConfig config = ModConfig.getInstance();
            boolean newState = !config.isCaveModeEnabled();
            config.setCaveModeEnabled(newState);

            CaveModeManager caveModeManager = CaveModeManager.getInstance();
            Universe universe = Universe.get();
            if (universe != null) {
                universe.getWorlds().values().forEach(activeWorld -> {
                    if (activeWorld == null) {
                        return;
                    }

                    activeWorld.execute(() -> {
                        for (PlayerRef onlineRef : activeWorld.getPlayerRefs()) {
                            Ref<EntityStore> onlineStoreRef = onlineRef.getReference();
                            if (onlineStoreRef == null || !onlineStoreRef.isValid()) {
                                continue;
                            }

                            Player onlinePlayer = onlineStoreRef.getStore().getComponent(onlineStoreRef, Player.getComponentType());
                            if (onlinePlayer == null) {
                                continue;
                            }

                            CaveModeManager.DynamicCaveModeState onlineState = caveModeManager.getState(onlinePlayer);
                            if (onlineState != null) {
                                onlineState.setDynamicModeEnabled(newState);
                                if (!newState) {
                                    onlineState.setCurrentlyUnderground(false);
                                }
                            }

                            try {
                                WorldMapHook.forceFullMapRefresh(onlinePlayer);
                            } catch (Exception e) {
                                LOGGER.warning("Failed to trigger map refresh for cave mode: " + e.getMessage());
                            }
                        }
                    });
                });
            } else {
                try {
                    WorldMapHook.forceFullMapRefresh(playerComponent);
                } catch (Exception e) {
                    LOGGER.warning("Failed to trigger map refresh for cave mode: " + e.getMessage());
                }
            }

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            playerRef.sendMessage(Message.raw("Cave Mode " + status).color(color));

            if (newState) {
                playerRef.sendMessage(Message.raw("Map will show cave view when underground.").color(Color.GRAY));
                playerRef.sendMessage(Message.raw("Threshold: Y=" + config.getCaveModeUndergroundThreshold() +
                        ", Layer: " + config.getCaveModeLayerSize() + " blocks, Radius: " +
                        config.getCaveModeRadius() + " chunks").color(Color.GRAY));
            }
        }, world);
    }
}

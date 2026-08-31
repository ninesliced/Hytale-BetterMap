package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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
 * Command to set the cave mode view radius.
 * <p>
 * The radius determines how many chunks around the player
 * are rendered in cave view mode.
 * </p>
 */
public class CaveModeRadiusCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(CaveModeRadiusCommand.class.getName());
    
    private final OptionalArg<Integer> radiusArg = this.withOptionalArg("radius", "Radius in chunks (1-16)", ArgTypes.INTEGER);

    public CaveModeRadiusCommand() {
        super("caveradius", "Set the cave view radius in chunks");
        this.addAliases("cmradius");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
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
            Integer newRadius = this.radiusArg.get(commandContext);

            if (newRadius == null) {
                playerRef.sendMessage(Message.raw("Current cave radius: " + config.getCaveModeRadius() + " chunks").color(Color.YELLOW));
                playerRef.sendMessage(Message.raw("Usage: /bettermap config caveradius <1-16>").color(Color.GRAY));
                return;
            }

            int radius = Math.max(1, Math.min(newRadius, 16));
            config.setCaveModeRadius(radius);

            CaveModeManager caveModeManager = CaveModeManager.getInstance();
            CaveModeManager.DynamicCaveModeState state = caveModeManager.getOrCreateState(playerComponent);
            state.setCaveRadius(radius);

            playerRef.sendMessage(Message.raw("Cave view radius set to " + radius + " chunks").color(Color.GREEN));

            if (caveModeManager.isPlayerUnderground(playerComponent)) {
                try {
                    WorldMapHook.forceFullMapRefresh(playerComponent);
                } catch (Exception e) {
                    LOGGER.warning("Failed to trigger map refresh for cave mode: " + e.getMessage());
                }
            }
        }, world);
    }
}

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
 * Command to set the cave mode layer size.
 * <p>
 * The layer size determines how many Y levels are grouped together
 * for the cave view. Smaller values mean more frequent map updates
 * when moving vertically in caves.
 * </p>
 */
public class CaveModeLayerCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(CaveModeLayerCommand.class.getName());

    private final OptionalArg<Integer> layerSizeArg = this.withOptionalArg("size", "Layer size (1-20)", ArgTypes.INTEGER);

    public CaveModeLayerCommand() {
        super("cavelayer", "Set the cave mode layer size (Y blocks per layer)");
        this.addAliases("cmlayer");
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
            Integer newLayerSize = this.layerSizeArg.get(commandContext);

            if (newLayerSize == null) {
                playerRef.sendMessage(Message.raw("Current cave layer size: " + config.getCaveModeLayerSize() + " blocks").color(Color.YELLOW));
                playerRef.sendMessage(Message.raw("Usage: /bettermap config cavelayer <1-20>").color(Color.GRAY));
                return;
            }

            int layerSize = Math.max(1, Math.min(newLayerSize, 20));
            config.setCaveModeLayerSize(layerSize);

            CaveModeManager caveModeManager = CaveModeManager.getInstance();
            CaveModeManager.DynamicCaveModeState state = caveModeManager.getOrCreateState(playerComponent);
            state.setLayerSize(layerSize);

            playerRef.sendMessage(Message.raw("Cave layer size set to " + layerSize + " blocks").color(Color.GREEN));
            playerRef.sendMessage(Message.raw("The map will update every " + layerSize + " Y levels in caves.").color(Color.GRAY));

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

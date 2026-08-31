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
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to set the cave mode Y threshold.
 * <p>
 * The threshold determines at what Y level the cave mode activates.
 * Below this Y level, the map will switch to cave view.
 * </p>
 */
public class CaveModeThresholdCommand extends AbstractCommand {
    
    private final OptionalArg<Integer> thresholdArg = this.withOptionalArg("y", "Y threshold (0-319)", ArgTypes.INTEGER);

    public CaveModeThresholdCommand() {
        super("cavethreshold", "Set the Y level threshold for cave mode activation");
        this.addAliases("cmthreshold", "cavey");
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
            Integer newThreshold = this.thresholdArg.get(commandContext);

            if (newThreshold == null) {
                playerRef.sendMessage(Message.raw("Current underground threshold: Y=" + config.getCaveModeUndergroundThreshold()).color(Color.YELLOW));
                playerRef.sendMessage(Message.raw("Usage: /bettermap config cavethreshold <0-319>").color(Color.GRAY));
                return;
            }

            int threshold = Math.max(0, Math.min(newThreshold, 319));
            config.setCaveModeUndergroundThreshold(threshold);

            CaveModeManager caveModeManager = CaveModeManager.getInstance();
            CaveModeManager.DynamicCaveModeState state = caveModeManager.getOrCreateState(playerComponent);
            state.setUndergroundThreshold(threshold);

            playerRef.sendMessage(Message.raw("Cave mode will activate below Y=" + threshold).color(Color.GREEN));
        }, world);
    }
}

package dev.ninesliced.commands.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.BetterMapConfig;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to set the player radar range.
 * <p>
 * Players can specify a numeric range (50-2000 blocks) or use -1 for unlimited range.
 * </p>
 */
public class RadarRangeCommand extends AbstractCommand {

    private final RequiredArg<Integer> rangeArg = this.withRequiredArg("range", "The range in blocks (-1 for infinite)", ArgTypes.INTEGER);

    public RadarRangeCommand() {
        super("radarrange", "Set player radar range (usage: /bm radarrange <number|-1>)");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Executes the radar range command.
     *
     * @param commandContext The command execution context.
     * @return A CompletableFuture representing the asynchronous execution.
     */
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

        Integer rangeValue = commandContext.get(rangeArg);

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            BetterMapConfig config = BetterMapConfig.getInstance();

            setNewRange(playerRef, config, rangeValue);
        }, world);
    }

    /**
     * Parses and sets a new radar range for the player.
     *
     * @param playerRef  The player reference.
     * @param config     The player's configuration.
     * @param rangeValue The range value provided by the player.
     */
    private void setNewRange(PlayerRef playerRef, BetterMapConfig config, int rangeValue) {
        if (rangeValue < 0) {
            config.setRadarRange(-1);
            playerRef.sendMessage(Message.raw("Radar range set to Infinite").color(Color.GREEN));
            return;
        }

        config.setRadarRange(rangeValue);
        playerRef.sendMessage(Message.raw("Radar range set to " + rangeValue + " blocks").color(Color.GREEN));
    }
}

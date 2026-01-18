package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.managers.WarpPrivacyManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to reload the BetterMap configuration.
 */
public class ReloadCommand extends AbstractCommand {
    /**
     * Constructs the Reload command.
     */
    protected ReloadCommand() {
        super("reload", "Reload BetterMap configuration");
    }

    @Override
    protected String generatePermissionNode() {
        return "reload";
    }

    /**
     * Executes the reload command, applying new configuration and updating worlds.
     *
     * @param context The command execution context.
     * @return A future that completes when execution is finished.
     */
    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext context) {
        BetterMapConfig.getInstance().reload();
        MapPrivacyManager.getInstance().updatePrivacyState();
        WarpPrivacyManager.getInstance().updatePrivacyState();
        PoiPrivacyManager.getInstance().updatePrivacyState();

        Universe universe = Universe.get();
        if (universe != null) {
            universe.getWorlds().values().forEach(world -> world.execute(() -> {
                WorldMapHook.updateWorldMapConfigs(world);
                WorldMapHook.broadcastMapSettings(world);
                WorldMapHook.refreshTrackers(world);
            }));
        }

        context.sendMessage(Message.raw("BetterMap configuration reloaded!").color(Color.GREEN));
        context.sendMessage(Message.raw("Exploration Radius: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(BetterMapConfig.getInstance().getExplorationRadius())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Min Scale: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(BetterMapConfig.getInstance().getMinScale())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Max Scale: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(BetterMapConfig.getInstance().getMaxScale())).color(Color.WHITE)));

        BetterMapConfig config = BetterMapConfig.getInstance();
        context.sendMessage(Message.raw("Map Quality: ").color(Color.YELLOW).insert(Message.raw(config.getMapQuality().name()).color(Color.WHITE)));

        if (config.getMapQuality() != config.getActiveMapQuality()) {
            context.sendMessage(Message.raw("WARNING: Map Quality change pending restart (Active: " + config.getActiveMapQuality().name() + ")").color(Color.RED));
        }

        context.sendMessage(Message.raw("NOTE: The server must be restarted for map quality/max chunks changes to take effect."));

        return CompletableFuture.completedFuture(null);
    }
}

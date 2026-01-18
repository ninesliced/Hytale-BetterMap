package dev.ninesliced.commands.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle sharing of all exploration data among players.
 */
public class ShareAllExplorationCommand extends AbstractCommand {

    public ShareAllExplorationCommand() {
        super("shareallexploration", "Toggle sharing of all exploration data");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
        this.addAliases("shareall");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        BetterMapConfig config = BetterMapConfig.getInstance();
        BetterMapConfig.getInstance().setShareAllExploration(!config.isShareAllExploration());
        context.sendMessage(Message.raw("ShareAllExploration set to: " + config.isShareAllExploration()).color(Color.GREEN));

        Universe universe = Universe.get();
        if (universe != null) {
            universe.getWorlds().values().forEach(WorldMapHook::refreshTrackers);
        }

        return CompletableFuture.completedFuture(null);
    }
}

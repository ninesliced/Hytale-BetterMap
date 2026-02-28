package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.utils.WorldMapHook;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle teleport actions from map marker context menus.
 */
public class WaypointContextMenuTeleportCommand extends AbstractCommand {

    public WaypointContextMenuTeleportCommand() {
        super("waypointcontextmenuteleport", "Toggle teleports from map marker context menus");
        this.addAliases("waypointcontexttp", "contextwaypointtp");
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
            commandContext.sendMessage(Message.raw("This command can only be used by a player.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
        if (ref == null) {
            return CompletableFuture.completedFuture(null);
        }

        var store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            ModConfig config = ModConfig.getInstance();
            boolean newState = !config.isAllowContextMenuWaypointTeleports();
            config.setAllowContextMenuWaypointTeleports(newState);

            Universe universe = Universe.get();
            if (universe != null) {
                universe.getWorlds().values().forEach(w -> {
                    if (w == null) return;
                    w.execute(() -> {
                        WorldMapHook.clearMarkerCaches(w);
                        WorldMapHook.refreshTrackers(w);
                    });
                });
            }

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;
            commandContext.sendMessage(Message.raw("Map marker context-menu teleports " + status + ".").color(color));
            if (newState) {
                commandContext.sendMessage(Message.raw("Teleport entries are enabled on marker context menus (permission still required)." ).color(Color.GRAY));
            } else {
                commandContext.sendMessage(Message.raw("Context-menu teleports are blocked for all markers, but waypoint menu teleports remain available.").color(Color.GRAY));
            }
        }, world);
    }
}

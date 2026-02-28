package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
 * Command to toggle native map right-click marker creation.
 */
public class MapMarkerCreationCommand extends AbstractCommand {

    public MapMarkerCreationCommand() {
        super("markercreation", "Toggle native map right-click marker creation");
        this.addAliases("mapmarkercreation", "markerrightclick");
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
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null) {
                return;
            }

            ModConfig config = ModConfig.getInstance();
            boolean newState = !config.isAllowNativeMapMarkerCreation();
            config.setAllowNativeMapMarkerCreation(newState);

            Universe universe = Universe.get();
            if (universe != null) {
                universe.getWorlds().values().forEach(w -> {
                    if (w == null) return;
                    w.execute(() -> {
                        WorldMapHook.updateWorldMapConfigs(w);
                        WorldMapHook.broadcastMapSettings(w);
                        WorldMapHook.clearMarkerCaches(w);
                        WorldMapHook.refreshTrackers(w);
                    });
                });
            }

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            commandContext.sendMessage(Message.raw("Native right-click map marker creation " + status + ".").color(color));
            if (newState) {
                commandContext.sendMessage(Message.raw("Players can create markers from the default map context menu.").color(Color.GRAY));
            } else {
                commandContext.sendMessage(Message.raw("The default map context menu marker creation option is blocked.").color(Color.GRAY));
            }
        }, world);
    }
}

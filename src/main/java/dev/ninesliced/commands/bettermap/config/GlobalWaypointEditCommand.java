package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.utils.WorldMapHook;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle global shared-waypoint edit/delete for everyone.
 */
public class GlobalWaypointEditCommand extends AbstractCommand {

    public GlobalWaypointEditCommand() {
        super("editglobalwaypoints", "Toggle global shared waypoint edit/delete for everyone");
        this.addAliases("globalwaypointedit", "waypointeditglobal");
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
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            ModConfig config = ModConfig.getInstance();
            boolean newState = !config.isAllowGlobalWaypointEditsForEveryone();
            config.setAllowGlobalWaypointEditsForEveryone(newState);

            Universe universe = Universe.get();
            if (universe != null) {
                universe.getWorlds().values().forEach(w -> {
                    if (w == null) return;
                    for (PlayerRef pr : w.getPlayerRefs()) {
                        Holder<EntityStore> holder = pr.getHolder();
                        if (holder == null) continue;
                        Player p = holder.getComponent(Player.getComponentType());
                        if (p != null) {
                            WorldMapHook.sendMapSettingsToPlayer(p);
                        }
                    }
                    WorldMapHook.clearMarkerCaches(w);
                    WorldMapHook.refreshTrackers(w);
                });
            }

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            playerRef.sendMessage(Message.raw("Global shared waypoint edit/delete is now " + status + ".").color(color));
            if (newState) {
                playerRef.sendMessage(Message.raw("All players can edit/delete shared waypoints (owners can always edit their own).").color(Color.GRAY));
            } else {
                playerRef.sendMessage(Message.raw("Only marker owners or players with bettermap.command.waypoint.editglobal can edit/delete shared waypoints.").color(Color.GRAY));
            }
        }, world);
    }
}

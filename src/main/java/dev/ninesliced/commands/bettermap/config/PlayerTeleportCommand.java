package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.utils.WorldMapHook;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

public class PlayerTeleportCommand extends AbstractCommand {

    public PlayerTeleportCommand() {
        super("playerteleport", "Toggle player-to-player map teleports");
        this.addAliases("playertp");
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
            boolean newState = !config.isAllowPlayerTeleports();
            config.setAllowPlayerTeleports(newState);

            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            playerRef.sendMessage(Message.raw("Player Teleports " + status).color(color));
            if (newState) {
                playerRef.sendMessage(Message.raw("Players can now teleport to other players on the map.").color(Color.GRAY));
            } else {
                playerRef.sendMessage(Message.raw("Player-to-player teleports are now disabled.").color(Color.GRAY));
            }
        }, world);
    }
}

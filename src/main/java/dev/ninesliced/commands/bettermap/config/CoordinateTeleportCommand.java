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
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

public class CoordinateTeleportCommand extends AbstractCommand {

    public CoordinateTeleportCommand() {
        super("coordinateteleport", "Toggle coordinate (click-on-map) teleports");
        this.addAliases("coordtp");
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
            boolean newState = !config.isAllowCoordinateTeleports();
            config.setAllowCoordinateTeleports(newState);

            MapPrivacyManager.getInstance().updatePrivacyState();

            for (PlayerRef pr : world.getPlayerRefs()) {
                Holder<EntityStore> h = pr.getHolder();
                if (h == null) continue;
                Player p = h.getComponent(Player.getComponentType());
                if (p != null) {
                    WorldMapHook.sendMapSettingsToPlayer(p);
                }
            }

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            playerRef.sendMessage(Message.raw("Coordinate Teleports " + status).color(color));
            if (newState) {
                playerRef.sendMessage(Message.raw("Players can now teleport by clicking on the map.").color(Color.GRAY));
            } else {
                playerRef.sendMessage(Message.raw("Coordinate teleports are now disabled.").color(Color.GRAY));
            }
        }, world);
    }
}

package dev.ninesliced.commands.bettermap.config;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Holder;
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
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;

/**
 * Command to toggle map marker teleports.
 * Usage: /bettermap config markerteleport [poi|warp|death|spawn|all]
 * Without argument, toggles all marker teleports at once.
 */
public class MarkerTeleportCommand extends AbstractCommand {

    private final OptionalArg<String> typeArg = this.withOptionalArg("type", "Type: poi, warp, death, spawn, or all", ArgTypes.STRING);

    public MarkerTeleportCommand() {
        super("markerteleport", "Toggle map marker teleports [poi|warp|death|spawn|all]");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
        this.addAliases("markertp");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext commandContext) {
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

        String typeInput = commandContext.get(this.typeArg);
        String type = (typeInput == null || typeInput.isEmpty()) ? "all" : typeInput.toLowerCase();

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            ModConfig config = ModConfig.getInstance();
            boolean newState;
            String label;

            switch (type) {
                case "poi" -> {
                    newState = !config.isAllowPoiTeleports();
                    config.setAllowPoiTeleports(newState);
                    label = "POI Teleports";
                }
                case "warp" -> {
                    newState = !config.isAllowWarpTeleports();
                    config.setAllowWarpTeleports(newState);
                    label = "Warp Teleports";
                }
                case "death" -> {
                    newState = !config.isAllowDeathTeleports();
                    config.setAllowDeathTeleports(newState);
                    label = "Death Teleports";
                }
                case "spawn" -> {
                    newState = !config.isAllowSpawnTeleports();
                    config.setAllowSpawnTeleports(newState);
                    label = "Spawn Teleports";
                }
                case "all" -> {
                    newState = !config.isAnyMarkerTeleportEnabled();
                    config.setAllMarkerTeleports(newState);
                    label = "All Marker Teleports";
                }
                default -> {
                    playerRef.sendMessage(Message.raw("Unknown type: " + type + ". Use: poi, warp, death, spawn, or all.").color(Color.RED));
                    return;
                }
            }

            MapPrivacyManager.getInstance().updatePrivacyState();

            for (PlayerRef pr : world.getPlayerRefs()) {
                Holder<EntityStore> h = pr.getHolder();
                if (h == null) continue;
                Player p = h.getComponent(Player.getComponentType());
                if (p != null) {
                    WorldMapHook.sendMapSettingsToPlayer(p);
                }
            }

            MapPrivacyManager.getInstance().updatePrivacyState();
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            playerRef.sendMessage(Message.raw(label + " " + status).color(color));
        }, world);
    }
}

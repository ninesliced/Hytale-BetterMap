package dev.ninesliced.commands.config;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerDeathPositionData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;

import java.util.logging.Logger;

public class HideDeathMarkerCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(HideDeathMarkerCommand.class.getName());

    public HideDeathMarkerCommand() {
        super("hidedeath", "Toggle hiding the death marker");
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

            BetterMapConfig config = BetterMapConfig.getInstance();
            boolean newState = !config.isHideDeathMarkerOnMap();
            config.setHideDeathMarkerOnMap(newState);

            PlayerConfig playerConfig = playerRef.getUuid() != null
                ? PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid())
                : null;
            if (playerConfig != null) {
                playerConfig.setOverrideGlobalDeathHide(false);
                PlayerConfigManager.getInstance().savePlayerConfig(playerRef.getUuid());
            }

            if (newState) {
                removeDeathMarkersFromAllPlayers(world);
            }

            PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            boolean visible = !newState;
            Color color = visible ? Color.GREEN : Color.RED;
            String status = visible ? "VISIBLE" : "HIDDEN";

            playerRef.sendMessage(Message.raw("Death markers are now " + status + " on the map.").color(color));
        }, world);
    }

    private void removeDeathMarkersFromAllPlayers(World world) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Holder<EntityStore> holder = playerRef.getHolder();
            if (holder == null) continue;
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) continue;

            try {
                PlayerWorldData worldData = player.getPlayerConfigData().getPerWorldData(world.getName());
                if (worldData == null) continue;

                List<PlayerDeathPositionData> deathPositions = worldData.getDeathPositions();
                if (deathPositions == null || deathPositions.isEmpty()) continue;

                List<String> markerIdsToRemove = new ArrayList<>();
                for (PlayerDeathPositionData deathPosition : deathPositions) {
                    if (deathPosition != null && deathPosition.getMarkerId() != null) {
                        markerIdsToRemove.add(deathPosition.getMarkerId());
                    }
                }

                if (markerIdsToRemove.isEmpty()) continue;

                UpdateWorldMap packet = new UpdateWorldMap(
                    null,
                    null,
                    markerIdsToRemove.toArray(new String[0])
                );
                playerRef.getPacketHandler().write(packet);
            } catch (Exception e) {
                LOGGER.warning("Failed to remove death markers from player: " + e.getMessage());
            }
        }
    }
}

package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class SpawnPrivacyProvider implements WorldMapManager.MarkerProvider {
    public static final String PROVIDER_ID = "spawn";
    private static final Logger LOGGER = Logger.getLogger(SpawnPrivacyProvider.class.getName());

    @Override
    public void update(World world, Player player, MarkersCollector collector) {
        try {
            ModConfig globalConfig = ModConfig.getInstance();

            if (player == null) {
                return;
            }

            UUID playerUuid = ((CommandSender) player).getUuid();
            PlayerConfig playerConfig = null;
            if (playerUuid != null) {
                playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);
            }

            boolean canOverrideSpawn = PermissionsUtil.canOverrideSpawn(player);
            boolean overrideEnabled = canOverrideSpawn
                && playerConfig != null
                && playerConfig.isOverrideGlobalSpawnHide();

            if (globalConfig.isHideSpawnOnMap() && !overrideEnabled) {
                return;
            }

            if (!overrideEnabled) {
                List<String> hiddenNames = globalConfig.getHiddenPoiNames();
                if (hiddenNames != null) {
                    for (String hidden : hiddenNames) {
                        if ("spawn".equalsIgnoreCase(hidden.trim())) {
                            return;
                        }
                    }
                }
            }

            if (playerUuid != null && playerConfig != null) {
                if (playerConfig.isHideSpawnOnMap()) {
                    return;
                }
                List<String> playerHiddenNames = playerConfig.getHiddenPoiNames();
                if (playerHiddenNames != null) {
                    for (String hidden : playerHiddenNames) {
                        if ("spawn".equalsIgnoreCase(hidden.trim())) {
                            return;
                        }
                    }
                }
            }

            GameplayConfig gameplayConfig = world.getGameplayConfig();
            if (gameplayConfig == null) {
                return;
            }

            WorldMapConfig worldMapConfig = gameplayConfig.getWorldMapConfig();
            if (worldMapConfig == null || !worldMapConfig.isDisplaySpawn()) {
                return;
            }

            ISpawnProvider spawnProvider = world.getWorldConfig().getSpawnProvider();
            if (spawnProvider == null) {
                return;
            }

            Transform spawnTransform = spawnProvider.getSpawnPoint(world, playerUuid);
            if (spawnTransform == null) {
                return;
            }

            Vector3d position = spawnTransform.getPosition();

            boolean hasNativeTeleport = player.getWorldMapTracker() != null
                && player.getWorldMapTracker().isAllowTeleportToMarkers();
            boolean showTeleport = globalConfig.isAllowMapMarkerTeleports()
                && PermissionsUtil.canTeleport(player)
                && !hasNativeTeleport;
            ContextMenuItem[] contextMenuItems = null;
            if (showTeleport) {
                int x = (int) Math.round(position.x);
                int y = (int) Math.round(position.y);
                int z = (int) Math.round(position.z);
                contextMenuItems = new ContextMenuItem[]{
                    new ContextMenuItem("Teleport", "bettermap waypoint markertp " + x + " " + y + " " + z)
                };
            }

            FormattedMessage displayName = new FormattedMessage();
            displayName.rawText = "Spawn";

            MapMarker marker = new MapMarker(
                "Spawn",
                displayName,
                displayName.rawText,
                "Spawn.png",
                PositionUtil.toTransformPacket(new Transform(position)),
                contextMenuItems,
                null
            );
            collector.add(marker);
        } catch (Exception e) {
            LOGGER.warning("Error in SpawnPrivacyProvider.update: " + e.getMessage());
        }
    }
}

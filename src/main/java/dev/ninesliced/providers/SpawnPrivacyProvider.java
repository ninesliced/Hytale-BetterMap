package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Provider that can optionally hide spawn markers on the world map.
 */
public class SpawnPrivacyProvider implements WorldMapManager.MarkerProvider {
    public static final String PROVIDER_ID = "spawn";
    private static final Logger LOGGER = Logger.getLogger(SpawnPrivacyProvider.class.getName());

    @Override
    public void update(World world, MapMarkerTracker tracker, int viewRadius, int chunkX, int chunkZ) {
        try {
            BetterMapConfig globalConfig = BetterMapConfig.getInstance();

            // Check if spawn markers should be hidden globally
            if (globalConfig.isHideSpawnOnMap()) {
                return;
            }

            // Check if "Spawn" is in global hiddenPoiNames
            List<String> hiddenNames = globalConfig.getHiddenPoiNames();
            if (hiddenNames != null) {
                for (String hidden : hiddenNames) {
                    if ("spawn".equalsIgnoreCase(hidden.trim())) {
                        return;
                    }
                }
            }

            Player player = tracker.getPlayer();
            if (player == null) {
                return;
            }

            // Check per-player settings (only if not globally hidden)
            UUID playerUuid = player.getUuid();
            if (playerUuid != null) {
                PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);
                if (playerConfig != null) {
                    // Check player's personal hide spawn setting
                    if (playerConfig.isHideSpawnOnMap()) {
                        return;
                    }
                    // Check player's personal hidden names
                    List<String> playerHiddenNames = playerConfig.getHiddenPoiNames();
                    if (playerHiddenNames != null) {
                        for (String hidden : playerHiddenNames) {
                            if ("spawn".equalsIgnoreCase(hidden.trim())) {
                                return;
                            }
                        }
                    }
                }
            }

            // Otherwise, show spawn marker (same logic as original SpawnMarkerProvider)
            var gameplayConfig = world.getGameplayConfig();
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

            Transform spawnTransform = spawnProvider.getSpawnPoint(player);
            if (spawnTransform == null) {
                return;
            }

            Vector3d position = spawnTransform.getPosition();
            float yaw = spawnTransform.getRotation().getYaw();

            tracker.trySendMarker(viewRadius, chunkX, chunkZ, position, yaw, "Spawn", "Spawn",
                position, (id, name, pos) -> new MapMarker(
                    id,
                    name,
                    "Spawn.png",
                    PositionUtil.toTransformPacket(new Transform(pos)),
                    null
                ));
        } catch (Exception e) {
            LOGGER.warning("Error in SpawnPrivacyProvider.update: " + e.getMessage());
        }
    }
}

package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PlayerRadarManager;
import dev.ninesliced.managers.PlayerRadarManager.RadarData;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Provides player markers on the world map, allowing players to see other players'
 * positions and distances. This implements a radar-like functionality for the map.
 */
public class PlayerRadarProvider implements WorldMapManager.MarkerProvider {

    private static final Logger LOGGER = Logger.getLogger(PlayerRadarProvider.class.getName());
    private static final String MARKER_PREFIX = "PlayerRadar-";
    private static final String MARKER_ICON = "Player.png";
    public static final String PROVIDER_ID = "BetterMapPlayerRadar";

    /**
     * Updates the player radar markers for the viewing player.
     */
    @Override
    public void update(World world, GameplayConfig gameplayConfig, WorldMapTracker tracker,
                       int viewRadius, int chunkX, int chunkZ) {
        try {
            Player viewingPlayer = tracker.getPlayer();
            UUID viewerUuid = ((CommandSender) viewingPlayer).getUuid();

            BetterMapConfig globalConfig = BetterMapConfig.getInstance();

            // Check global settings
            if (!globalConfig.isRadarEnabled() || globalConfig.isHidePlayersOnMap()) {
                return;
            }

            // Check per-player hide setting
            if (viewerUuid != null) {
                PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(viewerUuid);
                if (playerConfig != null && playerConfig.isHidePlayersOnMap()) {
                    return;
                }
            }

            List<RadarData> radarDataList = PlayerRadarManager.getInstance().getRadarData(world.getName());

            RadarData viewerData = null;
            for (RadarData data : radarDataList) {
                if (data.uuid.equals(viewerUuid.toString())) {
                    viewerData = data;
                    break;
                }
            }

            if (viewerData == null) {
                return;
            }
            Vector3d viewerPos = viewerData.position;

            int radarRange = globalConfig.getRadarRange();
            boolean infiniteRange = radarRange < 0;
            long rangeSquared = infiniteRange ? Long.MAX_VALUE : (long) radarRange * radarRange;

            for (RadarData otherData : radarDataList) {
                if (otherData.uuid.equals(viewerUuid.toString())) {
                    continue;
                }

                try {
                    Vector3d otherPos = otherData.position;

                    double dx = otherPos.x - viewerPos.x;
                    double dy = otherPos.y - viewerPos.y;
                    double dz = otherPos.z - viewerPos.z;
                    double distanceSquared = dx * dx + dy * dy + dz * dz;

                    if (!infiniteRange && distanceSquared > (double) rangeSquared) {
                        continue;
                    }

                    int distance = (int) Math.sqrt(distanceSquared);
                    String markerId = MARKER_PREFIX + otherData.uuid;
                    String markerName = otherData.name + " (" + distance + "m)";

                    float yaw = otherData.rotation != null ? otherData.rotation.getYaw() : 0.0f;

                    tracker.trySendMarker(
                        viewRadius,
                        chunkX,
                        chunkZ,
                        otherPos,
                        yaw,
                        markerId,
                        markerName,
                        otherData,
                        PlayerRadarProvider::createMarker
                    );
                } catch (Exception e) {}
            }
        } catch (Exception e) {
            LOGGER.warning("Error in PlayerRadarProvider.update: " + e.getMessage());
        }
    }

    /**
     * Creates a MapMarker for a player.
     */
    private static MapMarker createMarker(String id, String name, RadarData data) {
        com.hypixel.hytale.math.vector.Transform vecTransform = new com.hypixel.hytale.math.vector.Transform(
            data.position,
            data.rotation != null ? data.rotation : Vector3f.ZERO
        );
        return new MapMarker(
            id,
            name,
            MARKER_ICON,
            PositionUtil.toTransformPacket(vecTransform),
            null
        );
    }
}

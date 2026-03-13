package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PlayerRadarManager;
import dev.ninesliced.managers.PlayerRadarManager.RadarData;
import dev.ninesliced.utils.MarkerTeleportUtil;
import dev.ninesliced.utils.PermissionsUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public void update(World world, Player viewingPlayer, MarkersCollector collector) {
        try {
            UUID viewerUuid = ((CommandSender) viewingPlayer).getUuid();

            ModConfig globalConfig = ModConfig.getInstance();
            if (!globalConfig.isRadarEnabled()) {
                return;
            }

            PlayerConfig playerConfig = viewerUuid != null
                ? PlayerConfigManager.getInstance().getPlayerConfig(viewerUuid)
                : null;

            if (globalConfig.isHidePlayersOnMap()) {
                boolean canBypass = playerConfig != null
                    && playerConfig.isOverrideGlobalPlayersHide()
                    && PermissionsUtil.canOverridePlayers(viewingPlayer);
                if (!canBypass) {
                    return;
                }
            }

            if (playerConfig != null && playerConfig.isHidePlayersOnMap()) {
                return;
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

            // Build a UUID->PlayerRef lookup map once for efficient vanish checks
            Map<UUID, PlayerRef> playerRefMap = new HashMap<>();
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                playerRefMap.put(playerRef.getUuid(), playerRef);
            }

            for (RadarData otherData : radarDataList) {
                if (otherData.uuid.equals(viewerUuid.toString())) {
                    continue;
                }

                try {
                    boolean isHidden = false;
                    try {
                        UUID otherUuid = UUID.fromString(otherData.uuid);
                        PlayerRef playerRef = playerRefMap.get(otherUuid);
                        if (playerRef != null) {
                            isHidden = playerRef.getHiddenPlayersManager().isPlayerHidden(viewerUuid);
                        }
                    } catch (IllegalArgumentException e) { }

                    if (isHidden) {
                        continue;
                    }

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

                    MapMarker marker = createMarker(markerId, markerName, otherData);
                    MarkerTeleportUtil.injectTeleportContextMenu(marker, viewingPlayer, PermissionsUtil.MarkerType.PLAYER);
                    collector.add(marker);
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
        com.hypixel.hytale.protocol.Transform transform = PositionUtil.toTransformPacket(
            new com.hypixel.hytale.math.vector.Transform(
                data.position,
                data.rotation != null ? data.rotation : Vector3f.ZERO
            )
        );

        FormattedMessage displayName = new FormattedMessage();
        displayName.rawText = name;

        return new MapMarker(id, displayName, displayName.rawText, MARKER_ICON, transform, null, null);
    }
}

package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.listeners.ExplorationListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.ChunkUtil;
import dev.ninesliced.utils.MarkerTeleportUtil;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class PoiPlayerMarkerProvider implements WorldMapManager.MarkerProvider {
    public static final String PROVIDER_ID = "playerMarkers";
    private static final Logger LOGGER = Logger.getLogger(PoiPlayerMarkerProvider.class.getName());
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        try {
            if (world == null || collector == null) {
                return;
            }

            if (viewer == null) {
                return;
            }

            PlayerConfigData configData = viewer.getPlayerConfigData();
            if (configData == null) {
                return;
            }

            PlayerWorldData worldData = configData.getPerWorldData(world.getName());
            if (worldData == null) {
                return;
            }

            Collection<? extends UserMapMarker> markersCollection = worldData.getUserMapMarkers();
            if (markersCollection == null || markersCollection.isEmpty()) {
                return;
            }

            ModConfig globalConfig = ModConfig.getInstance();
            boolean canOverridePoi = PermissionsUtil.canOverridePoi(viewer);
            boolean canOverrideUnexplored = PermissionsUtil.canOverrideUnexploredPoi(viewer);
            PlayerConfig playerConfig = null;
            UUID playerUuid = ((CommandSender) viewer).getUuid();
            if (playerUuid != null) {
                playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);
            }
            boolean overrideEnabled = canOverridePoi
                && playerConfig != null
                && playerConfig.isOverrideGlobalPoiHide();
            boolean overrideUnexploredEnabled = canOverrideUnexplored
                && playerConfig != null
                && playerConfig.isOverrideGlobalPoiHide();
            boolean hideAll = globalConfig.isHideAllPoiOnMap() && !overrideEnabled;
            boolean hideUnexplored = globalConfig.isHideUnexploredPoiOnMap() && !overrideUnexploredEnabled;

            if (playerConfig != null && playerConfig.isHideAllPoiOnMap()) {
                hideAll = true;
            }

            List<String> hiddenPoiNames = new ArrayList<>();
            if (!overrideEnabled) {
                List<String> globalHidden = globalConfig.getHiddenPoiNames();
                if (globalHidden != null) {
                    hiddenPoiNames.addAll(globalHidden);
                }
            }

            if (playerConfig != null) {
                List<String> playerHidden = playerConfig.getHiddenPoiNames();
                if (playerHidden != null) {
                    hiddenPoiNames.addAll(playerHidden);
                }
            }

            if (hideUnexplored && !ExplorationListener.isTrackedWorld(world)) {
                hideUnexplored = false;
            }

            boolean filter = hideAll || hideUnexplored || !hiddenPoiNames.isEmpty();
            if (!filter) {
                for (UserMapMarker marker : markersCollection) {
                    if (marker == null) continue;
                    collector.add(convertToMapMarker(world, viewer, marker));
                }
                return;
            }

            Map<String, MapMarker> pointsOfInterest = world.getWorldMapManager().getPointsOfInterest();
            if (pointsOfInterest == null || pointsOfInterest.isEmpty()) {
                for (UserMapMarker marker : markersCollection) {
                    if (marker == null) continue;
                    collector.add(convertToMapMarker(world, viewer, marker));
                }
                return;
            }

            Set<String> poiIds = new HashSet<>(pointsOfInterest.keySet());
            Set<String> poiIdentities = new HashSet<>();
            for (MapMarker poi : pointsOfInterest.values()) {
                if (poi == null) continue;
                String identity = markerIdentity(poi);
                if (identity != null) {
                    poiIdentities.add(identity);
                }
            }

            ExplorationTracker.PlayerExplorationData explorationData = null;
            Set<Long> sharedExploredChunks = null;
            if (hideUnexplored) {
                if (globalConfig.isShareAllExploration()) {
                    sharedExploredChunks = ExplorationManager.getInstance().getAllExploredChunks(world.getName());
                } else {
                    explorationData = ExplorationTracker.getInstance().getPlayerData(viewer);
                }
            }

            for (UserMapMarker marker : markersCollection) {
                if (marker == null) continue;

                boolean isPoi = false;
                String id = marker.getId();
                if (id != null && poiIds.contains(id)) {
                    isPoi = true;
                } else {
                    String identity = userMarkerIdentity(marker);
                    if (identity != null && poiIdentities.contains(identity)) {
                        isPoi = true;
                    }
                }

                if (!isPoi) {
                    collector.add(convertToMapMarker(world, viewer, marker));
                    continue;
                }

                boolean hide = hideAll || shouldHideByName(marker, hiddenPoiNames);
                if (!hide && hideUnexplored) {
                    hide = !isMarkerExplored(marker, explorationData, sharedExploredChunks);
                }

                if (!hide) {
                    collector.add(convertToMapMarker(world, viewer, marker));
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error in PoiPlayerMarkerProvider.update: " + e.getMessage());
        }
    }

    /**
     * Converts a UserMapMarker to a MapMarker for the collector.
     */
    private static MapMarker convertToMapMarker(World world, Player viewer, UserMapMarker marker) {
        double markerY = WaypointManager.getMarkerYOrDefault(world, viewer, marker.getId(), 100.0);
        com.hypixel.hytale.protocol.Transform packetTransform = PositionUtil.toTransformPacket(
            new Transform(marker.getX(), markerY, marker.getZ())
        );

        FormattedMessage displayName = new FormattedMessage();
        displayName.rawText = marker.getName();

        MapMarker result = new MapMarker(
            marker.getId(),
            displayName,
            displayName.rawText,
            marker.getIcon(),
            packetTransform,
            null,
            null
        );
        MarkerTeleportUtil.injectTeleportContextMenu(result, viewer, PermissionsUtil.MarkerType.POI);
        return result;
    }

    private static boolean shouldHideByName(UserMapMarker marker, @Nullable List<String> hiddenPoiNames) {
        if (hiddenPoiNames == null || hiddenPoiNames.isEmpty()) {
            return false;
        }

        String normalizedName = normalize(marker.getName());
        String normalizedId = normalize(marker.getId());
        String normalizedImage = normalize(marker.getIcon());

        for (String hiddenName : hiddenPoiNames) {
            String normalizedHidden = normalize(hiddenName);
            if (normalizedHidden.isEmpty()) {
                continue;
            }
            if (normalizedHidden.equals(normalizedName)
                || normalizedHidden.equals(normalizedId)
                || normalizedHidden.equals(normalizedImage)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isMarkerExplored(UserMapMarker marker,
                                            @Nullable ExplorationTracker.PlayerExplorationData explorationData,
                                            @Nullable Set<Long> sharedExploredChunks) {
        int chunkX = ChunkUtil.blockToChunkCoord((int) marker.getX());
        int chunkZ = ChunkUtil.blockToChunkCoord((int) marker.getZ());
        long chunkIndex = ChunkUtil.chunkCoordsToIndex(chunkX, chunkZ);

        if (sharedExploredChunks != null) {
            return sharedExploredChunks.contains(chunkIndex);
        }

        if (explorationData == null) {
            return false;
        }

        return explorationData.getExploredChunks().isChunkExplored(chunkIndex);
    }

    private static String normalize(@Nullable String input) {
        if (input == null) {
            return "";
        }
        String stripped = HTML_TAG_PATTERN.matcher(input).replaceAll("");
        return stripped.trim().toLowerCase(Locale.ROOT);
    }

    private static String markerIdentity(MapMarker marker) {
        if (marker == null) {
            return null;
        }
        String name = normalize(getMarkerNameAsString(marker));
        String image = normalize(marker.markerImage);
        long chunkIndex = Long.MIN_VALUE;
        if (marker.transform != null && marker.transform.position != null) {
            int chunkX = ChunkUtil.blockToChunkCoord((int) marker.transform.position.x);
            int chunkZ = ChunkUtil.blockToChunkCoord((int) marker.transform.position.z);
            chunkIndex = ChunkUtil.chunkCoordsToIndex(chunkX, chunkZ);
        }
        return name + "|" + image + "|" + chunkIndex;
    }

    private static String userMarkerIdentity(UserMapMarker marker) {
        if (marker == null) {
            return null;
        }
        String name = normalize(marker.getName());
        String image = normalize(marker.getIcon());
        int chunkX = ChunkUtil.blockToChunkCoord((int) marker.getX());
        int chunkZ = ChunkUtil.blockToChunkCoord((int) marker.getZ());
        long chunkIndex = ChunkUtil.chunkCoordsToIndex(chunkX, chunkZ);
        return name + "|" + image + "|" + chunkIndex;
    }

    private static String getMarkerNameAsString(MapMarker marker) {
        if (marker.name == null) {
            return "";
        }
        return marker.name.rawText != null ? marker.name.rawText : "";
    }
}

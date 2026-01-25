package dev.ninesliced.providers;

import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.listeners.ExplorationEventListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.ChunkUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Filters player world map markers that overlap with POIs.
 */
public class PoiPlayerMarkerProvider implements WorldMapManager.MarkerProvider {
    public static final String PROVIDER_ID = "playerMarkers";
    private static final Logger LOGGER = Logger.getLogger(PoiPlayerMarkerProvider.class.getName());

    @Override
    public void update(World world, MapMarkerTracker tracker, int viewRadius, int chunkX, int chunkZ) {
        try {
            if (world == null || tracker == null) {
                return;
            }

            Player viewer = tracker.getPlayer();
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

            MapMarker[] markers = worldData.getWorldMapMarkers();
            if (markers == null || markers.length == 0) {
                return;
            }

            BetterMapConfig globalConfig = BetterMapConfig.getInstance();
            boolean hideAll = globalConfig.isHideAllPoiOnMap();
            boolean hideUnexplored = globalConfig.isHideUnexploredPoiOnMap();

            // Check per-player hide all and merge hidden names
            PlayerConfig playerConfig = null;
            UUID playerUuid = viewer.getUuid();
            if (playerUuid != null) {
                playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);
                if (playerConfig != null && playerConfig.isHideAllPoiOnMap()) {
                    hideAll = true;
                }
            }

            // Merge global and per-player hidden names
            List<String> hiddenPoiNames = new ArrayList<>();
            List<String> globalHidden = globalConfig.getHiddenPoiNames();
            if (globalHidden != null) {
                hiddenPoiNames.addAll(globalHidden);
            }

            // Add per-player hidden names
            if (playerConfig != null) {
                List<String> playerHidden = playerConfig.getHiddenPoiNames();
                if (playerHidden != null) {
                    hiddenPoiNames.addAll(playerHidden);
                }
            }

            if (hideUnexplored && !ExplorationEventListener.isTrackedWorld(world)) {
                hideUnexplored = false;
            }

            boolean filter = hideAll || hideUnexplored || !hiddenPoiNames.isEmpty();
            if (!filter) {
                for (MapMarker marker : markers) {
                    if (marker == null) continue;
                    tracker.trySendMarker(viewRadius, chunkX, chunkZ, marker);
                }
                return;
            }

            Map<String, MapMarker> pointsOfInterest = world.getWorldMapManager().getPointsOfInterest();
            if (pointsOfInterest == null || pointsOfInterest.isEmpty()) {
                for (MapMarker marker : markers) {
                    if (marker == null) continue;
                    tracker.trySendMarker(viewRadius, chunkX, chunkZ, marker);
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

            for (MapMarker marker : markers) {
                if (marker == null) continue;

                boolean isPoi = false;
                String id = marker.id;
                if (id != null && poiIds.contains(id)) {
                    isPoi = true;
                } else {
                    String identity = markerIdentity(marker);
                    if (identity != null && poiIdentities.contains(identity)) {
                        isPoi = true;
                    }
                }

                if (!isPoi) {
                    tracker.trySendMarker(viewRadius, chunkX, chunkZ, marker);
                    continue;
                }

                boolean hide = hideAll || shouldHideByName(marker, hiddenPoiNames);
                if (!hide && hideUnexplored) {
                    hide = !isMarkerExplored(marker, explorationData, sharedExploredChunks);
                }

                if (!hide) {
                    tracker.trySendMarker(viewRadius, chunkX, chunkZ, marker);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error in PoiPlayerMarkerProvider.update: " + e.getMessage());
        }
    }

    private static boolean shouldHideByName(MapMarker marker, @Nullable List<String> hiddenPoiNames) {
        if (hiddenPoiNames == null || hiddenPoiNames.isEmpty()) {
            return false;
        }

        String normalizedName = normalize(marker.name);
        String normalizedId = normalize(marker.id);
        String normalizedImage = normalize(marker.markerImage);

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

    private static boolean isMarkerExplored(MapMarker marker,
                                            @Nullable ExplorationTracker.PlayerExplorationData explorationData,
                                            @Nullable Set<Long> sharedExploredChunks) {
        if (marker.transform == null || marker.transform.position == null) {
            return true;
        }

        int chunkX = ChunkUtil.blockToChunkCoord(marker.transform.position.x);
        int chunkZ = ChunkUtil.blockToChunkCoord(marker.transform.position.z);
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
        String stripped = input.replaceAll("<[^>]*>", "");
        return stripped.trim().toLowerCase(Locale.ROOT);
    }

    private static String markerIdentity(MapMarker marker) {
        if (marker == null) {
            return null;
        }
        String name = normalize(marker.name);
        String image = normalize(marker.markerImage);
        long chunkIndex = Long.MIN_VALUE;
        if (marker.transform != null && marker.transform.position != null) {
            int chunkX = ChunkUtil.blockToChunkCoord(marker.transform.position.x);
            int chunkZ = ChunkUtil.blockToChunkCoord(marker.transform.position.z);
            chunkIndex = ChunkUtil.chunkCoordsToIndex(chunkX, chunkZ);
        }
        return name + "|" + image + "|" + chunkIndex;
    }
}

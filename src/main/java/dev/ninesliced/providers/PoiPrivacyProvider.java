package dev.ninesliced.providers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.listeners.ExplorationEventListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.utils.ChunkUtil;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Provides POI markers on the world map while allowing custom filtering.
 */
public class PoiPrivacyProvider implements WorldMapManager.MarkerProvider {

    public static final String PROVIDER_ID = "poi";
    private static final Logger LOGGER = Logger.getLogger(PoiPrivacyProvider.class.getName());

    public void update(World world, MapMarkerTracker tracker,
                       int viewRadius, int chunkX, int chunkZ) {
        try {
            if (world == null || tracker == null) {
                return;
            }

            Map<String, MapMarker> pointsOfInterest = world.getWorldMapManager().getPointsOfInterest();
            if (pointsOfInterest == null || pointsOfInterest.isEmpty()) {
                return;
            }

            Player viewer = tracker.getPlayer();

            BetterMapConfig config = BetterMapConfig.getInstance();
            if (config.isHideAllPoiOnMap()) {
                return;
            }
            boolean hideUnexplored = config.isHideUnexploredPoiOnMap();
            List<String> hiddenPoiNames = config.getHiddenPoiNames();

            if (hideUnexplored && !ExplorationEventListener.isTrackedWorld(world)) {
                hideUnexplored = false;
            }

            ExplorationTracker.PlayerExplorationData explorationData = null;
            Set<Long> sharedExploredChunks = null;
            if (hideUnexplored) {
                if (config.isShareAllExploration()) {
                    sharedExploredChunks = ExplorationManager.getInstance().getAllExploredChunks(world.getName());
                } else {
                    explorationData = ExplorationTracker.getInstance().getPlayerData(viewer);
                }
            }

            for (MapMarker marker : pointsOfInterest.values()) {
                if (marker == null) {
                    continue;
                }

                if (shouldHideByName(marker, hiddenPoiNames)) {
                    continue;
                }

                if (hideUnexplored && !isMarkerExplored(marker, explorationData, sharedExploredChunks)) {
                    continue;
                }

                tracker.trySendMarker(viewRadius, chunkX, chunkZ, marker);
            }
        } catch (Exception e) {
            LOGGER.warning("Error in PoiPrivacyProvider.update: " + e.getMessage());
        }
    }

    private static boolean shouldHideByName(MapMarker marker, @Nullable List<String> hiddenPoiNames) {
        if (hiddenPoiNames == null || hiddenPoiNames.isEmpty()) {
            return false;
        }

        String normalizedName = normalize(marker.name);
        String normalizedId = normalize(marker.id);

        for (String hiddenName : hiddenPoiNames) {
            String normalizedHidden = normalize(hiddenName);
            if (normalizedHidden.isEmpty()) {
                continue;
            }
            if (normalizedHidden.equals(normalizedName) || normalizedHidden.equals(normalizedId)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isMarkerExplored(MapMarker marker,
                                            @Nullable ExplorationTracker.PlayerExplorationData explorationData,
                                            @Nullable Set<Long> sharedExploredChunks) {
        Transform transform = marker.transform;
        if (transform == null || transform.position == null) {
            return true;
        }

        Position pos = transform.position;
        int chunkX = ChunkUtil.blockToChunkCoord(pos.x);
        int chunkZ = ChunkUtil.blockToChunkCoord(pos.z);
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
}

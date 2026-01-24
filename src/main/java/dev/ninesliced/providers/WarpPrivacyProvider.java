package dev.ninesliced.providers;

import com.hypixel.hytale.builtin.teleport.TeleportPlugin;
import com.hypixel.hytale.builtin.teleport.Warp;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.listeners.ExplorationEventListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.utils.ChunkUtil;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Provides warp markers on the world map while optionally hiding other players' warps.
 */
public class WarpPrivacyProvider implements WorldMapManager.MarkerProvider {

    public static final String PROVIDER_ID = "warps";
    private static final Logger LOGGER = Logger.getLogger(WarpPrivacyProvider.class.getName());
    private static final String MARKER_PREFIX = "Warp-";
    private static final String MARKER_LABEL_PREFIX = "Warp: ";
    private static final String MARKER_ICON = "Warp.png";

    public void update(World world, MapMarkerTracker tracker,
                       int viewRadius, int chunkX, int chunkZ) {
        try {
            if (world == null || tracker == null) {
                return;
            }

            TeleportPlugin plugin = TeleportPlugin.get();
            if (plugin == null) {
                return;
            }

            Map<String, Warp> warps = plugin.getWarps();
            if (warps == null || warps.isEmpty()) {
                return;
            }

//            if (!gameplayConfig.getWorldMapConfig().isDisplayWarps()) {
//                return;
//            }

            Player viewer = tracker.getPlayer();
            String viewerName = viewer.getDisplayName();

            BetterMapConfig config = BetterMapConfig.getInstance();
            boolean hideOtherWarps = config.isHideOtherWarpsOnMap();
            boolean hideUnexploredWarps = config.isHideUnexploredWarpsOnMap();
            if (hideUnexploredWarps && !ExplorationEventListener.isTrackedWorld(world)) {
                hideUnexploredWarps = false;
            }

            ExplorationTracker.PlayerExplorationData explorationData = null;
            Set<Long> sharedExploredChunks = null;
            if (hideUnexploredWarps) {
                if (config.isShareAllExploration()) {
                    sharedExploredChunks = ExplorationManager.getInstance().getAllExploredChunks(world.getName());
                } else if (viewer != null) {
                    explorationData = ExplorationTracker.getInstance().getPlayerData(viewer);
                }
            }

            for (Warp warp : warps.values()) {
                if (warp == null) {
                    continue;
                }

                String warpWorld = warp.getWorld();
                if (warpWorld == null || !warpWorld.equals(world.getName())) {
                    continue;
                }

                if (hideOtherWarps && !isVisibleToViewer(warp, viewerName)) {
                    continue;
                }

                Transform transform = warp.getTransform();
                if (transform == null || transform.getPosition() == null) {
                    continue;
                }

                if (hideUnexploredWarps && !isWarpExplored(transform, explorationData, sharedExploredChunks)) {
                    continue;
                }

                Vector3f rotation = transform.getRotation();
                float yaw = rotation != null ? rotation.getYaw() : 0.0f;

                tracker.trySendMarker(
                    viewRadius,
                    chunkX,
                    chunkZ,
                    transform.getPosition(),
                    yaw,
                    buildMarkerId(warp),
                    buildMarkerName(warp),
                    warp,
                    WarpPrivacyProvider::createMarker
                );
            }
        } catch (Exception e) {
            LOGGER.warning("Error in WarpPrivacyProvider.update: " + e.getMessage());
        }
    }

    private static boolean isVisibleToViewer(Warp warp, @Nullable String viewerName) {
        String creator = warp.getCreator();
        if (creator == null || creator.isEmpty()) {
            return true;
        }
        if (viewerName == null || viewerName.isEmpty()) {
            return false;
        }
        return creator.equalsIgnoreCase(viewerName);
    }

    private static boolean isWarpExplored(Transform transform,
                                          @Nullable ExplorationTracker.PlayerExplorationData explorationData,
                                          @Nullable Set<Long> sharedExploredChunks) {
        int chunkX = ChunkUtil.blockToChunkCoord(transform.getPosition().x);
        int chunkZ = ChunkUtil.blockToChunkCoord(transform.getPosition().z);
        long chunkIndex = ChunkUtil.chunkCoordsToIndex(chunkX, chunkZ);

        if (sharedExploredChunks != null) {
            return sharedExploredChunks.contains(chunkIndex);
        }

        if (explorationData == null) {
            return false;
        }

        return explorationData.getExploredChunks().isChunkExplored(chunkIndex);
    }


    private static String buildMarkerId(Warp warp) {
        String id = warp.getId();
        return id != null ? MARKER_PREFIX + id : MARKER_PREFIX;
    }

    private static String buildMarkerName(Warp warp) {
        String id = warp.getId();
        return id != null ? MARKER_LABEL_PREFIX + id : MARKER_LABEL_PREFIX + "Unknown";
    }

    private static MapMarker createMarker(String id, String name, Warp warp) {
        return new MapMarker(
            id,
            name,
            MARKER_ICON,
            PositionUtil.toTransformPacket(warp.getTransform()),
            null
        );
    }
}

package dev.ninesliced.providers;

import com.hypixel.hytale.builtin.teleport.TeleportPlugin;
import com.hypixel.hytale.builtin.teleport.Warp;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.integration.ExtendedTeleportIntegration;
import dev.ninesliced.listeners.ExplorationEventListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.ChunkUtil;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Provides warp markers on the world map while optionally hiding other players' warps.
 */
public class WarpPrivacyProvider implements WorldMapManager.MarkerProvider {

    public static final String PROVIDER_ID = "warps";
    private static final Logger LOGGER = Logger.getLogger(WarpPrivacyProvider.class.getName());
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
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


            Player viewer = tracker.getPlayer();
            String viewerName = resolveViewerName(viewer);

            BetterMapConfig globalConfig = BetterMapConfig.getInstance();
            boolean canOverrideWarps = viewer != null && PermissionsUtil.canOverrideWarps(viewer);
            boolean canOverrideUnexplored = viewer != null && PermissionsUtil.canOverrideUnexploredWarps(viewer);
            PlayerConfig playerConfig = null;
            if (viewer != null) {
                UUID playerUuid = ((CommandSender) viewer).getUuid();
                if (playerUuid != null) {
                    playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);
                }
            }
            boolean overrideAllEnabled = canOverrideWarps
                && playerConfig != null
                && playerConfig.isOverrideGlobalAllWarpsHide();
            boolean overrideOtherEnabled = canOverrideWarps
                && playerConfig != null
                && playerConfig.isOverrideGlobalOtherWarpsHide();
            
            boolean extendedTeleportAvailable = ExtendedTeleportIntegration.getInstance().isAvailable();
            boolean overrideUnexploredEnabled = canOverrideUnexplored
                && playerConfig != null
                && playerConfig.isOverrideGlobalAllWarpsHide();
            boolean hideAllWarps = globalConfig.isHideAllWarpsOnMap() && !overrideAllEnabled;
            boolean hideOtherWarps = extendedTeleportAvailable 
                && globalConfig.isHideOtherWarpsOnMap() && !overrideOtherEnabled;
            boolean hideUnexploredWarps = globalConfig.isHideUnexploredWarpsOnMap() && !overrideUnexploredEnabled;

            if (playerConfig != null) {
                if (!hideAllWarps && playerConfig.isHideAllWarpsOnMap()) {
                    hideAllWarps = true;
                }
                if (extendedTeleportAvailable && !hideOtherWarps && playerConfig.isHideOtherWarpsOnMap()) {
                    hideOtherWarps = true;
                }
            }

            if (hideAllWarps) {
                return;
            }

            if (hideUnexploredWarps && !ExplorationEventListener.isTrackedWorld(world)) {
                hideUnexploredWarps = false;
            }

            ExplorationTracker.PlayerExplorationData explorationData = null;
            Set<Long> sharedExploredChunks = null;
            if (hideUnexploredWarps) {
                if (globalConfig.isShareAllExploration()) {
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

                if (hideOtherWarps && !isVisibleToViewer(warp, viewer, viewerName)) {
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

    private static boolean isVisibleToViewer(Warp warp, @Nullable Player viewer, @Nullable String viewerName) {
        String creator = warp.getCreator();
        
        if (creator == null || creator.isEmpty()) {
            return true;
        }

        if (creator.startsWith("*") && !creator.equalsIgnoreCase("*Teleporter")) {
            return true;
        }

        if (viewer == null) {
            return false;
        }

        UUID viewerUuid = ((CommandSender) viewer).getUuid();

        if (creator.equalsIgnoreCase("*Teleporter")) {
            ExtendedTeleportIntegration integration = ExtendedTeleportIntegration.getInstance();
            if (integration.isAvailable()) {
                return viewerUuid != null && integration.isPlayerTeleporterOwner(viewerUuid, warp.getId());
            }
            return false;
        }

        String normalizedCreator = normalizeName(creator);
        if (normalizedCreator.isEmpty()) {
            return true;
        }

        String normalizedViewer = normalizeName(viewerName);
        if (!normalizedViewer.isEmpty() && normalizedCreator.equals(normalizedViewer)) {
            return true;
        }

        String displayName = viewer.getDisplayName();
        String normalizedDisplay = normalizeName(displayName);
        if (!normalizedDisplay.isEmpty() && normalizedCreator.equals(normalizedDisplay)) {
            return true;
        }

        if (viewerUuid != null) {
            String uuid = viewerUuid.toString().toLowerCase(Locale.ROOT);
            if (normalizedCreator.equals(uuid)) {
                return true;
            }
            String compactUuid = uuid.replace("-", "");
            if (normalizedCreator.equals(compactUuid)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static String resolveViewerName(@Nullable Player viewer) {
        if (viewer == null) {
            return null;
        }

        try {
            Ref<EntityStore> ref = viewer.getReference();
            if (ref != null) {
                PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                if (playerRef != null && playerRef.getUsername() != null && !playerRef.getUsername().isEmpty()) {
                    return playerRef.getUsername();
                }
            }
        } catch (Exception ignored) {
        }

        return viewer.getDisplayName();
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

    private static String normalizeName(@Nullable String input) {
        if (input == null) {
            return "";
        }
        String stripped = HTML_TAG_PATTERN.matcher(input).replaceAll("");
        return stripped.trim().toLowerCase(Locale.ROOT);
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

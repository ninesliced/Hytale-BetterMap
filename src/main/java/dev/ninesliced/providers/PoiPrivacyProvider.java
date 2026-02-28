package dev.ninesliced.providers;

import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.listeners.ExplorationListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.ChunkUtil;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.server.core.command.system.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Provides POI markers on the world map while allowing custom filtering.
 */
public class PoiPrivacyProvider implements WorldMapManager.MarkerProvider {

    public static final String PROVIDER_ID = "poi";
    private static final Logger LOGGER = Logger.getLogger(PoiPrivacyProvider.class.getName());
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        try {
            if (world == null || collector == null) {
                return;
            }

            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) {
                return;
            }

            Map<String, MapMarker> pointsOfInterest = mapManager.getPointsOfInterest();
            if (pointsOfInterest == null || pointsOfInterest.isEmpty()) {
                return;
            }

            ModConfig globalConfig = ModConfig.getInstance();
            boolean isPrivileged = viewer != null && PermissionsUtil.isAdmin(viewer);
            boolean showTeleport = viewer != null
                && globalConfig.isAllowMapMarkerTeleports()
                && (globalConfig.isAllowContextMenuWaypointTeleports() || isPrivileged)
                && PermissionsUtil.canTeleport(viewer);
            boolean canOverridePoi = viewer != null && PermissionsUtil.canOverridePoi(viewer);
            boolean canOverrideUnexplored = viewer != null && PermissionsUtil.canOverrideUnexploredPoi(viewer);
            PlayerConfig playerConfig = null;
            UUID playerUuid = viewer != null ? ((CommandSender) viewer).getUuid() : null;
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

            if (hideAll) {
                return;
            }

            if (viewer != null && playerUuid != null && playerConfig != null && playerConfig.isHideAllPoiOnMap()) {
                return;
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

            ExplorationTracker.PlayerExplorationData explorationData = null;
            Set<Long> sharedExploredChunks = null;
            if (hideUnexplored) {
                if (globalConfig.isShareAllExploration()) {
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

                collector.add(withTeleportContextMenu(marker, showTeleport));
            }
        } catch (Exception e) {
            LOGGER.warning("Error in PoiPrivacyProvider.update: " + e.getMessage());
        }
    }

    private static MapMarker withTeleportContextMenu(MapMarker marker, boolean showTeleport) {
        if (!showTeleport || marker == null || marker.transform == null || marker.transform.position == null) {
            return marker;
        }

        int x = (int) Math.round(marker.transform.position.x);
        int y = (int) Math.round(marker.transform.position.y);
        int z = (int) Math.round(marker.transform.position.z);
        String command = "bettermap waypoint markertp " + x + " " + y + " " + z;

        MapMarker copy = new MapMarker(marker);
        ContextMenuItem teleportItem = new ContextMenuItem("Teleport", command);

        if (copy.contextMenuItems == null || copy.contextMenuItems.length == 0) {
            copy.contextMenuItems = new ContextMenuItem[]{teleportItem};
            return copy;
        }

        for (ContextMenuItem item : copy.contextMenuItems) {
            if (item != null && command.equals(item.command)) {
                return copy;
            }
        }

        ContextMenuItem[] updated = new ContextMenuItem[copy.contextMenuItems.length + 1];
        System.arraycopy(copy.contextMenuItems, 0, updated, 0, copy.contextMenuItems.length);
        updated[copy.contextMenuItems.length] = teleportItem;
        copy.contextMenuItems = updated;
        return copy;
    }

    private static boolean shouldHideByName(MapMarker marker, @Nullable List<String> hiddenPoiNames) {
        if (hiddenPoiNames == null || hiddenPoiNames.isEmpty()) {
            return false;
        }

        String normalizedName = normalize(getMarkerNameAsString(marker));
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

    private static String getMarkerNameAsString(MapMarker marker) {
        if (marker.name == null) {
            return "";
        }
        return marker.name.rawText != null ? marker.name.rawText : "";
    }

    private static boolean isMarkerExplored(MapMarker marker,
                                            @Nullable ExplorationTracker.PlayerExplorationData explorationData,
                                            @Nullable Set<Long> sharedExploredChunks) {
        Transform transform = marker.transform;
        if (transform == null || transform.position == null) {
            return true;
        }

        Position pos = transform.position;
        int chunkX = ChunkUtil.blockToChunkCoord((int) pos.x);
        int chunkZ = ChunkUtil.blockToChunkCoord((int) pos.z);
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
}

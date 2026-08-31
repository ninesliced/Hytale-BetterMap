package dev.ninesliced.providers;

import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.state.BlockMapMarkersResource;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.entity.entities.Player;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.listeners.ExplorationListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.ChunkUtil;
import dev.ninesliced.utils.MarkerTeleportUtil;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class BlockMapMarkerPrivacyProvider implements WorldMapManager.MarkerProvider {
    public static final String PROVIDER_ID = "blockMapMarkers";
    private static final Logger LOGGER = Logger.getLogger(BlockMapMarkerPrivacyProvider.class.getName());
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        try {
            BlockMapMarkersResource resource = world.getChunkStore().getStore()
                .getResource(BlockMapMarkersResource.getResourceType());
            if (resource == null) {
                return;
            }

            var markers = resource.getMarkers();
            if (markers == null || markers.isEmpty()) {
                return;
            }

            ModConfig globalConfig = ModConfig.getInstance();
            boolean canOverridePoi = viewer != null && PermissionsUtil.canOverridePoi(viewer);
            boolean canOverrideUnexplored = viewer != null && PermissionsUtil.canOverrideUnexploredPoi(viewer);
            PlayerConfig playerConfig = null;
            UUID playerUuid = viewer != null ? viewer.getUuid() : null;
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

            if (playerConfig != null && playerConfig.isHideAllPoiOnMap()) {
                return;
            }

            List<String> hiddenNames = new ArrayList<>();
            if (!overrideEnabled) {
                List<String> globalHidden = globalConfig.getHiddenPoiNames();
                if (globalHidden != null) {
                    hiddenNames.addAll(globalHidden);
                }
            }

            if (playerConfig != null) {
                List<String> playerHidden = playerConfig.getHiddenPoiNames();
                if (playerHidden != null) {
                    hiddenNames.addAll(playerHidden);
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

            // Markers are keyed by block position now, and a discoverable one stays hidden until the
            // viewer has revealed it. This provider replaces the built-in one, so it has to honour
            // that per-player state itself.
            final boolean filterUnexplored = hideUnexplored;
            final ExplorationTracker.PlayerExplorationData viewerExploration = explorationData;
            final Set<Long> sharedExplored = sharedExploredChunks;
            final PlayerWorldData perWorldData = viewer != null
                ? viewer.getPlayerConfigData().getPerWorldData(world.getName())
                : null;

            markers.forEach((x, y, z, markerData) -> {
                String markerId = markerData.getMarkerId();
                if (markerId == null) {
                    return;
                }

                if (markerData.isDiscoverable() && (perWorldData == null || !perWorldData.isMarkerRevealed(markerId))) {
                    return;
                }

                String name = markerData.getName();
                String icon = markerData.getIcon();

                if (shouldHideByName(name, icon, hiddenNames)) {
                    return;
                }

                if (filterUnexplored && !isExplored(x, z, viewerExploration, sharedExplored)) {
                    return;
                }

                Transform transform = new Transform();
                transform.position = new Position(x + 0.5f, y, z + 0.5f);
                transform.orientation = new Direction(0, 0, 0);

                MapMarker marker = new MapMarker(
                    markerId,
                    Message.translation(name).getFormattedMessage(),
                    icon,
                    transform,
                    null,
                    null
                );
                MarkerTeleportUtil.injectTeleportContextMenu(marker, viewer, PermissionsUtil.MarkerType.POI);
                collector.add(marker);
            });
        } catch (Exception e) {
            LOGGER.warning("Error in BlockMapMarkerPrivacyProvider.update: " + e.getMessage());
        }
    }

    private static boolean shouldHideByName(String name, String icon, @Nullable List<String> hiddenNames) {
        if (hiddenNames == null || hiddenNames.isEmpty()) {
            return false;
        }

        String normalizedName = normalize(name);
        String normalizedIcon = normalize(icon);

        for (String hiddenName : hiddenNames) {
            String normalizedHidden = normalize(hiddenName);
            if (normalizedHidden.isEmpty()) {
                continue;
            }
            if (normalizedHidden.equals(normalizedName) || normalizedHidden.equals(normalizedIcon)) {
                return true;
            }
            if (normalizedName.contains(normalizedHidden) || normalizedIcon.contains(normalizedHidden)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExplored(int blockX, int blockZ,
                                      @Nullable ExplorationTracker.PlayerExplorationData explorationData,
                                      @Nullable Set<Long> sharedExploredChunks) {
        int chunkX = ChunkUtil.blockToChunkCoord(blockX);
        int chunkZ = ChunkUtil.blockToChunkCoord(blockZ);
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

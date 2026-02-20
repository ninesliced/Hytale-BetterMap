package dev.ninesliced.providers;

import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
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
import dev.ninesliced.utils.PermissionsUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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

            Long2ObjectMap<BlockMapMarkersResource.BlockMapMarkerData> markers = resource.getMarkers();
            if (markers == null || markers.isEmpty()) {
                return;
            }

            ModConfig globalConfig = ModConfig.getInstance();
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

            boolean hasNativeTeleport = viewer != null
                && viewer.getWorldMapTracker() != null
                && viewer.getWorldMapTracker().isAllowTeleportToMarkers();
            boolean showTeleport = viewer != null
                && globalConfig.isAllowMapMarkerTeleports()
                && PermissionsUtil.canTeleport(viewer)
                && !hasNativeTeleport;

            for (BlockMapMarkersResource.BlockMapMarkerData markerData : markers.values()) {
                String name = markerData.getName();
                String icon = markerData.getIcon();

                if (shouldHideByName(name, icon, hiddenNames)) {
                    continue;
                }

                if (hideUnexplored) {
                    var pos = markerData.getPosition();
                    if (!isExplored(pos.getX(), pos.getZ(), explorationData, sharedExploredChunks)) {
                        continue;
                    }
                }

                var pos = markerData.getPosition();
                Transform transform = new Transform();
                transform.position = new Position(pos.getX() + 0.5f, pos.getY(), pos.getZ() + 0.5f);
                transform.orientation = new Direction(0, 0, 0);

                FormattedMessage displayName = new FormattedMessage();
                displayName.rawText = name;

                ContextMenuItem[] contextMenuItems = null;
                if (showTeleport) {
                    int x = pos.getX();
                    int y = pos.getY();
                    int z = pos.getZ();
                    contextMenuItems = new ContextMenuItem[]{
                        new ContextMenuItem("Teleport", "bettermap waypoint markertp " + x + " " + y + " " + z)
                    };
                }

                MapMarker marker = new MapMarker(
                    markerData.getMarkerId(),
                    displayName,
                    displayName.rawText,
                    icon,
                    transform,
                    contextMenuItems,
                    null
                );
                collector.add(marker);
            }
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

package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.compat.EasyClaimsCompat;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.exploration.ExploredChunksTracker;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.listeners.ExplorationEventListener;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.utils.ChunkUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Marker provider that mirrors EasyClaims overlays but respects BetterMap exploration.
 */
public class EasyClaimsClaimMarkerProvider implements WorldMapManager.MarkerProvider {
    private static final Logger LOGGER = Logger.getLogger(EasyClaimsClaimMarkerProvider.class.getName());

    private static final int DEFAULT_VIEW_RADIUS = 100;
    private static final int CLAIM_CHUNK_SIZE = 32;
    private static final int CLAIM_SUB_CHUNK_FACTOR = 2;
    private static final int CLAIM_CHUNK_MAX_OFFSET = CLAIM_CHUNK_SIZE - 1;
    private static final String MARKER_ICON = "Spawn.png";
    private static final String YOUR_CLAIM_LABEL = "Your Claim";
    private static final long SHARED_CACHE_TTL_MS = 5000L;

    private final Map<String, ExplorationCacheEntry> sharedExplorationCache = new ConcurrentHashMap<>();

    @Override
    public void update(World world, GameplayConfig gameplayConfig, WorldMapTracker tracker,
                       int viewRadius, int centerChunkX, int centerChunkZ) {
        if (world == null || tracker == null) {
            return;
        }
        if (!EasyClaimsCompat.isAvailable()) {
            return;
        }
        if (!ExplorationEventListener.isTrackedWorld(world)) {
            return;
        }

        Player player = tracker.getPlayer();
        if (player == null) {
            return;
        }

        try {
            String worldName = world.getName();
            Map<String, UUID> worldClaims = EasyClaimsCompat.getClaimedChunksInWorld(worldName);
            if (worldClaims.isEmpty()) {
                return;
            }

            ExplorationLookup explorationLookup = createExplorationLookup(player, worldName);
            if (explorationLookup == null) {
                return;
            }

            int scanRadius = viewRadius > 0 ? Math.max(viewRadius, DEFAULT_VIEW_RADIUS) : DEFAULT_VIEW_RADIUS;
            int minX = centerChunkX - scanRadius;
            int maxX = centerChunkX + scanRadius;
            int minZ = centerChunkZ - scanRadius;
            int maxZ = centerChunkZ + scanRadius;

            Map<UUID, List<int[]>> ownerClaims = new HashMap<>();

            for (int chunkX = minX; chunkX <= maxX; chunkX++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    String key = chunkX + "," + chunkZ;
                    UUID owner = worldClaims.get(key);
                    if (owner == null) {
                        continue;
                    }
                    if (!isClaimChunkExplored(explorationLookup, chunkX, chunkZ)) {
                        continue;
                    }
                    ownerClaims.computeIfAbsent(owner, _ -> new ArrayList<>())
                            .add(new int[]{chunkX, chunkZ});
                }
            }

            if (ownerClaims.isEmpty()) {
                return;
            }

            UUID viewerUuid = ((CommandSender) player).getUuid();
            double markerY = resolveMarkerY(player);

            for (Map.Entry<UUID, List<int[]>> entry : ownerClaims.entrySet()) {
                UUID ownerUuid = entry.getKey();
                List<int[]> chunks = entry.getValue();
                if (chunks.isEmpty()) {
                    continue;
                }

                int sumX = 0;
                int sumZ = 0;
                for (int[] chunk : chunks) {
                    sumX += chunk[0];
                    sumZ += chunk[1];
                }

                int avgX = sumX / chunks.size();
                int avgZ = sumZ / chunks.size();

                String ownerName = EasyClaimsCompat.getPlayerName(ownerUuid);
                String displayName = ownerUuid.equals(viewerUuid)
                        ? YOUR_CLAIM_LABEL
                        : ownerName + "'s Claim";

                String markerId = buildClaimMarkerId(ownerUuid, avgX, avgZ);
                Vector3d centerPosition = new Vector3d(
                        avgX * CLAIM_CHUNK_SIZE + (CLAIM_CHUNK_SIZE / 2.0),
                        markerY,
                        avgZ * CLAIM_CHUNK_SIZE + (CLAIM_CHUNK_SIZE / 2.0)
                );
                tracker.trySendMarker(viewRadius, centerChunkX, centerChunkZ,
                        buildMarker(markerId, displayName, centerPosition));

                sendCornerMarkers(tracker, viewRadius, centerChunkX, centerChunkZ,
                        ownerName + "'s Claim", chunks, markerY);
            }
        } catch (Exception e) {
            LOGGER.fine("EasyClaims marker update failed: " + e.getMessage());
        }
    }

    private void sendCornerMarkers(WorldMapTracker tracker, int viewRadius, int centerChunkX, int centerChunkZ,
                                   String displayName, List<int[]> chunks, double markerY) {
        String[] cornerLabels = {"NW", "NE", "SW", "SE"};
        int[][] offsets = {
                {0, 0},
                {CLAIM_CHUNK_MAX_OFFSET, 0},
                {0, CLAIM_CHUNK_MAX_OFFSET},
                {CLAIM_CHUNK_MAX_OFFSET, CLAIM_CHUNK_MAX_OFFSET}
        };

        for (int[] chunk : chunks) {
            int chunkX = chunk[0];
            int chunkZ = chunk[1];
            int baseX = chunkX * CLAIM_CHUNK_SIZE;
            int baseZ = chunkZ * CLAIM_CHUNK_SIZE;

            for (int i = 0; i < cornerLabels.length; i++) {
                int blockX = baseX + offsets[i][0];
                int blockZ = baseZ + offsets[i][1];
                String markerId = "claim_corner_" + chunkX + "_" + chunkZ + "_" + cornerLabels[i];
                Vector3d position = new Vector3d(blockX, markerY, blockZ);
                tracker.trySendMarker(viewRadius, centerChunkX, centerChunkZ,
                        buildMarker(markerId, displayName, position));
            }
        }
    }

    private MapMarker buildMarker(String id, String name, Vector3d position) {
        com.hypixel.hytale.math.vector.Transform transform = new com.hypixel.hytale.math.vector.Transform(position);
        Transform packetTransform = PositionUtil.toTransformPacket(transform);
        return new MapMarker(id, name, MARKER_ICON, packetTransform, null);
    }

    private double resolveMarkerY(Player player) {
        try {
            TransformComponent transform = player.getTransformComponent();
            if (transform != null) {
                return transform.getPosition().y;
            }
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    private static String buildClaimMarkerId(UUID ownerUuid, int chunkX, int chunkZ) {
        String shortId = ownerUuid.toString().substring(0, 8);
        return "claim_" + shortId + "_" + chunkX + "_" + chunkZ;
    }

    private static boolean isClaimChunkExplored(ExplorationLookup lookup, int claimChunkX, int claimChunkZ) {
        int baseX = claimChunkX * CLAIM_SUB_CHUNK_FACTOR;
        int baseZ = claimChunkZ * CLAIM_SUB_CHUNK_FACTOR;
        return lookup.isExploredChunk(baseX, baseZ)
                || lookup.isExploredChunk(baseX + 1, baseZ)
                || lookup.isExploredChunk(baseX, baseZ + 1)
                || lookup.isExploredChunk(baseX + 1, baseZ + 1);
    }

    private ExplorationLookup createExplorationLookup(Player player, String worldName) {
        if (BetterMapConfig.getInstance().isShareAllExploration()) {
            Set<Long> explored = getSharedExploredChunks(worldName);
            return new SetLookup(explored);
        }

        ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(player);
        if (data == null) {
            return null;
        }
        return new TrackerLookup(data.getExploredChunks());
    }

    private interface ExplorationLookup {
        boolean isExploredChunk(int chunkX, int chunkZ);
    }

    private Set<Long> getSharedExploredChunks(String worldName) {
        long now = System.currentTimeMillis();
        ExplorationCacheEntry cached = sharedExplorationCache.get(worldName);
        if (cached != null && now - cached.timestampMs < SHARED_CACHE_TTL_MS) {
            return cached.exploredChunks;
        }

        Set<Long> explored = ExplorationManager.getInstance().getAllExploredChunks(worldName);
        sharedExplorationCache.put(worldName, new ExplorationCacheEntry(now, explored));
        return explored;
    }

    private static final class ExplorationCacheEntry {
        private final long timestampMs;
        private final Set<Long> exploredChunks;

        private ExplorationCacheEntry(long timestampMs, Set<Long> exploredChunks) {
            this.timestampMs = timestampMs;
            this.exploredChunks = exploredChunks;
        }
    }

    private static final class TrackerLookup implements ExplorationLookup {
        private final ExploredChunksTracker tracker;

        private TrackerLookup(ExploredChunksTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public boolean isExploredChunk(int chunkX, int chunkZ) {
            long index = ChunkUtil.chunkCoordsToIndex(chunkX, chunkZ);
            return tracker.isChunkExplored(index);
        }
    }

    private static final class SetLookup implements ExplorationLookup {
        private final Set<Long> explored;

        private SetLookup(Set<Long> explored) {
            this.explored = explored;
        }

        @Override
        public boolean isExploredChunk(int chunkX, int chunkZ) {
            long index = ChunkUtil.chunkCoordsToIndex(chunkX, chunkZ);
            return explored.contains(index);
        }
    }
}

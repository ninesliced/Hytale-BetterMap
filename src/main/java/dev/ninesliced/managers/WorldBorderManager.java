package dev.ninesliced.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.utils.MapImageCompat;
import dev.ninesliced.utils.ReflectionHelper;
import dev.ninesliced.utils.WorldBorderRenderer;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages world border rendering by modifying map chunk images in the WorldMapManager's cache.
 * <p>
 * Memory safety fix:
 * - processedChunks per world is capped at MAX_PROCESSED_CHUNKS. When exceeded, the set is cleared
 * (allowing re-processing of border chunks) rather than growing indefinitely.
 */
public class WorldBorderManager {

    private static final Logger LOGGER = Logger.getLogger(WorldBorderManager.class.getName());
    private static WorldBorderManager instance;

    /**
     * Maximum processed chunks to track per world before clearing.
     * Border chunks near the border are a small fraction of total; non-border chunks
     * are added to skip them. If this grows too large, we just re-evaluate.
     */
    private static final int MAX_PROCESSED_CHUNKS = 100_000;

    private final Set<String> registeredWorlds = new HashSet<>();
    private final Map<String, Set<Long>> processedChunks = new ConcurrentHashMap<>();
    private final Map<String, List<MapChunk>> chunksToResend = new ConcurrentHashMap<>();

    private WorldBorderManager() {
    }

    public static synchronized WorldBorderManager getInstance() {
        if (instance == null) {
            instance = new WorldBorderManager();
        }
        return instance;
    }

    public void registerForPlayer(@Nonnull Player player) {
        World world = player.getWorld();
        if (world != null) {
            registerForWorld(world);
        }
    }

    public void registerForWorld(@Nonnull World world) {
        String worldName = world.getName();
        if (!registeredWorlds.contains(worldName)) {
            registeredWorlds.add(worldName);
            processedChunks.put(worldName, ConcurrentHashMap.newKeySet());
        }
    }

    public void clearAllCaches() {
        processedChunks.values().forEach(Set::clear);
    }

    public void cleanup() {
        registeredWorlds.clear();
        processedChunks.clear();
        chunksToResend.clear();
    }

    public void hookWorldMapManager(@Nonnull World world) {
        ModConfig config = ModConfig.getInstance();
        if (!config.isWorldBorderEnabled()) {
            return;
        }

        String worldName = world.getName();
        Set<Long> processed = processedChunks.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());
        List<MapChunk> toResend = chunksToResend.computeIfAbsent(worldName, k -> new ArrayList<>());

        // FIX: Cap the processed set to prevent unbounded growth
        if (processed.size() > MAX_PROCESSED_CHUNKS) {
            processed.clear();
        }

        try {
            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) {
                return;
            }

            Object imagesField = ReflectionHelper.getFieldValueRecursive(mapManager, "images");
            if (imagesField == null) {
                return;
            }

            toResend.clear();
            processImageCache(imagesField, processed, toResend);

            if (!toResend.isEmpty()) {
                sendModifiedChunksToPlayers(world, toResend);
                toResend.clear();
            }
        } catch (Exception e) {
            LOGGER.fine("Failed to process world border: " + e.getMessage());
        }
    }

    private void sendModifiedChunksToPlayers(@Nonnull World world, @Nonnull List<MapChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        try {
            MapChunk[] chunkArray = chunks.toArray(new MapChunk[0]);
            UpdateWorldMap packet = new UpdateWorldMap(chunkArray, null, null);

            for (PlayerRef player : world.getPlayerRefs()) {
                try {
                    Ref<EntityStore> ref = player.getReference();
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }

                    PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef != null) {
                        playerRef.getPacketHandler().write(packet);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send border chunks: " + e.getMessage());
        }
    }

    private void processImageCache(Object imagesMap, Set<Long> processed, List<MapChunk> toResend) {
        try {
            java.lang.reflect.Method entrySetMethod = null;
            try {
                entrySetMethod = imagesMap.getClass().getMethod("long2ObjectEntrySet");
            } catch (NoSuchMethodException e1) {
                try {
                    entrySetMethod = imagesMap.getClass().getMethod("entrySet");
                } catch (NoSuchMethodException e2) {
                    return;
                }
            }

            Object entrySet = entrySetMethod.invoke(imagesMap);
            if (entrySet instanceof Iterable<?> entries) {
                for (Object entry : entries) {
                    processMapEntry(entry, processed, toResend);
                }
            }
        } catch (Exception ignored) {}
    }

    private void processMapEntry(Object entry, Set<Long> processed, List<MapChunk> toResend) {
        try {
            long chunkIndex;
            try {
                java.lang.reflect.Method getLongKeyMethod = entry.getClass().getMethod("getLongKey");
                chunkIndex = (long) getLongKeyMethod.invoke(entry);
            } catch (NoSuchMethodException e) {
                java.lang.reflect.Method getKeyMethod = entry.getClass().getMethod("getKey");
                Object key = getKeyMethod.invoke(entry);
                if (key instanceof Number n) {
                    chunkIndex = n.longValue();
                } else {
                    return;
                }
            }

            java.lang.reflect.Method getValueMethod = entry.getClass().getMethod("getValue");
            Object imageEntry = getValueMethod.invoke(entry);

            if (imageEntry != null) {
                processImageEntry(chunkIndex, imageEntry, processed, toResend);
            }
        } catch (Exception ignored) {}
    }

    private void processImageEntry(long chunkIndex, Object imageEntry, Set<Long> processed, List<MapChunk> toResend) {
        if (processed.contains(chunkIndex)) {
            return;
        }

        int chunkX = ChunkUtil.xOfChunkIndex(chunkIndex);
        int chunkZ = ChunkUtil.zOfChunkIndex(chunkIndex);

        if (!WorldBorderRenderer.chunkIntersectsBorder(chunkX, chunkZ)) {
            processed.add(chunkIndex);
            return;
        }

        try {
            java.lang.reflect.Field imageField = imageEntry.getClass().getDeclaredField("image");
            imageField.setAccessible(true);
            Object mapImageObj = imageField.get(imageEntry);

            if (mapImageObj instanceof MapImage mapImage) {
                int[] pixels = MapImageCompat.unpackPixels(mapImage);
                if (pixels != null && mapImage.width > 0 && mapImage.height > 0) {
                    WorldBorderRenderer.renderBorderOnData(pixels, mapImage.width, mapImage.height, chunkX, chunkZ);
                    MapImageCompat.repackPixels(mapImage, pixels);
                    processed.add(chunkIndex);
                    toResend.add(new MapChunk(chunkX, chunkZ, mapImage));
                }
            }
        } catch (Exception ignored) {}
    }
}

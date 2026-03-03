package dev.ninesliced.webmap.tiles;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.webmap.data.WebViewFilter;

import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Coordinates tile generation and caching for standard and composite zoom levels.
 */
public class TileManager {
    private static final int EMPTY_TILE_THRESHOLD = 500;

    private final BetterMap plugin;
    private final TileCache memoryCache;
    private final DiskTileCache diskCache;
    private final CompositeTileGenerator compositeTileGenerator;
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> pendingRequests;
    private final ConcurrentHashMap<String, CompletableFuture<PngEncoder.TileData>> pendingPixelRequests;
    private final ConcurrentHashMap<String, PngEncoder.TileData> pixelCache;
    private final Semaphore generationSemaphore;

    public TileManager(BetterMap plugin) {
        this.plugin = plugin;
        this.memoryCache = new TileCache(24000);
        this.diskCache = new DiskTileCache(plugin.getDataDirectory());
        this.compositeTileGenerator = new CompositeTileGenerator(this);
        this.pendingRequests = new ConcurrentHashMap<>();
        this.pendingPixelRequests = new ConcurrentHashMap<>();
        this.pixelCache = new ConcurrentHashMap<>();
        this.generationSemaphore = new Semaphore(16);
    }

    public CompletableFuture<byte[]> getTile(String worldName, TileQuality quality, int zoom, int tileX, int tileZ) {
        return getTile(worldName, quality, zoom, tileX, tileZ, WebViewFilter.Mode.GLOBAL, null);
    }

    public CompletableFuture<byte[]> getTile(String worldName,
                                             TileQuality quality,
                                             int zoom,
                                             int tileX,
                                             int tileZ,
                                             WebViewFilter.Mode mode,
                                             UUID playerUuid) {
        return zoom < 0
            ? getCompositeTile(worldName, quality, zoom, tileX, tileZ, mode, playerUuid)
            : getBaseTile(worldName, quality, tileX, tileZ, mode, playerUuid);
    }

    public CompletableFuture<byte[]> getBaseTile(String worldName, TileQuality quality, int tileX, int tileZ) {
        return getBaseTile(worldName, quality, tileX, tileZ, WebViewFilter.Mode.GLOBAL, null);
    }

    public CompletableFuture<byte[]> getBaseTile(String worldName,
                                                 TileQuality quality,
                                                 int tileX,
                                                 int tileZ,
                                                 WebViewFilter.Mode mode,
                                                 UUID playerUuid) {
        String cacheKey = TileCache.createKey(worldName, 0, tileX, tileZ, quality) + scopeKey(mode, playerUuid);
        boolean diskCacheEnabled = ModConfig.getInstance().isWebMapDiskCacheEnabled();
        boolean allowDiskCacheRead = diskCacheEnabled;
        boolean allowDiskCacheWrite = diskCacheEnabled && mode == WebViewFilter.Mode.GLOBAL;
        byte[] memoryCached = memoryCache.get(cacheKey);
        if (memoryCached != null) {
            return CompletableFuture.completedFuture(memoryCached);
        }

        CompletableFuture<byte[]> pending = pendingRequests.get(cacheKey);
        if (pending != null) {
            return pending;
        }

        if (allowDiskCacheRead) {
            byte[] diskCached = diskCache.get(worldName, quality, 0, tileX, tileZ);
            if (diskCached != null && shouldUseCachedDiskTile(worldName, quality, 0, tileX, tileZ)) {
                if (mode == WebViewFilter.Mode.PLAYER && !isTileVisible(worldName, tileX, tileZ, mode, playerUuid)) {
                    byte[] empty = PngEncoder.encodeEmpty(quality.tileSize());
                    return CompletableFuture.completedFuture(empty);
                }
                memoryCache.put(cacheKey, diskCached);
                return CompletableFuture.completedFuture(diskCached);
            }
        }

        CompletableFuture<byte[]> future = generateTile(worldName, quality, tileX, tileZ, mode, playerUuid);
        pendingRequests.put(cacheKey, future);
        future.whenComplete((data, throwable) -> {
            pendingRequests.remove(cacheKey);
            if (throwable == null && isCacheableTile(data, quality.tileSize())) {
                memoryCache.put(cacheKey, data);
                if (allowDiskCacheWrite && data.length > EMPTY_TILE_THRESHOLD) {
                    diskCache.putAsync(worldName, quality, 0, tileX, tileZ, data);
                }
            }
        });
        return future;
    }

    public CompletableFuture<PngEncoder.TileData> getBaseTileWithPixels(String worldName, TileQuality quality, int tileX, int tileZ) {
        return getBaseTileWithPixels(worldName, quality, tileX, tileZ, WebViewFilter.Mode.GLOBAL, null);
    }

    public CompletableFuture<PngEncoder.TileData> getBaseTileWithPixels(String worldName,
                                                                         TileQuality quality,
                                                                         int tileX,
                                                                         int tileZ,
                                                                         WebViewFilter.Mode mode,
                                                                         UUID playerUuid) {
        String cacheKey = TileCache.createKey(worldName, 0, tileX, tileZ, quality) + scopeKey(mode, playerUuid);
        boolean diskCacheEnabled = ModConfig.getInstance().isWebMapDiskCacheEnabled();
        boolean allowDiskCacheRead = diskCacheEnabled;
        boolean allowDiskCacheWrite = diskCacheEnabled && mode == WebViewFilter.Mode.GLOBAL;
        PngEncoder.TileData cached = pixelCache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<PngEncoder.TileData> pending = pendingPixelRequests.get(cacheKey);
        if (pending != null) {
            return pending;
        }

        if (allowDiskCacheRead) {
            byte[] diskCached = diskCache.get(worldName, quality, 0, tileX, tileZ);
            if (diskCached != null && diskCached.length > EMPTY_TILE_THRESHOLD && shouldUseCachedDiskTile(worldName, quality, 0, tileX, tileZ)) {
                if (mode == WebViewFilter.Mode.PLAYER && !isTileVisible(worldName, tileX, tileZ, mode, playerUuid)) {
                    return CompletableFuture.completedFuture(new PngEncoder.TileData(PngEncoder.encodeEmpty(quality.tileSize()), new int[0], quality.tileSize()));
                }
                int[] pixels = PngDecoder.decode(diskCached, quality.tileSize());
                if (pixels != null) {
                    PngEncoder.TileData data = new PngEncoder.TileData(diskCached, pixels, quality.tileSize());
                    if (pixelCache.size() < 4096) {
                        pixelCache.put(cacheKey, data);
                    }
                    return CompletableFuture.completedFuture(data);
                }
            }
        }

        CompletableFuture<PngEncoder.TileData> future = generateTileWithPixels(worldName, quality, tileX, tileZ, mode, playerUuid);
        pendingPixelRequests.put(cacheKey, future);
        future.whenComplete((data, throwable) -> {
            pendingPixelRequests.remove(cacheKey);
            if (throwable == null && data != null && !data.isEmpty()) {
                if (pixelCache.size() < 4096) {
                    pixelCache.put(cacheKey, data);
                }
                memoryCache.put(cacheKey, data.pngBytes());
                if (allowDiskCacheWrite) {
                    diskCache.putAsync(worldName, quality, 0, tileX, tileZ, data.pngBytes());
                }
            }
        });
        return future;
    }

    public CompletableFuture<PngEncoder.TileData> getTileWithPixels(String worldName,
                                                                     TileQuality quality,
                                                                     int zoom,
                                                                     int tileX,
                                                                     int tileZ,
                                                                     WebViewFilter.Mode mode,
                                                                     UUID playerUuid) {
        if (zoom >= 0) {
            return getBaseTileWithPixels(worldName, quality, tileX, tileZ, mode, playerUuid);
        }

        String cacheKey = TileCache.createKey(worldName, zoom, tileX, tileZ, quality) + scopeKey(mode, playerUuid);
        boolean diskCacheEnabled = ModConfig.getInstance().isWebMapDiskCacheEnabled();
        boolean allowDiskCacheRead = diskCacheEnabled && mode != WebViewFilter.Mode.PLAYER;
        boolean allowDiskCacheWrite = diskCacheEnabled && mode == WebViewFilter.Mode.GLOBAL;

        PngEncoder.TileData cached = pixelCache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<PngEncoder.TileData> pending = pendingPixelRequests.get(cacheKey);
        if (pending != null) {
            return pending;
        }

        if (allowDiskCacheRead) {
            byte[] diskCached = diskCache.get(worldName, quality, zoom, tileX, tileZ);
            if (diskCached != null && shouldUseCachedDiskTile(worldName, quality, zoom, tileX, tileZ) && diskCached.length > EMPTY_TILE_THRESHOLD) {
                int[] pixels = PngDecoder.decode(diskCached, quality.tileSize());
                if (pixels != null) {
                    PngEncoder.TileData data = new PngEncoder.TileData(diskCached, pixels, quality.tileSize());
                    if (pixelCache.size() < 4096) {
                        pixelCache.put(cacheKey, data);
                    }
                    memoryCache.put(cacheKey, diskCached);
                    return CompletableFuture.completedFuture(data);
                }
            }
        }

        if (isCompositeTileFullyUnexplored(worldName, zoom, tileX, tileZ, mode, playerUuid)) {
            return CompletableFuture.completedFuture(new PngEncoder.TileData(PngEncoder.encodeEmpty(quality.tileSize()), new int[0], quality.tileSize()));
        }

        CompletableFuture<PngEncoder.TileData> future = compositeTileGenerator.generateCompositeTileWithPixels(worldName, quality, zoom, tileX, tileZ, mode, playerUuid);
        pendingPixelRequests.put(cacheKey, future);
        future.whenComplete((data, throwable) -> {
            pendingPixelRequests.remove(cacheKey);
            if (throwable == null && data != null && !data.isEmpty()) {
                if (pixelCache.size() < 4096) {
                    pixelCache.put(cacheKey, data);
                }
                memoryCache.put(cacheKey, data.pngBytes());
                if (allowDiskCacheWrite && data.pngBytes().length > EMPTY_TILE_THRESHOLD) {
                    diskCache.putAsync(worldName, quality, zoom, tileX, tileZ, data.pngBytes());
                }
            }
        });
        return future;
    }

    private CompletableFuture<byte[]> getCompositeTile(String worldName,
                                                       TileQuality quality,
                                                       int zoom,
                                                       int tileX,
                                                       int tileZ,
                                                       WebViewFilter.Mode mode,
                                                       UUID playerUuid) {
        String cacheKey = TileCache.createKey(worldName, zoom, tileX, tileZ, quality) + scopeKey(mode, playerUuid);
        boolean diskCacheEnabled = ModConfig.getInstance().isWebMapDiskCacheEnabled();
        // Composite tiles on disk were rendered from GLOBAL data, so they contain imagery
        // for all players' explored chunks. Skip disk reads in PLAYER mode to avoid showing
        // chunks the player hasn't visited.
        boolean allowDiskCacheRead = diskCacheEnabled && mode != WebViewFilter.Mode.PLAYER;
        boolean allowDiskCacheWrite = diskCacheEnabled && mode == WebViewFilter.Mode.GLOBAL;

        byte[] memoryCached = memoryCache.get(cacheKey);
        if (memoryCached != null) {
            return CompletableFuture.completedFuture(memoryCached);
        }

        CompletableFuture<byte[]> pending = pendingRequests.get(cacheKey);
        if (pending != null) {
            return pending;
        }

        if (allowDiskCacheRead) {
            byte[] diskCached = diskCache.get(worldName, quality, zoom, tileX, tileZ);
            if (diskCached != null && shouldUseCachedDiskTile(worldName, quality, zoom, tileX, tileZ)) {
                memoryCache.put(cacheKey, diskCached);
                return CompletableFuture.completedFuture(diskCached);
            }
        }

        if (isCompositeTileFullyUnexplored(worldName, zoom, tileX, tileZ, mode, playerUuid)) {
            return CompletableFuture.completedFuture(PngEncoder.encodeEmpty(quality.tileSize()));
        }

        CompletableFuture<byte[]> future = getTileWithPixels(worldName, quality, zoom, tileX, tileZ, mode, playerUuid)
            .thenApply(PngEncoder.TileData::pngBytes);
        pendingRequests.put(cacheKey, future);
        future.whenComplete((data, throwable) -> {
            pendingRequests.remove(cacheKey);
            if (throwable == null && isCacheableTile(data, quality.tileSize())) {
                memoryCache.put(cacheKey, data);
                if (allowDiskCacheWrite && data.length > EMPTY_TILE_THRESHOLD) {
                    diskCache.putAsync(worldName, quality, zoom, tileX, tileZ, data);
                }
            }
        });
        return future;
    }

    private boolean isCacheableTile(byte[] data, int tileSize) {
        return data != null && !Arrays.equals(data, PngEncoder.encodeEmpty(tileSize));
    }

    private CompletableFuture<byte[]> generateTile(String worldName,
                                                   TileQuality quality,
                                                   int tileX,
                                                   int tileZ,
                                                   WebViewFilter.Mode mode,
                                                   UUID playerUuid) {
        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            return CompletableFuture.completedFuture(PngEncoder.encodeEmpty(quality.tileSize()));
        }

        if (!isTileVisible(worldName, tileX, tileZ, mode, playerUuid)) {
            return CompletableFuture.completedFuture(PngEncoder.encodeEmpty(quality.tileSize()));
        }

        WorldMapManager mapManager = world.getWorldMapManager();
        if (mapManager == null) {
            return CompletableFuture.completedFuture(PngEncoder.encodeEmpty(quality.tileSize()));
        }

        try {
            generationSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(PngEncoder.encodeEmpty(quality.tileSize()));
        }

        return mapManager.getImageAsync(tileX, tileZ)
            .thenApply(image -> encodeImageOrEmpty(image, quality))
            .exceptionally(ignored -> PngEncoder.encodeEmpty(quality.tileSize()))
            .whenComplete((ignored, throwable) -> generationSemaphore.release());
    }

    private CompletableFuture<PngEncoder.TileData> generateTileWithPixels(String worldName,
                                                                          TileQuality quality,
                                                                          int tileX,
                                                                          int tileZ,
                                                                          WebViewFilter.Mode mode,
                                                                          UUID playerUuid) {
        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            return CompletableFuture.completedFuture(new PngEncoder.TileData(PngEncoder.encodeEmpty(quality.tileSize()), new int[0], quality.tileSize()));
        }

        if (!isTileVisible(worldName, tileX, tileZ, mode, playerUuid)) {
            return CompletableFuture.completedFuture(new PngEncoder.TileData(PngEncoder.encodeEmpty(quality.tileSize()), new int[0], quality.tileSize()));
        }

        WorldMapManager mapManager = world.getWorldMapManager();
        if (mapManager == null) {
            return CompletableFuture.completedFuture(new PngEncoder.TileData(PngEncoder.encodeEmpty(quality.tileSize()), new int[0], quality.tileSize()));
        }

        try {
            generationSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(new PngEncoder.TileData(PngEncoder.encodeEmpty(quality.tileSize()), new int[0], quality.tileSize()));
        }

        return mapManager.getImageAsync(tileX, tileZ)
            .thenApply(image -> {
                if (image == null) {
                    return new PngEncoder.TileData(PngEncoder.encodeEmpty(quality.tileSize()), new int[0], quality.tileSize());
                }
                return PngEncoder.encodeWithPixels(image, quality.tileSize());
            })
            .exceptionally(ignored -> new PngEncoder.TileData(PngEncoder.encodeEmpty(quality.tileSize()), new int[0], quality.tileSize()))
            .whenComplete((ignored, throwable) -> generationSemaphore.release());
    }

    private byte[] encodeImageOrEmpty(MapImage image, TileQuality quality) {
        if (image == null) {
            return PngEncoder.encodeEmpty(quality.tileSize());
        }
        return PngEncoder.encode(image, quality.tileSize());
    }

    private boolean shouldUseCachedDiskTile(String worldName,
                                            TileQuality quality,
                                            int zoom,
                                            int tileX,
                                            int tileZ) {
        if (zoom < 0) {
            return true;
        }

        ModConfig config = ModConfig.getInstance();
        int refreshRadius = config.getWebMapRefreshRadiusChunks();
        int refreshMinutes = config.getWebMapRefreshIntervalMinutes();

        if (refreshMinutes <= 0) {
            return true;
        }

        if (!isTileWithinPlayerRefreshRadius(worldName, tileX, tileZ, refreshRadius)) {
            return true;
        }

        long ageMs = diskCache.getTileAgeMs(worldName, quality, zoom, tileX, tileZ);
        long refreshIntervalMs = refreshMinutes * 60_000L;
        return ageMs < refreshIntervalMs;
    }

    private boolean isTileWithinPlayerRefreshRadius(String worldName, int tileX, int tileZ, int radiusChunks) {
        if (radiusChunks < 0) {
            return false;
        }

        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            return false;
        }

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            try {
                Transform transform = playerRef.getTransform();
                if (transform == null) {
                    continue;
                }

                Vector3d position = transform.getPosition();
                int playerChunkX = (int) Math.floor(position.x) >> 5;
                int playerChunkZ = (int) Math.floor(position.z) >> 5;

                if (Math.abs(playerChunkX - tileX) <= radiusChunks && Math.abs(playerChunkZ - tileZ) <= radiusChunks) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    private boolean isTileVisible(String worldName, int tileX, int tileZ, WebViewFilter.Mode mode, UUID playerUuid) {
        if (!ModConfig.getInstance().isWebMapShowOnlyExplored()) {
            return true;
        }

        long chunkIndex = ChunkUtil.indexChunk(tileX, tileZ);
        ExplorationManager explorationManager = ExplorationManager.getInstance();

        if (mode == WebViewFilter.Mode.PLAYER && playerUuid != null) {
            Set<Long> explored = explorationManager.getExploredChunksForPlayer(worldName, playerUuid);
            return explored.contains(chunkIndex);
        }

        return explorationManager.getAllExploredChunks(worldName).contains(chunkIndex);
    }

    private String scopeKey(WebViewFilter.Mode mode, UUID playerUuid) {
        if (mode == WebViewFilter.Mode.PLAYER) {
            return "#player:" + (playerUuid == null ? "none" : playerUuid);
        }
        return "#global";
    }

    private boolean isCompositeTileFullyUnexplored(String worldName,
                                                   int zoom,
                                                   int tileX,
                                                   int tileZ,
                                                   WebViewFilter.Mode mode,
                                                   UUID playerUuid) {
        if (!ModConfig.getInstance().isWebMapShowOnlyExplored() || zoom >= 0) {
            return false;
        }

        Set<Long> explored;
        ExplorationManager explorationManager = ExplorationManager.getInstance();
        if (mode == WebViewFilter.Mode.PLAYER && playerUuid != null) {
            explored = explorationManager.getExploredChunksForPlayer(worldName, playerUuid);
        } else {
            explored = explorationManager.getAllExploredChunks(worldName);
        }

        if (explored.isEmpty()) {
            return true;
        }

        int chunksPerAxis = 1 << -zoom;
        int baseChunkX = tileX * chunksPerAxis;
        int baseChunkZ = tileZ * chunksPerAxis;

        for (int dz = 0; dz < chunksPerAxis; dz++) {
            for (int dx = 0; dx < chunksPerAxis; dx++) {
                long chunkIndex = ChunkUtil.indexChunk(baseChunkX + dx, baseChunkZ + dz);
                if (explored.contains(chunkIndex)) {
                    return false;
                }
            }
        }

        return true;
    }

    public void clearMemoryCache() {
        memoryCache.clear();
        pixelCache.clear();
    }

    public void clearAllCache() {
        clearMemoryCache();
        diskCache.clear();
    }

    public int getMemoryCacheSize() {
        return memoryCache.size();
    }

    public void shutdown() {
        diskCache.shutdown();
    }
}

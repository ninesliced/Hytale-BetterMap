package dev.ninesliced.webmap.tiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Persistent tile cache used to avoid expensive re-rendering between requests.
 */
public class DiskTileCache {
    private static final Logger LOGGER = Logger.getLogger(DiskTileCache.class.getName());
    private static final String CACHE_SCHEMA_VERSION = "v2";

    private final Path cacheDirectory;
    private final ConcurrentHashMap<String, Long> tileTimestamps;
    private final ExecutorService diskExecutor;

    public DiskTileCache(Path dataDirectory) {
        this.cacheDirectory = dataDirectory.resolve("webmap").resolve("tilecache");
        this.tileTimestamps = new ConcurrentHashMap<>();
        this.diskExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "BetterMap-WebMap-DiskIO");
            thread.setDaemon(true);
            return thread;
        });

        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            LOGGER.warning("Failed to create tile cache directory: " + e.getMessage());
        }
    }

    public byte[] get(String worldName, TileQuality quality, int zoom, int x, int z) {
        Path tilePath = getTilePath(worldName, quality, zoom, x, z);
        if (!Files.exists(tilePath)) {
            return null;
        }
        try {
            return Files.readAllBytes(tilePath);
        } catch (IOException e) {
            return null;
        }
    }

    public CompletableFuture<byte[]> getAsync(String worldName, TileQuality quality, int zoom, int x, int z) {
        return CompletableFuture.supplyAsync(() -> get(worldName, quality, zoom, x, z), diskExecutor);
    }

    public void putAsync(String worldName, TileQuality quality, int zoom, int x, int z, byte[] data) {
        tileTimestamps.put(createKey(worldName, quality, zoom, x, z), System.currentTimeMillis());
        diskExecutor.execute(() -> put(worldName, quality, zoom, x, z, data));
    }

    public void put(String worldName, TileQuality quality, int zoom, int x, int z, byte[] data) {
        Path tilePath = getTilePath(worldName, quality, zoom, x, z);
        try {
            Files.createDirectories(tilePath.getParent());
            Files.write(tilePath, data);
            tileTimestamps.put(createKey(worldName, quality, zoom, x, z), System.currentTimeMillis());
        } catch (IOException e) {
            LOGGER.fine("Failed to cache tile " + tilePath + ": " + e.getMessage());
        }
    }

    public long getTileAgeMs(String worldName, TileQuality quality, int zoom, int x, int z) {
        String key = createKey(worldName, quality, zoom, x, z);
        Long cachedTimestamp = tileTimestamps.get(key);
        if (cachedTimestamp != null) {
            return System.currentTimeMillis() - cachedTimestamp;
        }

        Path tilePath = getTilePath(worldName, quality, zoom, x, z);
        if (!Files.exists(tilePath)) {
            return Long.MAX_VALUE;
        }

        try {
            BasicFileAttributes attributes = Files.readAttributes(tilePath, BasicFileAttributes.class);
            long modified = attributes.lastModifiedTime().toMillis();
            tileTimestamps.put(key, modified);
            return System.currentTimeMillis() - modified;
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    public void clear() {
        tileTimestamps.clear();
        try {
            if (!Files.exists(cacheDirectory)) {
                return;
            }
            Files.walk(cacheDirectory)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            LOGGER.warning("Failed to clear tile cache: " + e.getMessage());
        }
    }

    public void shutdown() {
        diskExecutor.shutdown();
    }

    private Path getTilePath(String worldName, TileQuality quality, int zoom, int x, int z) {
        return cacheDirectory
            .resolve(CACHE_SCHEMA_VERSION)
            .resolve(worldName)
            .resolve(quality.id())
            .resolve(String.valueOf(zoom))
            .resolve(x + "_" + z + ".png");
    }

    private static String createKey(String worldName, TileQuality quality, int zoom, int x, int z) {
        return worldName + "/" + quality.id() + "/" + zoom + "/" + x + "/" + z;
    }
}

package dev.ninesliced.webmap.tiles;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock-free LRU-style in-memory cache for rendered tile bytes.
 */
public class TileCache {
    private final ConcurrentHashMap<String, byte[]> cache;
    private final ConcurrentLinkedDeque<String> accessOrder;
    private final int maxSize;
    private final AtomicInteger size;

    public TileCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>(maxSize);
        this.accessOrder = new ConcurrentLinkedDeque<>();
        this.size = new AtomicInteger(0);
    }

    public byte[] get(String key) {
        byte[] value = cache.get(key);
        if (value != null) {
            accessOrder.remove(key);
            accessOrder.addLast(key);
        }
        return value;
    }

    public void put(String key, byte[] data) {
        if (cache.putIfAbsent(key, data) == null) {
            accessOrder.addLast(key);
            for (int currentSize = size.incrementAndGet(); currentSize > maxSize; currentSize = size.decrementAndGet()) {
                String oldest = accessOrder.pollFirst();
                if (oldest == null || cache.remove(oldest) == null) {
                    break;
                }
            }
        } else {
            cache.put(key, data);
            accessOrder.remove(key);
            accessOrder.addLast(key);
        }
    }

    public void clear() {
        cache.clear();
        accessOrder.clear();
        size.set(0);
    }

    public int size() {
        return size.get();
    }

    public static String createKey(String worldName, int zoom, int x, int z, TileQuality quality) {
        return worldName + "/" + quality.id() + "/" + zoom + "/" + x + "/" + z;
    }
}

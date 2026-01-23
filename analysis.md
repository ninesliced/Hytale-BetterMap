# BetterMap Plugin - Deep Analysis & Optimization Report

## Executive Summary

This document provides a comprehensive analysis of the Hytale BetterMap plugin, focusing on performance bottlenecks, bugs, memory optimization opportunities, and recommendations for loading more chunks in high quality.

**Current Limitations:**
- HIGH quality: 3,000 chunks max (32x32 resolution)
- MEDIUM quality: 10,000 chunks max (16x16 resolution)  
- LOW quality: 30,000 chunks max (8x8 resolution)

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Performance Bottlenecks](#2-performance-bottlenecks)
3. [Memory Analysis](#3-memory-analysis)
4. [Thread Safety Issues & Bugs](#4-thread-safety-issues--bugs)
5. [Optimization Recommendations](#5-optimization-recommendations)
6. [Implementation Priority](#6-implementation-priority)

---

## 1. Architecture Overview

### Core Components

| Component | File | Purpose |
|-----------|------|---------|
| `ExplorationTicker` | `exploration/ExplorationTicker.java` | Scheduled executor running at 100ms intervals |
| `ExplorationTracker` | `exploration/ExplorationTracker.java` | Central tracker for player exploration data |
| `ExploredChunksTracker` | `exploration/ExploredChunksTracker.java` | Thread-safe chunk tracking with ReadWriteLock |
| `RestrictedSpiralIterator` | `utils/WorldMapHook.java` | Custom iterator for explored chunks |
| `MapExpansionManager` | `managers/MapExpansionManager.java` | Manages map boundary calculations |
| `ExplorationPersistence` | `configs/ExplorationPersistence.java` | Binary file I/O for chunk data |

### Data Flow

```
Player Movement → ExplorationTicker (100ms) → updateWorldPlayers()
    ↓
WorldMapHook.updateExplorationState()
    ↓
MapExpansionManager.updateBoundaries() → marks chunks explored
    ↓
RestrictedSpiralIterator.init() → builds sorted chunk list
    ↓
manageLoadedChunks() → sends UpdateWorldMap packets
```

---

## 2. Performance Bottlenecks

### 2.1 High-Frequency Tick Rate (CRITICAL)

**Location:** `ExplorationTicker.java:53`
```java
scheduler.scheduleAtFixedRate(this::tick, 1000, 100, TimeUnit.MILLISECONDS);
```

**Issue:** The ticker runs every 100ms (10 times per second), which is excessive for exploration tracking.

**Impact:** 
- Unnecessary CPU usage
- Frequent world thread context switches
- Adds up significantly with many players

**Recommendation:** 
Increase interval to 250-500ms. Players don't need sub-second map updates.

---

### 2.2 Inefficient Chunk Iteration in RestrictedSpiralIterator (CRITICAL)

**Location:** `WorldMapHook.java:454-526`

**Issues:**

1. **Full chunk set copying on every init():**
```java
exploredWorldChunks = data.getExploredChunks().getExploredChunks(); // Creates HashSet copy
```

2. **Sorting all chunks by distance every call:**
```java
rankedChunks.sort(Comparator.comparingDouble(idx -> {
    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
    return Math.sqrt(Math.pow(mx - cx, 2) + Math.pow(mz - cz, 2));
}));
```

3. **Creating new ArrayList on every iteration:**
```java
this.targetMapChunks = new ArrayList<>(boundaryChunks);
this.targetMapChunks.addAll(rankedChunks);
```

**Impact:** O(n log n) sorting operation runs frequently with potentially 30,000+ chunks.

**Recommendation:**
- Cache sorted chunk lists and only re-sort when player moves significantly
- Use squared distances (avoid Math.sqrt())
- Pre-allocate collections with expected capacity

---

### 2.3 Circular Area Calculation Creates Excessive Objects (HIGH)

**Location:** `ChunkUtil.java:63-77`

```java
public static Set<Long> getChunksInCircularArea(int centerChunkX, int centerChunkZ, int radiusChunks) {
    Set<Long> chunks = new HashSet<>();
    int radiusSquared = radiusChunks * radiusChunks;
    for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            if (dx * dx + dz * dz <= radiusSquared) {
                chunks.add(chunkCoordsToIndex(centerChunkX + dx, centerChunkZ + dz));
            }
        }
    }
    return chunks;
}
```

**Issue:** 
With default `explorationRadius = 16`, this creates up to π×16² ≈ 804 Long objects per call, plus HashSet overhead.

**Recommendation:**
- Pre-compute circular offsets as a static array
- Reuse chunk sets with clear() instead of creating new ones

---

### 2.4 Reflection Usage on Hot Path (HIGH)

**Location:** `WorldMapHook.java` - Multiple locations

```java
ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);
ReflectionHelper.getFieldValueRecursive(tracker, "loaded");
ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
```

**Issue:** Reflection is used in the update loop, which is orders of magnitude slower than direct field access.

**Recommendation:**
- Cache Field objects after first lookup
- Use MethodHandles instead of reflection for better JIT optimization
- Consider Unsafe for maximum performance (with caution)

---

### 2.5 Unoptimized Shared Exploration Loading (MEDIUM)

**Location:** `ExplorationManager.java:155-174`

```java
public java.util.Set<Long> getAllExploredChunks(String worldName) {
    Set<Long> allChunks = new HashSet<>();
    if (persistenceEnabled) {
        allChunks.addAll(persistence.loadAllChunks(worldName));  // Reads ALL files from disk!
    }
    // ... also iterates all player data
}
```

**Issue:** When `shareAllExploration` is enabled, this reads ALL player exploration files from disk on every iterator init.

**Recommendation:**
- Cache the shared exploration data with invalidation on changes
- Use memory-mapped files or a proper database for shared data

---

### 2.6 Synchronous Persistence Operations (MEDIUM)

**Location:** `ExplorationPersistence.java:183-192`

```java
try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
    out.writeInt(DATA_VERSION);
    out.writeInt(chunks.size());
    for (Long chunk : chunks) {
        out.writeLong(chunk);
    }
}
```

**Issue:** While some saves are offloaded to ForkJoinPool, the saveAllPlayers() method can still block.

**Recommendation:**
- Use dedicated I/O thread pool
- Implement write-ahead logging for crash safety
- Consider async compression

---

## 3. Memory Analysis

### 3.1 Current Memory Usage Estimation

For a player with 10,000 explored chunks:

| Data Structure | Memory Usage |
|---------------|--------------|
| LongOpenHashSet (10K longs) | ~80 KB |
| HashSet copy operations | +80 KB per copy |
| ArrayList for sorting | +80 KB |
| MapBoundaries per boundary chunk | ~32 bytes each |

**Total per player:** ~300-500 KB active memory during updates

### 3.2 Memory Pressure Points

1. **ExploredChunksTracker.getExploredChunks():**
   ```java
   return new HashSet<>(persistentComponent.getExploredChunks()); // Full copy every time!
   ```
   This creates a full copy of the chunk set on every access.

2. **Chunk packet creation:**
   ```java
   unloadPackets.add(new MapChunk(mx, mz, null));
   ```
   Creates new objects for each chunk update.

3. **No chunk data compression:**
   The persistence format stores raw longs (8 bytes each), which could be compressed significantly using delta encoding or run-length encoding for contiguous regions.

---

### 3.3 Why High Quality is Limited to 3,000 Chunks

**Location:** `BetterMapConfig.java:770-772`

```java
public enum MapQuality {
    LOW(0.25f, 30000),   // 8x8 per chunk = 64 bytes image data
    MEDIUM(0.5f, 10000), // 16x16 per chunk = 256 bytes image data
    HIGH(1.0f, 3000);    // 32x32 per chunk = 1024 bytes image data
}
```

**Memory calculation for map textures:**
- LOW: 30,000 × 64 bytes = ~1.9 MB
- MEDIUM: 10,000 × 256 bytes = ~2.5 MB  
- HIGH: 3,000 × 1,024 bytes = ~3.0 MB

The limit isn't just texture memory, but the overhead of:
- Packet serialization (UpdateWorldMap packets)
- Network bandwidth
- Client-side rendering

---

## 4. Thread Safety Issues & Bugs

### 4.1 Race Condition in RestrictedSpiralIterator (BUG)

**Location:** `WorldMapHook.java:397-405`

```java
private volatile Iterator<Long> currentIterator;
private volatile List<Long> targetMapChunks = new ArrayList<>();
// ...
public RestrictedSpiralIterator(...) {
    super();
    super.init(0, 0, 0, 1);  // Parent class may not be thread-safe
    this.data = data;
    this.tracker = tracker;
    this.currentIterator = Collections.emptyIterator();
    this.initialized = true;
}
```

**Issue:** While `volatile` is used, the iterator is read and written from multiple threads without proper synchronization for compound operations.

**Example race:**
1. Thread A checks `hasNext()` → true
2. Thread B calls `init()` → resets iterator
3. Thread A calls `next()` → NoSuchElementException or wrong data

**Recommendation:** Use `synchronized` blocks or `AtomicReference` for iterator swaps.

---

### 4.2 ConcurrentModificationException Risk (BUG)

**Location:** `WorldMapHook.java:227-234`

```java
List<Long> loadedSnapshot = new ArrayList<>(loaded);  // Snapshot

for (Long idx : loadedSnapshot) {
    if (!targetSet.contains(idx)) {
        toUnload.add(idx);
        // ...
    }
}

toUnload.forEach(loaded::remove);  // Modifies original set
```

**Issue:** The `loaded` set could be modified by another thread between snapshot creation and removal.

---

### 4.3 Potential Memory Leak in playerWorlds Map (BUG)

**Location:** `ExplorationEventListener.java:37`

```java
private static final java.util.Map<String, String> playerWorlds = new java.util.concurrent.ConcurrentHashMap<>();
```

**Issue:** If `onPlayerQuit` fails before `playerWorlds.remove(playerName)`, the entry persists.

**Evidence:**
```java
// Line 296 - only reached if no exceptions
playerWorlds.remove(playerName);
```

**Recommendation:** Use try-finally to ensure cleanup.

---

### 4.4 Exception Swallowing (CODE SMELL)

**Multiple locations:**

```java
} catch (Exception _) {}  // ExplorationPersistence.java:144
} catch (Exception ignored) {}  // ExplorationTicker.java:93-94
} catch (Exception e) {}  // PlayerRadarProvider.java:101
```

**Impact:** Silent failures make debugging difficult and can hide serious issues.

---

### 4.5 Possible NPE in Shared Exploration Mode

**Location:** `WorldMapHook.java:466-468`

```java
if (BetterMapConfig.getInstance().isShareAllExploration()) {
    World world = player.getWorld();
    String worldName = world != null ? world.getName() : "world";  // Fallback to "world"
```

**Issue:** If player's world is null but shared exploration is on, it defaults to "world" which may not be the correct world, causing data to be loaded from wrong location.

---

## 5. Optimization Recommendations

### 5.1 Immediate Performance Wins (Low Effort, High Impact)

#### A. Reduce Tick Rate
```java
// Change from 100ms to 250ms
scheduler.scheduleAtFixedRate(this::tick, 1000, 250, TimeUnit.MILLISECONDS);
```
**Estimated improvement:** 60% reduction in tick overhead

#### B. Cache Reflection Fields
```java
public class ReflectionCache {
    private static final Map<String, Field> fieldCache = new ConcurrentHashMap<>();
    
    public static Field getField(Class<?> clazz, String name) {
        String key = clazz.getName() + "." + name;
        return fieldCache.computeIfAbsent(key, k -> {
            // Use existing ReflectionHelper.getFieldRecursive method
            Field f = ReflectionHelper.getFieldRecursive(clazz, name);
            if (f != null) f.setAccessible(true);
            return f;
        });
    }
}
```
**Estimated improvement:** 10-20x faster field access

#### C. Pre-compute Circular Offsets
```java
public class ChunkUtil {
    private static final int MAX_RADIUS = 32;
    private static final long[][] CIRCULAR_OFFSETS = new long[MAX_RADIUS + 1][];
    
    static {
        for (int r = 0; r <= MAX_RADIUS; r++) {
            List<Long> offsets = new ArrayList<>();
            int rSq = r * r;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= rSq) {
                        offsets.add(chunkCoordsToIndex(dx, dz));
                    }
                }
            }
            CIRCULAR_OFFSETS[r] = offsets.stream().mapToLong(Long::longValue).toArray();
        }
    }
    
    public static void addChunksInCircle(Set<Long> target, int cx, int cz, int radius) {
        for (long offset : CIRCULAR_OFFSETS[Math.min(radius, MAX_RADIUS)]) {
            int dx = indexToChunkX(offset);
            int dz = indexToChunkZ(offset);
            target.add(chunkCoordsToIndex(cx + dx, cz + dz));
        }
    }
}
```
**Estimated improvement:** 80% reduction in chunk calculation time

---

### 5.2 Medium-Term Optimizations (Medium Effort)

#### A. Implement Spatial Indexing for Chunks

Replace `HashSet<Long>` with a spatial data structure:

```java
public class ChunkQuadTree {
    private static final int MAX_DEPTH = 10;
    private static final int BUCKET_SIZE = 64;
    
    private QuadNode root;
    
    public void add(int chunkX, int chunkZ) { /* ... */ }
    
    public List<Long> getChunksInRadius(int cx, int cz, int radius) {
        // O(log n) instead of O(n) for range queries
    }
    
    public List<Long> getNearestChunks(int cx, int cz, int count) {
        // Efficient k-nearest neighbors
    }
}
```

**Benefit:** Enables efficient range queries for chunk loading/unloading

#### B. Implement Chunk LOD (Level of Detail)

Dynamically adjust chunk resolution based on distance:

```java
public float getChunkScale(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
    int distance = Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ));
    
    if (distance < 16) return 1.0f;      // Full resolution
    if (distance < 64) return 0.5f;      // Half resolution
    if (distance < 256) return 0.25f;    // Quarter resolution
    return 0.125f;                        // Eighth resolution
}
```

**Benefit:** Load 3x more chunks while maintaining detail near player

#### C. Implement Delta Updates

Instead of recomputing everything on movement:

```java
public class IncrementalChunkManager {
    private volatile Set<Long> previouslyLoaded = new HashSet<>();
    private final Object lock = new Object();
    
    public ChunkDelta computeDelta(Set<Long> newChunks) {
        synchronized (lock) {
            Set<Long> toLoad = new HashSet<>(newChunks);
            toLoad.removeAll(previouslyLoaded);
            
            Set<Long> toUnload = new HashSet<>(previouslyLoaded);
            toUnload.removeAll(newChunks);
            
            previouslyLoaded = new HashSet<>(newChunks);
            return new ChunkDelta(toLoad, toUnload);
        }
    }
}
```

**Benefit:** Reduces packet size and processing time by 90%+ in steady state

---

### 5.3 Long-Term Architectural Changes (High Effort, High Impact)

#### A. Implement Chunk Streaming with Priority Queue

```java
public class ChunkStreamingManager {
    private final PriorityQueue<ChunkLoadRequest> loadQueue;
    private final int maxBandwidthChunksPerSecond;
    
    public void tick() {
        int loaded = 0;
        while (loaded < maxBandwidthChunksPerSecond && !loadQueue.isEmpty()) {
            ChunkLoadRequest req = loadQueue.poll();
            if (req.isStillNeeded()) {
                sendChunkToPlayer(req);
                loaded++;
            }
        }
    }
}
```

**Benefit:** Smooth loading, no lag spikes, prioritizes visible chunks

#### B. Implement Chunk Data Compression

```java
public class CompressedChunkStorage {
    // Store chunks as regions (like Minecraft's region files)
    // Use delta encoding for chunk indices
    // Apply LZ4 compression for ~4x size reduction
    
    public byte[] compress(Set<Long> chunks) {
        long[] sorted = chunks.stream().sorted().mapToLong(Long::longValue).toArray();
        
        // Delta encode
        for (int i = sorted.length - 1; i > 0; i--) {
            sorted[i] -= sorted[i-1];
        }
        
        // Variable-length encode and compress
        return LZ4.compress(VarInt.encode(sorted));
    }
}
```

**Benefit:** 4-10x storage reduction, faster I/O

#### C. Implement Region-Based Shared Exploration

Instead of per-player files for shared exploration:

```java
public class RegionBasedExploration {
    // Store exploration in 512x512 chunk regions
    // Use bitsets for compact storage (1 bit per chunk = 32KB per region)
    // Memory-map region files for fast access
    
    private final Map<Long, MappedByteBuffer> regionCache;
    
    public boolean isExplored(int chunkX, int chunkZ) {
        long regionKey = getRegionKey(chunkX >> 5, chunkZ >> 5);
        MappedByteBuffer region = regionCache.get(regionKey);
        int bitIndex = getBitIndex(chunkX & 31, chunkZ & 31);
        return (region.get(bitIndex >> 3) & (1 << (bitIndex & 7))) != 0;
    }
}
```

**Benefit:** Near-instant shared exploration lookups, minimal memory

---

### 5.4 Specific Changes to Increase High Quality Chunk Limit

To increase HIGH quality from 3,000 to 10,000+ chunks:

1. **Implement chunk texture caching on disk:**
   - Cache rendered chunk textures instead of re-rendering
   - Use DXT/BC1 compression for textures

2. **Implement view frustum culling:**
   - Only send chunks visible on the current map view
   - Reduces network bandwidth by 50-80%

3. **Implement progressive loading:**
   - Load low-res first, upgrade to high-res when idle
   - Provides immediate feedback while maintaining quality

4. **Batch packet sending:**
   ```java
   // Instead of individual MapChunk packets
   // Combine up to 100 chunks per UpdateWorldMap packet
   private static final int BATCH_SIZE = 100;
   ```

**Estimated new limits with optimizations:**
- HIGH quality: 8,000-12,000 chunks (3-4x improvement)
- MEDIUM quality: 25,000-40,000 chunks
- LOW quality: 80,000-100,000 chunks

---

## 6. Implementation Priority

### Phase 1: Quick Wins (1-2 days)
| Priority | Change | Impact | Risk |
|----------|--------|--------|------|
| P0 | Reduce tick rate to 250ms | High | Very Low |
| P0 | Cache reflection Field objects | Medium | Low |
| P0 | Pre-compute circular offsets | Medium | Low |
| P1 | Use squared distances in sorting | Low | Very Low |
| P1 | Fix exception swallowing | Low | Very Low |

### Phase 2: Stability & Memory (3-5 days)
| Priority | Change | Impact | Risk |
|----------|--------|--------|------|
| P0 | Fix RestrictedSpiralIterator race conditions | High | Medium |
| P0 | Add try-finally cleanup for player data | Medium | Low |
| P1 | Implement delta updates for chunks | High | Medium |
| P1 | Cache shared exploration data | High | Medium |
| P2 | Avoid copying chunk sets unnecessarily | Medium | Low |

### Phase 3: Architecture (1-2 weeks)
| Priority | Change | Impact | Risk |
|----------|--------|--------|------|
| P1 | Implement spatial indexing | Very High | Medium |
| P1 | Implement chunk streaming | Very High | High |
| P2 | Implement chunk compression | Medium | Low |
| P2 | Implement region-based storage | High | Medium |

### Phase 4: High Quality Expansion (2-3 weeks)
| Priority | Change | Impact | Risk |
|----------|--------|--------|------|
| P1 | Implement LOD system | Very High | High |
| P2 | Implement view frustum culling | High | Medium |
| P2 | Implement progressive loading | High | High |
| P3 | Implement texture caching | Medium | Medium |

---

## Appendix: Profiling Recommendations

To validate these findings and measure improvements:

1. **Add timing metrics:**
```java
public class PerformanceMetrics {
    private static final AtomicLong tickCount = new AtomicLong();
    private static final AtomicLong totalTickTimeNanos = new AtomicLong();
    
    public static void recordTick(long nanos) {
        tickCount.incrementAndGet();
        totalTickTimeNanos.addAndGet(nanos);
    }
    
    public static double getAverageTickMs() {
        return totalTickTimeNanos.get() / (double) tickCount.get() / 1_000_000.0;
    }
}
```

2. **Add memory tracking:**
```java
Runtime runtime = Runtime.getRuntime();
long usedMemory = runtime.totalMemory() - runtime.freeMemory();
LOGGER.info("Memory usage: " + (usedMemory / 1024 / 1024) + " MB");
```

3. **Enable GC logging in JVM args:**
```
-Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=10M
```

---

## Conclusion

The BetterMap plugin has a solid foundation but several opportunities for optimization. The most impactful changes are:

1. **Reducing tick rate** - Immediate 60% reduction in CPU overhead
2. **Caching reflection fields** - 10-20x faster field access
3. **Implementing delta updates** - 90% reduction in update processing
4. **Spatial indexing** - Enables efficient range queries for large chunk counts

With Phase 1-3 optimizations implemented, the HIGH quality chunk limit could realistically be increased from 3,000 to 8,000-12,000 chunks without impacting server performance.

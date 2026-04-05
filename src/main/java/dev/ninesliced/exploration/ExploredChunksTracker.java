package dev.ninesliced.exploration;

import dev.ninesliced.components.ExplorationComponent;
import dev.ninesliced.managers.ExplorationManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Thread-safe tracker for the set of explored chunks.
 * Uses a persistent component if available, otherwise falls back to memory storage.
 * Enforces a configurable per-player chunk cap to prevent unbounded memory growth.
 */
public class ExploredChunksTracker {
    private static final Logger LOGGER = Logger.getLogger(ExploredChunksTracker.class.getName());

    private final Set<Long> memoryExploredChunks;
    private final ExplorationComponent persistentComponent;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile long version = 0;

    private volatile Set<Long> cachedSnapshot = null;
    private volatile long cachedSnapshotVersion = -1;

    /**
     * Creates a new tracker.
     *
     * @param component The persistent component to use (can be null).
     */
    public ExploredChunksTracker(@Nullable ExplorationComponent component) {
        this.persistentComponent = component;
        if (component == null) {
            this.memoryExploredChunks = ConcurrentHashMap.newKeySet();
        } else {
            this.memoryExploredChunks = null;
        }
    }

    /**
     * Checks whether the chunk cap has been reached.
     * If so, new chunks will not be added.
     */
    private boolean isAtCapacity() {
        int cap = ExplorationManager.getInstance().getMaxStoredChunksPerPlayer();
        if (cap <= 0 || cap == Integer.MAX_VALUE) {
            return false;
        }
        return getExploredCount() >= cap;
    }

    /**
     * Marks a single chunk as explored.
     * Respects the per-player chunk cap — once the cap is reached, no new chunks are added.
     *
     * @param chunkIndex The chunk index to mark.
     * @return true if the chunk was newly added, false if already explored or at capacity.
     */
    public boolean markChunkExplored(long chunkIndex) {
        if (persistentComponent != null) {
            if (isAtCapacity() && !persistentComponent.isExplored(chunkIndex)) {
                return false;
            }
            boolean added = persistentComponent.addExploredChunk(chunkIndex);
            if (added) {
                version++;
                cachedSnapshot = null;
            }
            return added;
        }

        lock.writeLock().lock();
        try {
            if (isAtCapacity() && !memoryExploredChunks.contains(chunkIndex)) {
                return false;
            }
            boolean added = memoryExploredChunks.add(chunkIndex);
            if (added) {
                version++;
                cachedSnapshot = null;
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marks multiple chunks as explored.
     * Respects the per-player chunk cap — stops adding once the cap is reached.
     *
     * @param chunkIndices The set of chunk indices.
     * @return the number of newly added chunks.
     */
    public int markChunksExplored(@Nonnull Set<Long> chunkIndices) {
        if (chunkIndices.isEmpty()) return 0;

        int cap = ExplorationManager.getInstance().getMaxStoredChunksPerPlayer();
        boolean hasCap = cap > 0 && cap != Integer.MAX_VALUE;

        if (persistentComponent != null) {
            int added = 0;
            for (Long chunk : chunkIndices) {
                if (hasCap && (getExploredCount() + added) >= cap && !persistentComponent.isExplored(chunk)) {
                    break;
                }
                if (persistentComponent.addExploredChunk(chunk)) {
                    added++;
                }
            }
            if (added > 0) {
                version++;
                cachedSnapshot = null;
            }
            return added;
        }

        lock.writeLock().lock();
        try {
            int added = 0;
            for (Long chunk : chunkIndices) {
                if (hasCap && memoryExploredChunks.size() >= cap && !memoryExploredChunks.contains(chunk)) {
                    break;
                }
                if (memoryExploredChunks.add(chunk)) {
                    added++;
                }
            }
            if (added > 0) {
                version++;
                cachedSnapshot = null;
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if a chunk has been explored.
     *
     * @param chunkIndex The chunk index.
     * @return True if explored.
     */
    public boolean isChunkExplored(long chunkIndex) {
        if (persistentComponent != null) {
            return persistentComponent.isExplored(chunkIndex);
        }

        lock.readLock().lock();
        try {
            return memoryExploredChunks.contains(chunkIndex);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets a cached, unmodifiable snapshot of all explored chunk indices.
     * This is dramatically faster than creating a new HashSet copy every call.
     * The snapshot is automatically invalidated when chunks are added/removed.
     *
     * @return Unmodifiable set of all explored chunk indices.
     */
    @Nonnull
    public Set<Long> getExploredChunks() {
        long currentVersion = version;
        Set<Long> snapshot = cachedSnapshot;
        if (snapshot != null && cachedSnapshotVersion == currentVersion) {
            return snapshot;
        }

        if (persistentComponent != null) {
            snapshot = Collections.unmodifiableSet(new HashSet<>(persistentComponent.getExploredChunks()));
        } else {
            lock.readLock().lock();
            try {
                snapshot = Collections.unmodifiableSet(new HashSet<>(memoryExploredChunks));
            } finally {
                lock.readLock().unlock();
            }
        }

        cachedSnapshot = snapshot;
        cachedSnapshotVersion = currentVersion;
        return snapshot;
    }

    /**
     * Iterates over all explored chunks without creating a copy.
     * This is the most efficient way to process all explored chunks.
     *
     * @param action The action to perform on each chunk index.
     */
    public void forEachExploredChunk(@Nonnull Consumer<Long> action) {
        if (persistentComponent != null) {
            for (Long chunk : persistentComponent.getExploredChunks()) {
                action.accept(chunk);
            }
            return;
        }

        lock.readLock().lock();
        try {
            for (Long chunk : memoryExploredChunks) {
                action.accept(chunk);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the current version counter. Incremented on every mutation.
     * Callers can use this to efficiently detect changes without computing hashCode().
     *
     * @return The current version.
     */
    public long getVersion() {
        return version;
    }

    /**
     * Gets the count of explored chunks.
     *
     * @return The number of explored chunks.
     */
    public int getExploredCount() {
        if (persistentComponent != null) {
            return persistentComponent.getExploredChunks().size();
        }

        lock.readLock().lock();
        try {
            return memoryExploredChunks.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all explored chunks data.
     */
    public void clear() {
        if (persistentComponent != null) {
            persistentComponent.getExploredChunks().clear();
            version++;
            cachedSnapshot = null;
            return;
        }

        lock.writeLock().lock();
        try {
            memoryExploredChunks.clear();
            version++;
            cachedSnapshot = null;
        } finally {
            lock.writeLock().unlock();
        }
    }
}

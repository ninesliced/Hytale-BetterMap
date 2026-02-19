package dev.ninesliced.exploration;

import dev.ninesliced.components.ExplorationComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Thread-safe tracker for the set of explored chunks.
 * Uses a persistent component if available, otherwise falls back to memory storage.
 */
public class ExploredChunksTracker {
    private final Set<Long> memoryExploredChunks;
    private final ExplorationComponent persistentComponent;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // AtomicLong ensures version++ is a single atomic read-modify-write,
    private final AtomicLong version = new AtomicLong(0);

    // A single volatile reference holds both the snapshot and its version
    private volatile CacheEntry cache = null;

    private static final class CacheEntry {
        final Set<Long> snapshot;
        final long version;

        CacheEntry(Set<Long> snapshot, long version) {
            this.snapshot = snapshot;
            this.version = version;
        }
    }

    /**
     * Creates a new tracker.
     *
     * @param component The persistent component to use (can be null).
     */
    public ExploredChunksTracker(@Nullable ExplorationComponent component) {
        this.persistentComponent = component;
        this.memoryExploredChunks = (component == null) ? new HashSet<>() : null;
    }

    /**
     * Marks a single chunk as explored.
     *
     * @param chunkIndex The chunk index to mark.
     * @return true if the chunk was newly added, false if already explored.
     */
    public boolean markChunkExplored(long chunkIndex) {
        if (persistentComponent != null) {
            boolean added = persistentComponent.addExploredChunk(chunkIndex);
            if (added) {
                version++;
                cachedSnapshot = null;
            }
            return added;
        }

        lock.writeLock().lock();
        try {
            boolean added;
            if (persistentComponent != null) {
                if (persistentComponent.isExplored(chunkIndex)) return false;
                persistentComponent.addExploredChunk(chunkIndex);
                added = true;
            } else {
                added = memoryExploredChunks.add(chunkIndex);
            }
            if (added) {
                version.incrementAndGet();
                cache = null;
                ExplorationTracker.getInstance().incrementGlobalVersion();
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marks multiple chunks as explored.
     *
     * @param chunkIndices The set of chunk indices.
     * @return the number of newly added chunks.
     */
    public int markChunksExplored(@Nonnull Set<Long> chunkIndices) {
        if (chunkIndices.isEmpty()) return 0;
        
        if (persistentComponent != null) {
            int added = 0;
            for (Long chunk : chunkIndices) {
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
            if (persistentComponent != null) {
                for (Long chunk : chunkIndices) {
                    if (!persistentComponent.isExplored(chunk)) {
                        persistentComponent.addExploredChunk(chunk);
                        added++;
                    }
                }
            } else {
                int sizeBefore = memoryExploredChunks.size();
                memoryExploredChunks.addAll(chunkIndices);
                added = memoryExploredChunks.size() - sizeBefore;
            }
            if (added > 0) {
                version.incrementAndGet();
                cache = null;
                ExplorationTracker.getInstance().incrementGlobalVersion();
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
        lock.readLock().lock();
        try {
            if (persistentComponent != null) {
                return persistentComponent.isExplored(chunkIndex);
            }
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
        // Fast path: check outside the lock first.
        CacheEntry entry = cache;
        if (entry != null && entry.version == version.get()) {
            return entry.snapshot;
        }

        // Slow path: build snapshot under read lock, then re-validate version
        // to avoid publishing a snapshot that was already stale.
        lock.readLock().lock();
        try {
            entry = cache;
            long currentVersion = version.get();
            if (entry != null && entry.version == currentVersion) {
                return entry.snapshot;
            }

            Set<Long> snapshot;
            if (persistentComponent != null) {
                snapshot = Collections.unmodifiableSet(new HashSet<>(persistentComponent.getExploredChunks()));
            } else {
                snapshot = Collections.unmodifiableSet(new HashSet<>(memoryExploredChunks));
            }
            cache = new CacheEntry(snapshot, currentVersion);
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Iterates over all explored chunks without creating a copy.
     * This is the most efficient way to process all explored chunks.
     *
     * @param action The action to perform on each chunk index.
     */
    public void forEachExploredChunk(@Nonnull Consumer<Long> action) {
        lock.readLock().lock();
        try {
            if (persistentComponent != null) {
                for (Long chunk : persistentComponent.getExploredChunks()) {
                    action.accept(chunk);
                }
            } else {
                for (Long chunk : memoryExploredChunks) {
                    action.accept(chunk);
                }
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
        return version.get();
    }

    /**
     * Gets the count of explored chunks.
     *
     * @return The number of explored chunks.
     */
    public int getExploredCount() {
        lock.readLock().lock();
        try {
            if (persistentComponent != null) {
                return persistentComponent.getExploredChunks().size();
            }
            return memoryExploredChunks.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all explored chunks data.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            if (persistentComponent != null) {
                persistentComponent.getExploredChunks().clear();
            } else {
                memoryExploredChunks.clear();
            }
            version.incrementAndGet();
            cache = null;
            ExplorationTracker.getInstance().incrementGlobalVersion();
        } finally {
            lock.writeLock().unlock();
        }
    }
}

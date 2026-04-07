package dev.ninesliced.exploration;

import dev.ninesliced.components.ExplorationComponent;
import dev.ninesliced.managers.ExplorationManager;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Thread-safe tracker for the set of explored chunks.
 * Uses a persistent component if available, otherwise falls back to in-memory storage.
 * Enforces a configurable per-player chunk cap to prevent unbounded memory growth.
 * <p>
 * Memory: backed by fastutil LongOpenHashSet (~9 bytes/entry vs ~56 bytes for boxed HashSet).
 * Snapshots are returned as a lightweight live view rather than copied — callers that need
 * isolation should explicitly copy.
 */
public class ExploredChunksTracker {
    private static final Logger LOGGER = Logger.getLogger(ExploredChunksTracker.class.getName());

    private final LongOpenHashSet memoryExploredChunks;
    private final ExplorationComponent persistentComponent;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile long version = 0;

    public ExploredChunksTracker(@Nullable ExplorationComponent component) {
        this.persistentComponent = component;
        if (component == null) {
            this.memoryExploredChunks = new LongOpenHashSet();
        } else {
            this.memoryExploredChunks = null;
        }
    }

    /**
     * Internal accessor for the underlying primitive set. Used by callers that want to
     * iterate without boxing. The returned set must NOT be mutated externally.
     */
    @Nonnull
    public LongSet getRawSet() {
        if (persistentComponent != null) {
            return persistentComponent.getExploredChunks();
        }
        return memoryExploredChunks;
    }

    private boolean isAtCapacity() {
        int cap = ExplorationManager.getInstance().getMaxStoredChunksPerPlayer();
        if (cap <= 0 || cap == Integer.MAX_VALUE) {
            return false;
        }
        return getExploredCount() >= cap;
    }

    public boolean markChunkExplored(long chunkIndex) {
        if (persistentComponent != null) {
            if (isAtCapacity() && !persistentComponent.isExplored(chunkIndex)) {
                return false;
            }
            boolean added = persistentComponent.addExploredChunk(chunkIndex);
            if (added) {
                version++;
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
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marks multiple chunks as explored. Accepts the boxed Set<Long> form for backwards
     * compatibility with old call sites.
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
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marks multiple chunks as explored using a primitive LongSet (no boxing).
     * Preferred over the boxed Set<Long> overload for hot paths.
     */
    public int markChunksExplored(@Nonnull LongSet chunkIndices) {
        if (chunkIndices.isEmpty()) return 0;

        int cap = ExplorationManager.getInstance().getMaxStoredChunksPerPlayer();
        boolean hasCap = cap > 0 && cap != Integer.MAX_VALUE;

        if (persistentComponent != null) {
            int added = 0;
            LongIterator it = chunkIndices.iterator();
            while (it.hasNext()) {
                long chunk = it.nextLong();
                if (hasCap && (getExploredCount() + added) >= cap && !persistentComponent.isExplored(chunk)) {
                    break;
                }
                if (persistentComponent.addExploredChunk(chunk)) {
                    added++;
                }
            }
            if (added > 0) {
                version++;
            }
            return added;
        }

        lock.writeLock().lock();
        try {
            int added = 0;
            LongIterator it = chunkIndices.iterator();
            while (it.hasNext()) {
                long chunk = it.nextLong();
                if (hasCap && memoryExploredChunks.size() >= cap && !memoryExploredChunks.contains(chunk)) {
                    break;
                }
                if (memoryExploredChunks.add(chunk)) {
                    added++;
                }
            }
            if (added > 0) {
                version++;
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

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
     * Returns a live, unmodifiable Set<Long> view over the underlying primitive set.
     * NO COPY is performed. Iteration boxes lazily, but membership tests do not.
     * Callers must not assume snapshot semantics — if you need isolation, copy explicitly.
     */
    @Nonnull
    public Set<Long> getExploredChunks() {
        final LongSet raw = persistentComponent != null
                ? persistentComponent.getExploredChunks()
                : memoryExploredChunks;
        return new LongSetView(raw);
    }

    /**
     * Iterates over all explored chunks without creating a copy.
     */
    public void forEachExploredChunk(@Nonnull Consumer<Long> action) {
        if (persistentComponent != null) {
            LongIterator it = persistentComponent.getExploredChunks().iterator();
            while (it.hasNext()) {
                action.accept(it.nextLong());
            }
            return;
        }

        lock.readLock().lock();
        try {
            LongIterator it = memoryExploredChunks.iterator();
            while (it.hasNext()) {
                action.accept(it.nextLong());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Iterates over all explored chunks using a primitive callback (no boxing).
     */
    public void forEachExploredChunkLong(@Nonnull java.util.function.LongConsumer action) {
        if (persistentComponent != null) {
            LongIterator it = persistentComponent.getExploredChunks().iterator();
            while (it.hasNext()) {
                action.accept(it.nextLong());
            }
            return;
        }

        lock.readLock().lock();
        try {
            LongIterator it = memoryExploredChunks.iterator();
            while (it.hasNext()) {
                action.accept(it.nextLong());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public long getVersion() {
        return version;
    }

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

    public void clear() {
        if (persistentComponent != null) {
            persistentComponent.getExploredChunks().clear();
            version++;
            return;
        }

        lock.writeLock().lock();
        try {
            memoryExploredChunks.clear();
            // Trim oversized backing array — LongOpenHashSet keeps capacity after clear().
            memoryExploredChunks.trim();
            version++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Lightweight unmodifiable Set<Long> view that delegates membership and size to a
     * primitive LongSet. Iteration boxes one entry at a time rather than copying upfront.
     */
    private static final class LongSetView extends AbstractSet<Long> {
        private final LongSet delegate;

        LongSetView(LongSet delegate) {
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof Long) {
                return delegate.contains(((Long) o).longValue());
            }
            return false;
        }

        @Override
        public Iterator<Long> iterator() {
            final LongIterator it = delegate.iterator();
            return new Iterator<Long>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Long next() {
                    return it.nextLong();
                }
            };
        }
    }
}

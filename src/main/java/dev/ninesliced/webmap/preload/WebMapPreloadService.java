package dev.ninesliced.webmap.preload;

import com.hypixel.hytale.math.util.ChunkUtil;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.webmap.auth.WebMapViewer;
import dev.ninesliced.webmap.data.WebViewFilter;
import dev.ninesliced.webmap.tiles.TileManager;
import dev.ninesliced.webmap.tiles.TileQuality;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Preloads explored chunks into tile caches and reports progress for web clients.
 */
public class WebMapPreloadService {
    private final TileManager tileManager;
    private final ExecutorService executor;
    private final ConcurrentMap<String, Set<Long>> loadedChunksByScope;
    private final Object jobLock;
    private volatile PreloadJob activeJob;

    public WebMapPreloadService(TileManager tileManager) {
        this.tileManager = tileManager;
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "BetterMap-WebMap-Preload");
            thread.setDaemon(true);
            return thread;
        });
        this.loadedChunksByScope = new ConcurrentHashMap<>();
        this.jobLock = new Object();
        this.activeJob = null;
    }

    @Nonnull
    public Map<String, Object> start(@Nonnull WebMapViewer viewer,
                                     @Nonnull String worldName,
                                     @Nonnull TileQuality quality) {
        synchronized (jobLock) {
            PreloadJob current = activeJob;
            if (current != null && current.active) {
                return current.snapshotFor(viewer, true);
            }

            PreloadJob job = new PreloadJob(viewer.uuid(), viewer.username(), viewer.admin(), worldName, quality.id());
            job.active = true;
            job.done = false;
            job.cancelled = false;
            job.message = "Collecting explored chunks...";
            activeJob = job;
            Future<?> task = executor.submit(() -> runPreload(job, viewer, worldName, quality));
            job.future = task;
            return job.snapshotFor(viewer, false);
        }
    }

    @Nonnull
    public Map<String, Object> status(@Nonnull WebMapViewer viewer) {
        PreloadJob job = activeJob;
        if (job == null) {
            return idleStatus();
        }
        return job.snapshotFor(viewer, true);
    }

    @Nonnull
    public Map<String, Object> stop(@Nonnull WebMapViewer viewer) {
        PreloadJob job = activeJob;
        if (job == null) {
            return idleStatus();
        }

        if (!job.canStop(viewer)) {
            job.message = "Preload can only be stopped by the owner or an admin.";
            return job.snapshotFor(viewer, false);
        }

        job.cancelled = true;
        job.active = false;
        job.done = true;
        job.message = "Preload cancelled.";
        Future<?> future = job.future;
        if (future != null) {
            future.cancel(true);
        }

        synchronized (jobLock) {
            if (activeJob == job) {
                activeJob = null;
            }
        }

        return job.snapshotFor(viewer, false);
    }

    public void shutdown() {
        PreloadJob job = activeJob;
        if (job != null) {
            job.cancelled = true;
            Future<?> future = job.future;
            if (future != null) {
                future.cancel(true);
            }
        }
        activeJob = null;
        loadedChunksByScope.clear();
        executor.shutdownNow();
    }

    private void runPreload(PreloadJob job, WebMapViewer viewer, String worldName, TileQuality quality) {
        try {
            job.active = true;
            job.done = false;
            job.message = "Collecting explored chunks...";

            WebViewFilter.Mode preloadMode = viewer.admin() ? WebViewFilter.Mode.GLOBAL : WebViewFilter.Mode.PLAYER;
            UUID preloadPlayerUuid = viewer.admin() ? null : viewer.uuid();

            Set<Long> explored;
            ExplorationManager explorationManager = ExplorationManager.getInstance();
            if (viewer.admin()) {
                explored = explorationManager.getAllExploredChunks(worldName);
            } else {
                explored = explorationManager.getExploredChunksForPlayer(worldName, viewer.uuid());
            }

            String scopeKey = createScopeKey(worldName, quality) + (viewer.admin() ? "" : "|" + viewer.uuid());
            Set<Long> alreadyLoaded = loadedChunksByScope.computeIfAbsent(scopeKey, ignored -> ConcurrentHashMap.newKeySet());
            Set<Long> toLoad = new HashSet<>();
            for (Long chunkIndex : explored) {
                if (chunkIndex != null && !alreadyLoaded.contains(chunkIndex)) {
                    toLoad.add(chunkIndex);
                }
            }

            job.total = toLoad.size();
            job.processed = 0;
            job.errors = 0;
            job.loadedFromMemory = Math.max(0, explored.size() - toLoad.size());

            if (job.total == 0) {
                job.active = false;
                job.done = true;
                job.message = "Map already preloaded in memory for this world/quality.";
                return;
            }

            job.message = "Preloading explored chunks...";
            for (Long chunkIndex : toLoad) {
                if (job.cancelled || Thread.currentThread().isInterrupted()) {
                    job.message = "Preload cancelled.";
                    break;
                }

                if (chunkIndex == null) {
                    job.errors++;
                    job.processed++;
                    continue;
                }

                try {
                    int chunkX = decodeChunkX(chunkIndex);
                    int chunkZ = decodeChunkZ(chunkIndex);
                    CompletableFuture<byte[]> tileFuture = tileManager.getBaseTile(worldName, quality, chunkX, chunkZ, preloadMode, preloadPlayerUuid);
                    if (!waitForTileOrCancel(tileFuture, job)) {
                        job.message = "Preload cancelled.";
                        break;
                    }
                    alreadyLoaded.add(chunkIndex);
                } catch (Exception ignored) {
                    job.errors++;
                } finally {
                    job.processed++;
                }
            }

            job.active = false;
            job.done = true;
            if (job.cancelled) {
                job.message = "Preload cancelled.";
            } else if (job.errors > 0) {
                job.message = "Preload completed with " + job.errors + " errors.";
            } else {
                job.message = "Preload complete.";
            }
        } catch (Exception e) {
            job.active = false;
            job.done = true;
            job.errors++;
            job.message = "Preload failed: " + e.getMessage();
        } finally {
            synchronized (jobLock) {
                if (activeJob == job && !job.active) {
                    activeJob = null;
                }
            }
        }
    }

    private String createScopeKey(String worldName, TileQuality quality) {
        return worldName + "|" + quality.id();
    }

    private boolean waitForTileOrCancel(CompletableFuture<byte[]> future, PreloadJob job) {
        while (!future.isDone()) {
            if (job.cancelled || Thread.currentThread().isInterrupted()) {
                return false;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        future.join();
        return true;
    }

    private int decodeChunkX(long chunkIndex) {
        return (int) (chunkIndex >> 32);
    }

    private int decodeChunkZ(long chunkIndex) {
        return (int) chunkIndex;
    }

    @Nonnull
    private Map<String, Object> idleStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("active", false);
        status.put("done", true);
        status.put("cancelled", false);
        status.put("lockedByOther", false);
        status.put("canStop", false);
        status.put("ownerUsername", "");
        status.put("ownerUuid", "");
        status.put("world", "");
        status.put("quality", "");
        status.put("processed", 0);
        status.put("total", 0);
        status.put("loadedFromMemory", 0);
        status.put("errors", 0);
        status.put("message", "No active preload job.");
        status.put("percent", 0);
        return status;
    }

    private static final class PreloadJob {
        private final UUID ownerUuid;
        private final String ownerUsername;
        private final boolean ownerAdmin;
        private final String worldName;
        private final String quality;

        private volatile boolean active;
        private volatile boolean done;
        private volatile boolean cancelled;
        private volatile int processed;
        private volatile int total;
        private volatile int loadedFromMemory;
        private volatile int errors;
        private volatile String message;
        private volatile Future<?> future;

        private PreloadJob(UUID ownerUuid, String ownerUsername, boolean ownerAdmin, String worldName, String quality) {
            this.ownerUuid = ownerUuid;
            this.ownerUsername = ownerUsername == null ? "Unknown" : ownerUsername;
            this.ownerAdmin = ownerAdmin;
            this.worldName = worldName;
            this.quality = quality;
            this.message = "";
        }

        private boolean canStop(WebMapViewer viewer) {
            if (viewer == null) {
                return false;
            }
            return viewer.admin() || ownerUuid.equals(viewer.uuid());
        }

        private Map<String, Object> snapshotFor(WebMapViewer viewer, boolean lockedByOther) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("active", active);
            status.put("done", done);
            status.put("cancelled", cancelled);
            status.put("lockedByOther", lockedByOther && !ownerUuid.equals(viewer.uuid()));
            status.put("canStop", canStop(viewer));
            status.put("ownerUsername", ownerUsername);
            status.put("ownerUuid", ownerUuid.toString());
            status.put("ownerAdmin", ownerAdmin);
            status.put("world", worldName);
            status.put("quality", quality);
            status.put("processed", processed);
            status.put("total", total);
            status.put("loadedFromMemory", loadedFromMemory);
            status.put("errors", errors);
            status.put("message", message == null ? "" : message);
            int percent = total <= 0 ? 0 : (int) Math.min(100, Math.round((processed * 100.0) / total));
            status.put("percent", percent);
            return status;
        }
    }
}

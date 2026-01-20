package dev.ninesliced.exploration;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.managers.PlayerRadarManager;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Ticker service that updates exploration status for players periodically.
 */
public class ExplorationTicker {
    private static final Logger LOGGER = Logger.getLogger(ExplorationTicker.class.getName());
    private static ExplorationTicker INSTANCE;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean isRunning = false;
    private final AtomicBoolean runningTick = new AtomicBoolean(false);
    // Per-player last position cache
    private final Map<String, LastPos> lastPosByPlayerKey = new ConcurrentHashMap<>();

    // Cursor per world to avoid processing all players in one tick
    private final Map<String, Integer> rrIndexByWorld = new ConcurrentHashMap<>();

    // Per-world radar tracking
    private final Map<String, Long> lastRadarUpdateNsByWorld = new ConcurrentHashMap<>();

    private ExplorationTicker() {
    }

    /**
     * Gets the singleton instance of the ticker.
     *
     * @return The ticker instance.
     */
    @Nonnull
    public static ExplorationTicker getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ExplorationTicker();
        }
        return INSTANCE;
    }

    /**
     * Starts the scheduled ticker task.
     */
    public void start() {
        if (isRunning) return;
        isRunning = true;

        long rateMs = BetterMapConfig.getInstance().getUpdateRateMs();

        scheduler.scheduleWithFixedDelay(() -> {
            if (!isRunning) return;
            if (!runningTick.compareAndSet(false, true)) return;
            try {
                tick();
            } finally {
                runningTick.set(false);
            }
        }, 1000L, rateMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedules a one-off task to run on the ticker thread.
     *
     * @param task The task to run.
     */
    public void scheduleUpdate(Runnable task) {
        if (!isRunning) {
            return;
        }
        scheduler.schedule(task, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the ticker and shuts down the scheduler.
     */
    public void stop() {
        isRunning = false;
        scheduler.shutdown();
    }

    private void tick() {
        if (!isRunning) {
            return;
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        // Group players once per tick to avoid empty worlds scanning
        Map<World, List<Ref<EntityStore>>> playersByWorld = new HashMap<>();

        try {
            for (PlayerRef pref : universe.getPlayers()) {
                Ref<EntityStore> ref = pref.getReference();
                if (ref == null || !ref.isValid()) {
                    continue;
                }

                World w;
                try {
                    w = ref.getStore().getExternalData().getWorld();
                } catch (Exception e) {
                    continue;
                }

                if (w == null) {
                    continue;
                }

                playersByWorld.computeIfAbsent(w, __ -> new ArrayList<>()).add(ref);
            }
        } catch (Exception e) {
            LOGGER.warning("ExplorationTicker grouping failed: " + e.getMessage());
            return;
        }

        playersByWorld.forEach((world, playerRefs) -> {
            try {
                world.execute(() -> {
                    updateWorldPlayers(world, playerRefs);
                    maybeUpdateRadar(world);
                });
            } catch (IllegalThreadStateException its) {
                LOGGER.fine("[DEBUG] Skipping world update; world not accepting tasks: " + world.getName());
            }
        });
    }

    private void updateWorldPlayers(World world, List<Ref<EntityStore>> playerRefs) {
        if (world == null || !world.isAlive()) {
            return;
        }

        if (playerRefs == null || playerRefs.isEmpty()) {
            return;
        }

        BetterMapConfig cfg = BetterMapConfig.getInstance();
        int maxPlayersPerTick = Math.max(1, cfg.getMaxPlayersPerWorldPerTick());
        double minMove = cfg.getExplorationMinMoveBlocks();
        double minMoveSq = minMove * minMove;

        String worldKey = safeWorldKey(world);
        int startIdx = rrIndexByWorld.getOrDefault(worldKey, 0);

        int total = playerRefs.size();
        if (startIdx >= total) {
            startIdx = 0;
        }

        int processed = 0;
        int i = startIdx;

        try {
            while (processed < maxPlayersPerTick && processed < total) {
                Ref<EntityStore> playerRef = playerRefs.get(i);
                processed++;

                i++;
                if (i >= total) {
                    i = 0;
                }

                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }

                // Ensure player is still in this world
                if (playerRef.getStore().getExternalData().getWorld() != world) {
                    continue;
                }

                Player player = playerRef.getStore().getComponent(playerRef, Player.getComponentType());
                if (player == null) {
                    continue;
                }

                ExplorationTracker.PlayerExplorationData data =
                        ExplorationTracker.getInstance().getPlayerData(player.getDisplayName());
                if (data == null) {
                    continue;
                }

                TransformComponent tc = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
                if (tc == null) {
                    continue;
                }

                var pos = tc.getPosition();
                String playerKey = safePlayerKey(player, playerRef);

                LastPos prev = lastPosByPlayerKey.get(playerKey);
                if (prev != null) {
                    double dx = pos.x - prev.x;
                    double dz = pos.z - prev.z;
                    double distSq = (dx * dx) + (dz * dz);
                    if (distSq < minMoveSq) {
                        continue;
                    }
                }

                WorldMapTracker tracker = player.getWorldMapTracker();
                WorldMapHook.updateExplorationState(player, tracker, pos.x, pos.z);

                lastPosByPlayerKey.put(playerKey, new LastPos(pos.x, pos.z));
            }
        } catch (IllegalThreadStateException its) {
            // If the world is not accepting tasks mid-update, bail out safely.
            return;
        } catch (Exception e) {
            LOGGER.warning("ExplorationTicker failed: " + e.getMessage());
        } finally {
            rrIndexByWorld.put(worldKey, i);
        }
    }

    private void maybeUpdateRadar(World world) {
        if (world == null || !world.isAlive()) {
            return;
        }

        BetterMapConfig cfg = BetterMapConfig.getInstance();
        long radarRateMs = Math.max(1L, cfg.getRadarUpdateRateMs());

        String worldKey = safeWorldKey(world);
        long now = System.nanoTime();
        long last = lastRadarUpdateNsByWorld.getOrDefault(worldKey, 0L);

        long minDeltaNs = TimeUnit.MILLISECONDS.toNanos(radarRateMs);
        if (now - last < minDeltaNs) {
            return;
        }

        lastRadarUpdateNsByWorld.put(worldKey, now);

        try {
            PlayerRadarManager.getInstance().updateRadarData(world);
        } catch (Exception e) {
            LOGGER.fine("Radar update failed in world " + world.getName() + ": " + e.getMessage());
        }
    }

    private static String safeWorldKey(World world) {
        try {
            String name = world.getName();
            return (name != null) ? name : ("world@" + System.identityHashCode(world));
        } catch (Exception e) {
            return "world@" + System.identityHashCode(world);
        }
    }

    private static String safePlayerKey(Player player, Ref<EntityStore> playerRef) {
        try {
            String dn = player.getDisplayName();
            if (dn != null && !dn.isBlank()) {
                return dn;
            }
        } catch (Exception ignored) {
        }
        return "playerRef@" + System.identityHashCode(playerRef);
    }

    private record LastPos(double x, double z) {
    }
}

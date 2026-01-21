package dev.ninesliced.exploration;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.managers.PlayerRadarManager;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Ticker service that updates exploration status for players periodically.
 */
public class ExplorationTicker {
    private static final Logger LOGGER = Logger.getLogger(ExplorationTicker.class.getName());
    private static ExplorationTicker INSTANCE;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean isRunning = false;

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
        if (isRunning) {
            return;
        }
        isRunning = true;
        scheduler.scheduleAtFixedRate(this::tick, 1000, 100, TimeUnit.MILLISECONDS);
        LOGGER.info("Exploration Ticker started.");
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
        if (!isRunning) return;

        Universe universe = Universe.get();
        if (universe == null) return;

        universe.getWorlds().values().forEach(world -> {
            if (world == null || !world.isAlive()) return;

            try {
                world.execute(() -> {
                    if (!world.isAlive()) return;
                    updateWorldPlayers(world);
                    PlayerRadarManager.getInstance().updateRadarData(world);
                });
            } catch (IllegalThreadStateException ignored) {
            } catch (Exception ignored) {
            }
        });
    }

    private void updateWorldPlayers(World world) {
        if (world == null || !world.isAlive()) return;

        try {
            for (PlayerRef ref : world.getPlayerRefs()) {
                if (ref == null) continue;

                Ref<EntityStore> playerRef = ref.getReference();
                if (playerRef == null || !playerRef.isValid()) continue;

                try {
                    Player player = playerRef.getStore().getComponent(playerRef, Player.getComponentType());
                    if (player == null) continue;

                    ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(player.getDisplayName());
                    if (data == null) continue;

                    World playerWorld = player.getWorld();
                    if (playerWorld == null || !playerWorld.getName().equals(world.getName())) continue;

                    WorldMapTracker tracker = player.getWorldMapTracker();
                    if (tracker == null) continue;

                    TransformComponent tc = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
                    if (tc != null) {
                        var pos = tc.getPosition();
                        WorldMapHook.updateExplorationState(player, tracker, pos.x, pos.z);
                    }
                } catch (IllegalThreadStateException its) {
                    return;
                } catch (Exception e) {
                    LOGGER.fine("Error updating player in world " + world.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception ignored) {
        }
    }
}

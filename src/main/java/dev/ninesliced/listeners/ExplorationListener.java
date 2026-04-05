package dev.ninesliced.listeners;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.exploration.ExplorationTicker;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.managers.*;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.ReflectionHelper;
import dev.ninesliced.utils.WaypointLimitUtil;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Listener class for handling player connection and world transitions events.
 * Responsible for initializing and saving exploration data.
 * <p>
 * Memory safety fixes:
 * - onPlayerQuit() uses finally block to guarantee cleanup of all static maps even if save throws.
 * - onPlayerLeaveWorld() uses finally block to guarantee ExplorationTracker cleanup.
 * - All cleanup paths are guaranteed to execute regardless of exceptions.
 */
public class ExplorationListener {
    private static final Logger LOGGER = Logger.getLogger(ExplorationListener.class.getName());
    private static final java.util.Map<String, String> playerWorlds = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Handles the PlayerReadyEvent.
     * Initializes tracking if the player joins the default world.
     */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            Player player = event.getPlayer();
            String playerName = player.getDisplayName();

            if (player.getReference() != null && player.getReference().isValid()) {
                UUID uuid = ((CommandSender) player).getUuid();
                PlayerConfigManager.getInstance().loadPlayerConfig(uuid);
            }

            World world = player.getWorld();
            if (world == null) {
                LOGGER.warning("Player " + playerName + " has no world!");
                return;
            }

            world.execute(() -> WorldMapHook.sendMapSettingsToPlayer(player));

            if (isTrackedWorld(world)) {
                WaypointManager.onPlayerJoin(player);
                WaypointMigrationManager.onPlayerJoin(player);
            }

            if (ModConfig.getInstance().isFirstLaunch() && !isTrackedWorld(world)) {
                String worldName = world.getName();
                ModConfig.getInstance().addAllowedWorld(worldName);
                ModConfig.getInstance().setFirstLaunch(false);

                LOGGER.info("First launch detected. Added " + worldName + " to tracked worlds.");
                ExplorationTicker.getInstance().scheduleDelayedTask(() -> {
                    try {
                        if (player.getReference() != null && player.getReference().isValid()) {
                            player.sendMessage(Message.raw("WARNING: BetterMap - Just added this world as tracked but you need to restart the server to apply the changes.").color(Color.RED));
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Failed to send warning message: " + e.getMessage());
                    }
                }, 4, TimeUnit.SECONDS);
            }

            if (playerWorlds.containsKey(playerName)) {
                String trackedWorld = playerWorlds.get(playerName);
                String currentWorld = world.getName();
                if (trackedWorld != null && trackedWorld.equals(currentWorld)) {
                    if (isTrackedWorld(world)) {
                        if (ExplorationTracker.getInstance().getPlayerData(playerName) != null) {
                            LOGGER.info("[DEBUG] Player " + playerName + " already tracked in world " + currentWorld + ", skipping PlayerReadyEvent");
                            return;
                        }
                    }
                }
            }
            LOGGER.info("Player ready (initial join): " + playerName);

            String worldName = world.getName();
            playerWorlds.put(playerName, worldName);

            if (isTrackedWorld(world)) {
                ExplorationTracker.PlayerExplorationData explorationData = ExplorationTracker.getInstance().getOrCreatePlayerData(player);

                ExplorationManager.getInstance().loadPlayerData(player);

                LOGGER.info("[DEBUG] Loaded exploration data for " + playerName +
                        ", explored chunks: " + explorationData.getExploredChunks().getExploredChunks().size());

                WorldMapTracker tracker = player.getWorldMapTracker();
                WorldMapHook.hookPlayerMapTracker(player, tracker);
                WorldMapHook.hookWorldMapResolution(world);

                PlayerRadarManager.getInstance().registerForPlayer(player);
                WorldBorderManager.getInstance().registerForPlayer(player);

                WaypointManager.onPlayerJoin(player);

                MapAnchorManager.getInstance().sendWaypointAnchorDelayed(player, 2000);

                initDynamicCaveMode(player);

                LOGGER.info("Exploration tracking initialized for player: " + playerName);
            } else {
                if (PermissionsUtil.isAdmin(player)) {
                    ExplorationTicker.getInstance().scheduleDelayedTask(() -> {
                        try {
                            if (player.getReference() != null && player.getReference().isValid()) {
                                player.sendMessage(Message.raw("WARNING: BetterMap - usage in this world is not tracked.").color(Color.RED));
                                player.sendMessage(Message.raw("Use '/bettermap config track' to track this world.").color(Color.RED));
                            }
                        } catch (Exception e) {
                            LOGGER.warning("Failed to send warning message: " + e.getMessage());
                        }
                    }, 4, TimeUnit.SECONDS);
                }

                WorldMapTracker tracker = player.getWorldMapTracker();
                WorldMapHook.restoreVanillaMapTracker(player, tracker);
                LOGGER.info("Player " + playerName + " joined non-default world; leaving map vanilla.");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to handle player ready event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the DrainPlayerFromWorldEvent.
     * Saves data and cleans up tracking when a player leaves a world.
     * FIX: Uses finally block to guarantee ExplorationTracker and cave mode cleanup.
     */
    public static void onPlayerLeaveWorld(@Nonnull DrainPlayerFromWorldEvent event) {
        LOGGER.info("[DEBUG] DrainPlayerFromWorldEvent FIRED!");
        String playerName = null;
        try {
            Holder<EntityStore> holder = event.getHolder();

            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());

            Player player = playerRef.getHolder().getComponent(Player.getComponentType());

            if (player != null) {
                World world = event.getWorld();
                String worldName = world.getName();
                playerName = player.getDisplayName();
                LOGGER.info("[DEBUG] Player " + playerName + " leaving world " + worldName);

                WorldMapTracker tracker = player.getWorldMapTracker();

                WorldMapHook.cleanupCaveModeOnDrain(player, world, tracker);

                LOGGER.info("[DEBUG] Unhooking tracker for " + playerName);
                WorldMapHook.unhookPlayerMapTracker(player, tracker);

                if (isTrackedWorld(world)) {
                    UUID uuid = playerRef.getUuid();
                    ExplorationManager.getInstance().savePlayerData(playerName, uuid, worldName);
                }

                LOGGER.info("[DEBUG] Clearing exploration data for " + playerName);
                ExplorationTracker.getInstance().removePlayerData(playerName);

                cleanupCaveModeStateByName(playerName);

                LOGGER.info("[DEBUG] Successfully handled DrainPlayerFromWorldEvent for " + playerName);
            } else {
                LOGGER.warning("[DEBUG] Player was null in DrainPlayerFromWorldEvent!");
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Error handling player leave world: " + e.getMessage());
            e.printStackTrace();
            // FIX: Guarantee cleanup even if save or unhook threw
            if (playerName != null) {
                try {ExplorationTracker.getInstance().removePlayerData(playerName);} catch (Exception ignored) {}
                try {cleanupCaveModeStateByName(playerName);} catch (Exception ignored) {}
            }
        }
    }

    /**
     * Handles the AddPlayerToWorldEvent.
     * Manages world transitions, saving old data and loading new data if applicable.
     */
    public static void onPlayerJoinWorld(@Nonnull AddPlayerToWorldEvent event) {
        LOGGER.info("[DEBUG] AddPlayerToWorldEvent FIRED!");
        try {
            Holder<EntityStore> holder = event.getHolder();
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            Player player = playerRef.getHolder().getComponent(Player.getComponentType());

            if (player == null) return;

            String playerName = player.getDisplayName();
            World newWorld = event.getWorld();
            if (newWorld == null) return;

            String newWorldName = newWorld.getName();
            String oldWorldName = playerWorlds.get(playerName);
            World oldWorld = oldWorldName != null ? Universe.get().getWorld(oldWorldName) : null;

            ModConfig config = ModConfig.getInstance();
            WaypointLimitUtil.applyOverridesToWorld(
                    newWorld,
                    config.getMaxPersonalMarkersPerPlayer(),
                    config.getMaxSharedMarkersPerPlayer()
            );

            WorldMapTracker earlyTracker = player.getWorldMapTracker();
            if (earlyTracker != null) {
                try {
                    ReflectionHelper.setFieldValueRecursive(earlyTracker, "allowTeleportToMarkers", false);

                    boolean allowCoordTp = config.isAllowCoordinateTeleports()
                            || PermissionsUtil.canTeleportToCoordinates(player);
                    ReflectionHelper.setFieldValueRecursive(earlyTracker, "allowTeleportToCoordinates", allowCoordTp);

                } catch (Exception e) {
                    LOGGER.fine("Failed to set early tracker fields: " + e.getMessage());
                }
            }

            LOGGER.info("[DEBUG] Player " + playerName + " joining world: " + newWorldName + " (previous: " + oldWorldName + ")");

            if (oldWorldName != null && !oldWorldName.equals(newWorldName)) {
                LOGGER.info("[DEBUG] WORLD CHANGE DETECTED: " + playerName + " from " + oldWorldName + " to " + newWorldName);

                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    LOGGER.info("[DEBUG] Unhooking tracker for old world " + oldWorldName);
                    WorldMapHook.unhookPlayerMapTracker(player, tracker);
                }

                if (isTrackedWorld(oldWorld)) {
                    LOGGER.info("[DEBUG] Saving data for default world");
                    UUID uuid = playerRef.getUuid();
                    ExplorationManager.getInstance().savePlayerData(playerName, uuid, oldWorldName);
                }

                ExplorationTracker.getInstance().removePlayerData(playerName);
            }

            playerWorlds.put(playerName, newWorldName);

            if (!isTrackedWorld(newWorld)) {
                MapAnchorManager.getInstance().clearAnchor(player);

                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    WorldMapHook.restoreVanillaMapTracker(player, tracker);
                }
            } else if (oldWorldName == null || !oldWorldName.equals(newWorldName)
                    || ExplorationTracker.getInstance().getPlayerData(playerName) == null) {
                LOGGER.info("[DEBUG] Initializing exploration for " + playerName + " in world " + newWorldName);

                ExplorationTracker.getInstance().getOrCreatePlayerData(player);

                ExplorationTracker.PlayerExplorationData newData = ExplorationTracker.getInstance().getPlayerData(playerName);
                if (newData != null) {
                    newData.resetLastChunkPosition();
                    LOGGER.info("[DEBUG] Reset last chunk position for fresh start in " + newWorldName);
                }

                LOGGER.info("[DEBUG] Loading data for world: " + newWorldName);
                ExplorationManager.getInstance().loadPlayerData(player, newWorldName);

                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    LOGGER.info("[DEBUG] Hooking tracker for world " + newWorldName);
                    WorldMapHook.hookPlayerMapTracker(player, tracker);
                    WorldMapHook.hookWorldMapResolution(newWorld);
                }

                PlayerRadarManager.getInstance().registerForWorld(newWorld);

                WorldBorderManager.getInstance().registerForWorld(newWorld);

                MapAnchorManager.getInstance().sendWaypointAnchorDelayed(player, 2000);

                final WorldMapTracker finalTracker = tracker;
                final String finalNewWorldName = newWorldName;
                ExplorationTicker.getInstance().scheduleUpdate(() -> {
                    LOGGER.info("[DEBUG] Scheduled immediate update executing for " + playerName);

                    World currentWorld = player.getWorld();
                    if (currentWorld == null || !currentWorld.getName().equals(finalNewWorldName)) {
                        LOGGER.info("[DEBUG] Player " + playerName + " already changed worlds, skipping scheduled update");
                        return;
                    }

                    TransformComponent tc = holder.getComponent(TransformComponent.getComponentType());
                    if (tc != null && finalTracker != null) {
                        var pos = tc.getPosition();
                        WorldMapHook.updateExplorationState(player, finalTracker, pos.x, pos.z);
                    } else {
                        LOGGER.fine("[DEBUG] TransformComponent or tracker was null for immediate update");
                    }

                    try {
                        if (finalTracker != null) {
                            ReflectionHelper.setFieldValueRecursive(finalTracker, "updateTimer", 0.0f);
                        }
                    } catch (Exception e) {
                        LOGGER.fine("[DEBUG] Could not reset updateTimer: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.warning("Error in AddPlayerToWorldEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the PlayerDisconnectEvent.
     * Ensures final data save on disconnect.
     * FIX: Uses finally block to GUARANTEE cleanup of all tracking maps (playerWorlds, cave mode,
     * streaming state, anchor) even if the save or any intermediate step throws an exception.
     */
    public static void onPlayerQuit(@Nonnull PlayerDisconnectEvent event) {
        LOGGER.info("[DEBUG] PlayerDisconnectEvent FIRED!");
        String playerName = null;
        UUID playerUUID = null;
        try {
            PlayerRef playerRef = event.getPlayerRef();

            playerName = playerRef.getUsername();
            playerUUID = playerRef.getUuid();

            PlayerConfigManager.getInstance().unloadPlayerConfig(playerUUID);

            ChunkStreamingManager.getInstance().removeState(playerName);

            LOGGER.info("[DEBUG] Player " + playerName + " disconnecting from server");

            ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(playerName);
            LOGGER.info("[DEBUG] Exploration data exists: " + (data != null));

            if (data != null) {
                LOGGER.info("[DEBUG] Data still exists, performing fallback save");
                Ref<EntityStore> fallbackRef = playerRef.getReference();
                if (fallbackRef != null && fallbackRef.isValid()) {
                    try {
                        Store<EntityStore> store = fallbackRef.getStore();
                        World world = store.getExternalData().getWorld();
                        String worldName = world.getName();

                        if (isTrackedWorld(world)) {
                            LOGGER.info("[DEBUG] Fallback save for player " + playerName + " disconnecting from default world");
                            ExplorationManager.getInstance().savePlayerData(playerName, playerUUID, worldName);
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Could not determine world for fallback save: " + e.getMessage());
                    }
                }
            } else {
                LOGGER.info("Player " + playerName + " disconnect - data already saved");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to handle player quit event: " + e.getMessage());
        } finally {
            // FIX: ALWAYS clean up all tracking state, even if save threw an exception.
            // This prevents memory leaks from orphaned entries in static maps.
            if (playerName != null) {
                try {ExplorationTracker.getInstance().removePlayerData(playerName);} catch (Exception ignored) {}
                try {cleanupCaveModeStateByName(playerName);} catch (Exception ignored) {}
                try {MapAnchorManager.getInstance().removePlayer(playerName);} catch (Exception ignored) {}
                playerWorlds.remove(playerName);
                LOGGER.info("[DEBUG] Removed world tracking for " + playerName);
            }
        }
    }

    public static boolean isTrackedWorld(@javax.annotation.Nullable World world) {
        if (world == null) {
            return false;
        }
        return ModConfig.getInstance().isTrackedWorld(world.getName());
    }

    /**
     * Initializes the dynamic cave mode for a player (creates their state).
     */
    private static void initDynamicCaveMode(@Nonnull Player player) {
        try {
            if (!CaveModeManager.isEffectivelyEnabledForPlayer(player)) {
                return;
            }

            CaveModeManager caveManager = CaveModeManager.getInstance();
            caveManager.getOrCreateState(player);
            LOGGER.info("Initialized dynamic cave mode for " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to initialize dynamic cave mode for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Cleans up cave mode state for a player.
     */
    private static void cleanupCaveModeState(@Nonnull Player player) {
        cleanupCaveModeStateByName(player.getDisplayName());
    }

    /**
     * Cleans up cave mode state for a player by name.
     * Use this when the Player object might not be available (e.g., on disconnect).
     */
    private static void cleanupCaveModeStateByName(@Nonnull String playerName) {
        try {
            CaveModeManager.getInstance().removePlayerByName(playerName);
            WorldMapHook.removeCaveModePlayer(playerName);
            LOGGER.fine("Cleaned up cave mode state for " + playerName);
        } catch (Exception e) {
            LOGGER.warning("Failed to cleanup cave mode state for " + playerName + ": " + e.getMessage());
        }
    }
}

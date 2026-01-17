package dev.ninesliced.listeners;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.exploration.*;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PlayerRadarManager;
import dev.ninesliced.utils.ReflectionHelper;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Listener class for handling player connection and world transitions events.
 * Responsible for initializing and saving exploration data.
 */
public class ExplorationEventListener {
    private static final Logger LOGGER = Logger.getLogger(ExplorationEventListener.class.getName());
    private static final java.util.Map<String, String> playerWorlds = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Handles the PlayerReadyEvent.
     * Initializes tracking if the player joins the default world.
     *
     * @param event The event.
     */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            Player player = event.getPlayer();
            String playerName = player.getDisplayName();

            if (player.getReference() != null && player.getReference().isValid()) {
                UUID uuid = ((CommandSender) player).getUuid();
                PlayerConfigManager.getInstance().loadPlayerConfig(uuid);
            }

            WorldMapHook.sendMapSettingsToPlayer(player);

            World world = player.getWorld();
            if (playerWorlds.containsKey(playerName)) {
                String trackedWorld = playerWorlds.get(playerName);
                String currentWorld = world != null ? world.getName() : null;
                if (trackedWorld != null && trackedWorld.equals(currentWorld)) {
                    LOGGER.info("[DEBUG] Player " + playerName + " already tracked in world " + currentWorld + ", skipping PlayerReadyEvent");
                    return;
                }
            }
            LOGGER.info("Player ready (initial join): " + playerName);

            if (world == null)
                return;

            String worldName = world.getName();
            playerWorlds.put(playerName, worldName);

            if (isTrackedWorld(world)) {
                ExplorationTracker.getInstance().getOrCreatePlayerData(player);
                ExplorationManager.getInstance().loadPlayerData(player);

                WorldMapTracker tracker = player.getWorldMapTracker();
                WorldMapHook.hookPlayerMapTracker(player, tracker);
                WorldMapHook.hookWorldMapResolution(world);

                PlayerRadarManager.getInstance().registerForPlayer(player);

                LOGGER.info("Exploration tracking initialized for player: " + playerName);
            } else {
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
     *
     * @param event The event.
     */
    public static void onPlayerLeaveWorld(@Nonnull DrainPlayerFromWorldEvent event) {
        LOGGER.info("[DEBUG] DrainPlayerFromWorldEvent FIRED!");
        try {
            Holder<EntityStore> holder = event.getHolder();

            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());

            Player player = playerRef.getComponent(Player.getComponentType());

            if (player != null) {
                World world = event.getWorld();
                String worldName = world.getName();
                LOGGER.info("[DEBUG] Player " + player.getDisplayName() + " leaving world " + worldName + " (world shutting down)");

                WorldMapTracker tracker = player.getWorldMapTracker();
                LOGGER.info("[DEBUG] Unhooking tracker for " + player.getDisplayName());
                WorldMapHook.unhookPlayerMapTracker(player, tracker);

                if (isTrackedWorld(world)) {
                    UUID uuid = playerRef.getUuid();
                    ExplorationManager.getInstance().savePlayerData(player.getDisplayName(), uuid, worldName);
                }

                LOGGER.info("[DEBUG] Clearing exploration data for " + player.getDisplayName());
                ExplorationTracker.getInstance().removePlayerData(player.getDisplayName());

                LOGGER.info("[DEBUG] Successfully handled DrainPlayerFromWorldEvent for " + player.getDisplayName());
            } else {
                LOGGER.warning("[DEBUG] Player was null in DrainPlayerFromWorldEvent!");
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Error handling player leave world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the AddPlayerToWorldEvent.
     * Manages world transitions, saving old data and loading new data if applicable.
     *
     * @param event The event.
     */
    public static void onPlayerJoinWorld(@Nonnull AddPlayerToWorldEvent event) {
        LOGGER.info("[DEBUG] AddPlayerToWorldEvent FIRED!");
        try {
            Holder<EntityStore> holder = event.getHolder();
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            Player player = playerRef.getComponent(Player.getComponentType());

            if (player != null) {
                String playerName = player.getDisplayName();
                World newWorld = event.getWorld();
                String newWorldName = newWorld.getName();
                String oldWorldName = playerWorlds.get(playerName);
                World oldWorld = oldWorldName != null ? Universe.get().getWorld(oldWorldName) : null;

                LOGGER.info("[DEBUG] Player " + playerName + " joining world: " + newWorldName + " (previous: " + oldWorldName + ")");

                if (oldWorldName != null && !oldWorldName.equals(newWorldName)) {
                    LOGGER.info("[DEBUG] WORLD CHANGE DETECTED: " + playerName + " from " + oldWorldName + " to " + newWorldName);

                    WorldMapTracker tracker = player.getWorldMapTracker();
                    LOGGER.info("[DEBUG] Unhooking tracker for old world " + oldWorldName);
                    WorldMapHook.unhookPlayerMapTracker(player, tracker);

                    if (isTrackedWorld(oldWorld)) {
                        LOGGER.info("[DEBUG] Saving data for default world");
                        UUID uuid = playerRef.getUuid();
                        ExplorationManager.getInstance().savePlayerData(playerName, uuid, oldWorldName);
                    }

                    ExplorationTracker.getInstance().removePlayerData(playerName);
                }

                if (!isTrackedWorld(newWorld)) {
                    WorldMapTracker tracker = player.getWorldMapTracker();
                    WorldMapHook.restoreVanillaMapTracker(player, tracker);
                } else if (oldWorldName == null || !oldWorldName.equals(newWorldName)) {
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
                    LOGGER.info("[DEBUG] Hooking tracker for world " + newWorldName);
                    WorldMapHook.hookPlayerMapTracker(player, tracker);
                    WorldMapHook.hookWorldMapResolution(newWorld);

                    PlayerRadarManager.getInstance().registerForWorld(newWorld);

                    ExplorationTicker.getInstance().scheduleUpdate(() -> {
                        LOGGER.info("[DEBUG] Scheduled immediate update executing for " + playerName);
                        TransformComponent tc = holder.getComponent(TransformComponent.getComponentType());
                        if (tc != null) {
                            var pos = tc.getPosition();
                            WorldMapHook.updateExplorationState(player, tracker, pos.x, pos.z);
                        } else {
                            LOGGER.warning("[DEBUG] TransformComponent expected but null for immediate update");
                        }

                        try {
                            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);
                        } catch (Exception e) {
                            LOGGER.warning("[DEBUG] Could not reset updateTimer: " + e.getMessage());
                        }
                    });
                }

                playerWorlds.put(playerName, newWorldName);
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Error in AddPlayerToWorldEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the PlayerDisconnectEvent.
     * Ensures final data save on disconnect.
     *
     * @param event The event.
     */
    public static void onPlayerQuit(@Nonnull PlayerDisconnectEvent event) {
        LOGGER.info("[DEBUG] PlayerDisconnectEvent FIRED!");
        try {
            PlayerRef playerRef = event.getPlayerRef();

            String playerName = playerRef.getUsername();
            UUID playerUUID = playerRef.getUuid();

            PlayerConfigManager.getInstance().unloadPlayerConfig(playerUUID);

            LOGGER.info("[DEBUG] Player " + playerName + " disconnecting from server");

            ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(playerName);
            LOGGER.info("[DEBUG] Exploration data exists: " + (data != null));

            if (data != null) {
                LOGGER.info("[DEBUG] Data still exists, performing fallback save");
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    try {
                        Store<EntityStore> store = ref.getStore();
                        World world = store.getExternalData().getWorld();
                        String worldName = world.getName();

                        if (isTrackedWorld(world)) {
                            LOGGER.info("[DEBUG] Fallback save for player " + playerName + " disconnecting from default world");
                            ExplorationManager.getInstance().savePlayerData(playerName, playerUUID, worldName);
                        }
                        ExplorationTracker.getInstance().removePlayerData(playerName);
                    } catch (Exception e) {
                        LOGGER.warning("Could not determine world for fallback save: " + e.getMessage());
                        ExplorationTracker.getInstance().removePlayerData(playerName);
                    }
                } else {
                    ExplorationTracker.getInstance().removePlayerData(playerName);
                }
            } else {
                LOGGER.info("Player " + playerName + " disconnect - data already saved");
            }

            playerWorlds.remove(playerName);
            LOGGER.info("[DEBUG] Removed world tracking for " + playerName);
        } catch (Exception e) {
            LOGGER.warning("Failed to handle player quit event: " + e.getMessage());
        }
    }

    public static boolean isTrackedWorld(@javax.annotation.Nullable World world) {
        if (world == null) {
            return false;
        }
        return BetterMapConfig.getInstance().isTrackedWorld(world.getName());
    }
}

package dev.ninesliced.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.UpdateAnchorUI;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.anchoraction.AnchorActionModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.exploration.ExplorationTicker;
import dev.ninesliced.ui.ConfigMenuPage;
import dev.ninesliced.ui.WaypointEditPage;
import dev.ninesliced.ui.WaypointMenuPage;
import dev.ninesliced.utils.PermissionsUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages the BetterMap anchor UI injected into the world map's "MapServerContent" anchor point.
 * <p>
 * Uses the Hytale Anchor UI system ({@code UpdateAnchorUI} packet) to inject a full-featured
 * waypoint panel directly into the Map page. Supports all waypoint operations: create, edit,
 * delete, and teleport — mirroring the full WaypointMenuPage functionality inline on the map.
 */
public class MapAnchorManager {

    private static final Logger LOGGER = Logger.getLogger(MapAnchorManager.class.getName());

    public static final String MAP_ANCHOR_ID = "MapServerContent";

    private static final String ACTION_OPEN_MANAGER = "bettermap_openManager";
    private static final String ACTION_OPEN_CONFIG = "bettermap_openConfig";
    private static final String ACTION_OPEN_ADMIN_CONFIG = "bettermap_openAdminConfig";
    private static final String ACTION_CREATE = "bettermap_create";
    private static final String ACTION_EDIT = "bettermap_edit";
    private static final String ACTION_DELETE = "bettermap_delete";
    private static final String ACTION_TELEPORT = "bettermap_teleport";
    private static final String ACTION_TOGGLE_EXPAND = "bettermap_toggleExpand";

    private static final MapAnchorManager INSTANCE = new MapAnchorManager();

    private final Map<String, PlayerRef> activePlayers = new ConcurrentHashMap<>();

    private final Map<String, Integer> lastMarkerCounts = new ConcurrentHashMap<>();

    private final Map<String, Boolean> expandedPlayers = new ConcurrentHashMap<>();

    private ScheduledExecutorService pollScheduler;
    private ScheduledFuture<?> pollFuture;

    private static final int POLL_INTERVAL_SECONDS = 3;

    private MapAnchorManager() {
    }

    public static MapAnchorManager getInstance() {
        return INSTANCE;
    }

    /**
     * Registers anchor action handlers with the global {@link AnchorActionModule}.
     * Must be called once during plugin setup.
     */
    public void initialize() {
        AnchorActionModule anchorModule = AnchorActionModule.get();
        if (anchorModule == null) {
            LOGGER.warning("AnchorActionModule not available – MapAnchorManager will not register handlers.");
            return;
        }

        anchorModule.register(ACTION_OPEN_MANAGER, (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, com.google.gson.JsonObject data) -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(playerRef));
        });

        anchorModule.register(ACTION_OPEN_CONFIG, (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, com.google.gson.JsonObject data) -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(playerRef));
        });

        anchorModule.register(ACTION_OPEN_ADMIN_CONFIG, (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, com.google.gson.JsonObject data) -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            if (PermissionsUtil.isAdmin(player)) {
                player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(playerRef, true));
            } else {
                player.getPageManager().openCustomPage(ref, store, new ConfigMenuPage(playerRef));
            }
        });

        anchorModule.register(ACTION_CREATE, (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, com.google.gson.JsonObject data) -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store, new WaypointEditPage(playerRef, null));
        });

        anchorModule.register(ACTION_TOGGLE_EXPAND, (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, com.google.gson.JsonObject data) -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(playerRef));
        });

        anchorModule.register(ACTION_EDIT, (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, com.google.gson.JsonObject data) -> {
            if (!data.has("waypointId")) return;
            String waypointId = data.get("waypointId").getAsString();

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            if (WaypointManager.isSharedId(waypointId)) {
                UserMapMarker marker = WaypointManager.getMarker(player, waypointId);
                if (marker == null || !PermissionsUtil.canEditSharedWaypoint(player, marker)) {
                    player.sendMessage(Message.raw("You do not have permission to edit shared waypoints."));
                    return;
                }
            }
            player.getPageManager().openCustomPage(ref, store, new WaypointEditPage(playerRef, waypointId));
        });

        anchorModule.register(ACTION_DELETE, (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, com.google.gson.JsonObject data) -> {
            if (!data.has("waypointId")) return;
            String waypointId = data.get("waypointId").getAsString();

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            if (WaypointManager.isSharedId(waypointId)) {
                UserMapMarker marker = WaypointManager.getMarker(player, waypointId);
                if (marker == null || !PermissionsUtil.canEditSharedWaypoint(player, marker)) {
                    player.sendMessage(Message.raw("You do not have permission to delete shared waypoints."));
                    return;
                }
            }
            boolean removed = WaypointManager.removeMarker(player, waypointId);
            if (removed) {
                LOGGER.fine("Deleted waypoint " + waypointId + " for " + player.getDisplayName());
            }
        });

        anchorModule.register(ACTION_TELEPORT, (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, com.google.gson.JsonObject data) -> {
            if (!data.has("waypointId")) return;
            String waypointId = data.get("waypointId").getAsString();

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            if (!PermissionsUtil.canTeleport(player) || !ModConfig.getInstance().isAllowWaypointTeleports()) {
                return;
            }

            UserMapMarker marker = WaypointManager.getMarker(player, waypointId);
            if (marker == null) return;

            World world = store.getExternalData().getWorld();
            if (world == null) return;

            float markerX = marker.getX();
            float markerZ = marker.getZ();
            Double storedY = WaypointManager.getMarkerY(world, player, marker.getId());

            double destinationY = storedY != null ? storedY : 64.0;
            try {
                if (storedY == null) {
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(markerX, markerZ);
                    WorldChunk chunk = world.getChunk(chunkIndex);
                    if (chunk != null) {
                        int blockX = MathUtil.floor(markerX);
                        int blockZ = MathUtil.floor(markerZ);
                        int localX = blockX & 31;
                        int localZ = blockZ & 31;
                        short surfaceHeight = chunk.getHeight(localX, localZ);
                        destinationY = surfaceHeight + 1.0;
                    }
                }
            } catch (Exception e) {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    destinationY = transform.getPosition().y;
                }
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            Vector3f currentRotation = transform != null ? transform.getRotation() : Vector3f.ZERO;
            Vector3d destination = new Vector3d(markerX, destinationY, markerZ);
            Teleport teleport = new Teleport(destination, currentRotation);

            world.execute(() -> store.addComponent(ref, Teleport.getComponentType(), teleport));
        });

        LOGGER.info("MapAnchorManager: Anchor action handlers registered.");

        startPolling();
    }

    /**
     * Cleans up by unregistering all anchor action handlers.
     */
    public void cleanup() {
        stopPolling();

        AnchorActionModule anchorModule = AnchorActionModule.get();
        if (anchorModule != null) {
            anchorModule.unregister(ACTION_OPEN_MANAGER);
            anchorModule.unregister(ACTION_OPEN_CONFIG);
            anchorModule.unregister(ACTION_OPEN_ADMIN_CONFIG);
            anchorModule.unregister(ACTION_CREATE);
            anchorModule.unregister(ACTION_EDIT);
            anchorModule.unregister(ACTION_DELETE);
            anchorModule.unregister(ACTION_TELEPORT);
            anchorModule.unregister(ACTION_TOGGLE_EXPAND);
        }
        activePlayers.clear();
        lastMarkerCounts.clear();
        expandedPlayers.clear();
        LOGGER.info("MapAnchorManager: Cleaned up.");
    }

    /**
     * Sends (or refreshes) the waypoint anchor panel for a given player.
     * This injects UI into the {@code MapServerContent} anchor on the Map page.
     */
    public void sendWaypointAnchor(@Nonnull Player player) {
        PlayerRef playerRef = resolvePlayerRef(player);
        if (playerRef == null) {
            LOGGER.warning("MapAnchorManager: Could not resolve PlayerRef for " + player.getDisplayName() + ", skipping anchor UI send.");
            return;
        }

        try {
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            String playerName = player.getDisplayName();
            boolean isAdmin = PermissionsUtil.isAdmin(player);

            List<UserMapMarker> markers = WaypointManager.getUserMarkers(player);
            int markerCount = markers != null ? markers.size() : 0;

            commands.append(isAdmin ? "Hud/BetterMap/MapWaypointBarAdmin.ui" : "Hud/BetterMap/MapWaypointBar.ui");
            commands.set("#WaypointCount.Text", "Waypoints: " + markerCount);
            commands.set("#ConfigButton.Visible", true);
            commands.set("#AdminConfigSpacer.Visible", isAdmin);
            commands.set("#AdminConfigButton.Visible", isAdmin);

            lastMarkerCounts.put(player.getDisplayName(), markerCount);

            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ManageWaypointsButton",
                EventData.of("action", ACTION_OPEN_MANAGER),
                false
            );

            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AddWaypointButton",
                EventData.of("action", ACTION_CREATE),
                false
            );

            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ConfigButton",
                EventData.of("action", ACTION_OPEN_CONFIG),
                false
            );

            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AdminConfigButton",
                EventData.of("action", ACTION_OPEN_ADMIN_CONFIG),
                false
            );

            playerRef.getPacketHandler().writeNoCache(
                new UpdateAnchorUI(MAP_ANCHOR_ID, true, commands.getCommands(), events.getEvents())
            );

            activePlayers.put(player.getDisplayName(), playerRef);
            LOGGER.fine("Sent waypoint anchor UI to " + player.getDisplayName());

        } catch (Exception e) {
            LOGGER.warning("Failed to send waypoint anchor UI to " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Sends the anchor UI with a delay, giving time for waypoint data to load first.
     * Use this on player join instead of {@link #sendWaypointAnchor(Player)}.
     */
    public void sendWaypointAnchorDelayed(@Nonnull Player player, long delayMs) {
        String playerName = player.getDisplayName();

        ExplorationTicker.getInstance().scheduleDelayedTask(() -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    LOGGER.fine("MapAnchorManager: Delayed anchor send skipped; reference invalid for " + playerName);
                    return;
                }

                Store<EntityStore> store = ref.getStore();
                if (store == null || store.getExternalData() == null) return;

                World world = store.getExternalData().getWorld();
                if (world == null || !world.isAlive()) return;

                world.execute(() -> {
                    try {
                        Ref<EntityStore> worldRef = player.getReference();
                        if (worldRef == null || !worldRef.isValid()) {
                            return;
                        }

                        Store<EntityStore> worldStore = worldRef.getStore();
                        if (worldStore == null) {
                            return;
                        }

                        Player worldPlayer = worldStore.getComponent(worldRef, Player.getComponentType());
                        if (worldPlayer == null || worldPlayer.getReference() == null || !worldPlayer.getReference().isValid()) {
                            return;
                        }
                        sendWaypointAnchor(worldPlayer);
                    } catch (Exception e) {
                        LOGGER.warning("Failed to send delayed anchor UI to " + playerName + " on world thread: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                LOGGER.warning("Failed to dispatch delayed anchor UI for " + playerName + ": " + e.getMessage());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Clears the anchor UI for a player.
     */
    public void clearAnchor(@Nonnull Player player) {
        PlayerRef playerRef = resolvePlayerRef(player);
        if (playerRef == null) {
            LOGGER.warning("MapAnchorManager: Could not resolve PlayerRef for " + player.getDisplayName() + ", skipping anchor UI clear.");
            return;
        }

        try {
            playerRef.getPacketHandler().writeNoCache(
                new UpdateAnchorUI(MAP_ANCHOR_ID, true, null, null)
            );
            activePlayers.remove(player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to clear anchor UI for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Refreshes the anchor UI for a player (e.g., after a waypoint is added/removed/edited).
     */
    public void refreshAnchor(@Nonnull Player player) {
        if (activePlayers.containsKey(player.getDisplayName())) {
            sendWaypointAnchor(player);
        }
    }

    /**
     * Refreshes the anchor for all active players in a given world.
     */
    public void refreshAllInWorld(@Nonnull World world) {
        for (PlayerRef ref : world.getPlayerRefs()) {
            if (ref == null) continue;
            Player player = ref.getComponent(Player.getComponentType());
            if (player != null && activePlayers.containsKey(player.getDisplayName())) {
                sendWaypointAnchor(player);
            }
        }
    }

    /**
     * Removes tracking for a player (on disconnect).
     */
    public void removePlayer(@Nonnull String playerName) {
        activePlayers.remove(playerName);
        lastMarkerCounts.remove(playerName);
        expandedPlayers.remove(playerName);
    }

    private PlayerRef resolvePlayerRef(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return null;
        }
        return store.getComponent(ref, PlayerRef.getComponentType());
    }

    private List<UserMapMarker> buildWaypointEntries(
        @Nonnull Player player,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events
    ) {
        List<UserMapMarker> markers = WaypointManager.getUserMarkers(player);

        if (markers == null || markers.isEmpty()) {
            commands.set("#WaypointAnchorList.Visible", false);
            commands.set("#EmptyLabel.Visible", true);
            commands.set("#WaypointCount.Text", "Waypoints (0)");
            return markers != null ? markers : List.of();
        }

        commands.set("#WaypointCount.Text", "Waypoints (" + markers.size() + ")");
        commands.set("#EmptyLabel.Visible", false);

        boolean canTeleport = PermissionsUtil.canTeleport(player)
            && ModConfig.getInstance().isAllowWaypointTeleports();

        int index = 0;
        for (UserMapMarker marker : markers) {
            if (marker == null || marker.getId() == null) continue;

            String itemPath = "#WaypointAnchorList[" + index + "]";

            commands.append("#WaypointAnchorList", "Hud/BetterMap/MapWaypointItem.ui");

            String name = marker.getName();
            commands.set(itemPath + " #WpName.Text", (name != null && !name.isEmpty()) ? name : "Unnamed");

            commands.set(itemPath + " #XValue.Text",
                String.format(Locale.ROOT, "X: %.0f", marker.getX()));
            commands.set(itemPath + " #ZValue.Text",
                String.format(Locale.ROOT, "Z: %.0f", marker.getZ()));

            boolean isShared = WaypointManager.isSharedId(marker.getId());
            commands.set(itemPath + " #WpSharedLabel.Text", isShared ? "(S)" : "");

            String icon = marker.getIcon();
            String iconPath = (icon != null && !icon.isEmpty()) ? icon : "UserA.png";
            commands.set(itemPath + " #WpIcon.Background", "Common/" + iconPath);

            Color tint = marker.getColorTint();
            if (tint != null) {
                commands.set(itemPath + " #WpIcon.Background.Color",
                    String.format("#%02X%02X%02X", tint.red & 0xFF, tint.green & 0xFF, tint.blue & 0xFF));
            }

            boolean canEdit = !isShared || PermissionsUtil.canEditSharedWaypoint(player, marker);

            commands.set(itemPath + " #EditButton.Visible", canEdit);
            if (canEdit) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #EditButton",
                    new EventData()
                        .put("action", ACTION_EDIT)
                        .put("waypointId", marker.getId()),
                    false
                );
            }

            commands.set(itemPath + " #TeleportButton.Visible", canTeleport);
            if (canTeleport) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #TeleportButton",
                    new EventData()
                        .put("action", ACTION_TELEPORT)
                        .put("waypointId", marker.getId()),
                    false
                );
            }

            commands.set(itemPath + " #DeleteButton.Visible", canEdit);
            if (canEdit) {
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    itemPath + " #DeleteButton",
                    new EventData()
                        .put("action", ACTION_DELETE)
                        .put("waypointId", marker.getId()),
                    false
                );
            }

            index++;
        }

        return markers;
    }

    /**
     * Starts a periodic polling task that detects when waypoints are created or
     * removed through Hytale's native map context menu (which bypasses our hooks).
     * Compares the current marker count against the last known count and refreshes
     * the anchor UI if a change is detected.
     */
    private void startPolling() {
        pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BetterMap-AnchorPoll");
            t.setDaemon(true);
            return t;
        });

        pollFuture = pollScheduler.scheduleAtFixedRate(() -> {
            try {
                pollForMarkerChanges();
            } catch (Exception e) {
                LOGGER.warning("Anchor poll error: " + e.getMessage());
            }
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info("MapAnchorManager: Marker change polling started (every " + POLL_INTERVAL_SECONDS + "s).");
    }

    /**
     * Stops the periodic polling task.
     */
    private void stopPolling() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
            pollFuture = null;
        }
        if (pollScheduler != null) {
            pollScheduler.shutdown();
            try {
                if (!pollScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    pollScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                pollScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            pollScheduler = null;
        }
    }

    /**
     * Checks all active players for marker count changes and refreshes their anchor UI if needed.
     * Dispatches the actual component access onto the player's world thread to avoid async violations.
     */
    private void pollForMarkerChanges() {
        for (Map.Entry<String, PlayerRef> entry : activePlayers.entrySet()) {
            String playerName = entry.getKey();
            PlayerRef playerRef = entry.getValue();

            try {
                java.util.UUID worldUuid = playerRef.getWorldUuid();
                if (worldUuid == null) continue;

                World world = com.hypixel.hytale.server.core.universe.Universe.get().getWorld(worldUuid);
                if (world == null || !world.isAlive()) continue;

                world.execute(() -> {
                    try {
                        Player player = playerRef.getComponent(Player.getComponentType());
                        if (player == null || player.getReference() == null || !player.getReference().isValid()) {
                            return;
                        }

                        List<UserMapMarker> currentMarkers = WaypointManager.getUserMarkers(player);
                        int currentCount = currentMarkers != null ? currentMarkers.size() : 0;
                        Integer lastCount = lastMarkerCounts.get(playerName);

                        if (lastCount == null || currentCount != lastCount) {
                            LOGGER.fine("Marker count changed for " + playerName + ": " + lastCount + " -> " + currentCount);
                            sendWaypointAnchor(player);
                        }
                    } catch (Exception e) {
                        LOGGER.fine("Poll check failed for " + playerName + ": " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                LOGGER.fine("Poll dispatch failed for " + playerName + ": " + e.getMessage());
            }
        }
    }
}

package dev.ninesliced.providers;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.compat.MultiHudCompat;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.hud.HudPosition;
import dev.ninesliced.hud.LocationHud;
import dev.ninesliced.managers.PlayerConfigManager;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the lifecycle and state of Location HUDs for all players.
 * <p>
 * Handles creating, updating, enabling, and disabling {@link LocationHud} instances.
 * Supports both vanilla Hytale HUD system and MultipleHUD mod for compatibility.
 * </p>
 *
 * @see LocationHud
 * @see MultiHudCompat
 */
public class LocationHudProvider {

    private static final Logger LOGGER = Logger.getLogger(LocationHudProvider.class.getName());
    private static final String HUD_IDENTIFIER = LocationHud.HUD_IDENTIFIER;
    private static final Field MAP_VISIBLE_FIELD = resolveMapVisibleField();

    private final Map<PlayerRef, LocationHud> huds = new HashMap<>();
    private final Set<UUID> warnedMissingMultiHud = new HashSet<>();
    private final Set<UUID> attachedHuds = new HashSet<>();
    private final Set<UUID> visibleHuds = new HashSet<>();

    /**
     * Updates and displays the HUD for a player entity.
     * Creates the HUD if it doesn't exist and attaches it using MultipleHUD when available.
     * Hides the HUD if the feature is disabled.
     *
     * @param dt             time delta since last tick
     * @param index          entity index in the chunk
     * @param archetypeChunk chunk containing entity data
     * @param store          entity store
     * @param commandBuffer  command buffer for operations
     */
    public void showHud(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);
        boolean isEnabled = config != null && config.isLocationEffectivelyEnabled();

        if (!isEnabled) {
            hideHudIfVisible(player, playerRef, playerUuid);
            return;
        }

        if (isWorldMapVisible(player)) {
            hideHudIfVisible(player, playerRef, playerUuid);
            return;
        }

        LocationHud hud = this.huds.computeIfAbsent(playerRef, LocationHud::new);
        hud.setEnabled(true);
        hud.setPosition(HudPosition.fromId(config.getEffectiveLocationHudPosition()));
        hud.updateHud(dt, index, archetypeChunk, store, commandBuffer);

        if (MultiHudCompat.isAvailable()) {
            MultiHudCompat.setCustomHud(player, playerRef, HUD_IDENTIFIER, hud);
            this.visibleHuds.add(playerUuid);
        } else {
            attachVanillaHud(player, playerRef, playerUuid, hud);
        }
    }

    /**
     * Hides the HUD if it's currently visible for the player.
     */
    private void hideHudIfVisible(Player player, PlayerRef playerRef, UUID playerUuid) {
        if (!this.visibleHuds.contains(playerUuid) && !this.attachedHuds.contains(playerUuid)) {
            return;
        }

        LocationHud hud = this.huds.get(playerRef);
        if (hud != null) {
            hud.setEnabled(false);
        }

        if (MultiHudCompat.isAvailable()) {
            MultiHudCompat.hideCustomHud(player, playerRef, HUD_IDENTIFIER);
        } else if (this.attachedHuds.contains(playerUuid)) {
            player.getHudManager().removeCustomHud(playerRef, HUD_IDENTIFIER);
        }

        this.visibleHuds.remove(playerUuid);
        this.attachedHuds.remove(playerUuid);
    }

    /**
     * Attaches HUD using vanilla Hytale system (single HUD only).
     */
    private void attachVanillaHud(Player player, PlayerRef playerRef, UUID playerUuid, LocationHud hud) {
        if (!this.attachedHuds.contains(playerUuid)) {
            if (this.warnedMissingMultiHud.add(playerUuid)) {
                LOGGER.log(Level.WARNING, "MultipleHUD not found, Location HUD may conflict with other mods");
            }
            player.getHudManager().addCustomHud(playerRef, hud);
            this.attachedHuds.add(playerUuid);
        } else {
            hud.show();
        }
    }

    /**
     * Removes HUD data for a disconnected player.
     *
     * @param playerRef reference to the player
     */
    public void removeHud(@Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        this.huds.remove(playerRef);
        this.attachedHuds.remove(playerUuid);
        this.visibleHuds.remove(playerUuid);
        this.warnedMissingMultiHud.remove(playerUuid);
    }

    private static Field resolveMapVisibleField() {
        try {
            Field field = WorldMapTracker.class.getDeclaredField("clientHasWorldMapVisible");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ex) {
            return null;
        }
    }

    private boolean isWorldMapVisible(@Nonnull Player player) {
        try {
            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker == null || MAP_VISIBLE_FIELD == null) {
                return false;
            }
            Object value = MAP_VISIBLE_FIELD.get(tracker);
            return value instanceof Boolean && (Boolean) value;
        } catch (IllegalAccessException ex) {
            return false;
        }
    }

    /**
     * Removes and detaches HUD for a connected player.
     *
     * @param player    player entity
     * @param playerRef reference to the player
     */
    public void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        hideHudIfVisible(player, playerRef, playerUuid);
        this.huds.remove(playerRef);
        this.warnedMissingMultiHud.remove(playerUuid);
    }

    /**
     * Checks if a HUD exists for the player.
     *
     * @param playerRef player reference to check
     * @return true if HUD exists
     */
    public boolean hasHudForPlayer(@Nonnull PlayerRef playerRef) {
        return this.huds.containsKey(playerRef);
    }

    /**
     * Enables and attaches the location HUD for a player.
     *
     * @param player    player entity
     * @param playerRef player reference
     */
    public void enableHudForPlayer(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);

        LocationHud hud = this.huds.get(playerRef);

        if (hud == null) {
            try {
                hud = new LocationHud(playerRef);
                this.huds.put(playerRef, hud);
                hud.setEnabled(true);
                attachHud(player, playerRef, playerUuid, hud);
            } catch (Exception e) {
                this.huds.remove(playerRef);
                this.attachedHuds.remove(playerUuid);
                this.visibleHuds.remove(playerUuid);
                if (config != null) {
                    config.setLocationEnabled(false);
                }
                throw e;
            }
        } else {
            hud.setEnabled(true);
            attachHud(player, playerRef, playerUuid, hud);
        }
    }

    /**
     * Attaches HUD using appropriate system (MultipleHUD or vanilla).
     */
    private void attachHud(Player player, PlayerRef playerRef, UUID playerUuid, LocationHud hud) {
        if (MultiHudCompat.isAvailable()) {
            MultiHudCompat.setCustomHud(player, playerRef, HUD_IDENTIFIER, hud);
            this.visibleHuds.add(playerUuid);
        } else if (!this.attachedHuds.contains(playerUuid)) {
            player.getHudManager().addCustomHud(playerRef, hud);
            this.attachedHuds.add(playerUuid);
        } else {
            hud.show();
        }
    }

    /**
     * Enables location HUD in player settings without immediate attachment.
     *
     * @param playerRef player reference
     */
    public void enableHudForPlayer(@Nonnull PlayerRef playerRef) {
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid());
        if (config != null) {
            config.setLocationEnabled(true);
        }
    }

    /**
     * Disables the location HUD for a player and hides it from screen.
     *
     * @param player    player entity
     * @param playerRef player reference
     */
    public void disableHudForPlayer(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        hideHudIfVisible(player, playerRef, playerUuid);
    }

    /**
     * Disables the location HUD for a player (legacy method without hiding).
     *
     * @param playerRef player reference
     */
    public void disableHudForPlayer(@Nonnull PlayerRef playerRef) {
        LocationHud hud = this.huds.get(playerRef);
        if (hud != null) {
            hud.setEnabled(false);
        }
    }

    /**
     * Cleans up all managed HUDs. Call on server shutdown.
     */
    public void cleanup() {
        huds.clear();
        attachedHuds.clear();
        visibleHuds.clear();
        warnedMissingMultiHud.clear();
    }
}

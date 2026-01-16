package dev.ninesliced.providers;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.hud.LocationHud;
import dev.ninesliced.managers.PlayerConfigManager;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the lifecycle and state of Location HUDs for all players.
 * <p>
 * This provider handles creating, updating, enabling, and disabling the specific
 * {@link LocationHud} instances for each player. It also persists player-specific
 * settings regarding the visibility of the HUD.
 * </p>
 */
public class LocationHudProvider {
    private final Map<PlayerRef, LocationHud> huds = new HashMap<>();

    /**
     * Updates and displays the HUD for a specific player entity.
     * <p>
     * If the HUD does not exist for the player, it is created and attached.
     * The HUD's enabled state is synchronized with the player's settings before updating.
     * </p>
     *
     * @param dt             The time delta since the last tick.
     * @param index          The index of the entity in the chunk.
     * @param archetypeChunk The chunk containing the entity data.
     * @param store          The entity store.
     * @param commandBuffer  The command buffer for operations.
     */
    @SuppressWarnings("unchecked")
    public void showHud(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Holder holder = EntityUtils.toHolder(index, archetypeChunk);
        Player player = (Player) holder.getComponent(Player.getComponentType());
        PlayerRef playerRef = (PlayerRef) holder.getComponent(PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        if (!this.huds.containsKey(playerRef)) {
            LocationHud hud = new LocationHud(playerRef);
            this.huds.put(playerRef, hud);
            player.getHudManager().setCustomHud(playerRef, hud);
        }

        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid());

        LocationHud hud = this.huds.get(playerRef);
        hud.setEnabled(config == null ? false : config.isLocationEnabled());

        hud.updateHud(dt, index, archetypeChunk, store, commandBuffer);
        hud.show();
    }

    /**
     * Removes the HUD instance and associated settings for a disconnected or cleanly removed player.
     *
     * @param playerRef The reference to the player to remove.
     */
    public void removeHud(@Nonnull PlayerRef playerRef) {
        this.huds.remove(playerRef);
    }

    /**
     * Checks if a HUD instance exists for the specified player.
     *
     * @param playerRef The player reference to check.
     * @return true if a HUD exists, false otherwise.
     */
    public boolean hasHudForPlayer(@Nonnull PlayerRef playerRef) {
        return this.huds.containsKey(playerRef);
    }

    /**
     * Enables the location HUD for a player, creating it if necessary.
     * <p>
     * This method updates the player's settings to enable the HUD and immediately
     * initializes and registers the HUD if it is not already present.
     * </p>
     *
     * @param player    The player entity instance.
     * @param playerRef The player reference.
     */
    public void enableHudForPlayer(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid());

        if (!this.huds.containsKey(playerRef)) {
            try {
                LocationHud locationHud = new LocationHud(playerRef);
                this.huds.put(playerRef, locationHud);
                locationHud.setEnabled(true);

                player.getHudManager().setCustomHud(playerRef, locationHud);
            } catch (Exception e) {
                this.huds.remove(playerRef);
                config.setLocationEnabled(false);
                throw e;
            }
        } else {
             LocationHud hud = this.huds.get(playerRef);
             hud.setEnabled(true);
             hud.show();
        }
    }

    /**
     * Enables the location HUD setting for a player without immediately creating the HUD.
     * <p>
     * Use {@link #enableHudForPlayer(Player, PlayerRef)} if immediate creation is required.
     * </p>
     *
     * @param playerRef The player reference to enable the HUD for.
     */
    public void enableHudForPlayer(@Nonnull PlayerRef playerRef) {
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid());
        config.setLocationEnabled(true);
    }

    /**
     * Disables the location HUD for a player.
     * <p>
     * This updates the settings to prevent future updates and disables the current HUD instance
     * if it exists.
     * </p>
     *
     * @param playerRef The player to disable the HUD for.
     */
    public void disableHudForPlayer(@Nonnull PlayerRef playerRef) {
        if (this.huds.containsKey(playerRef)) {
            LocationHud hud = this.huds.get(playerRef);
            hud.setEnabled(false);
        }
    }

    /**
     * Cleans up all managed HUDs. Should be called on server shutdown or reload.
     */
    public void cleanup() {
        huds.clear();
    }
}

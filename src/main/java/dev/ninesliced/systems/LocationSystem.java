package dev.ninesliced.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.BetterMap;
import dev.ninesliced.providers.LocationHudProvider;

import javax.annotation.Nonnull;

/**
 * A ticking system responsible for driving the location HUD updates.
 * <p>
 * This system iterates over all entities possessing a {@link Player} component
 * and triggers their HUD update via the {@link LocationHudProvider}.
 * </p>
 */
public class LocationSystem extends EntityTickingSystem<EntityStore> {
    private final Query<EntityStore> query;

    /**
     * Initializes the LocationSystem.
     * <p>
     * Sets up the query to match all entities that have a {@link Player} component.
     * </p>
     */
    public LocationSystem() {
        this.query = Query.and(Player.getComponentType());
    }

    /**
     * Retrieves the query used to filter entities for this system.
     *
     * @return The query matching player entities.
     */
    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    /**
     * Processes each matching player entity every tick.
     * <p>
     * Retrieves the {@link LocationHudProvider} and delegates the HUD update logic to it.
     * </p>
     *
     * @param dt             The time delta since the last tick.
     * @param index          The entity index within the archetype chunk.
     * @param archetypeChunk The chunk containing the entity data.
     * @param store          The entity store.
     * @param commandBuffer  The command buffer.
     */
    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                    @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Holder holder = EntityUtils.toHolder(index, archetypeChunk);
        Player player = (Player) holder.getComponent(Player.getComponentType());
        PlayerRef playerRef = (PlayerRef) holder.getComponent(PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        LocationHudProvider provider = BetterMap.get().getLocationHudProvider();
        if (provider != null) {
            provider.showHud(dt, index, archetypeChunk, store, commandBuffer);
        }
    }
}

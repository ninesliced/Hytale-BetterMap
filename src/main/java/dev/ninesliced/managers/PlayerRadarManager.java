package dev.ninesliced.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.providers.PlayerRadarProvider;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the registration and lifecycle of the PlayerRadarProvider for world maps.
 * <p>
 * This manager ensures that the radar marker provider is registered only once per world
 * and handles proper cleanup when worlds are unloaded.
 * </p>
 */
public class PlayerRadarManager {

    private static final Logger LOGGER = Logger.getLogger(PlayerRadarManager.class.getName());
    private static PlayerRadarManager instance;

    private final Set<String> registeredWorlds = new HashSet<>();
    private final Map<String, List<RadarData>> worldRadarCache = new ConcurrentHashMap<>();
    private final PlayerRadarProvider radarProvider;

    private PlayerRadarManager() {
        this.radarProvider = new PlayerRadarProvider();
    }

    /**
     * Gets the singleton instance of the PlayerRadarManager.
     *
     * @return The manager instance.
     */
    public static synchronized PlayerRadarManager getInstance() {
        if (instance == null) {
            instance = new PlayerRadarManager();
        }
        return instance;
    }

    /**
     * Gets the PlayerRadarProvider instance.
     *
     * @return The radar provider.
     */
    public PlayerRadarProvider getRadarProvider() {
        return radarProvider;
    }

    /**
     * Updates the radar data cache for the given world.
     * Must be called from the main world thread.
     *
     * @param world The world to update.
     */
    public void updateRadarData(@Nonnull World world) {
        List<RadarData> radarDataList = new ArrayList<>();

        try {
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                Vector3d pos = null;
                Vector3f rot = null;

                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                    if (tc != null) {
                        pos = tc.getPosition();
                        rot = tc.getRotation();
                    }
                }

                if (pos == null) {
                    com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
                    if (transform == null) continue;
                    pos = transform.getPosition();
                    rot = transform.getRotation();
                }

                if (pos == null) continue;

                RadarData data = new RadarData(
                        playerRef.getUuid().toString(),
                        playerRef.getUsername(),
                        new Vector3d(pos.x, pos.y, pos.z),
                        rot != null ? new Vector3f(rot.x, rot.y, rot.z) : Vector3f.ZERO
                );

                radarDataList.add(data);
            }
        } catch (Exception _) {}

        worldRadarCache.put(world.getName(), radarDataList);
    }

    /**
     * Gets the cached radar data for a world.
     * Safe to call from any thread.
     *
     * @param worldName The name of the world.
     * @return List of radar data.
     */
    public List<RadarData> getRadarData(String worldName) {
        return worldRadarCache.getOrDefault(worldName, Collections.emptyList());
    }

    /**
     * Registers the radar marker provider for a player's current world.
     * <p>
     * This method ensures that the provider is only registered once per world
     * to avoid duplicate markers.
     * </p>
     *
     * @param player The player for whose world to register the provider.
     */
    public void registerForPlayer(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null) {
            LOGGER.warning("Cannot register radar provider: player world is null");
            return;
        }

        registerForWorld(world);
    }

    /**
     * Registers the radar marker provider for a specific world.
     *
     * @param world The world to register the provider for.
     */
    public void registerForWorld(@Nonnull World world) {
        String worldName = world.getName();

        if (registeredWorlds.contains(worldName)) {
            return;
        }

        try {
            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) {
                LOGGER.warning("Cannot register radar provider: WorldMapManager is null for world " + worldName);
                return;
            }

            if (!mapManager.getMarkerProviders().containsKey(PlayerRadarProvider.PROVIDER_ID)) {
                mapManager.addMarkerProvider(PlayerRadarProvider.PROVIDER_ID, radarProvider);
                registeredWorlds.add(worldName);
                LOGGER.info("Registered PlayerRadarProvider for world: " + worldName);
            } else {
                registeredWorlds.add(worldName);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to register radar provider for world " + worldName + ": " + e.getMessage());
        }
    }

    /**
     * Unregisters the radar provider for a specific world.
     * <p>
     * Called when a world is being unloaded or shut down.
     * </p>
     *
     * @param worldName The name of the world to unregister.
     */
    public void unregisterForWorld(@Nonnull String worldName) {
        registeredWorlds.remove(worldName);
        LOGGER.info("Unregistered PlayerRadarProvider for world: " + worldName);
    }

    /**
     * Cleans up all registrations. Called on plugin shutdown.
     */
    public void cleanup() {
        registeredWorlds.clear();
        LOGGER.info("PlayerRadarManager cleaned up");
    }

    /**
     * Data class for caching player radar information.
     */
    public static class RadarData {
        public final String uuid;
        public final String name;
        public final Vector3d position;
        public final Vector3f rotation;

        public RadarData(String uuid, String name, Vector3d position, Vector3f rotation) {
            this.uuid = uuid;
            this.name = name;
            this.position = position;
            this.rotation = rotation;
        }
    }
}

package dev.ninesliced.utils;

import dev.ninesliced.configs.ModConfig;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.asset.type.gameplay.worldmap.UserMapMarkerConfig;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarkersStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Utility for working with Hytale's user marker limits.
 * Supports runtime overrides via reflection.
 */
public final class WaypointLimitUtil {
    private static final Logger LOGGER = Logger.getLogger(WaypointLimitUtil.class.getName());
    private WaypointLimitUtil() {
    }

    /**
     * Returns the current max markers per player for the given scope.
     */
    public static int getMaxMarkers(@Nullable World world, boolean shared) {
        ModConfig cfg = ModConfig.getInstance();
        return shared ? cfg.getMaxSharedMarkersPerPlayer() : cfg.getMaxPersonalMarkersPerPlayer();
    }

    /**
     * Returns the current number of markers a player has for the given scope.
     */
    public static int getCurrentMarkers(@Nonnull Player player, boolean shared) {
        World world = player.getWorld();
        if (world == null) {
            return -1;
        }

        if (!shared) {
            UserMapMarkersStore personalStore = player.getPlayerConfigData().getPerWorldData(world.getName());
            if (personalStore == null) {
                return -1;
            }
            return personalStore.getUserMapMarkers().size();
        }

        UserMapMarkersStore sharedStore = world.getChunkStore().getStore().getResource(WorldMarkersResource.getResourceType());
        if (sharedStore == null) {
            return -1;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        String displayName = player.getDisplayName();
        int count = 0;

        for (UserMapMarker marker : sharedStore.getUserMapMarkers()) {
            if (marker == null) {
                continue;
            }

            UUID createdBy = marker.getCreatedByUuid();
            if (createdBy != null && createdBy.equals(uuid)) {
                count++;
                continue;
            }

            if (createdBy == null && displayName != null) {
                String createdByName = marker.getCreatedByName();
                if (createdByName != null && createdByName.equalsIgnoreCase(displayName)) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Returns an error message if the player cannot create a marker, or null if allowed.
     */
    @Nullable
    public static String getCreationError(@Nonnull Player player, boolean shared) {
        World world = player.getWorld();
        if (world == null) {
            return "World not loaded.";
        }
        try {
            ModConfig cfg = ModConfig.getInstance();
            int limit = shared ? cfg.getMaxSharedMarkersPerPlayer() : cfg.getMaxPersonalMarkersPerPlayer();

            if (limit < 0) {
                return null;
            }
            if (limit <= 0) {
                return shared
                    ? "Shared waypoint creation is disabled (limit 0)."
                    : "Personal waypoint creation is disabled (limit 0).";
            }
            int current = getCurrentMarkers(player, shared);
            if (current < 0) {
                return "Could not access waypoint storage.";
            }
            if (current >= limit) {
                return "Waypoint limit reached (" + current + "/" + limit + ").";
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to read marker limits: " + e.getMessage());
            return "Could not validate waypoint limits.";
        }
        return null;
    }

    /**
     * Applies overrides to all worlds. Use -1 for unlimited.
     */
    public static void applyOverridesToAllWorlds(int personalOverride, int sharedOverride) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        universe.getWorlds().values().forEach(world -> applyOverridesToWorld(world, personalOverride, sharedOverride));
    }

    /**
     * Applies overrides to a single world. Use -1 for unlimited.
     */
    public static void applyOverridesToWorld(@Nonnull World world, int personalOverride, int sharedOverride) {
        try {
            UserMapMarkerConfig config = world.getGameplayConfig().getWorldMapConfig().getUserMapMarkerConfig();
            int personal = toNativeLimit(personalOverride);
            int shared = toNativeLimit(sharedOverride);

            ReflectionHelper.setFieldValue(config, "maxPersonalMarkersPerPlayer", personal);
            ReflectionHelper.setFieldValue(config, "maxSharedMarkersPerPlayer", shared);
        } catch (Exception e) {
            LOGGER.warning("Failed to apply marker limit overrides: " + e.getMessage());
        }
    }

    private static int toNativeLimit(int configuredLimit) {
        if (configuredLimit < 0) {
            return Integer.MAX_VALUE;
        }
        if (configuredLimit == 0) {
            return 0;
        }

        return configuredLimit + 1;
    }
}

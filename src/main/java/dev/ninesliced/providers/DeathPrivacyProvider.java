package dev.ninesliced.providers;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Provider that can optionally hide death markers on the world map.
 */
public class DeathPrivacyProvider implements WorldMapManager.MarkerProvider {
    public static final String PROVIDER_ID = "death";
    private static final Logger LOGGER = Logger.getLogger(DeathPrivacyProvider.class.getName());

    private WorldMapManager.MarkerProvider delegate;

    /**
     * Sets the original provider to delegate to when not hiding.
     *
     * @param delegate The original death marker provider.
     */
    public void setDelegate(WorldMapManager.MarkerProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public void update(World world, MapMarkerTracker tracker, int viewRadius, int chunkX, int chunkZ) {
        try {
            BetterMapConfig globalConfig = BetterMapConfig.getInstance();

            // Check if death markers should be hidden globally
            if (globalConfig.isHideDeathMarkerOnMap()) {
                return;
            }

            // Check if "death" is in global hiddenPoiNames
            List<String> hiddenNames = globalConfig.getHiddenPoiNames();
            if (hiddenNames != null) {
                for (String hidden : hiddenNames) {
                    if ("death".equalsIgnoreCase(hidden.trim())) {
                        return;
                    }
                }
            }

            // Check per-player settings (only if not globally hidden)
            Player player = tracker.getPlayer();
            if (player != null) {
                UUID playerUuid = player.getUuid();
                if (playerUuid != null) {
                    PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);
                    if (playerConfig != null) {
                        // Check player's personal hide death setting
                        if (playerConfig.isHideDeathMarkerOnMap()) {
                            return;
                        }
                        // Check player's personal hidden names
                        List<String> playerHiddenNames = playerConfig.getHiddenPoiNames();
                        if (playerHiddenNames != null) {
                            for (String hidden : playerHiddenNames) {
                                if ("death".equalsIgnoreCase(hidden.trim())) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            // Delegate to original provider
            if (delegate != null) {
                delegate.update(world, tracker, viewRadius, chunkX, chunkZ);
            }
        } catch (Exception e) {
            LOGGER.warning("Error in DeathPrivacyProvider.update: " + e.getMessage());
        }
    }
}

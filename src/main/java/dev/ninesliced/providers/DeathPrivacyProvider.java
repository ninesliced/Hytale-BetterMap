package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerDeathPositionData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.PermissionsUtil;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class DeathPrivacyProvider implements WorldMapManager.MarkerProvider {
    public static final String PROVIDER_ID = "death";
    private static final Logger LOGGER = Logger.getLogger(DeathPrivacyProvider.class.getName());
    private static final String MARKER_ICON = "Death.png";

    @Override
    public void update(World world, Player player, MarkersCollector collector) {
        try {
            ModConfig globalConfig = ModConfig.getInstance();

            if (player == null) {
                return;
            }

            UUID playerUuid = ((CommandSender) player).getUuid();
            PlayerConfig playerConfig = null;
            if (playerUuid != null) {
                playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(playerUuid);
            }

            boolean canOverrideDeath = PermissionsUtil.canOverrideDeath(player);
            boolean overrideEnabled = canOverrideDeath
                && playerConfig != null
                && playerConfig.isOverrideGlobalDeathHide();
            boolean globalHide = globalConfig.isHideDeathMarkerOnMap();

            if (globalHide && !overrideEnabled) {
                return;
            }

            if (!overrideEnabled) {
                List<String> hiddenNames = globalConfig.getHiddenPoiNames();
                if (hiddenNames != null) {
                    for (String hidden : hiddenNames) {
                        if ("death".equalsIgnoreCase(hidden.trim())) {
                            return;
                        }
                    }
                }
            }

            if (playerConfig != null) {
                if (playerConfig.isHideDeathMarkerOnMap()) {
                    return;
                }
                List<String> playerHiddenNames = playerConfig.getHiddenPoiNames();
                if (playerHiddenNames != null) {
                    for (String hidden : playerHiddenNames) {
                        if ("death".equalsIgnoreCase(hidden.trim())) {
                            return;
                        }
                    }
                }
            }

            PlayerWorldData worldData = player.getPlayerConfigData().getPerWorldData(world.getName());
            if (worldData == null) {
                return;
            }

            List<PlayerDeathPositionData> deathPositions = worldData.getDeathPositions();
            if (deathPositions == null || deathPositions.isEmpty()) {
                return;
            }

            boolean hasNativeTeleport = player.getWorldMapTracker() != null
                && player.getWorldMapTracker().isAllowTeleportToMarkers();
            boolean showTeleport = globalConfig.isAllowMapMarkerTeleports()
                && PermissionsUtil.canTeleport(player)
                && !hasNativeTeleport;

            for (PlayerDeathPositionData deathPosition : deathPositions) {
                if (deathPosition == null) {
                    continue;
                }

                Transform transform = deathPosition.getTransform();
                if (transform == null || transform.getPosition() == null) {
                    continue;
                }

                String markerId = deathPosition.getMarkerId();
                int deathDay = deathPosition.getDay();
                String markerName = "Death (Day " + deathDay + ")";

                MapMarker marker = createMarker(markerId, markerName, deathPosition, showTeleport);
                collector.add(marker);
            }
        } catch (Exception e) {
            LOGGER.warning("Error in DeathPrivacyProvider.update: " + e.getMessage());
        }
    }

    private static MapMarker createMarker(String id, String name, PlayerDeathPositionData deathPosition, boolean showTeleport) {
        FormattedMessage displayName = new FormattedMessage();
        displayName.rawText = name;

        ContextMenuItem[] contextMenuItems = null;
        if (showTeleport && deathPosition.getTransform() != null && deathPosition.getTransform().getPosition() != null) {
            int x = (int) Math.round(deathPosition.getTransform().getPosition().x);
            int y = (int) Math.round(deathPosition.getTransform().getPosition().y);
            int z = (int) Math.round(deathPosition.getTransform().getPosition().z);
            contextMenuItems = new ContextMenuItem[]{
                new ContextMenuItem("Teleport", "bettermap waypoint markertp " + x + " " + y + " " + z)
            };
        }

        return new MapMarker(
            id,
            displayName,
            displayName.rawText,
            MARKER_ICON,
            PositionUtil.toTransformPacket(deathPosition.getTransform()),
            contextMenuItems,
            null
        );
    }
}

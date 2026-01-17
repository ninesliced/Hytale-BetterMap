package dev.ninesliced.providers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.configs.BetterMapConfig;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Provides player markers on the world map, allowing players to see other players'
 * positions and distances. This implements a radar-like functionality for the map.
 */
public class PlayerRadarProvider implements WorldMapManager.MarkerProvider {

    private static final Logger LOGGER = Logger.getLogger(PlayerRadarProvider.class.getName());
    private static final String MARKER_PREFIX = "PlayerRadar-";
    private static final String MARKER_ICON = "Player.png";
    public static final String PROVIDER_ID = "BetterMapPlayerRadar";

    /**
     * Updates the player radar markers for the viewing player.
     */
    @Override
    public void update(World world, GameplayConfig gameplayConfig, WorldMapTracker tracker,
                       int viewRadius, int chunkX, int chunkZ) {
        try {
            Player viewingPlayer = tracker.getPlayer();

            UUID viewerUuid = ((CommandSender) viewingPlayer).getUuid();

            BetterMapConfig config = BetterMapConfig.getInstance();
            if (!config.isRadarEnabled() || config.isHidePlayersOnMap()) {
                return;
            }

            int radarRange = config.getRadarRange();
            boolean infiniteRange = radarRange < 0;
            int rangeSquared = infiniteRange ? Integer.MAX_VALUE : radarRange * radarRange;

            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> ref = viewingPlayer.getReference();
            if (ref == null || !ref.isValid()) return;
            TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
            if (transformComponent == null) return;

            Vector3d viewerPos = transformComponent.getPosition();

            for (PlayerRef otherPlayerRef : world.getPlayerRefs()) {
                if (otherPlayerRef.getUuid().equals(viewerUuid)) {
                    continue;
                }

                try {
                    Transform otherTransform = otherPlayerRef.getTransform();
                    if (otherTransform == null) continue;

                    Vector3d otherPos = otherTransform.getPosition();
                    if (otherPos == null) continue;

                    double dx = otherPos.x - viewerPos.x;
                    double dy = otherPos.y - viewerPos.y;
                    double dz = otherPos.z - viewerPos.z;
                    double distanceSquared = dx * dx + dy * dy + dz * dz;

                    if (!infiniteRange && distanceSquared > (double) rangeSquared) {
                        continue;
                    }

                    int distance = (int) Math.sqrt(distanceSquared);
                    String markerId = MARKER_PREFIX + otherPlayerRef.getUuid().toString();
                    String markerName = otherPlayerRef.getUsername() + " (" + distance + "m)";

                    MapMarker marker = createPlayerMarker(markerId, markerName, otherPos, otherTransform.getRotation());
                    tracker.trySendMarker(viewRadius, chunkX, chunkZ, marker);
                } catch (Exception e) {
                    // Silently ignore individual player marker failures
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error in PlayerRadarProvider.update: " + e.getMessage());
        }
    }

    /**
     * Creates a MapMarker for a player.
     */
    private MapMarker createPlayerMarker(String id, String name, Vector3d position, Vector3f rotation) {
        com.hypixel.hytale.protocol.Transform transform = new com.hypixel.hytale.protocol.Transform();
        transform.position = new Position(position.x, position.y, position.z);
        transform.orientation = new Direction(
                rotation != null ? rotation.x : 0.0f,
                rotation != null ? rotation.y : 0.0f,
                rotation != null ? rotation.z : 0.0f
        );
        return new MapMarker(id, name, MARKER_ICON, transform, null);
    }
}

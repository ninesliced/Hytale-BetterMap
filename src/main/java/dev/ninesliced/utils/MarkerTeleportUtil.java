package dev.ninesliced.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;

import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.utils.PermissionsUtil.MarkerType;

public final class MarkerTeleportUtil {

    private static final String TELEPORT_COMMAND_PREFIX = "bettermap mtp ";

    private MarkerTeleportUtil() {
    }

    public static void injectTeleportContextMenu(@Nonnull MapMarker marker, @Nullable Player player, @Nonnull MarkerType markerType) {
        if (player == null) return;

        ModConfig config = ModConfig.getInstance();
        boolean configAllows = config.isAllowMapMarkerTeleports()
            && config.isAllowTeleportForMarkerType(markerType);
        if (!configAllows && !PermissionsUtil.canTeleportToMarkerType(player, markerType)) return;

        ContextMenuItem teleportItem = new ContextMenuItem("Teleport", TELEPORT_COMMAND_PREFIX + marker.id);

        if (marker.contextMenuItems == null) {
            marker.contextMenuItems = new ContextMenuItem[] { teleportItem };
        } else {
            ContextMenuItem[] existing = marker.contextMenuItems;
            ContextMenuItem[] expanded = new ContextMenuItem[existing.length + 1];
            expanded[0] = teleportItem;
            System.arraycopy(existing, 0, expanded, 1, existing.length);
            marker.contextMenuItems = expanded;
        }
    }
}

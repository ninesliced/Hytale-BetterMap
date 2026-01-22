package dev.ninesliced.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

public class BetterMapWaypointAddCommand extends AbstractPlayerCommand {
    private final OptionalArg<String> nameArg = this.withOptionalArg("name", "Name of the waypoint", ArgTypes.STRING);
    private final OptionalArg<String> colorArg = this.withOptionalArg("color", "Color name (red/green/blue)", ArgTypes.STRING);
    private final OptionalArg<Boolean> globalArg = this.withOptionalArg("global", "Save as global waypoint (requires permission)", ArgTypes.BOOLEAN);
    private static final Pattern AUTO_NAME_PATTERN = Pattern.compile("Waypoint_\\d+");

    public BetterMapWaypointAddCommand() {
        super("add", "Add a waypoint at your current location");
        this.addAliases("create");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not find player component"));
            return;
        }

        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            context.sendMessage(Message.raw("Could not find transform component"));
            return;
        }

        String name = this.nameArg.get(context);

        if (name == null || name.isEmpty()) {
            name = generateAutoName(player.getPlayerConfigData().getPerWorldData(world.getName()));
        }

        String colorInput = this.colorArg.get(context);
        String icon;
        if (colorInput == null || colorInput.isEmpty()) {
            icon = "Coordinate.png";
        } else {
            String normalized = colorInput.trim().toLowerCase();
            String capitalized = Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
            icon = capitalized + "Marker.png";
        }

        Transform transform = PositionUtil.toTransformPacket(new com.hypixel.hytale.math.vector.Transform(transformComponent.getPosition()));

        boolean makeGlobal = Boolean.TRUE.equals(this.globalArg.get(context));
        if (makeGlobal && !PermissionsUtil.canUseGlobalWaypoints(player)) {
            context.sendMessage(Message.raw("You don't have permission to create global waypoints. Creating a personal waypoint instead."));
            makeGlobal = false;
        }

        WaypointManager.addWaypoint(player, name, icon, transform, makeGlobal);
        String scope = makeGlobal ? "global" : "personal";
        context.sendMessage(Message.raw("Added " + scope + " waypoint '" + name + "' at your location!"));
    }

    private String generateAutoName(@Nonnull PlayerWorldData perWorldData) {
        MapMarker[] markers = perWorldData.getWorldMapMarkers();
        int max = 0;
        if (markers != null) {
            for (MapMarker m : markers) {
                if (m != null && m.name != null) {
                    Matcher matcher = AUTO_NAME_PATTERN.matcher(m.name.trim());
                    if (matcher.matches()) {
                        try {
                            int n = Integer.parseInt(m.name.substring("Waypoint_".length()));
                            if (n > max) max = n;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return "Waypoint_" + (max + 1);
    }
}

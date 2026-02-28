package dev.ninesliced.commands.bettermap.waypoint;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WaypointLimitUtil;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

public class WaypointAddCommand extends AbstractPlayerCommand {
    private final OptionalArg<String> nameArg = this.withOptionalArg("name", "Name of the waypoint", ArgTypes.STRING);
    private final OptionalArg<Boolean> globalArg = this.withOptionalArg("global", "Save as global waypoint (requires permission)", ArgTypes.BOOLEAN);
    private static final Pattern AUTO_NAME_PATTERN = Pattern.compile("Waypoint_\\d+");

    public WaypointAddCommand() {
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
            name = generateAutoName(player);
        }

        String icon = "UserA.png";
        float x = (float) transformComponent.getPosition().x;
        double y = transformComponent.getPosition().y;
        float z = (float) transformComponent.getPosition().z;

        boolean makeShared = Boolean.TRUE.equals(this.globalArg.get(context));
        if (makeShared && !PermissionsUtil.canUseGlobalWaypoints(player)) {
            context.sendMessage(Message.raw("You don't have permission to create shared waypoints. Creating a personal waypoint instead."));
            makeShared = false;
        }

        String limitError = WaypointLimitUtil.getCreationError(player, makeShared);
        if (limitError != null) {
            context.sendMessage(Message.raw(limitError));
            return;
        }

        WaypointManager.addMarker(player, name, icon, x, z, y, null, makeShared);
        String scope = makeShared ? "shared" : "personal";
        context.sendMessage(Message.raw("Added " + scope + " waypoint '" + name + "' at your location!"));
    }

    private String generateAutoName(@Nonnull Player player) {
        List<UserMapMarker> markers = WaypointManager.getUserMarkers(player);
        int max = 0;
        for (UserMapMarker m : markers) {
            if (m != null) {
                String markerName = m.getName();
                if (markerName == null) {
                    continue;
                }
                Matcher matcher = AUTO_NAME_PATTERN.matcher(markerName.trim());
                if (matcher.matches()) {
                    try {
                        int n = Integer.parseInt(markerName.substring("Waypoint_".length()));
                        if (n > max) max = n;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return "Waypoint_" + (max + 1);
    }
}

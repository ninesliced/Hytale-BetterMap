package dev.ninesliced.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public class BetterMapWaypointUpdateCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> targetArg = this.withRequiredArg("target", "Waypoint name or marker id", ArgTypes.STRING);
    private final OptionalArg<String> newNameArg = this.withOptionalArg("newName", "New name for the waypoint", ArgTypes.STRING);
    private final OptionalArg<String> colorArg = this.withOptionalArg("color", "Color name (red/green/blue/etc)", ArgTypes.STRING);

    public BetterMapWaypointUpdateCommand() {
        super("update", "Update a map waypoint");
        this.addAliases("edit");
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
        if (player == null) return;

        String target = this.targetArg.get(context);
        String newNameRaw = this.newNameArg.get(context);
        String newColorInput = this.colorArg.get(context);

        if (newNameRaw == null && newColorInput == null) {
            context.sendMessage(Message.raw("You must provide either a name or a color to update."));
            return;
        }

        MapMarker marker = dev.ninesliced.managers.WaypointManager.findWaypoint(player, target);
        
        if (marker != null) {
             String icon = null;
             if (newColorInput != null && !newColorInput.isEmpty()) {
                String normalized = newColorInput.trim().toLowerCase();
                String capitalized = Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
                icon = capitalized + "Marker.png";
             }
             
             dev.ninesliced.managers.WaypointManager.updateWaypoint(player, marker.id, newNameRaw, icon, null);
             context.sendMessage(Message.raw("Updated waypoint: " + (marker.name != null ? marker.name : target)));
        } else {
            context.sendMessage(Message.raw("Could not find waypoint with that name or id."));
        }
    }
}

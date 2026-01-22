package dev.ninesliced.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.ui.WaypointMenuPage;

import javax.annotation.Nonnull;

public class BetterMapWaypointCommand extends AbstractCommandCollection {
    public BetterMapWaypointCommand() {
        super("waypoint", "Manage map waypoints");
        this.addAliases("marker");
        this.addSubCommand(new BetterMapWaypointAddCommand());
        this.addSubCommand(new BetterMapWaypointDeleteCommand());
        this.addSubCommand(new BetterMapWaypointDeleteGlobalCommand());
        this.addSubCommand(new BetterMapWaypointListCommand());
        this.addSubCommand(new BetterMapWaypointUpdateCommand());
        this.addSubCommand(new BetterMapWaypointTeleportCommand());
        this.addSubCommand(new BetterMapWaypointIdCommand());
        this.addSubCommand(new BetterMapMenuCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }
}

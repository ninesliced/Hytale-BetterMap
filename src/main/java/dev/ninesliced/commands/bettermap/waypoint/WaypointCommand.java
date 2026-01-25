package dev.ninesliced.commands.bettermap.waypoint;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.ui.WaypointMenuPage;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.concurrent.CompletableFuture;

public class WaypointCommand extends AbstractCommand {
    public WaypointCommand() {
        super("waypoint", "Manage map waypoints");
        this.addAliases("marker");
        this.addSubCommand(new WaypointAddCommand());
        this.addSubCommand(new WaypointDeleteCommand());
        this.addSubCommand(new WaypointDeleteGlobalCommand());
        this.addSubCommand(new WaypointListCommand());
        this.addSubCommand(new WaypointUpdateCommand());
        this.addSubCommand(new WaypointTeleportCommand());
        this.addSubCommand(new WaypointIdCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }

    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        return CompletableFuture.runAsync(() -> {
            Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
            if (ref == null) return;
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) return;

            player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(playerRef));
        });
    }
}

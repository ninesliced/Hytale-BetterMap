package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.hud.HudPosition;
import dev.ninesliced.managers.PlayerConfigManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import dev.ninesliced.utils.PlayerRefUtil;

import java.awt.*;
import java.util.concurrent.CompletableFuture;
import dev.ninesliced.utils.PlayerRefUtil;

/**
 * Command to change the Location HUD position.
 * Usage: /bettermap locationpos <position>
 */
public class PlayerLocationPositionCommand extends AbstractCommand {

    private final RequiredArg<String> positionArg;

    public PlayerLocationPositionCommand() {
        super("locationpos", "Change the location HUD position");
        this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER, HytalePermissionsProvider.GROUP_WORLD_EDITOR);
        this.positionArg = this.withRequiredArg("position",
                "Position: " + HudPosition.getAllIds(), ArgTypes.STRING);
    }

    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        if (!ModConfig.getInstance().isLocationEnabled()) {
            commandContext.sendMessage(Message.raw("Location HUD is disabled on this server.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        if (!commandContext.isPlayer()) {
            commandContext.sendMessage(Message.raw("This command can only be used by players.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String positionInput = this.positionArg.get(commandContext);
        if (positionInput == null || positionInput.isBlank()) {
            commandContext.sendMessage(Message.raw("Usage: /bettermap locationpos <position>").color(Color.YELLOW));
            commandContext.sendMessage(Message.raw("Positions: " + HudPosition.getAllIds()).color(Color.GRAY));
            return CompletableFuture.completedFuture(null);
        }

        String normalized = positionInput.trim().toLowerCase();
        HudPosition newPosition = HudPosition.fromId(normalized);

        if (!normalized.equals(newPosition.getId())) {
            commandContext.sendMessage(Message.raw("Invalid position. Available: " + HudPosition.getAllIds()).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
        if (ref == null) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (player == null || playerRef == null) {
                return;
            }

            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid());
            config.setLocationHudPosition(newPosition.getId());
            PlayerConfigManager.getInstance().savePlayerConfig(playerRef.getUuid());

            PlayerRefUtil.resolve(player).sendMessage(Message.raw("Location HUD position set to: ")
                    .color(Color.GREEN)
                    .insert(Message.raw(newPosition.getDisplayName()).color(Color.CYAN)));
        }, world);
    }
}

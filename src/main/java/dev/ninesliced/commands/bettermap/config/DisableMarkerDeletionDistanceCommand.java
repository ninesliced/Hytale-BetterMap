package dev.ninesliced.commands.bettermap.config;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.ninesliced.configs.ModConfig;

public class DisableMarkerDeletionDistanceCommand extends AbstractCommand {

    public DisableMarkerDeletionDistanceCommand() {
        super("disablemarkerdeletiondistance", "Toggle distance restriction override for marker deletion");
        this.addAliases("markerdeletiondistance");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext commandContext) {
        if (!commandContext.isPlayer()) {
            commandContext.sendMessage(Message.raw("This command can only be used by a player.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
        if (ref == null) {
            return CompletableFuture.completedFuture(null);
        }

        var store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null) {
                return;
            }

            ModConfig config = ModConfig.getInstance();
            boolean newState = !config.isDisableDistanceRestrictionsForMarkerDeletion();
            config.setDisableDistanceRestrictionsForMarkerDeletion(newState);

            String status = newState ? "ENABLED" : "DISABLED";
            Color color = newState ? Color.GREEN : Color.RED;

            commandContext.sendMessage(Message.raw("Disable distance restrictions for marker deletion " + status + ".").color(color));
            commandContext.sendMessage(Message.raw("A server restart is required for this change to take effect.").color(Color.GRAY));
        }, world);
    }
}

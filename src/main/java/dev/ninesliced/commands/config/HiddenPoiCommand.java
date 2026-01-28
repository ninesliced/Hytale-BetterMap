package dev.ninesliced.commands.config;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;

/**
 * Command to manage the list of hidden POI names.
 * Usage: /bm config hiddenpoi <add|remove|list|clear> [name]
 */
public class HiddenPoiCommand extends AbstractCommand {

    private final OptionalArg<String> actionArg = this.withOptionalArg("action", "Action: list/add/remove/clear", ArgTypes.STRING);
    private final OptionalArg<String> nameArg = this.withOptionalArg("name", "POI name to add/remove", ArgTypes.STRING);

    public HiddenPoiCommand() {
        super("hiddenpoi", "Manage hidden POI names list");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
        this.addAliases("hiddenpois");
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

        String action = commandContext.get(actionArg);
        String name = commandContext.get(nameArg);

        return CompletableFuture.runAsync(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerComponent == null || playerRef == null) {
                return;
            }

            BetterMapConfig config = BetterMapConfig.getInstance();

            if (action == null || action.isEmpty()) {
                showUsage(playerRef);
                return;
            }

            switch (action.toLowerCase(Locale.ROOT)) {
                case "list":
                    listHiddenPois(playerRef, config);
                    break;
                case "add":
                    if (name == null || name.isEmpty()) {
                        playerRef.sendMessage(Message.raw("Usage: /bm config hiddenpoi add <name>").color(Color.YELLOW));
                        return;
                    }
                    addHiddenPoi(playerRef, config, name, world);
                    break;
                case "remove":
                    if (name == null || name.isEmpty()) {
                        playerRef.sendMessage(Message.raw("Usage: /bm config hiddenpoi remove <name>").color(Color.YELLOW));
                        return;
                    }
                    removeHiddenPoi(playerRef, config, name, world);
                    break;
                case "clear":
                    clearHiddenPois(playerRef, config, world);
                    break;
                default:
                    showUsage(playerRef);
                    break;
            }
        }, world);
    }

    private void showUsage(PlayerRef playerRef) {
        playerRef.sendMessage(Message.raw("Hidden POI Commands:").color(Color.ORANGE));
        playerRef.sendMessage(Message.raw("  /bm config hiddenpoi list - Show hidden POI names").color(Color.GRAY));
        playerRef.sendMessage(Message.raw("  /bm config hiddenpoi add <name> - Add a POI name to hide").color(Color.GRAY));
        playerRef.sendMessage(Message.raw("  /bm config hiddenpoi remove <name> - Remove a POI name").color(Color.GRAY));
        playerRef.sendMessage(Message.raw("  /bm config hiddenpoi clear - Clear all hidden POI names").color(Color.GRAY));
    }

    private void listHiddenPois(PlayerRef playerRef, BetterMapConfig config) {
        List<String> hiddenNames = config.getHiddenPoiNames();
        if (hiddenNames == null || hiddenNames.isEmpty()) {
            playerRef.sendMessage(Message.raw("No POI names are currently hidden.").color(Color.GRAY));
            return;
        }

        playerRef.sendMessage(Message.raw("Hidden POI Names (" + hiddenNames.size() + "):").color(Color.ORANGE));
        for (String name : hiddenNames) {
            playerRef.sendMessage(Message.raw("  - " + name).color(Color.WHITE));
        }
    }

    private void addHiddenPoi(PlayerRef playerRef, BetterMapConfig config, String name, World world) {
        List<String> hiddenNames = new ArrayList<>(config.getHiddenPoiNames());

        for (String existing : hiddenNames) {
            if (existing.equalsIgnoreCase(name)) {
                playerRef.sendMessage(Message.raw("'" + name + "' is already in the hidden list.").color(Color.YELLOW));
                return;
            }
        }

        hiddenNames.add(name);
        config.setHiddenPoiNames(hiddenNames);
        PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
        WorldMapHook.clearMarkerCaches(world);
        WorldMapHook.refreshTrackers(world);
        resetOverride(playerRef);

        playerRef.sendMessage(Message.raw("Added '" + name + "' to hidden POI list.").color(Color.GREEN));
        playerRef.sendMessage(Message.raw("POIs matching this name will now be hidden.").color(Color.GRAY));
    }

    private void removeHiddenPoi(PlayerRef playerRef, BetterMapConfig config, String name, World world) {
        List<String> hiddenNames = new ArrayList<>(config.getHiddenPoiNames());
        boolean removed = hiddenNames.removeIf(existing -> existing.equalsIgnoreCase(name));

        if (!removed) {
            playerRef.sendMessage(Message.raw("'" + name + "' was not found in the hidden list.").color(Color.YELLOW));
            return;
        }

        config.setHiddenPoiNames(hiddenNames);
        PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
        WorldMapHook.clearMarkerCaches(world);
        WorldMapHook.refreshTrackers(world);
        resetOverride(playerRef);

        playerRef.sendMessage(Message.raw("Removed '" + name + "' from hidden POI list.").color(Color.GREEN));
        playerRef.sendMessage(Message.raw("POIs matching this name will now be visible.").color(Color.GRAY));
    }

    private void clearHiddenPois(PlayerRef playerRef, BetterMapConfig config, World world) {
        List<String> hiddenNames = config.getHiddenPoiNames();
        if (hiddenNames == null || hiddenNames.isEmpty()) {
            playerRef.sendMessage(Message.raw("The hidden POI list is already empty.").color(Color.YELLOW));
            return;
        }

        int count = hiddenNames.size();
        config.setHiddenPoiNames(new ArrayList<>());
        PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
        WorldMapHook.clearMarkerCaches(world);
        WorldMapHook.refreshTrackers(world);
        resetOverride(playerRef);

        playerRef.sendMessage(Message.raw("Cleared " + count + " entries from hidden POI list.").color(Color.GREEN));
    }

    private void resetOverride(PlayerRef playerRef) {
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(playerRef.getUuid());
        if (playerConfig != null) {
            playerConfig.setOverrideGlobalPoiHide(false);
            PlayerConfigManager.getInstance().savePlayerConfig(playerRef.getUuid());
        }
    }
}

package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to manage the player's personal hidden POI names list.
 * Usage: /bm hiddenpoi <list|add|remove|clear> [name]
 */
public class PlayerHiddenPoiCommand extends AbstractCommand {

    private final OptionalArg<String> actionArg = this.withOptionalArg("action", "Action: list/add/remove/clear", ArgTypes.STRING);
    private final OptionalArg<String> nameArg = this.withOptionalArg("name", "POI name to add/remove", ArgTypes.STRING);

    public PlayerHiddenPoiCommand() {
        super("hiddenpoi", "Manage your personal hidden POI list");
        this.addAliases("hiddenpois");
    }

    @Override
    protected String generatePermissionNode() {
        return "hiddenpoi";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = ((com.hypixel.hytale.server.core.command.system.CommandSender) context.sender()).getUuid();
        Player player = (Player) context.sender();
        World world = player.getWorld();
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

        if (world == null || config == null) {
            context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String action = context.get(actionArg);
        String name = context.get(nameArg);

        if (action == null || action.isEmpty()) {
            showUsage(context);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            switch (action.toLowerCase(Locale.ROOT)) {
                case "list":
                    listHiddenPois(context, config);
                    break;
                case "add":
                    if (name == null || name.isEmpty()) {
                        context.sendMessage(Message.raw("Usage: /bm hiddenpoi add <name>").color(Color.YELLOW));
                        return;
                    }
                    addHiddenPoi(context, config, name, uuid, world);
                    break;
                case "remove":
                    if (name == null || name.isEmpty()) {
                        context.sendMessage(Message.raw("Usage: /bm hiddenpoi remove <name>").color(Color.YELLOW));
                        return;
                    }
                    removeHiddenPoi(context, config, name, uuid, world);
                    break;
                case "clear":
                    clearHiddenPois(context, config, uuid, world);
                    break;
                default:
                    showUsage(context);
                    break;
            }
        }, world);
    }

    private void showUsage(CommandContext context) {
        context.sendMessage(Message.raw("Personal Hidden POI Commands:").color(Color.ORANGE));
        context.sendMessage(Message.raw("  /bm hiddenpoi list - Show your hidden POI names").color(Color.GRAY));
        context.sendMessage(Message.raw("  /bm hiddenpoi add <name> - Add a POI name to hide").color(Color.GRAY));
        context.sendMessage(Message.raw("  /bm hiddenpoi remove <name> - Remove a POI name").color(Color.GRAY));
        context.sendMessage(Message.raw("  /bm hiddenpoi clear - Clear your hidden POI list").color(Color.GRAY));
        
        BetterMapConfig globalConfig = BetterMapConfig.getInstance();
        List<String> globalHidden = globalConfig.getHiddenPoiNames();
        if (globalHidden != null && !globalHidden.isEmpty()) {
            context.sendMessage(Message.raw("Server-wide hidden POIs: " + globalHidden.size() + " entries").color(Color.YELLOW));
        }
    }

    private void listHiddenPois(CommandContext context, PlayerConfig config) {
        List<String> hiddenNames = config.getHiddenPoiNames();
        if (hiddenNames == null || hiddenNames.isEmpty()) {
            context.sendMessage(Message.raw("You have no personal hidden POI names.").color(Color.GRAY));
        } else {
            context.sendMessage(Message.raw("Your Hidden POI Names (" + hiddenNames.size() + "):").color(Color.ORANGE));
            for (String name : hiddenNames) {
                context.sendMessage(Message.raw("  - " + name).color(Color.WHITE));
            }
        }
        
        BetterMapConfig globalConfig = BetterMapConfig.getInstance();
        List<String> globalHidden = globalConfig.getHiddenPoiNames();
        if (globalHidden != null && !globalHidden.isEmpty()) {
            context.sendMessage(Message.raw("Server-wide Hidden POIs (" + globalHidden.size() + "):").color(Color.YELLOW));
            for (String name : globalHidden) {
                context.sendMessage(Message.raw("  - " + name).color(Color.GRAY));
            }
        }
    }

    private void addHiddenPoi(CommandContext context, PlayerConfig config, String name, UUID uuid, World world) {
        List<String> hiddenNames = new ArrayList<>(config.getHiddenPoiNames());

        for (String existing : hiddenNames) {
            if (existing.equalsIgnoreCase(name)) {
                context.sendMessage(Message.raw("'" + name + "' is already in your hidden list.").color(Color.YELLOW));
                return;
            }
        }

        hiddenNames.add(name);
        config.setHiddenPoiNames(hiddenNames);
        PlayerConfigManager.getInstance().savePlayerConfig(uuid);
        PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
        WorldMapHook.clearMarkerCaches(world);
        WorldMapHook.refreshTrackers(world);

        context.sendMessage(Message.raw("Added '" + name + "' to your hidden POI list.").color(Color.GREEN));
    }

    private void removeHiddenPoi(CommandContext context, PlayerConfig config, String name, UUID uuid, World world) {
        List<String> hiddenNames = new ArrayList<>(config.getHiddenPoiNames());
        boolean removed = hiddenNames.removeIf(existing -> existing.equalsIgnoreCase(name));

        if (!removed) {
            context.sendMessage(Message.raw("'" + name + "' was not found in your hidden list.").color(Color.YELLOW));
            return;
        }

        config.setHiddenPoiNames(hiddenNames);
        PlayerConfigManager.getInstance().savePlayerConfig(uuid);
        PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
        WorldMapHook.clearMarkerCaches(world);
        WorldMapHook.refreshTrackers(world);

        context.sendMessage(Message.raw("Removed '" + name + "' from your hidden POI list.").color(Color.GREEN));
    }

    private void clearHiddenPois(CommandContext context, PlayerConfig config, UUID uuid, World world) {
        List<String> hiddenNames = config.getHiddenPoiNames();
        if (hiddenNames == null || hiddenNames.isEmpty()) {
            context.sendMessage(Message.raw("Your hidden POI list is already empty.").color(Color.YELLOW));
            return;
        }

        int count = hiddenNames.size();
        config.setHiddenPoiNames(new ArrayList<>());
        PlayerConfigManager.getInstance().savePlayerConfig(uuid);
        PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
        WorldMapHook.clearMarkerCaches(world);
        WorldMapHook.refreshTrackers(world);

        context.sendMessage(Message.raw("Cleared " + count + " entries from your hidden POI list.").color(Color.GREEN));
    }
}

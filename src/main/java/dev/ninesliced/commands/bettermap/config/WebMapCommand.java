package dev.ninesliced.commands.bettermap.config;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.webmap.WebMapService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to manage BetterMap's web map server.
 */
public class WebMapCommand extends AbstractCommand {
    private final OptionalArg<String> actionArg = this.withOptionalArg("action", "status|start|stop|restart|port|scope|cache|explored|open", ArgTypes.STRING);
    private final OptionalArg<String> valueArg = this.withOptionalArg("value", "Optional value for action", ArgTypes.STRING);

    public WebMapCommand() {
        super("webmap", "Manage BetterMap WebMap server");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        ModConfig config = ModConfig.getInstance();
        WebMapService webMapService = BetterMap.get().getWebMapService();

        String action = context.get(actionArg);
        String value = context.get(valueArg);

        if (action == null || action.isBlank() || action.equalsIgnoreCase("status")) {
            sendStatus(context, config, webMapService);
            return CompletableFuture.completedFuture(null);
        }

        switch (action.trim().toLowerCase()) {
            case "start", "on", "enable" -> {
                config.setWebMapEnabled(true);
                webMapService.start();
                context.sendMessage(Message.raw("WebMap started at " + webMapService.getBaseUrl()).color(Color.GREEN));
            }
            case "stop", "off", "disable" -> {
                config.setWebMapEnabled(false);
                webMapService.stop();
                context.sendMessage(Message.raw("WebMap stopped.").color(Color.YELLOW));
            }
            case "restart" -> {
                if (!config.isWebMapEnabled()) {
                    context.sendMessage(Message.raw("WebMap is disabled. Enable it first with /bm config webmap start").color(Color.YELLOW));
                } else {
                    webMapService.stop();
                    webMapService.start();
                    context.sendMessage(Message.raw("WebMap restarted at " + webMapService.getBaseUrl()).color(Color.GREEN));
                }
            }
            case "port" -> {
                if (value == null) {
                    context.sendMessage(Message.raw("Current WebMap port: " + config.getWebMapPort()).color(Color.YELLOW));
                } else {
                    try {
                        int port = Integer.parseInt(value);
                        config.setWebMapPort(port);
                        if (webMapService.isRunning()) {
                            webMapService.stop();
                            webMapService.start();
                        }
                        context.sendMessage(Message.raw("WebMap port set to " + config.getWebMapPort()).color(Color.GREEN));
                    } catch (NumberFormatException e) {
                        context.sendMessage(Message.raw("Invalid port. Use a number between 1024 and 65535.").color(Color.RED));
                    }
                }
            }
            case "scope", "mode" -> {
                if (value == null) {
                    context.sendMessage(Message.raw("Current WebMap scope: " + config.getWebMapDataMode().name().toLowerCase()).color(Color.YELLOW));
                } else {
                    String mode = value.trim().toLowerCase();
                    if ("global".equals(mode)) {
                        config.setWebMapDataMode(ModConfig.WebMapDataMode.GLOBAL);
                        context.sendMessage(Message.raw("WebMap scope set to global.").color(Color.GREEN));
                    } else if ("player".equals(mode) || "single".equals(mode) || "single_player".equals(mode)) {
                        config.setWebMapDataMode(ModConfig.WebMapDataMode.PLAYER);
                        context.sendMessage(Message.raw("WebMap scope set to player.").color(Color.GREEN));
                    } else {
                        context.sendMessage(Message.raw("Invalid scope. Use global or player.").color(Color.RED));
                    }
                }
            }
            case "cache" -> {
                if (value == null) {
                    context.sendMessage(Message.raw("Disk tile cache: " + config.isWebMapDiskCacheEnabled()).color(Color.YELLOW));
                } else {
                    boolean enabled = "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "enable".equalsIgnoreCase(value);
                    boolean disabled = "false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "disable".equalsIgnoreCase(value);
                    if (!enabled && !disabled) {
                        context.sendMessage(Message.raw("Invalid cache value. Use on/off.").color(Color.RED));
                    } else {
                        config.setWebMapDiskCacheEnabled(enabled);
                        context.sendMessage(Message.raw("Disk tile cache " + (enabled ? "enabled" : "disabled") + ".").color(Color.GREEN));
                    }
                }
            }
            case "explored" -> {
                if (value == null) {
                    context.sendMessage(Message.raw("Show only explored chunks: " + config.isWebMapShowOnlyExplored()).color(Color.YELLOW));
                } else {
                    boolean enabled = "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "enable".equalsIgnoreCase(value);
                    boolean disabled = "false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "disable".equalsIgnoreCase(value);
                    if (!enabled && !disabled) {
                        context.sendMessage(Message.raw("Invalid explored value. Use on/off.").color(Color.RED));
                    } else {
                        config.setWebMapShowOnlyExplored(enabled);
                        context.sendMessage(Message.raw("Show only explored chunks " + (enabled ? "enabled" : "disabled") + ".").color(Color.GREEN));
                    }
                }
            }
            case "open", "url" -> context.sendMessage(
                Message.raw("Open WebMap: ").color(Color.YELLOW)
                    .insert(Message.raw(webMapService.getBaseUrl()).color(Color.CYAN).link(webMapService.getBaseUrl()))
            );
            default -> context.sendMessage(Message.raw("Usage: /bm config webmap [status|start|stop|restart|port <n>|scope <global|player>|cache <on|off>|explored <on|off>|open]").color(Color.YELLOW));
        }

        return CompletableFuture.completedFuture(null);
    }

    private void sendStatus(CommandContext context, ModConfig config, WebMapService webMapService) {
        context.sendMessage(Message.raw("=== BetterMap WebMap ===").color(Color.ORANGE));
        context.sendMessage(Message.raw("Enabled: " + config.isWebMapEnabled()).color(Color.YELLOW));
        context.sendMessage(Message.raw("Running: " + webMapService.isRunning()).color(Color.YELLOW));
        context.sendMessage(Message.raw("Port: " + config.getWebMapPort()).color(Color.YELLOW));
        context.sendMessage(Message.raw("Scope: " + config.getWebMapDataMode().name().toLowerCase()).color(Color.YELLOW));
        context.sendMessage(Message.raw("Disk cache: " + config.isWebMapDiskCacheEnabled()).color(Color.YELLOW));
        context.sendMessage(Message.raw("Only explored chunks: " + config.isWebMapShowOnlyExplored()).color(Color.YELLOW));
        context.sendMessage(
            Message.raw("URL: ").color(Color.YELLOW)
                .insert(Message.raw(webMapService.getBaseUrl()).color(Color.CYAN).link(webMapService.getBaseUrl()))
        );
    }
}

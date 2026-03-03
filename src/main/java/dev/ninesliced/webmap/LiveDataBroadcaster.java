package dev.ninesliced.webmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.webmap.auth.WebMapAccessPolicy;
import dev.ninesliced.webmap.auth.WebMapViewer;
import dev.ninesliced.webmap.data.WebViewFilter;
import dev.ninesliced.webmap.data.WorldDataCollector;
import dev.ninesliced.webmap.preload.WebMapPreloadService;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically broadcasts player and marker state to connected websocket clients.
 */
public class LiveDataBroadcaster {
    private static final Gson GSON = new GsonBuilder().create();

    private final WorldDataCollector worldDataCollector;
    private final WebMapPreloadService preloadService;
    private final Map<Channel, WebMapViewer> channels;
    private final Object broadcastLock = new Object();
    private ScheduledExecutorService scheduler;

    public LiveDataBroadcaster(WorldDataCollector worldDataCollector,
                               WebMapPreloadService preloadService) {
        this.worldDataCollector = worldDataCollector;
        this.preloadService = preloadService;
        this.channels = new ConcurrentHashMap<>();
    }

    public void start() {
        stopSchedulerOnly();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BetterMap-WebMap-Broadcaster");
            thread.setDaemon(true);
            return thread;
        });

        long intervalMs = resolveIntervalMs();
        scheduler.scheduleAtFixedRate(this::broadcastSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        stopSchedulerOnly();
        for (Channel channel : channels.keySet()) {
            if (channel.isOpen()) {
                channel.close();
            }
        }
        channels.clear();
    }

    public void addChannel(Channel channel, WebMapViewer viewer) {
        channels.put(channel, viewer);
    }

    public void removeChannel(Channel channel) {
        channels.remove(channel);
    }

    public void broadcastNow() {
        broadcastSafely();
    }

    private void broadcastSafely() {
        synchronized (broadcastLock) {
            broadcast();
        }
    }

    private void broadcast() {
        if (channels.isEmpty()) {
            return;
        }

        for (Map.Entry<Channel, WebMapViewer> entry : channels.entrySet()) {
            Channel channel = entry.getKey();
            if (!channel.isActive()) {
                continue;
            }

            WebMapViewer viewer = entry.getValue();
            WebViewFilter filter = WebMapAccessPolicy.enforceFilter(WebViewFilter.global(), viewer);
            boolean allowGlobalMode = WebMapAccessPolicy.allowGlobalMode(viewer);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "world_update");
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("preload", preloadService.status(viewer));

            Map<String, Object> worlds = new LinkedHashMap<>();
            worldDataCollector.getWorlds().forEach(world -> {
                Object worldName = world.get("name");
                if (worldName instanceof String name) {
                    worlds.put(name, worldDataCollector.buildSnapshot(name, filter, allowGlobalMode));
                }
            });
            payload.put("worlds", worlds);

            channel.writeAndFlush(new TextWebSocketFrame(GSON.toJson(payload)));
        }
    }

    private long resolveIntervalMs() {
        return Math.max(100L, Math.min(250L, ModConfig.getInstance().getUpdateRateMs()));
    }

    private void stopSchedulerOnly() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }
}

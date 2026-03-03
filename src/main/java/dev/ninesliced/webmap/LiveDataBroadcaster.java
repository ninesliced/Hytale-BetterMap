package dev.ninesliced.webmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.webmap.data.WorldDataCollector;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    private final Set<Channel> channels;
    private final Object broadcastLock = new Object();
    private ScheduledExecutorService scheduler;

    public LiveDataBroadcaster(WorldDataCollector worldDataCollector) {
        this.worldDataCollector = worldDataCollector;
        this.channels = ConcurrentHashMap.newKeySet();
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
        for (Channel channel : channels) {
            if (channel.isOpen()) {
                channel.close();
            }
        }
        channels.clear();
    }

    public void addChannel(Channel channel) {
        channels.add(channel);
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "world_update");
        payload.put("timestamp", System.currentTimeMillis());

        Map<String, Object> worlds = new LinkedHashMap<>();
        worldDataCollector.getWorlds().forEach(world -> {
            Object worldName = world.get("name");
            if (worldName instanceof String name) {
                worlds.put(name, worldDataCollector.buildSnapshot(name));
            }
        });
        payload.put("worlds", worlds);

        TextWebSocketFrame frame = new TextWebSocketFrame(GSON.toJson(payload));
        for (Channel channel : channels) {
            if (!channel.isActive()) {
                continue;
            }
            channel.writeAndFlush(frame.retainedDuplicate());
        }
        frame.release();
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

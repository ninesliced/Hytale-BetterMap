package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import dev.ninesliced.commands.bettermap.config.ConfigCommand;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.providers.CaveModeImageBuilder;
import dev.ninesliced.utils.MapImageCompat;
import dev.ninesliced.utils.PlayerRefUtil;
import dev.ninesliced.utils.ReflectionHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Admin benchmark comparing Hytale's default map image generation against BetterMap's
 * reduced-quality generation and the cave image builder, over the same set of chunks.
 *
 * Fairness: before each timed batch the world chunks are pre-loaded through the chunk
 * store (untimed), and an untimed warm-up pass absorbs one-time JIT/worldgen costs, so
 * no pass benefits from chunks the previous pass loaded.
 *
 * The world's imageScale is temporarily altered while the benchmark runs and restored
 * afterwards, so avoid running this on a busy production server.
 */
public class BenchmarkCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(BenchmarkCommand.class.getName());
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private static final int DEFAULT_CHUNKS = 10000;
    private static final int MIN_CHUNKS = 100;
    private static final int MAX_CHUNKS = 25000;
    private static final int SURFACE_BATCH = 256;
    private static final int CAVE_BATCH = 32;
    private static final long BATCH_TIMEOUT_SECONDS = 180;

    private final OptionalArg<Integer> chunksArg =
            this.withOptionalArg("chunks", "Map chunks to benchmark (default 10000)", ArgTypes.INTEGER);

    public BenchmarkCommand() {
        super("benchmark", "Benchmark map generation: Hytale default vs BetterMap vs cave");
        this.requirePermission(ConfigCommand.CONFIG_PERMISSION);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Player player = PlayerRefUtil.fromContext(context);
        World world = player != null ? player.getWorld() : null;
        if (player == null || world == null) {
            context.sendMessage(Message.raw("Could not resolve player/world.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Integer requested = context.get(this.chunksArg);
        int count = Math.max(MIN_CHUNKS, Math.min(MAX_CHUNKS, requested != null ? requested : DEFAULT_CHUNKS));

        if (!RUNNING.compareAndSet(false, true)) {
            context.sendMessage(Message.raw("A benchmark is already running.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw("Benchmark starting over " + count + " map chunks around you."
                + " Map quality is temporarily altered during the run; avoid busy production servers.").color(Color.YELLOW));

        // Capture the player position on the world thread, then run the benchmark on a
        // dedicated thread so neither the world thread nor a command worker is blocked.
        CompletableFuture.runAsync(() -> {
            int centerChunkX = 0;
            int centerChunkZ = 0;
            int playerY = 40;
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    centerChunkX = MathUtil.floor(tc.getPosition().x()) >> 5;
                    centerChunkZ = MathUtil.floor(tc.getPosition().z()) >> 5;
                    playerY = MathUtil.floor(tc.getPosition().y());
                }
            }
            final int cx = centerChunkX;
            final int cz = centerChunkZ;
            final int py = playerY;
            Thread thread = new Thread(() -> {
                try {
                    runBenchmark(context, world, cx, cz, py, count);
                } catch (Throwable t) {
                    LOGGER.warning("Benchmark failed: " + t);
                    context.sendMessage(Message.raw("Benchmark failed: " + t.getMessage()).color(Color.RED));
                } finally {
                    RUNNING.set(false);
                }
            }, "BetterMap-Benchmark");
            thread.setDaemon(true);
            thread.start();
        }, world).exceptionally(ex -> {
            RUNNING.set(false);
            context.sendMessage(Message.raw("Benchmark failed to start: " + ex.getMessage()).color(Color.RED));
            return null;
        });

        return CompletableFuture.completedFuture(null);
    }

    private void runBenchmark(CommandContext context, World world, int centerX, int centerZ, int playerY, int count) {
        WorldMapManager manager = world.getWorldMapManager();
        if (manager == null || !manager.isWorldMapEnabled()) {
            context.sendMessage(Message.raw("World map is not enabled in this world.").color(Color.RED));
            return;
        }

        WorldMapSettings settings = manager.getWorldMapSettings();
        float originalScale = settings.getImageScale();
        ModConfig.MapQuality quality = ModConfig.getInstance().getActiveMapQuality();
        long[] indices = buildChunkIndices(centerX, centerZ, count);

        try {
            // Untimed warm-up: absorbs one-time JIT and worldgen-bootstrap costs so the
            // first timed pass is not penalized.
            context.sendMessage(Message.raw("Warm-up pass...").color(Color.GRAY));
            ReflectionHelper.setFieldValueRecursive(settings, "imageScale", 1.0f);
            manager.clearImages();
            int warmupCount = Math.min(SURFACE_BATCH, indices.length);
            warmChunks(world, indices, 0, warmupCount);
            List<CompletableFuture<MapImage>> warmup = new ArrayList<>(warmupCount);
            for (int i = 0; i < warmupCount; i++) {
                warmup.add(manager.getImageAsync(indices[i]));
            }
            joinAll(warmup);

            RunResult vanilla = runSurfacePass(context, manager, settings, world, indices, 1.0f,
                    "Hytale default (" + imageSize(1.0f) + "px)");
            RunResult betterMap = runSurfacePass(context, manager, settings, world, indices, quality.scale,
                    "BetterMap " + quality + " (" + imageSize(quality.scale) + "px)");

            int caveY = Math.max(16, Math.min(250, playerY));
            CaveModeImageBuilder.clearImageCache();
            RunResult caveCold = runCavePass(context, world, indices, imageSize(quality.scale), caveY,
                    "Cave cold (y=" + caveY + ")");
            RunResult caveWarm = runCavePass(context, world, indices, imageSize(quality.scale), caveY,
                    "Cave cached");

            context.sendMessage(Message.raw("=== Benchmark results: " + indices.length + " map chunks ===").color(Color.CYAN));
            sendResult(context, vanilla);
            sendResult(context, betterMap);
            sendResult(context, caveCold);
            sendResult(context, caveWarm);
            if (vanilla.nanos() > 0 && betterMap.nanos() > 0) {
                double speedup = (double) vanilla.nanos() / betterMap.nanos();
                double payloadRatio = betterMap.payloadBytes() > 0
                        ? (double) vanilla.payloadBytes() / betterMap.payloadBytes() : 0;
                context.sendMessage(Message.raw(String.format(
                        "BetterMap vs default: %.2fx faster, %.1fx smaller payload", speedup, payloadRatio)).color(Color.GREEN));
            }
        } finally {
            ReflectionHelper.setFieldValueRecursive(settings, "imageScale", originalScale);
            manager.clearImages();
            CaveModeImageBuilder.clearImageCache();
        }
    }

    private RunResult runSurfacePass(CommandContext context, WorldMapManager manager, WorldMapSettings settings,
                                     World world, long[] indices, float scale, String label) {
        ReflectionHelper.setFieldValueRecursive(settings, "imageScale", scale);
        manager.clearImages();
        context.sendMessage(Message.raw("Running: " + label).color(Color.GRAY));

        long heapBefore = gcAndGetUsedHeap();
        long cpuBefore = processCpuNanos();
        long generationNanos = 0;
        int success = 0;
        int failed = 0;
        int reportStep = Math.max(SURFACE_BATCH, indices.length / 4);
        int nextReport = reportStep;

        for (int start = 0; start < indices.length; start += SURFACE_BATCH) {
            int end = Math.min(indices.length, start + SURFACE_BATCH);
            // Untimed: pre-load the world chunks so generation never pays chunk loading.
            warmChunks(world, indices, start, end);

            List<CompletableFuture<MapImage>> futures = new ArrayList<>(end - start);
            long t0 = System.nanoTime();
            for (int i = start; i < end; i++) {
                futures.add(manager.getImageAsync(indices[i]));
            }
            joinAll(futures);
            generationNanos += System.nanoTime() - t0;

            for (CompletableFuture<MapImage> f : futures) {
                if (f.getNow(null) != null) {
                    success++;
                } else {
                    failed++;
                }
            }
            if (end >= nextReport) {
                context.sendMessage(Message.raw(label + ": " + end + "/" + indices.length).color(Color.GRAY));
                nextReport += reportStep;
            }
        }

        long cpuAfter = processCpuNanos();
        long heapAfter = gcAndGetUsedHeap();
        int size = imageSize(scale);
        long payloadBytes = (long) success * (23 + 4L * size * size);
        long cpuDelta = (cpuBefore >= 0 && cpuAfter >= 0) ? cpuAfter - cpuBefore : -1;
        return new RunResult(label, success, failed, generationNanos, heapAfter - heapBefore, cpuDelta, payloadBytes);
    }

    private RunResult runCavePass(CommandContext context, World world, long[] indices, int size, int caveY, String label) {
        context.sendMessage(Message.raw("Running: " + label).color(Color.GRAY));

        long heapBefore = gcAndGetUsedHeap();
        long cpuBefore = processCpuNanos();
        long generationNanos = 0;
        int success = 0;
        int failed = 0;
        int reportStep = Math.max(CAVE_BATCH, indices.length / 4);
        int nextReport = reportStep;

        for (int start = 0; start < indices.length; start += CAVE_BATCH) {
            int end = Math.min(indices.length, start + CAVE_BATCH);
            warmChunks(world, indices, start, end);

            List<CompletableFuture<CaveModeImageBuilder>> futures = new ArrayList<>(end - start);
            long t0 = System.nanoTime();
            for (int i = start; i < end; i++) {
                futures.add(CaveModeImageBuilder.build(indices[i], size, size, world, caveY, 12));
            }
            joinAll(futures);
            generationNanos += System.nanoTime() - t0;

            for (CompletableFuture<CaveModeImageBuilder> f : futures) {
                CaveModeImageBuilder builder = f.getNow(null);
                if (builder != null && MapImageCompat.hasPixelData(builder.getImage())) {
                    success++;
                } else {
                    failed++;
                }
            }
            if (end >= nextReport) {
                context.sendMessage(Message.raw(label + ": " + end + "/" + indices.length).color(Color.GRAY));
                nextReport += reportStep;
            }
        }

        long cpuAfter = processCpuNanos();
        long heapAfter = gcAndGetUsedHeap();
        long payloadBytes = (long) success * (23 + 4L * size * size);
        long cpuDelta = (cpuBefore >= 0 && cpuAfter >= 0) ? cpuAfter - cpuBefore : -1;
        return new RunResult(label, success, failed, generationNanos, heapAfter - heapBefore, cpuDelta, payloadBytes);
    }

    private void warmChunks(World world, long[] indices, int start, int end) {
        List<CompletableFuture<?>> futures = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            try {
                futures.add(world.getChunkStore().getChunkReferenceAsync(indices[i]));
            } catch (Exception ignored) {
            }
        }
        joinAll(futures);
    }

    private void joinAll(List<? extends CompletableFuture<?>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> null)
                    .join();
        } catch (Exception ignored) {
        }
    }

    private void sendResult(CommandContext context, RunResult r) {
        double seconds = r.nanos() / 1e9;
        double perSec = seconds > 0 ? r.success() / seconds : 0;
        double avgMs = r.success() > 0 ? (r.nanos() / 1e6) / r.success() : 0;
        StringBuilder sb = new StringBuilder(r.label())
                .append(": ").append(String.format("%.1fs", seconds))
                .append(" | ").append(String.format("%.0f chunks/s", perSec))
                .append(" | ").append(String.format("%.2f ms/chunk", avgMs))
                .append(" | payload ").append(formatMb(r.payloadBytes()))
                .append(" | heap ").append(r.heapDeltaBytes() >= 0 ? "+" : "").append(formatMb(r.heapDeltaBytes()));
        if (r.cpuDeltaNanos() >= 0) {
            sb.append(" | cpu ").append(String.format("%.1fs", r.cpuDeltaNanos() / 1e9));
        }
        if (r.failed() > 0) {
            sb.append(" | failed ").append(r.failed());
        }
        context.sendMessage(Message.raw(sb.toString()).color(Color.WHITE));
    }

    private static long[] buildChunkIndices(int centerX, int centerZ, int count) {
        int side = (int) Math.ceil(Math.sqrt(count));
        long[] result = new long[count];
        int i = 0;
        int half = side / 2;
        for (int dx = -half; dx < side - half && i < count; dx++) {
            for (int dz = -half; dz < side - half && i < count; dz++) {
                result[i++] = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(centerX + dx, centerZ + dz);
            }
        }
        return result;
    }

    private static int imageSize(float scale) {
        return MathUtil.fastFloor(32.0F * scale);
    }

    private static String formatMb(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static long gcAndGetUsedHeap() {
        System.gc();
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static long processCpuNanos() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                return sunOs.getProcessCpuTime();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private record RunResult(String label, int success, int failed, long nanos,
                             long heapDeltaBytes, long cpuDeltaNanos, long payloadBytes) {}
}

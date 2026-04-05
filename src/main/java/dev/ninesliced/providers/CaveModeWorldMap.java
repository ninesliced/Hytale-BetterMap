package dev.ninesliced.providers;

import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMapSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.map.WorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * World map provider for cave mode.
 * This generates map images at a specific Y level slice instead of top-down surface view.
 */
public class CaveModeWorldMap implements IWorldMap {
    private static final Logger LOGGER = Logger.getLogger(CaveModeWorldMap.class.getName());

    private final Player player;
    private final int targetYLevel;
    private final int verticalRange;

    /**
     * Creates a cave mode world map for a player.
     *
     * @param player        The player.
     * @param yLevel        The Y level to sample around.
     * @param verticalRange The range above and below.
     */
    public CaveModeWorldMap(@Nonnull Player player, int yLevel, int verticalRange) {
        this.player = player;
        this.targetYLevel = yLevel;
        this.verticalRange = verticalRange;
    }

    @Nonnull
    @Override
    public WorldMapSettings getWorldMapSettings() {
        UpdateWorldMapSettings settingsPacket = new UpdateWorldMapSettings();
        settingsPacket.defaultScale = 64.0F;
        settingsPacket.minScale = 16.0F;
        settingsPacket.maxScale = 128.0F;
        return new WorldMapSettings(null, 3.0F, 2.0F, 3, 32, settingsPacket);
    }

    @Nonnull
    @Override
    public CompletableFuture<WorldMap> generate(World world, int imageWidth, int imageHeight, @Nonnull LongSet chunksToGenerate) {
        CompletableFuture<CaveModeImageBuilder>[] futures = new CompletableFuture[chunksToGenerate.size()];
        int futureIndex = 0;
        LongIterator iterator = chunksToGenerate.iterator();

        while (iterator.hasNext()) {
            futures[futureIndex++] = CaveModeImageBuilder.build(
                    iterator.nextLong(),
                    imageWidth,
                    imageHeight,
                    world,
                    targetYLevel,
                    verticalRange
            );
        }

        return CompletableFuture.allOf(futures).thenApply(unused -> {
            WorldMap worldMap = new WorldMap(futures.length);

            for (CompletableFuture<CaveModeImageBuilder> future : futures) {
                CaveModeImageBuilder builder = future.getNow(null);
                if (builder != null) {
                    worldMap.getChunks().put(builder.getIndex(), builder.getImage());
                }
            }

            return worldMap;
        });
    }

    @Nonnull
    @Override
    public CompletableFuture<Map<String, MapMarker>> generatePointsOfInterest(World world) {
        return CompletableFuture.completedFuture(Collections.emptyMap());
    }
}

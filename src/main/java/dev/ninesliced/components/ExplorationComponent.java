package dev.ninesliced.components;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.LongArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;

/**
 * Component that stores the exploration data for a player.
 * It tracks which chunks have been explored.
 */
public class ExplorationComponent implements Component<EntityStore> {

    /**
     * Codec for serializing and deserializing the ExplorationComponent.
     */
    public static final BuilderCodec<ExplorationComponent> CODEC = BuilderCodec.builder(ExplorationComponent.class, ExplorationComponent::new)
            .append(
                    new KeyedCodec<>("ExploredChunks", new LongArrayCodec()),
                    (component, chunks) -> {
                        if (chunks != null) {
                            component.exploredChunks = new LongOpenHashSet(chunks);
                        } else {
                            component.exploredChunks = new LongOpenHashSet();
                        }
                    },
                    component -> component.exploredChunks.toLongArray()
            )
            .add()
            .build();

    private LongSet exploredChunks = new LongOpenHashSet();

    /**
     * Constructs a new ExplorationComponent.
     */
    public ExplorationComponent() {
    }

    /**
     * Gets the set of explored chunk indices.
     *
     * @return The set of explored chunk indices.
     */
    public LongSet getExploredChunks() {
        return exploredChunks;
    }

    /**
     * Marks a chunk as explored.
     *
     * @param chunkIndex The index of the chunk to mark as explored.
     * @return true if the chunk was newly added, false if it was already explored.
     */
    public boolean addExploredChunk(long chunkIndex) {
        return exploredChunks.add(chunkIndex);
    }

    /**
     * Checks if a chunk is explored.
     *
     * @param chunkIndex The index of the chunk.
     * @return True if the chunk is explored, false otherwise.
     */
    public boolean isExplored(long chunkIndex) {
        return exploredChunks.contains(chunkIndex);
    }

    /**
     * Creates a clone of this component.
     *
     * @return A deep copy of the component.
     */
    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        ExplorationComponent clone = new ExplorationComponent();
        clone.exploredChunks = new LongOpenHashSet(this.exploredChunks);
        return clone;
    }
}

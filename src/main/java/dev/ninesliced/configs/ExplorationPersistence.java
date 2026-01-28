package dev.ninesliced.configs;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.utils.ChunkUtil;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles persistence of exploration data to disk.
 */
public class ExplorationPersistence {

    private static final Logger LOGGER = Logger.getLogger(ExplorationPersistence.class.getName());
    private static final int DATA_VERSION = 1;

    private final Path storageDir;

    /**
     * Initializes the persistence manager, setting up the storage directory.
     */
    public ExplorationPersistence() {
        Path serverRoot = Paths.get(".").toAbsolutePath().normalize();
        this.storageDir = serverRoot.resolve("mods").resolve("BetterMap").resolve("Data");

        LOGGER.info("Exploration storage root directory: " + this.storageDir.toString());
        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to create exploration data directory: " + e.getMessage());
        }
    }

    /**
     * Loads exploration data for a player in a specific world.
     *
     * @param player    The player to load data for.
     * @param worldName The name of the world to load data from.
     */
    public void load(@Nonnull Player player, @Nonnull String worldName) {
        UUID playerUUID = ((CommandSender) player).getUuid();
        if (playerUUID == null)
            return;

        Path worldDir = storageDir.resolve(worldName);
        Path file = worldDir.resolve(playerUUID + ".bin");

        if (!Files.exists(file)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version != DATA_VERSION) {
                LOGGER.warning("Unknown data version for player " + player.getDisplayName() + ": " + version);
            }

            int count = in.readInt();
            Set<Long> loadedChunks = new HashSet<>(count);

            for (int i = 0; i < count; i++) {
                loadedChunks.add(in.readLong());
            }

            ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            data.getExploredChunks().markChunksExplored(loadedChunks);

            for (long chunkIdx : loadedChunks) {
                int x = ChunkUtil.indexToChunkX(chunkIdx);
                int z = ChunkUtil.indexToChunkZ(chunkIdx);

                data.getMapExpansion().updateBoundaries(x, z, 0);
            }

            LOGGER.info("Loaded " + count + " explored chunks for " + player.getDisplayName() + " in world " + worldName);

        } catch (IOException e) {
            LOGGER.severe("Failed to load exploration data for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Saves exploration data for a player in their current world.
     *
     * @param player The player to save data for.
     */
    public void save(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            UUIDComponent uuidComp = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComp != null && player.getWorld() != null) {
                save(player.getDisplayName(), uuidComp.getUuid(), player.getWorld().getName());
            }
        }
    }

    /**
     * Saves exploration data for all players in the server.
     */
    public void saveAllPlayers() {
        Universe universe = Universe.get();
        if (universe == null) return;

        universe.getWorlds().values().forEach(world -> {
            try {
                world.execute(() -> {
                    LOGGER.info("Saving exploration data for world: " + world.getName());
                    world.getPlayerRefs().forEach(playerRef -> {
                        LOGGER.info(" - Saving player: " + playerRef);
                        Player player = playerRef.getHolder().getComponent(Player.getComponentType());
                        if (player != null) {
                            String playerName = player.getDisplayName();
                            UUID uuid = ((CommandSender) player).getUuid();
                            String worldName = world.getName();

                            ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(playerName);
                            if (data != null && uuid != null) {
                                Set<Long> chunks = data.getExploredChunks().getExploredChunks();
                                java.util.concurrent.ForkJoinPool.commonPool().execute(() -> 
                                    save(playerName, uuid, worldName, chunks)
                                );
                                LOGGER.info("Saved exploration data for player: " + playerName);
                            }
                        }
                    });
                });
            } catch (Exception _) {}
        });
    }

    /**
     * Saves exploration data specifically given player details and world name.
     *
     * @param playerName The name of the player.
     * @param playerUUID The UUID of the player.
     * @param worldName  The name of the world.
     */
    public void save(String playerName, UUID playerUUID, @Nonnull String worldName) {
        if (playerUUID == null) {
            LOGGER.warning("Cannot save data: Player UUID is null for " + playerName);
            return;
        }

        ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(playerName);
        if (data == null) {
            return;
        }

        save(playerName, playerUUID, worldName, data.getExploredChunks().getExploredChunks());
    }

    public void save(String playerName, UUID playerUUID, @Nonnull String worldName, Set<Long> chunks) {
        Path worldDir = storageDir.resolve(worldName);
        try {
            if (!Files.exists(worldDir)) {
                Files.createDirectories(worldDir);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to create world exploration directory: " + e.getMessage());
            return;
        }

        Path file = worldDir.resolve(playerUUID.toString() + ".bin");
        LOGGER.info("[DEBUG] Saving " + chunks.size() + " chunks for " + playerName + " in world " + worldName);

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(DATA_VERSION);
            out.writeInt(chunks.size());

            for (Long chunk : chunks) {
                out.writeLong(chunk);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to save exploration data for " + playerName + ": " + e.getMessage());
        }
    }

    /**
     * Loads chunks from all player files in the specified world folder.
     *
     * @param worldName The name of the world.
     * @return A set of all explored chunk indices.
     */
    public Set<Long> loadAllChunks(@Nonnull String worldName) {
        Set<Long> allChunks = new HashSet<>();
        Path worldDir = storageDir.resolve(worldName);

        if (!Files.exists(worldDir)) {
            return allChunks;
        }

        try (java.util.stream.Stream<Path> stream = Files.list(worldDir)) {
            stream.filter(path -> path.toString().endsWith(".bin")).forEach(file -> {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                    int version = in.readInt();
                    if (version == DATA_VERSION) {
                        int count = in.readInt();
                        for (int i = 0; i < count; i++) {
                            allChunks.add(in.readLong());
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warning("Failed to load chunk data from " + file.getFileName() + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            LOGGER.severe("Failed to list files in " + worldDir + ": " + e.getMessage());
        }

        return allChunks;
    }
}

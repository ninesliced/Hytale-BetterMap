package dev.ninesliced.configs;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ninesliced.managers.CaveModeManager;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Handles persistence of cave exploration data to disk.
 * Cave data is stored per-player, per-world as a flat set of explored chunk indices.
 * Files are stored alongside base exploration data as cave-{playerUUID}.bin
 */
public class CavePersistence {

    private static final Logger LOGGER = Logger.getLogger(CavePersistence.class.getName());
    private static final int DATA_VERSION = 2;
    private static final String CAVE_FILE_PREFIX = "cave-";

    private final Path storageDir;

    private final Map<String, Set<Long>> loadAllChunksCache = new ConcurrentHashMap<>();
    private final Set<String> dirtyWorlds = ConcurrentHashMap.newKeySet();

    /**
     * Dedicated single-thread executor for save I/O to avoid contention on ForkJoinPool.commonPool().
     */
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BetterMap-CaveSave");
        t.setDaemon(true);
        return t;
    });

    /**
     * Initializes the cave persistence manager, setting up the storage directory.
     */
    public CavePersistence() {
        Path serverRoot = Paths.get(".").toAbsolutePath().normalize();
        this.storageDir = serverRoot.resolve("mods").resolve("BetterMap").resolve("Data");

        LOGGER.info("Cave exploration storage directory: " + this.storageDir.toString());
        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to create cave exploration data directory: " + e.getMessage());
        }
    }

    /**
     * Loads cave exploration data for a player in a specific world.
     *
     * @param player    The player to load data for.
     * @param worldName The name of the world to load data from.
     */
    public void load(@Nonnull Player player, @Nonnull String worldName) {
        UUID playerUUID = ((CommandSender) player).getUuid();
        if (playerUUID == null)
            return;

        Path worldDir = storageDir.resolve(worldName);
        Path file = worldDir.resolve(CAVE_FILE_PREFIX + playerUUID + ".bin");

        if (!Files.exists(file)) {
            LOGGER.fine("No cave data file found for " + player.getDisplayName() + " in " + worldName);
            return;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            
            if (version != DATA_VERSION) {
                LOGGER.warning("Incompatible cave data version for player " + player.getDisplayName() + ": " + version + " (expected " + DATA_VERSION + ")");
                return;
            }
            
            CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getOrCreateState(player);
            
            int chunkCount = in.readInt();
            Set<Long> chunks = state.getExploredCaveChunks();
            for (int i = 0; i < chunkCount; i++) {
                chunks.add(in.readLong());
            }
            LOGGER.info("Loaded " + chunkCount + " cave chunks for " + player.getDisplayName() + " in world " + worldName);

        } catch (IOException e) {
            LOGGER.severe("Failed to load cave exploration data for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Saves cave exploration data for a player in their current world.
     *
     * @param player The player to save data for.
     */
    public void save(@Nonnull Player player) {
        if (player.getWorld() == null) return;
        
        UUID uuid = ((CommandSender) player).getUuid();
        if (uuid == null) return;
        
        save(player.getDisplayName(), uuid, player.getWorld().getName());
    }

    /**
     * Saves cave exploration data for all players in the server.
     */
    public void saveAllPlayers() {
        Universe universe = Universe.get();
        if (universe == null) return;

        universe.getWorlds().values().forEach(world -> {
            try {
                world.execute(() -> {
                    LOGGER.fine("Saving cave exploration data for world: " + world.getName());
                    world.getPlayerRefs().forEach(playerRef -> {
                        Player player = playerRef.getComponent(Player.getComponentType());
                        if (player != null) {
                            String playerName = player.getDisplayName();
                            UUID uuid = ((CommandSender) player).getUuid();
                            String worldName = world.getName();

                            CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
                            if (state != null && uuid != null) {
                                Set<Long> chunks = new HashSet<>(state.getExploredCaveChunks());
                                
                                if (!chunks.isEmpty()) {
                                    saveExecutor.execute(() -> 
                                        save(playerName, uuid, worldName, chunks)
                                    );
                                }
                            }
                        }
                    });
                });
            } catch (Exception e) {
                LOGGER.warning("Error saving cave data for world: " + e.getMessage());
            }
        });
    }

    /**
     * Saves cave exploration data for a specific player in a world.
     *
     * @param playerName The name of the player.
     * @param playerUUID The UUID of the player.
     * @param worldName  The name of the world.
     */
    public void save(String playerName, UUID playerUUID, @Nonnull String worldName) {
        if (playerUUID == null) {
            LOGGER.warning("Cannot save cave data: Player UUID is null for " + playerName);
            return;
        }

        CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getStateByName(playerName);
        if (state == null) {
            return;
        }

        Set<Long> chunks = new HashSet<>(state.getExploredCaveChunks());
        
        if (!chunks.isEmpty()) {
            save(playerName, playerUUID, worldName, chunks);
        }
    }

    /**
     * Saves cave exploration data with provided chunk data.
     *
     * @param playerName The player name.
     * @param playerUUID The player UUID.
     * @param worldName  The world name.
     * @param chunks     Set of explored chunk indices.
     */
    public void save(String playerName, UUID playerUUID, @Nonnull String worldName, Set<Long> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        Path worldDir = storageDir.resolve(worldName);
        try {
            if (!Files.exists(worldDir)) {
                Files.createDirectories(worldDir);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to create world cave directory: " + e.getMessage());
            return;
        }

        Path file = worldDir.resolve(CAVE_FILE_PREFIX + playerUUID.toString() + ".bin");
        
        LOGGER.fine("[CAVE SAVE] Saving " + chunks.size() + " cave chunks for " + playerName + " in " + worldName);

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(DATA_VERSION);
            out.writeInt(chunks.size());
            
            for (Long chunk : chunks) {
                out.writeLong(chunk);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to save cave exploration data for " + playerName + ": " + e.getMessage());
        }
        dirtyWorlds.add(worldName);
    }

    /**
     * Shuts down the dedicated save I/O executor.
     */
    public void shutdown() {
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warning("Cave save executor did not terminate in time, forcing shutdown...");
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Loads all cave chunks from all player files in the specified world folder.
     *
     * @param worldName The name of the world.
     * @return A set of all explored cave chunk indices.
     */
    public Set<Long> loadAllChunks(@Nonnull String worldName) {
        boolean isDirty = dirtyWorlds.remove(worldName);
        if (!isDirty) {
            Set<Long> cached = loadAllChunksCache.get(worldName);
            if (cached != null) {
                return cached;
            }
        }

        Set<Long> allChunks = new HashSet<>();
        Path worldDir = storageDir.resolve(worldName);

        if (!Files.exists(worldDir)) {
            loadAllChunksCache.put(worldName, allChunks);
            return allChunks;
        }

        try (java.util.stream.Stream<Path> stream = Files.list(worldDir)) {
            stream.filter(path -> path.getFileName().toString().startsWith(CAVE_FILE_PREFIX) && path.toString().endsWith(".bin")).forEach(file -> {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                    int version = in.readInt();
                    if (version == DATA_VERSION) {
                        int chunkCount = in.readInt();
                        for (int i = 0; i < chunkCount; i++) {
                            allChunks.add(in.readLong());
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warning("Failed to load cave data from " + file.getFileName() + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            LOGGER.severe("Failed to list files in " + worldDir + ": " + e.getMessage());
        }

        loadAllChunksCache.put(worldName, allChunks);
        return allChunks;
    }
}

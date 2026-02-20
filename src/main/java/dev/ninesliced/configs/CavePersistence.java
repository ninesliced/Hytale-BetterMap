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
import java.util.Set;
import java.util.UUID;
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
                                    java.util.concurrent.ForkJoinPool.commonPool().execute(() -> 
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
        
        LOGGER.info("[CAVE SAVE] Saving " + chunks.size() + " cave chunks for " + playerName + " in " + worldName);

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(DATA_VERSION);
            out.writeInt(chunks.size());
            
            for (Long chunk : chunks) {
                out.writeLong(chunk);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to save cave exploration data for " + playerName + ": " + e.getMessage());
        }
    }

    /**
     * Loads all cave chunks from all player files in the specified world folder.
     *
     * @param worldName The name of the world.
     * @return A set of all explored cave chunk indices.
     */
    public Set<Long> loadAllChunks(@Nonnull String worldName) {
        Set<Long> allChunks = new HashSet<>();
        Path worldDir = storageDir.resolve(worldName);

        if (!Files.exists(worldDir)) {
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

        return allChunks;
    }

    /**
     * Deletes all persisted cave exploration files for all worlds.
     *
     * @return Number of deleted files.
     */
    public int clearAllData() {
        if (!Files.exists(storageDir)) {
            return 0;
        }

        int deleted = 0;
        try (java.util.stream.Stream<Path> worldDirs = Files.list(storageDir)) {
            for (Path worldDir : (Iterable<Path>) worldDirs::iterator) {
                if (!Files.isDirectory(worldDir)) {
                    continue;
                }

                try (java.util.stream.Stream<Path> files = Files.list(worldDir)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        String name = file.getFileName().toString();
                        if (!name.startsWith(CAVE_FILE_PREFIX) || !name.endsWith(".bin")) {
                            continue;
                        }
                        try {
                            Files.deleteIfExists(file);
                            deleted++;
                        } catch (IOException e) {
                            LOGGER.warning("Failed deleting cave exploration file " + file + ": " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warning("Failed listing cave files in " + worldDir + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Failed listing cave storage directory " + storageDir + ": " + e.getMessage());
        }

        LOGGER.info("Deleted " + deleted + " persisted cave exploration file(s)");
        return deleted;
    }

    /**
     * Deletes all persisted cave exploration files for one player across all worlds.
     *
     * @param playerUUID The player UUID.
     * @return Number of deleted files.
     */
    public int clearPlayerData(@Nonnull UUID playerUUID) {
        if (!Files.exists(storageDir)) {
            return 0;
        }

        int deleted = 0;
        String targetFile = CAVE_FILE_PREFIX + playerUUID + ".bin";

        try (java.util.stream.Stream<Path> worldDirs = Files.list(storageDir)) {
            for (Path worldDir : (Iterable<Path>) worldDirs::iterator) {
                if (!Files.isDirectory(worldDir)) {
                    continue;
                }

                Path file = worldDir.resolve(targetFile);
                try {
                    if (Files.deleteIfExists(file)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    LOGGER.warning("Failed deleting cave exploration file " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Failed listing cave storage directory " + storageDir + ": " + e.getMessage());
        }

        return deleted;
    }

    /**
     * Lists all player UUIDs that have persisted cave exploration data.
     */
    @Nonnull
    public Set<UUID> listSavedPlayerUuids() {
        Set<UUID> uuids = new HashSet<>();
        if (!Files.exists(storageDir)) {
            return uuids;
        }

        try (java.util.stream.Stream<Path> worldDirs = Files.list(storageDir)) {
            for (Path worldDir : (Iterable<Path>) worldDirs::iterator) {
                if (!Files.isDirectory(worldDir)) {
                    continue;
                }

                try (java.util.stream.Stream<Path> files = Files.list(worldDir)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        String name = file.getFileName().toString();
                        if (!name.startsWith(CAVE_FILE_PREFIX) || !name.endsWith(".bin")) {
                            continue;
                        }

                        String uuidPart = name.substring(CAVE_FILE_PREFIX.length(), name.length() - 4);
                        try {
                            uuids.add(UUID.fromString(uuidPart));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warning("Failed listing cave files in " + worldDir + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Failed listing cave storage directory " + storageDir + ": " + e.getMessage());
        }

        return uuids;
    }
}

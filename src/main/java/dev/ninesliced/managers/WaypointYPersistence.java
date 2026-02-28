package dev.ninesliced.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Persists waypoint Y coordinates separately from Hytale's built-in UserMapMarker store.
 * Hytale currently serializes user markers with X/Z only, so Y needs a sidecar file.
 */
public final class WaypointYPersistence {
    private static final Logger LOGGER = Logger.getLogger(WaypointYPersistence.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, Double>>() { }.getType();

    private static final WaypointYPersistence INSTANCE = new WaypointYPersistence();

    private final Map<Path, Map<String, Double>> cacheByFile = new HashMap<>();
    private Path dataRoot;

    private WaypointYPersistence() {
    }

    public static WaypointYPersistence getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(@Nonnull Path configDir) {
        this.dataRoot = configDir.resolve("Data");

        try {
            if (!Files.exists(dataRoot)) {
                Files.createDirectories(dataRoot);
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to create waypoint Y data directory: " + e.getMessage());
        }

        cacheByFile.clear();
    }

    public synchronized void setPersonalY(@Nonnull String worldName, @Nonnull UUID playerUuid, @Nonnull String markerId, double y) {
        Path file = getPersonalFile(worldName, playerUuid);
        Map<String, Double> values = loadFile(file);
        values.put(markerId, y);
        saveFile(file, values);
    }

    public synchronized void setGlobalY(@Nonnull String worldName, @Nonnull String markerId, double y) {
        Path file = getGlobalFile(worldName);
        Map<String, Double> values = loadFile(file);
        values.put(markerId, y);
        saveFile(file, values);
    }

    @Nullable
    public synchronized Double getPersonalY(@Nonnull String worldName, @Nonnull UUID playerUuid, @Nonnull String markerId) {
        Path file = getPersonalFile(worldName, playerUuid);
        return loadFile(file).get(markerId);
    }

    @Nullable
    public synchronized Double getGlobalY(@Nonnull String worldName, @Nonnull String markerId) {
        Path file = getGlobalFile(worldName);
        return loadFile(file).get(markerId);
    }

    public synchronized void removePersonal(@Nonnull String worldName, @Nonnull UUID playerUuid, @Nonnull String markerId) {
        Path file = getPersonalFile(worldName, playerUuid);
        Map<String, Double> values = loadFile(file);
        if (values.remove(markerId) != null) {
            saveFile(file, values);
        }
    }

    public synchronized void removeGlobal(@Nonnull String worldName, @Nonnull String markerId) {
        Path file = getGlobalFile(worldName);
        Map<String, Double> values = loadFile(file);
        if (values.remove(markerId) != null) {
            saveFile(file, values);
        }
    }

    private Path getWorldDir(@Nonnull String worldName) {
        Path root = dataRoot != null ? dataRoot : Path.of(".").toAbsolutePath().normalize().resolve("mods").resolve("BetterMap").resolve("Data");
        return root.resolve(worldName);
    }

    private Path getPersonalFile(@Nonnull String worldName, @Nonnull UUID playerUuid) {
        return getWorldDir(worldName).resolve(playerUuid + "_waypoint_y.json");
    }

    private Path getGlobalFile(@Nonnull String worldName) {
        return getWorldDir(worldName).resolve("global_waypoint_y.json");
    }

    @Nonnull
    private Map<String, Double> loadFile(@Nonnull Path file) {
        Map<String, Double> cached = cacheByFile.get(file);
        if (cached != null) {
            return cached;
        }

        Map<String, Double> loaded = new HashMap<>();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Map<String, Double> fromDisk = GSON.fromJson(reader, FILE_TYPE);
                if (fromDisk != null) {
                    loaded.putAll(fromDisk);
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to load waypoint Y file " + file + ": " + e.getMessage());
            }
        }

        cacheByFile.put(file, loaded);
        return loaded;
    }

    private void saveFile(@Nonnull Path file, @Nonnull Map<String, Double> values) {
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to prepare waypoint Y storage directory: " + e.getMessage());
            return;
        }

        if (values.isEmpty()) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOGGER.warning("Failed to delete empty waypoint Y file " + file + ": " + e.getMessage());
            }
            cacheByFile.put(file, values);
            return;
        }

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(values, FILE_TYPE, writer);
        } catch (Exception e) {
            LOGGER.warning("Failed to save waypoint Y file " + file + ": " + e.getMessage());
        }

        cacheByFile.put(file, values);
    }
}
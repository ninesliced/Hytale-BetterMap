package dev.ninesliced.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages loading and saving of player-specific configurations.
 */
public class PlayerConfigManager {
    private static final Logger LOGGER = Logger.getLogger(PlayerConfigManager.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PlayerConfigManager INSTANCE;

    private final Path configDir;
    private final Map<UUID, PlayerConfig> playerConfigs = new ConcurrentHashMap<>();

    private PlayerConfigManager(Path rootDir) {
        this.configDir = rootDir.resolve("player_configs");
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to create player_configs directory: " + e.getMessage());
        }
    }

    public static synchronized void initialize(Path rootDir) {
        if (INSTANCE == null) {
            INSTANCE = new PlayerConfigManager(rootDir);
        }
    }

    public static PlayerConfigManager getInstance() {
        return INSTANCE;
    }

    public PlayerConfig getPlayerConfig(UUID uuid) {
        if (!playerConfigs.containsKey(uuid)) {
            loadPlayerConfig(uuid);
        }
        return playerConfigs.get(uuid);
    }

    public void loadPlayerConfig(UUID uuid) {
        Path configFile = configDir.resolve(uuid.toString() + ".json");
        PlayerConfig config = null;

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                config = GSON.fromJson(reader, PlayerConfig.class);
                if (config != null) {
                    config.setPlayerUuid(uuid);
                }
            } catch (IOException e) {
                LOGGER.warning("Failed to load config for player " + uuid + ", using defaults: " + e.getMessage());
            }
        }

        if (config == null) {
            config = createDefaultConfig(uuid);
        }

        playerConfigs.put(uuid, config);
    }

    public void savePlayerConfig(UUID uuid) {
        PlayerConfig config = playerConfigs.get(uuid);
        if (config == null) return;

        Path configFile = configDir.resolve(uuid.toString() + ".json");
        Path tempFile = configDir.resolve(uuid.toString() + ".json.tmp");
        try (Writer writer = Files.newBufferedWriter(tempFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to write config for player " + uuid + ": " + e.getMessage());
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            return;
        }
        try {
            Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                LOGGER.warning("Failed to finalize config for player " + uuid + ": " + ex.getMessage());
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to finalize config for player " + uuid + ": " + e.getMessage());
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
        }
    }

    public void unloadPlayerConfig(UUID uuid) {
        savePlayerConfig(uuid);
        playerConfigs.remove(uuid);
    }

    public boolean hasPoiPrivacyOverrides() {
        for (PlayerConfig config : playerConfigs.values()) {
            if (config == null) {
                continue;
            }
            if (config.isHideAllPoiOnMap()
                || config.isHideSpawnOnMap()
                || config.isHideDeathMarkerOnMap()) {
                return true;
            }
            List<String> hidden = config.getHiddenPoiNames();
            if (hidden != null && !hidden.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasWarpPrivacyOverrides() {
        for (PlayerConfig config : playerConfigs.values()) {
            if (config == null) {
                continue;
            }
            if (config.isHideAllWarpsOnMap() || config.isHideOtherWarpsOnMap()) {
                return true;
            }
        }
        return false;
    }

    private PlayerConfig createDefaultConfig(UUID uuid) {
        ModConfig mainConfig = ModConfig.getInstance();
        return new PlayerConfig(uuid,
                mainConfig.getMinScale(),
                mainConfig.getMaxScale(),
                false
        );
    }
}

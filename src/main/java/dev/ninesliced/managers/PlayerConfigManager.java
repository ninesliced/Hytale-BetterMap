package dev.ninesliced.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Nullable
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
        LOGGER.info("Loaded config for player: " + uuid);
    }

    public void savePlayerConfig(UUID uuid) {
        PlayerConfig config = playerConfigs.get(uuid);
        if (config == null) return;

        Path configFile = configDir.resolve(uuid.toString() + ".json");
        try (Writer writer = Files.newBufferedWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save config for player " + uuid + ": " + e.getMessage());
        }
    }

    public void unloadPlayerConfig(UUID uuid) {
        savePlayerConfig(uuid);
        playerConfigs.remove(uuid);
        LOGGER.info("Unloaded config for player: " + uuid);
    }

    private PlayerConfig createDefaultConfig(UUID uuid) {
        BetterMapConfig mainConfig = BetterMapConfig.getInstance();
        return new PlayerConfig(uuid,
                mainConfig.getMinScale(),
                mainConfig.getMaxScale(),
                mainConfig.isLocationEnabled()
        );
    }
}

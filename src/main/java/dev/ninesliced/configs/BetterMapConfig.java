package dev.ninesliced.configs;

import com.google.gson.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration manager for the BetterMap mod.
 * Handles loading, saving, and accessing configuration settings.
 */
public class BetterMapConfig {
    private static final Logger LOGGER = Logger.getLogger(BetterMapConfig.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BetterMapConfig INSTANCE;

    private int explorationRadius = 16;
    private int updateRateMs = 500;
    private MapQuality mapQuality = MapQuality.MEDIUM;
    private float minScale = 10.0f;
    private float maxScale = 256.0f;
    private boolean debug = false;
    private boolean locationEnabled = true;

    private transient Path configPath;
    private transient MapQuality activeMapQuality;

    /**
     * Private constructor to enforce singleton pattern.
     */
    public BetterMapConfig() {
    }

    /**
     * Gets the singleton instance of BetterMapConfig.
     *
     * @return The instance.
     */
    public static synchronized BetterMapConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BetterMapConfig();
        }
        return INSTANCE;
    }

    /**
     * Initializes the configuration manager with the server root path.
     *
     * @param rootPath The root directory of the server.
     */
    public void initialize(Path rootPath) {
        Path configDir = rootPath.resolve("mods").resolve("BetterMap");
        this.configPath = configDir.resolve("config.json");

        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (Files.exists(configPath)) {
                load();
            } else {
                save();
            }

            this.activeMapQuality = this.mapQuality;
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize configuration: " + e.getMessage());
        }
    }

    /**
     * Loads the configuration from disk.
     */
    public void load() {
        try (Reader reader = Files.newBufferedReader(configPath)) {
            JsonElement element = JsonParser.parseReader(reader);

            if (element.isJsonObject()) {
                JsonObject jsonObject = element.getAsJsonObject();
                BetterMapConfig loaded = GSON.fromJson(element, BetterMapConfig.class);

                boolean needsSave = false;

                if (loaded != null) {
                    this.explorationRadius = loaded.explorationRadius;
                    this.updateRateMs = loaded.updateRateMs;

                    if (jsonObject.has("minScale")) {
                        this.minScale = loaded.minScale;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("maxScale")) {
                        this.maxScale = loaded.maxScale;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("mapQuality")) {
                        this.mapQuality = loaded.mapQuality;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("debug")) {
                        this.debug = loaded.debug;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("locationEnabled")) {
                        this.locationEnabled = loaded.locationEnabled;
                    } else {
                        needsSave = true;
                    }

                    if (needsSave) {
                        save();
                    }

                    updateLoggers();
                    LOGGER.info("Configuration loaded from " + configPath);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to load configuration: " + e.getMessage());
            this.mapQuality = MapQuality.MEDIUM;
            updateLoggers();
        }
    }

    /**
     * Updates the logger levels based on the debug setting.
     */
    private void updateLoggers() {
        Level level = debug ? Level.ALL : Level.OFF;

        setLoggerLevel("dev.ninesliced", level);

        setLoggerLevel("dev.ninesliced.commands", level);
        setLoggerLevel("dev.ninesliced.exploration", level);

        setLoggerLevel("dev.ninesliced.listeners.ExplorationEventListener", level);
        setLoggerLevel("dev.ninesliced.utils.WorldMapHook", level);
        setLoggerLevel("dev.ninesliced.configs.ExplorationPersistence", level);
    }

    private void setLoggerLevel(String loggerName, Level level) {
        Logger logger = Logger.getLogger(loggerName);
        logger.setLevel(level);
    }

    /**
     * Saves the current configuration to disk.
     */
    public void save() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(this, writer);
            LOGGER.info("Configuration saved to " + configPath);
        } catch (IOException e) {
            LOGGER.severe("Failed to save configuration: " + e.getMessage());
        }
    }

    /**
     * Reloads the configuration from disk if it exists.
     */
    public void reload() {
        if (configPath != null && Files.exists(configPath)) {
            load();
        }
    }

    /**
     * Gets the exploration radius in chunks.
     *
     * @return The exploration radius.
     */
    public int getExplorationRadius() {
        return explorationRadius;
    }

    /**
     * Gets the update rate in milliseconds.
     *
     * @return The update rate.
     */
    public int getUpdateRateMs() {
        return updateRateMs;
    }

    /**
     * Gets the configured map quality.
     *
     * @return The map quality.
     */
    public MapQuality getMapQuality() {
        return mapQuality;
    }

    /**
     * Gets the currently active map quality (may differ from config if restart is pending).
     *
     * @return The active map quality.
     */
    public MapQuality getActiveMapQuality() {
        return activeMapQuality != null ? activeMapQuality : mapQuality;
    }

    /**
     * Gets the minimum map scale.
     *
     * @return The minimum scale.
     */
    public float getMinScale() {
        return minScale;
    }

    /**
     * Sets the minimum map scale and saves the config.
     *
     * @param minScale The new minimum scale.
     */
    public void setMinScale(float minScale) {
        this.minScale = minScale;
        save();
    }

    /**
     * Checks if debug mode is enabled.
     *
     * @return True if debug mode is on.
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Sets the debug mode and updates loggers.
     *
     * @param debug The new debug state.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
        updateLoggers();
        save();
    }

    /**
     * Gets the maximum map scale.
     *
     * @return The maximum scale.
     */
    public float getMaxScale() {
        return maxScale;
    }

    /**
     * Sets the maximum map scale and saves the config.
     *
     * @param maxScale The new maximum scale.
     */
    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
        save();
    }

    /**
     * Sets the exploration radius and saves the config.
     *
     * @param explorationRadius The new exploration radius.
     */
    public void setExplorationRadius(int explorationRadius) {
        this.explorationRadius = explorationRadius;
        save();
    }

    /**
     * Sets the map quality and saves the config.
     *
     * @param mapQuality The new map quality
     */
    public void setQuality(MapQuality mapQuality) {
        this.mapQuality = mapQuality;
        save();
    }

    public boolean isLocationEnabled() {
        return locationEnabled;
    }

    public void setLocationEnabled(boolean locationEnabled) {
        this.locationEnabled = locationEnabled;
    }

    /**
     * Enum representing different map quality settings.
     */
    public enum MapQuality {
        LOW(0.25f, 30000),
        MEDIUM(0.5f, 10000),
        HIGH(1.0f, 3000);

        public final float scale;
        public final int maxChunks;

        MapQuality(float scale, int maxChunks) {
            this.scale = scale;
            this.maxChunks = maxChunks;
        }
    }
}

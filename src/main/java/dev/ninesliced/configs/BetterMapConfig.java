package dev.ninesliced.configs;

import com.google.gson.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private boolean locationEnabled = false;
    private boolean shareAllExploration = false;
    private int maxChunksToLoad = 10000;
    private boolean radarEnabled = true;
    private int radarRange = -1;
    private boolean hidePlayersOnMap = false;
    private boolean hideOtherWarpsOnMap = false;
    private boolean hideUnexploredWarpsOnMap = true;
    private boolean allowWaypointTeleports = true;
    private boolean allowMapMarkerTeleports = true;
    private boolean hideAllPoiOnMap = false;
    private boolean hideUnexploredPoiOnMap = true;
    private List<String> hiddenPoiNames = new ArrayList<>();
    private int autoSaveInterval = 5;
    private List<String> allowedWorlds = new ArrayList<>(Arrays.asList("default", "world"));

    private transient Path configPath;
    private transient Path configDir;
    private transient MapQuality activeMapQuality;
    private transient int activeMaxChunksToLoad;

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
        this.configDir = rootPath.resolve("mods").resolve("BetterMap");
        this.configPath = this.configDir.resolve("config.json");

        try {
            if (!Files.exists(this.configDir)) {
                Files.createDirectories(this.configDir);
            }

            if (Files.exists(configPath)) {
                load();
            } else {
                save();
            }

            this.activeMapQuality = this.mapQuality;
            this.activeMaxChunksToLoad = this.maxChunksToLoad;
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize configuration: " + e.getMessage());
        }
    }

    /**
     * Gets the directory where BetterMap stores its config and data files.
     *
     * @return The BetterMap config directory path, or null if not initialized.
     */
    public Path getConfigDirectory() {
        return this.configDir;
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

                    if (jsonObject.has("shareAllExploration")) {
                        this.shareAllExploration = loaded.shareAllExploration;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("maxChunksToLoad")) {
                        this.maxChunksToLoad = loaded.maxChunksToLoad;
                    } else {
                        this.maxChunksToLoad = this.mapQuality.maxChunks;
                        needsSave = true;
                    }

                    if (this.maxChunksToLoad > this.mapQuality.maxChunks) {
                        this.maxChunksToLoad = this.mapQuality.maxChunks;
                        needsSave = true;
                        LOGGER.warning("maxChunksToLoad exceeded limit for " + this.mapQuality + " quality. Clamped to " + this.maxChunksToLoad);
                    }

                    if (jsonObject.has("radarEnabled")) {
                        this.radarEnabled = loaded.radarEnabled;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("radarRange")) {
                        this.radarRange = loaded.radarRange;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("hidePlayersOnMap")) {
                        this.hidePlayersOnMap = loaded.hidePlayersOnMap;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("hideOtherWarpsOnMap")) {
                        this.hideOtherWarpsOnMap = loaded.hideOtherWarpsOnMap;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("hideUnexploredWarpsOnMap")) {
                        this.hideUnexploredWarpsOnMap = loaded.hideUnexploredWarpsOnMap;
                    } else {
                        needsSave = true;
                    }
                    if (jsonObject.has("allowWaypointTeleports")) {
                        this.allowWaypointTeleports = loaded.allowWaypointTeleports;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("allowMapMarkerTeleports")) {
                        this.allowMapMarkerTeleports = loaded.allowMapMarkerTeleports;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("hideAllPoiOnMap")) {
                        this.hideAllPoiOnMap = loaded.hideAllPoiOnMap;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("hideUnexploredPoiOnMap")) {
                        this.hideUnexploredPoiOnMap = loaded.hideUnexploredPoiOnMap;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("hiddenPoiNames") && loaded.hiddenPoiNames != null) {
                        this.hiddenPoiNames = loaded.hiddenPoiNames;
                    } else {
                        needsSave = true;
                    }
                    if (jsonObject.has("autoSaveInterval")) {
                        this.autoSaveInterval = loaded.autoSaveInterval;
                    } else {
                        needsSave = true;
                    }

                    if (jsonObject.has("allowedWorlds") && loaded.allowedWorlds != null) {
                        this.allowedWorlds = loaded.allowedWorlds;
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
        setLoggerLevel("dev.ninesliced.managers.PlayerConfigManager", level);
        setLoggerLevel("dev.ninesliced.managers.ExplorationManager", level);
        setLoggerLevel("dev.ninesliced.managers.WaypointManager", level);
        setLoggerLevel("dev.ninesliced.managers.MapPrivacyManager", level);
        setLoggerLevel("dev.ninesliced.managers.WarpPrivacyManager", level);
        setLoggerLevel("dev.ninesliced.managers.PoiPrivacyManager", level);
        setLoggerLevel("dev.ninesliced.managers.PlayerRadarManager", level);
        setLoggerLevel("dev.ninesliced.providers.LocationHudProvider", level);
        setLoggerLevel("dev.ninesliced.providers.WarpPrivacyProvider", level);
        setLoggerLevel("dev.ninesliced.providers.PoiPrivacyProvider", level);
        setLoggerLevel("dev.ninesliced.systems.LocationSystem", level);
        setLoggerLevel("dev.ninesliced.components.ExplorationComponent", level);
        setLoggerLevel("dev.ninesliced.exploration.ExplorationData", level);
        setLoggerLevel("dev.ninesliced.exploration.ExplorationPersistenceHandler", level);
        setLoggerLevel("dev.ninesliced.exploration.ExplorationSyncHandler", level);
        setLoggerLevel("dev.ninesliced.exploration.WaypointData", level);
        setLoggerLevel("dev.ninesliced.exploration.RadarSystem", level);
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

    /**currently active max chunks to load.
     *
     * @return The active max chunks.
     */
    public int getActiveMaxChunksToLoad() {
        return activeMaxChunksToLoad > 0 ? activeMaxChunksToLoad : maxChunksToLoad;
    }

    /**
     * Gets the
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
        this.minScale = Math.max(minScale, 2);
        save();
    }

    /**
     * Gets the auto-save interval in minutes.
     *
     * @return The interval in minutes.
     */
    public int getAutoSaveInterval() {
        return autoSaveInterval;
    }

    /**
     * Sets the auto-save interval in minutes.
     *
     * @param autoSaveInterval The new interval.
     */
    public void setAutoSaveInterval(int autoSaveInterval) {
        this.autoSaveInterval = autoSaveInterval;
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
        this.maxChunksToLoad = mapQuality.maxChunks;
        save();
    }

    public boolean isLocationEnabled() {
        return locationEnabled;
    }

    /**
     * Checks if all players exploration data should be shared.
     *
     * @return True if shared exploration is enabled.
     */
    public boolean isShareAllExploration() {
        return shareAllExploration;
    }

    /**
     * Sets whether exploration data should be shared among all players.
     *
     * @param shareAllExploration True to share all exploration.
     */
    public void setShareAllExploration(boolean shareAllExploration) {
        this.shareAllExploration = shareAllExploration;
        save();
    }

    public void setLocationEnabled(boolean locationEnabled) {
        this.locationEnabled = locationEnabled;
    }

    /**
     * Gets the max chunks to load.
     *
     * @return The max chunks to load.
     */
    public int getMaxChunksToLoad() {
        return maxChunksToLoad;
    }

    /**
     * Sets the max chunks to load and saves config.
     *
     * @param maxChunksToLoad The new max chunks limit.
     */
    public void setMaxChunksToLoad(int maxChunksToLoad) {
        this.maxChunksToLoad = maxChunksToLoad;
        save();
    }

    /**
     * Checks if player radar is enabled by default.
     *
     * @return True if radar is enabled by default.
     */
    public boolean isRadarEnabled() {
        return radarEnabled;
    }

    /**
     * Gets the default radar range in blocks.
     *
     * @return The default radar range, or -1 for infinite.
     */
    public int getRadarRange() {
        return radarRange;
    }

    /**
     * Checks if players should be hidden on the map.
     *
     * @return True if players are hidden on the map.
     */
    public boolean isHidePlayersOnMap() {
        return hidePlayersOnMap;
    }

    /**
     * Checks if other players' warps should be hidden on the map.
     *
     * @return True if other players' warps are hidden.
     */
    public boolean isHideOtherWarpsOnMap() {
        return hideOtherWarpsOnMap;
    }

    /**
     * Checks if warps in unexplored regions should be hidden on the map.
     *
     * @return True if warps in unexplored regions are hidden.
     */
    public boolean isHideUnexploredWarpsOnMap() {
        return hideUnexploredWarpsOnMap;
    }

    /**
     * Checks if waypoint teleports are allowed.
     *
     * @return True if waypoint teleports are allowed.
     */
    public boolean isAllowWaypointTeleports() {
        return allowWaypointTeleports;
    }

    /**
     * Checks if map marker teleports (POIs/warps) are allowed.
     *
     * @return True if map marker teleports are allowed.
     */
    public boolean isAllowMapMarkerTeleports() {
        return allowMapMarkerTeleports;
    }

    /**
     * Checks if all POI markers should be hidden on the map.
     *
     * @return True if all POIs are hidden.
     */
    public boolean isHideAllPoiOnMap() {
        return hideAllPoiOnMap;
    }

    /**
     * Checks if POI markers in unexplored regions should be hidden on the map.
     *
     * @return True if POIs in unexplored regions are hidden.
     */
    public boolean isHideUnexploredPoiOnMap() {
        return hideUnexploredPoiOnMap;
    }

    /**
     * Gets the list of POI names to hide on the map.
     *
     * @return The list of hidden POI names.
     */
    public List<String> getHiddenPoiNames() {
        return hiddenPoiNames;
    }

    /**
     * Sets whether players should be hidden on the map.
     *
     * @param hidePlayersOnMap True to hide players.
     */
    public void setHidePlayersOnMap(boolean hidePlayersOnMap) {
        this.hidePlayersOnMap = hidePlayersOnMap;
        save();
    }

    /**
     * Sets whether other players' warps should be hidden on the map.
     *
     * @param hideOtherWarpsOnMap True to hide other players' warps.
     */
    public void setHideOtherWarpsOnMap(boolean hideOtherWarpsOnMap) {
        this.hideOtherWarpsOnMap = hideOtherWarpsOnMap;
        save();
    }

    /**
     * Sets whether warps in unexplored regions should be hidden on the map.
     *
     * @param hideUnexploredWarpsOnMap True to hide warps in unexplored regions.
     */
    public void setHideUnexploredWarpsOnMap(boolean hideUnexploredWarpsOnMap) {
        this.hideUnexploredWarpsOnMap = hideUnexploredWarpsOnMap;
        save();
    }

    /**
     * Sets whether waypoint teleports are allowed.
     *
     * @param allowWaypointTeleports True to allow waypoint teleports.
     */
    public void setAllowWaypointTeleports(boolean allowWaypointTeleports) {
        this.allowWaypointTeleports = allowWaypointTeleports;
        save();
    }

    /**
     * Sets whether map marker teleports (POIs/warps) are allowed.
     *
     * @param allowMapMarkerTeleports True to allow map marker teleports.
     */
    public void setAllowMapMarkerTeleports(boolean allowMapMarkerTeleports) {
        this.allowMapMarkerTeleports = allowMapMarkerTeleports;
        save();
    }

    /**
     * Sets whether all POI markers should be hidden on the map.
     *
     * @param hideAllPoiOnMap True to hide all POIs.
     */
    public void setHideAllPoiOnMap(boolean hideAllPoiOnMap) {
        this.hideAllPoiOnMap = hideAllPoiOnMap;
        save();
    }

    /**
     * Sets whether POI markers in unexplored regions should be hidden on the map.
     *
     * @param hideUnexploredPoiOnMap True to hide POIs in unexplored regions.
     */
    public void setHideUnexploredPoiOnMap(boolean hideUnexploredPoiOnMap) {
        this.hideUnexploredPoiOnMap = hideUnexploredPoiOnMap;
        save();
    }

    /**
     * Sets the list of POI names to hide on the map.
     *
     * @param hiddenPoiNames The new list.
     */
    public void setHiddenPoiNames(List<String> hiddenPoiNames) {
        this.hiddenPoiNames = hiddenPoiNames != null ? hiddenPoiNames : new ArrayList<>();
        save();
    }

    /**
     * Sets whether radar is enabled.
     *
     * @param radarEnabled True to enable radar.
     */
    public void setRadarEnabled(boolean radarEnabled) {
        this.radarEnabled = radarEnabled;
        save();
    }

    /**
     * Sets the radar range.
     *
     * @param radarRange The new radar range.
     */
    public void setRadarRange(int radarRange) {
        this.radarRange = radarRange;
        save();
    }
    
    /**
     * Gets the list of allowed worlds.
     *
     * @return The list of allowed worlds.
     */
    public List<String> getAllowedWorlds() {
        return allowedWorlds;
    }

    /**
     * Checks if a world is allowed/tracked.
     *
     * @param worldName The world name.
     * @return True if allowed.
     */
    public boolean isTrackedWorld(String worldName) {
        return allowedWorlds != null && allowedWorlds.contains(worldName);
    }

    /**
     * Sets the list of allowed worlds.
     *
     * @param allowedWorlds The new list.
     */
    public void setAllowedWorlds(List<String> allowedWorlds) {
        this.allowedWorlds = allowedWorlds;
        save();
    }

    /**
     * Adds a world to the allowed list if not present.
     *
     * @param worldName The world name to add.
     * @return True if added, false if already present.
     */
    public boolean addAllowedWorld(String worldName) {
        if (this.allowedWorlds == null) {
            this.allowedWorlds = new ArrayList<>();
        }
        if (!this.allowedWorlds.contains(worldName)) {
            this.allowedWorlds.add(worldName);
            save();
            return true;
        }
        return false;
    }

    /**
     * Removes a world from the allowed list if present.
     *
     * @param worldName The world name to remove.
     * @return True if removed, false if not present.
     */
    public boolean removeAllowedWorld(String worldName) {
        if (this.allowedWorlds != null && this.allowedWorlds.contains(worldName)) {
            this.allowedWorlds.remove(worldName);
            save();
            return true;
        }
        return false;
    }

    /**
     * Enum representing different map quality settings.
     * 
     * Optimization: Increased chunk limits based on performance testing.
     * Delta updates and priority streaming allow higher limits without lag.
     */
    public enum MapQuality {
        LOW(0.25f, 80000),      // Increased from 30000 to 80000
        MEDIUM(0.5f, 25000),    // Increased from 10000 to 25000
        HIGH(1.0f, 8000);       // Increased from 3000 to 8000

        public final float scale;
        public final int maxChunks;

        MapQuality(float scale, int maxChunks) {
            this.scale = scale;
            this.maxChunks = maxChunks;
        }
    }
}

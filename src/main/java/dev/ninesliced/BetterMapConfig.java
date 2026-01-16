package dev.ninesliced;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class BetterMapConfig {
    private static final Logger LOGGER = Logger.getLogger(BetterMapConfig.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BetterMapConfig INSTANCE;

    private int explorationRadius = 16;
    private int updateRateMs = 500;
    private MapQuality mapQuality = MapQuality.MEDIUM;
    private float minScale = 10.0f;
    private float maxScale = 256.0f;

    private transient Path configPath;

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

    public BetterMapConfig() {
    }

    public static synchronized BetterMapConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BetterMapConfig();
        }
        return INSTANCE;
    }

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
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize configuration: " + e.getMessage());
        }
    }

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
                    
                    if (needsSave) {
                        save();
                    }
                    
                    LOGGER.info("Configuration loaded from " + configPath);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to load configuration: " + e.getMessage());
            // Fail safe
            this.mapQuality = MapQuality.MEDIUM;
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(this, writer);
            LOGGER.info("Configuration saved to " + configPath);
        } catch (IOException e) {
            LOGGER.severe("Failed to save configuration: " + e.getMessage());
        }
    }
    
    public void reload() {
        if (configPath != null && Files.exists(configPath)) {
            load();
        }
    }

    public int getExplorationRadius() {
        return explorationRadius;
    }
    
    public int getUpdateRateMs() {
        return updateRateMs;
    }

    public MapQuality getMapQuality() {
        return mapQuality;
    }

    public float getMinScale() {
        return minScale;
    }

    public void setMinScale(float minScale) {
        this.minScale = minScale;
        save();
    }

    public float getMaxScale() {
        return maxScale;
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
        save();
    }
}

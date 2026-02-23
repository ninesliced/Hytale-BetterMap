package dev.ninesliced.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarkersStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles migration of legacy waypoint files (global-pings.json and player-specific pings)
 * to Hytale's built-in UserMapMarker system.
 */
public class WaypointMigrationManager {
    private static final Logger LOGGER = Logger.getLogger(WaypointMigrationManager.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final String PERSONAL_ID_PREFIX = "user_personal_";
    private static final String SHARED_ID_PREFIX = "user_shared_";
    
    private static final Set<String> processedFiles = new HashSet<>();
    private static boolean globalMigrationDone = false;
    
    private WaypointMigrationManager() {
    }
    
    /**
     * Called when a player joins. Checks for and migrates legacy waypoint files.
     * Checks both "Data" and "data" folders for case-sensitivity compatibility.
     */
    public static void onPlayerJoin(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null || !WaypointManager.isTrackedWorld(world)) {
            return;
        }
        
        Path serverRoot = Paths.get(".").toAbsolutePath().normalize();
        Path betterMapDir = serverRoot.resolve("mods").resolve("BetterMap");
        
        Path dataDirUppercase = betterMapDir.resolve("Data");
        Path dataDirLowercase = betterMapDir.resolve("data");
        
        List<Path> dataDirs = new ArrayList<>();
        if (Files.exists(dataDirUppercase)) {
            dataDirs.add(dataDirUppercase);
        }
        if (Files.exists(dataDirLowercase) && !dataDirUppercase.equals(dataDirLowercase)) {
            dataDirs.add(dataDirLowercase);
        }
        
        if (dataDirs.isEmpty()) {
            return;
        }
        
        if (!globalMigrationDone) {
            for (Path dataDir : dataDirs) {
                migrateGlobalWaypoints(world, dataDir);
            }
            globalMigrationDone = true;
        }
        
        for (Path dataDir : dataDirs) {
            migratePersonalWaypoints(player, world, dataDir);
        }
    }
    
    /**
     * Migrates global waypoints from global-pings.json to Hytale's shared markers.
     */
    private static void migrateGlobalWaypoints(@Nonnull World world, @Nonnull Path dataDir) {
        Path globalFile = dataDir.resolve("global-pings.json");
        
        if (!Files.exists(globalFile)) {
            LOGGER.info("[Migration] No global-pings.json found, skipping global migration");
            return;
        }
        
        String fileKey = globalFile.toString();
        if (processedFiles.contains(fileKey)) {
            return;
        }
        
        LOGGER.info("[Migration] Found global-pings.json, starting migration...");
        
        try {
            String content = Files.readString(globalFile);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            
            if (!root.has("Waypoints")) {
                LOGGER.info("[Migration] No Waypoints array in global-pings.json");
                deleteFile(globalFile);
                processedFiles.add(fileKey);
                return;
            }
            
            JsonArray waypoints = root.getAsJsonArray("Waypoints");
            int migratedCount = 0;
            
            UserMapMarkersStore sharedStore = world.getChunkStore().getStore()
                    .getResource(WorldMarkersResource.getResourceType());
            
            if (sharedStore == null) {
                LOGGER.warning("[Migration] Could not get shared markers store");
                return;
            }
            
            List<UserMapMarker> existingMarkers = new ArrayList<>(sharedStore.getUserMapMarkers());
            Set<String> existingIds = new HashSet<>();
            for (UserMapMarker m : existingMarkers) {
                if (m.getId() != null) {
                    existingIds.add(m.getId());
                }
            }
            
            for (JsonElement element : waypoints) {
                JsonObject wp = element.getAsJsonObject();
                
                String worldName = getStringOrNull(wp, "World");
                if (worldName != null && !worldName.equals(world.getName())) {
                    continue;
                }
                
                String oldId = getStringOrNull(wp, "Id");
                String name = getStringOrNull(wp, "Name");
                String oldIcon = getStringOrNull(wp, "Icon");
                float x = wp.has("X") ? wp.get("X").getAsFloat() : 0;
                float z = wp.has("Z") ? wp.get("Z").getAsFloat() : 0;
                String ownerUuid = getStringOrNull(wp, "OwnerUuid");
                String ownerName = getStringOrNull(wp, "OwnerName");
                
                String newId = SHARED_ID_PREFIX + UUID.randomUUID().toString();
                
                boolean alreadyExists = false;
                for (UserMapMarker m : existingMarkers) {
                    if (m.getName() != null && m.getName().equals(name)) {
                        float dx = Math.abs(m.getX() - x);
                        float dz = Math.abs(m.getZ() - z);
                        if (dx < 1.0f && dz < 1.0f) {
                            alreadyExists = true;
                            break;
                        }
                    }
                }
                
                if (alreadyExists) {
                    LOGGER.info("[Migration] Skipping already migrated global waypoint: " + name);
                    continue;
                }
                
                IconColorMapping mapping = mapOldIconToNew(oldIcon);
                
                UserMapMarker marker = new UserMapMarker();
                marker.setId(newId);
                marker.setName(name != null ? name : "Migrated Waypoint");
                marker.setIcon(mapping.icon);
                marker.setPosition(x, z);
                marker.setColorTint(mapping.color);
                
                if (ownerName != null) {
                    marker.withCreatedByName(ownerName);
                }
                if (ownerUuid != null) {
                    try {
                        marker.withCreatedByUuid(UUID.fromString(ownerUuid));
                    } catch (IllegalArgumentException e) {
                    }
                }
                
                sharedStore.addUserMapMarker(marker);
                migratedCount++;
                LOGGER.info("[Migration] Migrated global waypoint: " + name + " (icon: " + oldIcon + " -> " + mapping.icon + ")");
            }
            
            LOGGER.info("[Migration] Migrated " + migratedCount + " global waypoints");
            
            deleteFile(globalFile);
            processedFiles.add(fileKey);
            
        } catch (Exception e) {
            LOGGER.warning("[Migration] Failed to migrate global waypoints: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Migrates personal waypoints for a specific player.
     */
    private static void migratePersonalWaypoints(@Nonnull Player player, @Nonnull World world, @Nonnull Path dataDir) {
        String worldName = world.getName();
        Path worldDir = dataDir.resolve(worldName);
        
        if (!Files.exists(worldDir)) {
            return;
        }
        
        UUID playerUuid = getPlayerUuid(player);
        if (playerUuid == null) {
            return;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldDir, playerUuid.toString() + "*.json")) {
            for (Path file : stream) {
                migratePersonalFile(player, world, file);
            }
        } catch (IOException e) {
            LOGGER.warning("[Migration] Error scanning for personal waypoint files: " + e.getMessage());
        }
        
        Path pingsFile = worldDir.resolve(playerUuid.toString() + "-pings.json");
        if (Files.exists(pingsFile)) {
            migratePersonalFile(player, world, pingsFile);
        }
    }
    
    /**
     * Migrates a single personal waypoint file.
     */
    private static void migratePersonalFile(@Nonnull Player player, @Nonnull World world, @Nonnull Path file) {
        String fileKey = file.toString();
        if (processedFiles.contains(fileKey)) {
            return;
        }
        
        LOGGER.info("[Migration] Found personal waypoint file: " + file.getFileName());
        
        try {
            String content = Files.readString(file);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            
            if (!root.has("Waypoints")) {
                LOGGER.info("[Migration] No Waypoints array in " + file.getFileName());
                deleteFile(file);
                processedFiles.add(fileKey);
                return;
            }
            
            JsonArray waypoints = root.getAsJsonArray("Waypoints");
            int migratedCount = 0;
            
            UserMapMarkersStore personalStore = player.getPlayerConfigData().getPerWorldData(world.getName());
            
            if (personalStore == null) {
                LOGGER.warning("[Migration] Could not get personal markers store for player");
                return;
            }
            
            List<UserMapMarker> existingMarkers = new ArrayList<>(personalStore.getUserMapMarkers());
            Set<String> existingIds = new HashSet<>();
            for (UserMapMarker m : existingMarkers) {
                if (m.getId() != null) {
                    existingIds.add(m.getId());
                }
            }
            
            for (JsonElement element : waypoints) {
                JsonObject wp = element.getAsJsonObject();
                
                if (wp.has("Shared") && wp.get("Shared").getAsBoolean()) {
                    continue;
                }
                
                String oldId = getStringOrNull(wp, "Id");
                String name = getStringOrNull(wp, "Name");
                String oldIcon = getStringOrNull(wp, "Icon");
                float x = wp.has("X") ? wp.get("X").getAsFloat() : 0;
                float z = wp.has("Z") ? wp.get("Z").getAsFloat() : 0;
                
                String newId = PERSONAL_ID_PREFIX + UUID.randomUUID().toString();
                
                boolean alreadyExists = false;
                for (UserMapMarker m : existingMarkers) {
                    if (m.getName() != null && m.getName().equals(name)) {
                        float dx = Math.abs(m.getX() - x);
                        float dz = Math.abs(m.getZ() - z);
                        if (dx < 1.0f && dz < 1.0f) {
                            alreadyExists = true;
                            break;
                        }
                    }
                }
                
                if (alreadyExists) {
                    LOGGER.info("[Migration] Skipping already migrated personal waypoint: " + name);
                    continue;
                }
                
                IconColorMapping mapping = mapOldIconToNew(oldIcon);
                
                UserMapMarker marker = new UserMapMarker();
                marker.setId(newId);
                marker.setName(name != null ? name : "Migrated Waypoint");
                marker.setIcon(mapping.icon);
                marker.setPosition(x, z);
                marker.setColorTint(mapping.color);
                marker.withCreatedByName(player.getDisplayName());
                marker.withCreatedByUuid(getPlayerUuid(player));
                
                personalStore.addUserMapMarker(marker);
                migratedCount++;
                LOGGER.info("[Migration] Migrated personal waypoint: " + name + " (icon: " + oldIcon + " -> " + mapping.icon + ")");
            }
            
            LOGGER.info("[Migration] Migrated " + migratedCount + " personal waypoints from " + file.getFileName());
            
            deleteFile(file);
            processedFiles.add(fileKey);
            
        } catch (Exception e) {
            LOGGER.warning("[Migration] Failed to migrate personal waypoints from " + file.getFileName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Maps old icon names to new UserA-F icons with appropriate colors.
     */
    private static IconColorMapping mapOldIconToNew(String oldIcon) {
        if (oldIcon == null || oldIcon.isEmpty()) {
            return new IconColorMapping("UserA.png", new Color((byte) 255, (byte) 255, (byte) 255)); // White
        }
        
        String iconLower = oldIcon.toLowerCase();
        
        if (iconLower.contains("coordinate")) {
            return new IconColorMapping("UserA.png", new Color((byte) 255, (byte) 255, (byte) 255)); // White
        }
        
        if (iconLower.contains("redmarker") || iconLower.contains("red")) {
            return new IconColorMapping("UserA.png", new Color((byte) 255, (byte) 68, (byte) 68)); // #FF4444
        }
        
        if (iconLower.contains("greenmarker") || iconLower.contains("green")) {
            return new IconColorMapping("UserA.png", new Color((byte) 68, (byte) 255, (byte) 68)); // #44FF44
        }
        
        if (iconLower.contains("bluemarker") || iconLower.contains("blue")) {
            return new IconColorMapping("UserA.png", new Color((byte) 68, (byte) 68, (byte) 255)); // #4444FF
        }
        
        if (iconLower.contains("yellowmarker") || iconLower.contains("yellow")) {
            return new IconColorMapping("UserA.png", new Color((byte) 255, (byte) 255, (byte) 68)); // #FFFF44
        }
        
        if (iconLower.contains("orangemarker") || iconLower.contains("orange")) {
            return new IconColorMapping("UserA.png", new Color((byte) 255, (byte) 136, (byte) 0)); // #FF8800
        }
        
        if (iconLower.contains("cyanmarker") || iconLower.contains("cyan")) {
            return new IconColorMapping("UserA.png", new Color((byte) 68, (byte) 255, (byte) 255)); // #44FFFF
        }
        
        if (iconLower.contains("pinkmarker") || iconLower.contains("magentamarker") || 
            iconLower.contains("pink") || iconLower.contains("magenta")) {
            return new IconColorMapping("UserA.png", new Color((byte) 255, (byte) 68, (byte) 255)); // #FF44FF
        }
        
        if (iconLower.startsWith("user") && iconLower.endsWith(".png")) {
            return new IconColorMapping(oldIcon, new Color((byte) 255, (byte) 255, (byte) 255));
        }
        
        return new IconColorMapping("UserA.png", new Color((byte) 255, (byte) 255, (byte) 255));
    }
    
    private static String getStringOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
    
    private static UUID getPlayerUuid(Player player) {
        try {
            return ((com.hypixel.hytale.server.core.command.system.CommandSender) player).getUuid();
        } catch (Exception e) {
            return null;
        }
    }
    
    private static void deleteFile(Path file) {
        try {
            Files.delete(file);
            LOGGER.info("[Migration] Deleted legacy file: " + file.getFileName());
        } catch (IOException e) {
            LOGGER.warning("[Migration] Failed to delete legacy file " + file.getFileName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Helper record for icon and color mapping.
     */
    private record IconColorMapping(String icon, Color color) {
    }
}

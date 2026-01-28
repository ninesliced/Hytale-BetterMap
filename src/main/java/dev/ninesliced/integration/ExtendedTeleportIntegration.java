package dev.ninesliced.integration;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * BetterMap integration with ExtendedTeleport mod to check teleporter ownership.
 */
public class ExtendedTeleportIntegration {
    private static final Logger LOGGER = Logger.getLogger(ExtendedTeleportIntegration.class.getName());
    private static ExtendedTeleportIntegration instance;
    
    private boolean available = false;
    private boolean initializationAttempted = false;
    private Method getInstanceMethod;
    private Method getTeleporterByWarpNameMethod;
    private Method isOwnerMethod;
    private Method getOwnerUuidMethod;

    private ExtendedTeleportIntegration() {
        tryInitialize();
    }

    public static synchronized ExtendedTeleportIntegration getInstance() {
        if (instance == null) {
            instance = new ExtendedTeleportIntegration();
        }
        return instance;
    }

    private void tryInitialize() {
        if (initializationAttempted) {
            return;
        }
        initializationAttempted = true;
        
        try {
            // Load the TeleporterManager class directly
            Class<?> managerClass = Class.forName("com.hytale.extendedteleport.TeleporterManager");
            
            // Get the getInstance() method
            getInstanceMethod = managerClass.getMethod("getInstance");
            
            // Verify manager is available
            Object manager = getInstanceMethod.invoke(null);
            if (manager == null) {
                // Manager not ready yet, allow retry later
                initializationAttempted = false;
                return;
            }
            
            // Get getTeleporterByWarpName method
            getTeleporterByWarpNameMethod = managerClass.getMethod("getTeleporterByWarpName", String.class);
            
            // Get TeleporterInfo class and its isOwner method
            Class<?> teleporterInfoClass = Class.forName("com.hytale.extendedteleport.data.TeleporterInfo");
            isOwnerMethod = teleporterInfoClass.getMethod("isOwner", UUID.class);
            
            // Get getOwnerUuid method from TeleporterInfo
            getOwnerUuidMethod = teleporterInfoClass.getMethod("getOwnerUuid");

            available = true;
            LOGGER.info("BetterMap: ExtendedTeleport integration enabled");
        } catch (ClassNotFoundException e) {
            // ExtendedTeleport not installed - this is expected, no warning needed
        } catch (NoSuchMethodException e) {
            LOGGER.warning("BetterMap: ExtendedTeleport API changed - integration disabled: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.warning("BetterMap: Failed to initialize ExtendedTeleport integration: " + e.getMessage());
        }
    }

    private Object getTeleporterManager() {
        if (getInstanceMethod == null) {
            return null;
        }
        try {
            return getInstanceMethod.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if ExtendedTeleport integration is available.
     */
    public boolean isAvailable() {
        if (!available && !initializationAttempted) {
            tryInitialize();
        }
        return available;
    }

    /**
     * Checks if the given player owns the teleporter associated with the warp name.
     *
     * @param playerUuid The player's UUID
     * @param warpName The warp name (teleporter name)
     * @return true if the player owns this teleporter, false otherwise or if not available
     */
    public boolean isPlayerTeleporterOwner(UUID playerUuid, String warpName) {
        if (!isAvailable() || playerUuid == null || warpName == null) {
            return false;
        }

        try {
            Object manager = getTeleporterManager();
            if (manager == null) {
                return false;
            }

            // Get the TeleporterInfo by warp name
            Object teleporterInfo = getTeleporterByWarpNameMethod.invoke(manager, warpName);
            if (teleporterInfo == null) {
                return false;
            }

            // Check if the player owns this teleporter
            Boolean isOwner = (Boolean) isOwnerMethod.invoke(teleporterInfo, playerUuid);
            return isOwner != null && isOwner;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the owner UUID of a teleporter by warp name.
     *
     * @param warpName The warp name (teleporter name)
     * @return The owner UUID, or null if not found or not available
     */
    public UUID getTeleporterOwner(String warpName) {
        if (!isAvailable() || warpName == null) {
            return null;
        }

        try {
            Object manager = getTeleporterManager();
            if (manager == null) {
                return null;
            }

            Object teleporterInfo = getTeleporterByWarpNameMethod.invoke(manager, warpName);
            if (teleporterInfo == null) {
                return null;
            }

            return (UUID) getOwnerUuidMethod.invoke(teleporterInfo);
        } catch (Exception e) {
            return null;
        }
    }
}

package dev.ninesliced.integration;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

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
            Class<?> managerClass = Class.forName("com.hytale.extendedteleport.TeleporterManager");
            
            getInstanceMethod = managerClass.getMethod("getInstance");
            
            Object manager = getInstanceMethod.invoke(null);
            if (manager == null) {
                initializationAttempted = false;
                return;
            }
            
            getTeleporterByWarpNameMethod = managerClass.getMethod("getTeleporterByWarpName", String.class);
            
            Class<?> teleporterInfoClass = Class.forName("com.hytale.extendedteleport.data.TeleporterInfo");
            isOwnerMethod = teleporterInfoClass.getMethod("isOwner", UUID.class);
            
            getOwnerUuidMethod = teleporterInfoClass.getMethod("getOwnerUuid");

            available = true;
            LOGGER.info("BetterMap: ExtendedTeleport integration enabled");
        } catch (ClassNotFoundException e) {
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

    public boolean isAvailable() {
        if (!available && !initializationAttempted) {
            tryInitialize();
        }
        return available;
    }

    public boolean isPlayerTeleporterOwner(UUID playerUuid, String warpName) {
        if (!isAvailable() || playerUuid == null || warpName == null) {
            return false;
        }

        try {
            Object manager = getTeleporterManager();
            if (manager == null) {
                return false;
            }

            Object teleporterInfo = getTeleporterByWarpNameMethod.invoke(manager, warpName);
            if (teleporterInfo == null) {
                return false;
            }

            Boolean isOwner = (Boolean) isOwnerMethod.invoke(teleporterInfo, playerUuid);
            return isOwner != null && isOwner;
        } catch (Exception e) {
            return false;
        }
    }

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

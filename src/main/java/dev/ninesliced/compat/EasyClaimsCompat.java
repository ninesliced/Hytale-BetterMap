package dev.ninesliced.compat;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reflection-based access to EasyClaims APIs without a hard dependency.
 */
public final class EasyClaimsCompat {
    private static final Logger LOGGER = Logger.getLogger(EasyClaimsCompat.class.getName());
    private static final String ACCESS_CLASS = "com.easyclaims.EasyClaimsAccess";

    private static boolean initialized = false;
    private static boolean available = false;
    private static Method getClaimedChunksInWorld;
    private static Method getPlayerName;

    private EasyClaimsCompat() {
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> accessClass = Class.forName(ACCESS_CLASS);
            getClaimedChunksInWorld = accessClass.getMethod("getClaimedChunksInWorld", String.class);
            getPlayerName = accessClass.getMethod("getPlayerName", UUID.class);
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        } catch (Exception e) {
            available = false;
            LOGGER.warning("EasyClaims compatibility disabled: " + e.getMessage());
        }
    }

    /**
     * Checks if EasyClaims accessors are available at runtime.
     */
    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    /**
     * Returns a map of claimed chunks for a world (chunkKey -> owner UUID).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, UUID> getClaimedChunksInWorld(String worldName) {
        if (!isAvailable()) {
            return Collections.emptyMap();
        }
        try {
            Object result = getClaimedChunksInWorld.invoke(null, worldName);
            if (result instanceof Map<?, ?> map) {
                return (Map<String, UUID>) map;
            }
        } catch (Exception e) {
            LOGGER.fine("EasyClaims getClaimedChunksInWorld failed: " + e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * Resolves a player name from a UUID using EasyClaims storage.
     */
    public static String getPlayerName(UUID uuid) {
        if (!isAvailable() || uuid == null) {
            return "Unknown";
        }
        try {
            Object result = getPlayerName.invoke(null, uuid);
            if (result instanceof String name) {
                return name;
            }
        } catch (Exception e) {
            LOGGER.fine("EasyClaims getPlayerName failed: " + e.getMessage());
        }
        return "Unknown";
    }
}

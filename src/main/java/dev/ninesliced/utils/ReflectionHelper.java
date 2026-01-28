package dev.ninesliced.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Utility for performing reflection operations safely.
 * Caches Field objects and uses MethodHandles for better performance.
 */
public class ReflectionHelper {

    private static final Logger LOGGER = Logger.getLogger(ReflectionHelper.class.getName());
    
    /**
     * Cache for Field objects to avoid repeated lookups.
     */
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Cache for recursive Field lookups.
     */
    private static final Map<String, Field> RECURSIVE_FIELD_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Cache for MethodHandles (faster than Field.get/set).
     */
    private static final Map<String, MethodHandle> METHOD_HANDLE_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Lookup for creating MethodHandles.
     */
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * Gets a field from a class, setting it accessible.
     */
    @Nullable
    public static Field getField(@Nonnull Class<?> clazz, @Nonnull String fieldName) {
        String cacheKey = clazz.getName() + "#" + fieldName;
        
        return FIELD_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                LOGGER.warning("Field not found: " + clazz.getName() + "." + fieldName);
                return null;
            }
        });
    }

    /**
     * Uses cached MethodHandles for better JIT optimization.
     */
    @Nullable
    public static Object getFieldValue(@Nonnull Object instance, @Nonnull String fieldName) {
        try {
            String cacheKey = instance.getClass().getName() + "#" + fieldName + "#getter";
            
            MethodHandle getter = METHOD_HANDLE_CACHE.get(cacheKey);
            if (getter == null) {
                Field field = getField(instance.getClass(), fieldName);
                if (field == null) {
                    return null;
                }
                getter = LOOKUP.unreflectGetter(field);
                METHOD_HANDLE_CACHE.put(cacheKey, getter);
            }
            
            return getter.invoke(instance);
        } catch (Throwable e) {
            LOGGER.warning("Cannot access field: " + fieldName + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Uses cached MethodHandles for better JIT optimization.
     */
    public static boolean setFieldValue(@Nonnull Object instance, @Nonnull String fieldName, @Nullable Object value) {
        try {
            String cacheKey = instance.getClass().getName() + "#" + fieldName + "#setter";
            
            MethodHandle setter = METHOD_HANDLE_CACHE.get(cacheKey);
            if (setter == null) {
                Field field = getField(instance.getClass(), fieldName);
                if (field == null) {
                    return false;
                }
                setter = LOOKUP.unreflectSetter(field);
                METHOD_HANDLE_CACHE.put(cacheKey, setter);
            }
            
            setter.invoke(instance, value);
            return true;
        } catch (Throwable e) {
            LOGGER.warning("Cannot access field for setting: " + fieldName + " - " + e.getMessage());
        }
        return false;
    }

    /**
     * Gets a method from a class.
     *
     * @param clazz          The class.
     * @param methodName     The method name.
     * @param parameterTypes Parameter types.
     * @return The method, or null if not found.
     */
    @Nullable
    public static Method getMethod(@Nonnull Class<?> clazz, @Nonnull String methodName, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            LOGGER.warning("Method not found: " + clazz.getName() + "." + methodName);
            return null;
        }
    }

    /**
     * Invokes a method on an instance.
     *
     * @param instance       The instance.
     * @param methodName     The method name.
     * @param parameterTypes Parameter types.
     * @param args           Arguments.
     * @return The return value, or null on failure.
     */
    @Nullable
    public static Object invokeMethod(@Nonnull Object instance, @Nonnull String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = getMethod(instance.getClass(), methodName, parameterTypes);
            if (method != null) {
                return method.invoke(instance, args);
            }
        } catch (Exception e) {
            LOGGER.warning("Cannot invoke method: " + methodName + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Recursively searches for a field in the class hierarchy.
     *
     * @param clazz     The starting class.
     * @param fieldName The field name.
     * @return The field, or null if not found.
     */
    @Nullable
    public static Field getFieldRecursive(@Nonnull Class<?> clazz, @Nonnull String fieldName) {
        String cacheKey = clazz.getName() + "#" + fieldName;
        
        // Check cache first
        Field cached = RECURSIVE_FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Search hierarchy
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                RECURSIVE_FIELD_CACHE.put(cacheKey, field);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Gets value of a field found recursively in hierarchy.
     *
     * @param instance  The instance.
     * @param fieldName The field name.
     * @return The value.
     */
    @Nullable
    public static Object getFieldValueRecursive(@Nonnull Object instance, @Nonnull String fieldName) {
        try {
            String cacheKey = instance.getClass().getName() + "#" + fieldName + "#getter";
            
            MethodHandle getter = METHOD_HANDLE_CACHE.get(cacheKey);
            if (getter == null) {
                Field field = getFieldRecursive(instance.getClass(), fieldName);
                if (field == null) {
                    return null;
                }
                getter = LOOKUP.unreflectGetter(field);
                METHOD_HANDLE_CACHE.put(cacheKey, getter);
            }
            
            return getter.invoke(instance);
        } catch (Throwable e) {
            LOGGER.warning("Cannot access field recursively: " + fieldName + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Sets value of a field found recursively in hierarchy.
     *
     * @param instance  The instance.
     * @param fieldName The field name.
     * @param value     The new value.
     */
    public static void setFieldValueRecursive(@Nonnull Object instance, @Nonnull String fieldName, @Nullable Object value) {
        try {
            String cacheKey = instance.getClass().getName() + "#" + fieldName + "#setter";
            
            MethodHandle setter = METHOD_HANDLE_CACHE.get(cacheKey);
            if (setter == null) {
                Field field = getFieldRecursive(instance.getClass(), fieldName);
                if (field == null) {
                    return;
                }
                setter = LOOKUP.unreflectSetter(field);
                METHOD_HANDLE_CACHE.put(cacheKey, setter);
            }
            
            setter.invoke(instance, value);
        } catch (Throwable e) {
            LOGGER.warning("Cannot access field recursively for setting: " + fieldName + " - " + e.getMessage());
        }
    }
}

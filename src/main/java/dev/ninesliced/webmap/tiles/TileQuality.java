package dev.ninesliced.webmap.tiles;

import dev.ninesliced.configs.ModConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Discrete output quality profiles for web map tile rendering.
 */
public enum TileQuality {
    LOW("low", 128),
    MEDIUM("medium", 256),
    HIGH("high", 512);

    private final String id;
    private final int tileSize;

    TileQuality(String id, int tileSize) {
        this.id = id;
        this.tileSize = tileSize;
    }

    public String id() {
        return id;
    }

    public int tileSize() {
        return tileSize;
    }

    @Nonnull
    public static TileQuality fromConfig(@Nullable ModConfig.MapQuality quality) {
        if (quality == null) {
            return MEDIUM;
        }
        return switch (quality) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
        };
    }

    @Nonnull
    public static TileQuality parseOrDefault(@Nullable String value, @Nonnull TileQuality fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (TileQuality quality : values()) {
            if (quality.id.equals(normalized)) {
                return quality;
            }
        }
        return fallback;
    }
}

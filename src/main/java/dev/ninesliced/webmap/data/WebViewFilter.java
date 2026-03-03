package dev.ninesliced.webmap.data;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * Runtime web view filtering selected in the web UI.
 */
public record WebViewFilter(Mode mode, @Nullable UUID playerUuid) {
    public enum Mode {
        GLOBAL,
        PLAYER
    }

    @Nonnull
    public static WebViewFilter global() {
        return new WebViewFilter(Mode.GLOBAL, null);
    }

    @Nonnull
    public static WebViewFilter parse(@Nullable String modeRaw, @Nullable String playerRaw) {
        Mode mode = "player".equalsIgnoreCase(modeRaw) ? Mode.PLAYER : Mode.GLOBAL;
        UUID playerUuid = null;
        if (playerRaw != null && !playerRaw.isBlank()) {
            try {
                playerUuid = UUID.fromString(playerRaw.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new WebViewFilter(mode, playerUuid);
    }

    public boolean includesPlayer(@Nonnull PlayerRef playerRef) {
        if (mode != Mode.PLAYER || playerUuid == null) {
            return true;
        }
        return playerUuid.equals(playerRef.getUuid());
    }

    @Nonnull
    public String modeId() {
        return mode.name().toLowerCase(Locale.ROOT);
    }
}

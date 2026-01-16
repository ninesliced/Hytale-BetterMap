package dev.ninesliced.configs;

import java.util.UUID;

/**
 * Configuration specific to a single player.
 */
public class PlayerConfig {
    private transient UUID playerUuid;
    private float minScale;
    private float maxScale;

    public PlayerConfig(UUID playerUuid, float minScale, float maxScale) {
        this.playerUuid = playerUuid;
        this.minScale = minScale;
        this.maxScale = maxScale;
    }

    public float getMinScale() {
        return minScale;
    }

    public void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    public float getMaxScale() {
        return maxScale;
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }
}


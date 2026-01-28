package dev.ninesliced.configs;

import com.hypixel.hytale.math.vector.Transform;

/**
 * Persisted death marker data for restoring markers after hide toggles.
 */
public class SavedDeathMarker {
    private String markerId;
    private Transform transform;
    private int day;

    public SavedDeathMarker() {
    }

    public SavedDeathMarker(String markerId, Transform transform, int day) {
        this.markerId = markerId;
        this.transform = transform;
        this.day = day;
    }

    public String getMarkerId() {
        return markerId;
    }

    public Transform getTransform() {
        return transform;
    }

    public int getDay() {
        return day;
    }
}

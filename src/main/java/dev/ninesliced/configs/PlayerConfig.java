package dev.ninesliced.configs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Configuration specific to a single player.
 */
public class PlayerConfig {
    private transient UUID playerUuid;
    private float minScale;
    private float maxScale;
    private boolean locationEnabled;
    
    // Per-player POI privacy settings (only apply if not globally disabled)
    private boolean hideAllPoiOnMap = false;
    private boolean hideSpawnOnMap = false;
    private boolean hideDeathMarkerOnMap = false;
    private List<String> hiddenPoiNames = new ArrayList<>();
    private boolean hidePlayersOnMap = false;
    private boolean hideAllWarpsOnMap = false;
    private boolean hideOtherWarpsOnMap = false;
    private List<SavedDeathMarker> savedDeathMarkers = new ArrayList<>();
    private boolean overrideGlobalPoiHide = false;
    private boolean overrideGlobalSpawnHide = false;
    private boolean overrideGlobalDeathHide = false;
    private boolean overrideGlobalPlayersHide = false;
    private boolean overrideGlobalAllWarpsHide = false;
    private boolean overrideGlobalOtherWarpsHide = false;

    public PlayerConfig(UUID playerUuid, float minScale, float maxScale, boolean locationEnabled) {
        this.playerUuid = playerUuid;
        this.minScale = minScale;
        this.maxScale = maxScale;
        this.locationEnabled = locationEnabled;
    }

    public float getMinScale() {
        return minScale;
    }

    public void setMinScale(float minScale) {
        this.minScale = Math.max(minScale, 2);
    }

    public float getMaxScale() {
        return maxScale;
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    public boolean isLocationEnabled() {
        return locationEnabled;
    }

    public void setLocationEnabled(boolean locationEnabled) {
        this.locationEnabled = locationEnabled;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public boolean isHideAllPoiOnMap() {
        return hideAllPoiOnMap;
    }

    public void setHideAllPoiOnMap(boolean hideAllPoiOnMap) {
        this.hideAllPoiOnMap = hideAllPoiOnMap;
    }

    public boolean isHideSpawnOnMap() {
        return hideSpawnOnMap;
    }

    public void setHideSpawnOnMap(boolean hideSpawnOnMap) {
        this.hideSpawnOnMap = hideSpawnOnMap;
    }

    public boolean isHideDeathMarkerOnMap() {
        return hideDeathMarkerOnMap;
    }

    public void setHideDeathMarkerOnMap(boolean hideDeathMarkerOnMap) {
        this.hideDeathMarkerOnMap = hideDeathMarkerOnMap;
    }

    public List<String> getHiddenPoiNames() {
        if (hiddenPoiNames == null) {
            hiddenPoiNames = new ArrayList<>();
        }
        return hiddenPoiNames;
    }

    public void setHiddenPoiNames(List<String> hiddenPoiNames) {
        this.hiddenPoiNames = hiddenPoiNames != null ? hiddenPoiNames : new ArrayList<>();
    }

    public boolean isHidePlayersOnMap() {
        return hidePlayersOnMap;
    }

    public void setHidePlayersOnMap(boolean hidePlayersOnMap) {
        this.hidePlayersOnMap = hidePlayersOnMap;
    }

    public boolean isHideAllWarpsOnMap() {
        return hideAllWarpsOnMap;
    }

    public void setHideAllWarpsOnMap(boolean hideAllWarpsOnMap) {
        this.hideAllWarpsOnMap = hideAllWarpsOnMap;
    }

    public boolean isHideOtherWarpsOnMap() {
        return hideOtherWarpsOnMap;
    }

    public void setHideOtherWarpsOnMap(boolean hideOtherWarpsOnMap) {
        this.hideOtherWarpsOnMap = hideOtherWarpsOnMap;
    }

    public boolean isOverrideGlobalPoiHide() {
        return overrideGlobalPoiHide;
    }

    public void setOverrideGlobalPoiHide(boolean overrideGlobalPoiHide) {
        this.overrideGlobalPoiHide = overrideGlobalPoiHide;
    }

    public boolean isOverrideGlobalSpawnHide() {
        return overrideGlobalSpawnHide;
    }

    public void setOverrideGlobalSpawnHide(boolean overrideGlobalSpawnHide) {
        this.overrideGlobalSpawnHide = overrideGlobalSpawnHide;
    }

    public boolean isOverrideGlobalDeathHide() {
        return overrideGlobalDeathHide;
    }

    public void setOverrideGlobalDeathHide(boolean overrideGlobalDeathHide) {
        this.overrideGlobalDeathHide = overrideGlobalDeathHide;
    }

    public boolean isOverrideGlobalPlayersHide() {
        return overrideGlobalPlayersHide;
    }

    public void setOverrideGlobalPlayersHide(boolean overrideGlobalPlayersHide) {
        this.overrideGlobalPlayersHide = overrideGlobalPlayersHide;
    }

    public boolean isOverrideGlobalAllWarpsHide() {
        return overrideGlobalAllWarpsHide;
    }

    public void setOverrideGlobalAllWarpsHide(boolean overrideGlobalAllWarpsHide) {
        this.overrideGlobalAllWarpsHide = overrideGlobalAllWarpsHide;
    }

    public boolean isOverrideGlobalOtherWarpsHide() {
        return overrideGlobalOtherWarpsHide;
    }

    public void setOverrideGlobalOtherWarpsHide(boolean overrideGlobalOtherWarpsHide) {
        this.overrideGlobalOtherWarpsHide = overrideGlobalOtherWarpsHide;
    }

    public List<SavedDeathMarker> getSavedDeathMarkers() {
        if (savedDeathMarkers == null) {
            savedDeathMarkers = new ArrayList<>();
        }
        return savedDeathMarkers;
    }

    public void setSavedDeathMarkers(List<SavedDeathMarker> savedDeathMarkers) {
        this.savedDeathMarkers = savedDeathMarkers != null ? savedDeathMarkers : new ArrayList<>();
    }
}

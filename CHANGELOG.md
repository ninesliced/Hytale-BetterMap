# Changelog

## v1.2.7

* Fix world not tracking on world with different name than "world" or "default"
* Fix teleport on right click on map

## v1.2.6

* Fix the issue with the new release changes in the marker API (map not becoming black)

## v1.2.5 - Waypoint Fixes

*  **Waypoint creation & management:** Fixed several issues with waypoint creation, updating, and deletion where local waypoints were not being deleted or updated correctly.

## v1.2.4 - Various Fixes

**Fixes & Improvements**
* **Black Map after world switch:** Fixed an issue where the map would appear black after switching worlds.
* **minimal min scale enforcement:** Ensured that the minimum scale cannot be set below 2.0 to prevent zoom crashes.
* **Waypoint command:** Added `/wp` and `/waypoint` for easier access to the waypoint menu.
* **Optimized Exploration Ticker:** Improved performance of the exploration ticker for smoother operation.


## v1.2.3 - Minor Fixes

**Fixes**

* **Reactivated Global Waypoints:** Fixed an issue where global waypoints were not properly synchronizing between server and client in certain scenarios.
* **/bm waypoint Command:** Resolved a bug that caused the Waypoint UI to not open for the correct rights (no permission required now).
* **Player Radar Sync:** Fixed the double player display issue on the radar when player visibility settings were toggled.


## v1.2.2 - Fixes & Configuration Expansion

**Fixes & Improvements**

* **Global Waypoints:** Temporarily **disabled** due to a bug. We will fix this in the next update.
* **Shared Map Generation:** Fixed a critical issue where the "Linked Map" (Shared Exploration) would stop generating new chunks. It now updates correctly for all players.
* **Map Zoom:** Fixed issues regarding map zoom changes when using the `minscale` command.
* **Player Radar & Visibility:** Fixed the show/hide players functionality and enhanced the Player Radar to correctly sync with player visibility settings.

### **New Configuration Toggles**

We added several new commands to control map visibility and interaction. These require the config permission.

* `/bm config hidewarps`
* Hides other players' warps on the map.


* `/bm config hideunexploredwarps`
* Hides warps located in unexplored regions.


* `/bm config hidepois`
* Hides all POI markers on the map.


* `/bm config hideunexploredpoi`
* Hides POIs located in unexplored regions.


* `/bm config waypointteleport`
* Toggles the ability to teleport to waypoints.


* `/bm config markerteleport`
* Toggles the ability to teleport to map markers.

### **Permission Changes**

Permissions have been restructured to make basic features accessible to everyone by default.

**Public Access (No Permission Required)**

* `/bettermap` (or `/bm`) - Main command.
* `/bm waypoint` (or `/bm menu`) - Opens Waypoint UI.

**Admin Permissions**

* `dev.ninesliced.bettermap.command.config`: Required for all `/bm config` commands (including the new visibility toggles).
* `dev.ninesliced.bettermap.command.reload`: Required for `/bm reload`.


## v1.2.1 - Compatibility Fix

**Fixes**

* **Location Command Disabled:** Temporarily disabled the `/bm location` command due to compatibility issues. We are actively working on a fix and will re-enable this feature in a future update.

## v1.2.0 - Waypoints, Radar & Shared Map

**New Features**

* **Waypoint System:** Added a full UI menu to manage locations.
* **Management:** Add waypoints at your current position, remove them, or update them with custom names and colors.
* **Sharing:** Share waypoints with other players.
* **Teleport:** Teleport directly to saved waypoints (requires permission).


* **Shared Exploration:** Added a "Linked Map" feature. When enabled, all players share the same exploration data, allowing you to see areas discovered by others in real-time.
* **Compass Radar:** Players can now see other players on their compass.
* This is enabled by default but can be toggled or restricted by a range.


* **Location Overlay:** Added an on-screen UI displaying player coordinates and info.
* **Global:** Admins can set the server default using `/bm config location`.
* **Personal:** Players can toggle their own display via `/bm location` (settings are now persistent per player).


* **World Management:** Added capability to whitelist specific worlds for the mod to activate.
* *Fix:* Solves issues with hosting providers (like Apex) where default world names change. Use `/bm config track` to add the current world to the whitelist.


* **Crash Protection (Auto-Save):** The server now auto-saves explored areas periodically (default every 5 minutes) to prevent data loss in the event of a crash.

**Configuration Updates**

* **Chunk Loading Control:** Admins can now manually change the number of visible chunks via `/bm config maxchunk`.
* *Limit:* This cannot exceed the recommended limits based on quality settings (High: 3,000 | Medium: 10,000 | Low: 30,000).


* **Player Visibility:** Added option to hide player cursors on the map via `/bm config hideplayers`.
* **Personal Persistence:** Player-specific settings (like Min/Max scale and Location UI) are now saved in a dedicated player config file.

**Updated Config File (`config.json`)**

```json
{
  "explorationRadius": 16,
  "updateRateMs": 500,
  "mapQuality": "MEDIUM",
  "minScale": 10.0,
  "maxScale": 256.0,
  "debug": false,
  "locationEnabled": true,
  "shareAllExploration": false,
  "maxChunksToLoad": 10000,
  "radarEnabled": true,
  "radarRange": -1,
  "hidePlayersOnMap": false,
  "autoSaveInterval": 5,
  "allowedWorlds": [
    "default",
    "world"
  ]
}

```

**Commands & Permissions**
Permissions have been restructured. You can either do `/op add <player>` to give full access or assign specific permissions as needed using `/perm group add Adventure <permission>`.

* **Basic User Permission:** `dev.ninesliced.bettermap.command.base`
* Allows access to the main command, personal settings, and waypoint creation.
* `/bettermap` (or `/bm`) - Main command.
* `/bm waypoint` (or `/bm menu`) - Opens Waypoint UI.
* `/bm location` - Toggles personal coordinate display.
* `/bm min <value>` & `/bm max <value>` - Sets personal zoom levels.


* **Teleport Permission:** `dev.ninesliced.bettermap.command.base.teleport`
* Allows teleporting to waypoints via the UI.


* **Admin/Config Permission:** `dev.ninesliced.bettermap.command.base.config`
* Allows access to all server-wide configuration commands.
* `/bm config radar <range>` - Toggle radar/set range.
* `/bm config hideplayers` - Hide player cursors on map.
* `/bm config location` - Toggle server default location UI. (disabled by default)
* `/bm config track` / `untrack` - Add/Remove current world from whitelist.
* `/bm config maxchunk <number>` - Set max loaded chunks.
* `/bm config shareallexploration` - Toggle linked maps.
* `/bm config autosave <minutes>` - Set auto-save interval.



---

## v1.1.0 - Optimization & Customization Update

**Optimizations & Fixes**

* **Dynamic Chunk Loading:** Fixed the "Blue Map" issue and crashes caused by memory overflows. The mod now dynamically loads discovered chunks nearest to the player and unloads further ones to optimize memory usage.
* **Map Quality Settings:** Introduced a new `mapQuality` setting to balance visual fidelity and performance.
* **Options:** `LOW`, `MEDIUM`, `HIGH` (Default is `MEDIUM`).
* **Map Quality details:**
* `LOW`: Loads up to 30,000 chunks with 8x8 images.
* `MEDIUM`: Loads up to 10,000 chunks with 16x16 images.
* `HIGH`: Loads up to 3,000 chunks with 32x32 images.


* **Performance Note:** `HIGH` quality increases texture resolution but drastically limits the number of chunks loaded simultaneously to prevent Memory Overflow errors.


* **Debug Toggle:** Added a toggle to enable or disable debug logs to prevent console crowding.

**New Features**

* **Custom Zoom Scaling:** Players can now customize the map zoom limits. You can define how far you can zoom out (`minScale`) and how close you can zoom in (`maxScale`).

**Configuration**
The `config.json` was updated in this version:

```json
{
  "explorationRadius": 16,
  "updateRateMs": 500,
  "mapQuality": "MEDIUM", // Load 10000 chunks with 16x16 images
  "minScale": 10.0, // Base zoom out is 32.0
  "maxScale": 256.0, // Base zoom in is 256.0
  "debug": false
}

```

*Note: Changing `mapQuality` requires a server restart to take effect.*

**Commands & Permissions (Legacy v1.1)**

* `/bettermap` (Aliases: `/bm`, `/map`)
* **Description:** Displays current settings (Radius, Scale, Quality, Debug status).
* **Permission:** `command.bettermap`


* `/bettermap minscale <value>`
* **Description:** Sets the minimum map zoom scale (lower value = zoom out further). Must be greater than 2.0.
* **Permission:** `command.bettermap.minscale`


* `/bettermap maxscale <value>`
* **Description:** Sets the maximum map zoom scale (higher value = zoom in closer).
* **Permission:** `command.bettermap.maxscale`


* `/bettermap debug <true/false>`
* **Description:** Toggles debug logging on or off.
* **Permission:** `command.bettermap.debug`


* `/bettermap reload`
* **Description:** Reloads the configuration file and applies changes to loaded worlds immediately.
* **Permission:** `command.bettermap.reload`



---

## v1.0.0 - Initial Release

**New Features**

* **Persistent Map:** Added the core functionality to save explored map areas. Fog of war no longer resets between sessions.
* **Custom View Range:** Players (or admins) can now define the size of the exploration circle.
* **Data Storage:** Implemented a file saving system. Map data is stored in `mods/bettermap/data/"worldname"/"userId"`.
* **Configuration:** Added `config.json` support located in `mods/bettermap/`.

**Commands**

* Added `/bettermap`: This command reloads the configuration file instantly without requiring a server restart.

**Credits**

* Mod created by Paralaxe and Theobosse (Team Ninesliced).
* Special thanks to Xytronix for contributing to this project.

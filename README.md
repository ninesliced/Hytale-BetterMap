# Hytale BetterMap Plugin

<img width="2582" height="720" alt="bettermap_banner" src="https://github.com/user-attachments/assets/70cd978d-6ded-4382-aefa-93c1600e183a" />

**Enhance your world exploration with saved map data, customizable zoom, waypoints, shared mapping, and performance-optimized rendering.**

---

## License

**ALL RIGHTS RESERVED.**

ASK US BEFORE IF YOU WANT TO PUBLISH A FORKED VERSION OF THIS PLUGIN.
YOU CAN FORK IT AS YOU WANT FOR A PRIVATE USE, OR CONTRIBUTE TO THE ORIGINAL PROJECT.

---

## What is this?

By default, the Hytale in-game map is fleeting. It only displays a small circular area around the player, and as soon as you walk away, the map "forgets" where you have been.

**Hytale BetterMap** changes this. This server plugin introduces a saved exploration feature. As you travel through the world, the plugin records the areas you have visited, effectively removing the "fog of war" permanently. Your map grows bigger the more you explore, allowing you to retrace your steps and navigate with ease.

## Key Features

1.  **Persistent Exploration:** The map retains all previously visited areas across sessions. Data is saved automatically to prevent loss during server crashes.
2.  **Waypoint System:** Never lose a location again. Open the waypoint menu to add markers at your current position, customize their names and colors, and share them with other players.
3.  **Teleport:** Players with permission can teleport directly to their saved waypoints.
4.  **Linked Exploration (Shared Map):** Optionally enable a shared map mode where all players contribute to a single global map, allowing you to see areas discovered by friends in real-time.
5.  **Compass Radar:** Easily locate other players nearby directly on your compass. The range can be customized or toggled off by admins.
6.  **Location Overlay:** Display your current coordinates and direction on-screen via a toggleable HUD (`/bm location`). (_Temporarily disabled for compatibility fixes_)
7.  **Customizable Zoom:** You are no longer locked to the default zoom. Set your own Minimum (zoom out) and Maximum (zoom in) scales. Settings are saved per player.
8.  **Multi-World Support:** Whitelist specific worlds for the mod to track, resolving compatibility issues with server hosts (like Apex) that change default world names.

## Performance & Optimization

*   **Dynamic Chunk Loading:** The plugin intelligently manages memory by loading only the explored chunks nearest to the player and unloading distant ones.
*   **Map Quality Settings:** Admins can balance visual fidelity and performance by choosing between `LOW`, `MEDIUM`, or `HIGH` quality.
*   `LOW`: Loads up to 30,000 chunks (8x8 resolution).
*   `MEDIUM`: Loads up to 10,000 chunks (16x16 resolution).
*   `HIGH`: Loads up to 3,000 chunks (32x32 resolution).

_Note: High quality strictly limits loaded chunks to prevent memory errors._

You can also manually set the maximum number of loaded chunks via `/bm config maxchunk`, within recommended limits.

## Commands & Permissions

The command system has been updated. Basic features are now available to all players by default, while configuration commands require specific permissions.

You can either do `/op add <player>` to give full access or assign specific permissions as needed using `/perm group add Adventure <permission>`.

### Public Commands (No Permission Required)

1.  `/bettermap` (or `/bm`)
    
    *   Displays current map settings and status.
2.  `/bm waypoint menu` or `/waypoint` or `/wp`
    
    *   Opens the Waypoint UI to manage, share, or delete waypoints.
3.  `/bm min <value>`
    
    *   Sets your personal minimum zoom scale (default base is 32).
4.  `/bm max <value>`
    
    *   Sets your personal maximum zoom scale (default base is 256).

### Global Waypoints

**Permission:** `dev.ninesliced.bettermap.command.waypoint.global`

*   Allows the user to create, view, and manage global (shared) waypoints visible to all players.

### Teleportation

**Permission:** `dev.ninesliced.bettermap.command.teleport`

*   **Waypoint Teleport:** Allows the user to teleport to locations via the Waypoint UI buttons.

### Admin & Configuration Commands

**Permission:** `dev.ninesliced.bettermap.command.config`

1.  `/bm config radar <range>`
    
    *   Sets the radar range (use `-1` for infinite).
2.  `/bm config location`
    
    *   Toggles the server-wide default for the location HUD.
3.  `/bm config hideplayers`
    
    *   Hides player cursors on the map.
4.  `/bm config hidewarps`
    
    *   Hides other players' warps on the map.
5.  `/bm config hideunexploredwarps`
    
    *   Hides warps in unexplored regions.
6.  `/bm config hidepois`
    
    *   Hides all POI markers on the map.
7.  `/bm config hideunexploredpoi`
    
    *   Hides POIs in unexplored regions.
8.  `/bm config waypointteleport`
    
    *   Toggles the ability to teleport to waypoints.
9.  `/bm config markerteleport`
    
    *   Toggles the ability to teleport to map markers.
10.  `/bm config shareallexploration`
    
    *   Toggles "Linked Map" mode (shared exploration data).
11.  `/bm config track` / `untrack`
    
    *   Adds or removes the current world from the active whitelist.
12.  `/bm config maxchunk <number>`
    
    *   Manually overrides the maximum number of loaded chunks.
13.  `/bm config autosave <minutes>`
    
    *   Sets the interval for auto-saving map data.

### Reload Command

**Permission:** `dev.ninesliced.bettermap.command.reload`

*   `/bm reload`
*   Reloads the configuration file immediately.

## Configuration & Data Storage

All plugin files are located within the server's `mods` directory.

### Configuration File

You can modify the plugin settings in `mods/bettermap/config.json`.

_Note: Changing `mapQuality` or `maxChunksToLoad` requires a server restart to take effect._

**Default Configuration:**

```
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
  "hideOtherWarpsOnMap": false,
  "hideUnexploredWarpsOnMap": true,
  "allowWaypointTeleports": true,
  "allowMapMarkerTeleports": true,
  "hideAllPoiOnMap": false,
  "hideUnexploredPoiOnMap": true,
  "hiddenPoiNames": [],
  "autoSaveInterval": 5,
  "allowedWorlds": [
    "default",
    "world"
  ]
}
```

### Saved Exploration Data

Map data is saved per world. You can find the saved exploration files here: `mods/bettermap/data/`

## Examples:

*   Waypoint list using command `/bettermap waypoint menu`:

![alt text](https://media.forgecdn.net/attachments/1473/704/waypoint-menu-png.png)

*   Waypoint on map: ![alt text](https://media.forgecdn.net/attachments/1473/705/waypoint-example-png.png)
*   Waypoint edit menu: ![alt text](https://media.forgecdn.net/attachments/1473/706/waypoint-edit-png.png)
*   High Quality map: ![alt text](https://media.forgecdn.net/attachments/1467/936/capture-decran-2026-01-15-181745.png)
*   Medium Quality map: ![alt text](https://media.forgecdn.net/attachments/1470/4/example-map-quality-medium-png.png)
*   Low Quality map: ![alt text](https://media.forgecdn.net/attachments/1470/2/example-map-quality-low-png.png)

## Credits

This project was created to improve the exploration quality of life in Hytale.

*   **Created by:** Paralaxe and Theobosse
*   **Contributors:** Xytronix
*   **Team:** [Ninesliced](https://ninesliced.com/)

***

_Found a bug? Have a suggestion? Please report it in the comments or on our discord server!_
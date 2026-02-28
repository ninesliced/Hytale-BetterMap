# Hytale BetterMap Plugin

<img width="2582" height="720" alt="bettermap_banner" src="https://github.com/user-attachments/assets/70cd978d-6ded-4382-aefa-93c1600e183a" />

**Enhance your world exploration with saved map data, cave map rendering, customizable zoom, waypoints, shared mapping, and performance-optimized rendering.**

---

<a href="https://zap-hosting.com/ninesliced?voucher=ninesliced"><img src="https://media.forgecdn.net/attachments/1538/710/zap-hosting-collaboration-banner-png.png" alt="ZAP-Hosting Gameserver and Webhosting"></a>

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
2.  **Cave Mode:** Seamlessly switch to cave chunks when entering underground areas. Includes options to view surface data while mining or use a specific "Fog of War" for caves.
3.  **Enhanced Waypoint System:** Manage locations using Hytale's native markers. Includes a user-friendly menu, "Here" button for quick updates, and context-menu editing options.
4.  **Interactive Teleportation:** Players with permission can teleport to waypoints via the UI or by simply right-clicking a location on the map.
5.  **World Border Visualization:** View the server's world border limits directly on your map.
6.  **Linked Exploration (Shared Map):** Optionally enable a shared map mode where all players contribute to a single global map, allowing you to see areas discovered by friends in real-time.
7.  **Compass/Map player Radar:** Easily locate other players nearby directly on your compass. The range can be customized or toggled off by admins.
8.  **Customizable HUD & UI:** A new in-game configuration menu (`/bm`) allows you to adjust settings, manage waypoints, and access help without doing other commands.
10. **Position HUD:** A Position HUD (compatible with MultipleHUD) that displays your coordinates on-screen.
9.  **Map Anchors:** Quick-access shortcuts on the map UI for opening the waypoint and configuration menus.
10. **Multi-World Support:** Whitelist specific worlds for the mod to track, resolving compatibility issues with server hosts that change default world names. Auto-tracks the world on first join.

## Performance & Optimization

Thanks to major optimizations, the map can now handle significantly higher chunk counts without performance loss.

* **Dynamic Chunk Loading:** The plugin intelligently manages memory by loading only the explored chunks nearest to the player and unloading distant ones.
* **Map Quality Settings:** Admins can balance visual fidelity and performance by choosing between `LOW`, `MEDIUM`, or `HIGH` quality.
* `LOW`: Loads up to **80,000** chunks (8x8 resolution).
* `MEDIUM`: Loads up to **25,000** chunks (16x16 resolution).
* `HIGH`: Loads up to **8,000** chunks (32x32 resolution).

_Note: High quality strictly limits loaded chunks to prevent memory errors._

You can also manually set the maximum number of loaded chunks via `/bm config maxchunk`, within recommended limits or using our UI config menu with `/bm` and in the admin tab (need admin permission).

## Commands & Permissions

The command system has been overhauled with a new UI-based configuration. Basic features are available to all players, while admin features require specific permissions.

You can either do `/op add <player>` to give full access or assign specific permissions as needed using `/perm group add Adventure <permission>`.

### Public Commands (No Permission Required)

1.  `/bettermap` (or `/bm`)
    
    * Opens the main **BetterMap Configuration UI** to manage user settings and view help.
2.  `/bm waypoint menu` or `/waypoint` or `/wp`
    
    * Opens the Waypoint UI to manage, share, or delete waypoints.
3.  `/bm min <value>`
    
    * Sets your personal minimum zoom scale (default base is 32).
4.  `/bm max <value>`
    
    * Sets your personal maximum zoom scale (default base is 256).

### Admin

**Permission:** `bettermap.admin`

* Grants full admin access to BetterMap. Allows viewing all settings and overrides under `/bettermap`.

### Teleportation

**Permission:** `bettermap.command.teleport`

* **Map Teleport:** Allows the user to teleport to locations via the Waypoint UI buttons or by **right-clicking** anywhere on the map.

### Global Shared Waypoint Edit/Delete

**Permission:** `bettermap.command.waypoint.editglobal`

* Allows editing/deleting shared waypoints created by other players.
* Marker owners can always edit/delete their own shared waypoints.
* This permission overrides the global toggle if the toggle is disabled.

### Map Privacy Overrides

**Permissions:**

* `bettermap.command.override.players` - Bypass global hide players
* `bettermap.command.override.warps` - Bypass global hide all/other warps
* `bettermap.command.override.unexploredwarps` - Bypass global hide unexplored warps
* `bettermap.command.override.poi` - Bypass global hide all POIs and hidden POI names
* `bettermap.command.override.unexploredpoi` - Bypass global hide unexplored POIs
* `bettermap.command.override.spawn` - Bypass global hide spawn
* `bettermap.command.override.death` - Bypass global hide death markers
* `bettermap.command.override.waypoints` - Bypass global hide waypoints

### Configuration Commands

**Permission:** `bettermap.command.config`

* Required to use `/bm config` subcommands and to access admin settings in the UI.

1.  `/bm config radar <range>`
    
    * Sets the radar range (use `-1` for infinite).
2.  `/bm config location`
    
    * Toggles the server-wide default for the location HUD.
3.  `/bm config hideplayers`
    
    * Hides player cursors on the map.
4.  `/bm config hidewarps`
    
    * Hides other players' warps on the map.
5.  `/bm config hideunexploredwarps`
    
    * Hides warps in unexplored regions.
6.  `/bm config hidepois`
    
    * Hides all POI markers on the map.
7.  `/bm config hideunexploredpoi`
    
    * Hides POIs in unexplored regions.
8.  `/bm config hideglobalwaypoints`

    * Hides global waypoints on the map for all players.
9.  `/bm config editglobalwaypoints`

    * Toggles whether all players can edit/delete shared waypoints created by others.
10. `/bm config waypointteleport`

    * Toggles the ability to teleport to waypoints.
11. `/bm config markerteleport`

    * Toggles the ability to teleport to map markers.
12. `/bm config shareallexploration`

    * Toggles "Linked Map" mode (shared exploration data).
13. `/bm config track` / `untrack`

    * Adds or removes the current world from the active whitelist.
14. `/bm config maxchunk <number>`

    * Manually overrides the maximum number of loaded chunks.
15. `/bm config autosave <minutes>`

    * Sets the interval for auto-saving map data.
16. `/bm config worldborder`
    
    * Toggles the world border visualization on the map.
17. `/bm config worldborderradius <radius>`
    
    * Sets the world border radius in blocks.
18. `/bm config worldborderoffset <x> <z>`
    
    * Sets the world border center offset (X and Z coordinates).

### Reload Command

**Permission:** `bettermap.command.reload`

* `/bm reload`
* Reloads the configuration file immediately.

## Configuration & Data Storage

All plugin files are located within the server's `mods` directory.

### Configuration File

You can modify the plugin settings in `mods/bettermap/config.json`.

_Note: Changing `mapQuality` or `maxChunksToLoad` requires a server restart to take effect._

**Default Configuration:**

```json
{
  "explorationRadius": 16,
  "updateRateMs": 500,
  "mapQuality": "MEDIUM",
  "minScale": 10.0,
  "maxScale": 256.0,
  "debug": false,
  "locationEnabled": true,
  "locationHudPosition": "top_right",
  "shareAllExploration": false,
  "maxChunksToLoad": 25000,
  "radarEnabled": true,
  "radarRange": -1,
  "hidePlayersOnMap": false,
  "hideAllWarpsOnMap": false,
  "hideOtherWarpsOnMap": false,
  "hideUnexploredWarpsOnMap": true,
  "allowWaypointTeleports": true,
    "allowGlobalWaypointEditsForEveryone": false,
  "allowMapMarkerTeleports": true,
  "hideAllPoiOnMap": false,
  "hideUnexploredPoiOnMap": true,
  "hideSpawnOnMap": false,
  "hideDeathMarkerOnMap": false,
  "hideGlobalWaypointsOnMap": false,
  "hiddenPoiNames": [],
  "autoSaveInterval": 5,
  "maxPersonalMarkersPerPlayer": 12,
  "maxSharedMarkersPerPlayer": 12,
  "allowedWorlds": [
    "default",
    "world"
  ],
  "caveModeEnabled": true,
  "caveModeLayerSize": 10,
  "caveModeUndergroundThreshold": 100,
  "caveModeRadius": 4,
  "discoverSurfaceUnderground": false,
  "caveFogOfWar": false,
  "worldBorderEnabled": false,
  "worldBorderRadius": 5000,
  "worldBorderOffsetX": 0,
  "worldBorderOffsetZ": 0
}
```

### Saved Exploration Data

Map data is saved per world. You can find the saved exploration files here: `mods/bettermap/data/`

## Examples:

* BetterMap config menu (using `/bm`):

![alt text](https://media.forgecdn.net/attachments/1538/760/bettermap_config_ui-png.png)

* BetterMap help menu (using `/bm help`):

![help](https://media.forgecdn.net/attachments/1538/761/bettermap_help_ui-png.png)

*   Waypoint list using command `/bettermap waypoint menu`:

![alt text](https://media.forgecdn.net/attachments/1473/704/waypoint-menu-png.png)

*   Waypoint on map:

![alt text](https://media.forgecdn.net/attachments/1538/758/waypoint_create-png.png) ![alt text](https://media.forgecdn.net/attachments/1538/757/waypoint_conextmenu-png.png)

*   Waypoint edit menu:

![alt text](https://media.forgecdn.net/attachments/1538/759/waypoint_edit-png.png)
*   Cave mode:

<div style="display: flex; justify-content: space-between;">
  <img src="https://media.forgecdn.net/attachments/1538/742/cave_mode_2-png.png" alt="Description 1" style="width: 50%;">
  <img src="https://media.forgecdn.net/attachments/1538/743/cave_mode-png.png" alt="Description 2" style="width: 49%;">
</div>

*   High Quality map:

![alt text](https://media.forgecdn.net/attachments/1467/936/capture-decran-2026-01-15-181745.png)
*   Medium Quality map:

![alt text](https://media.forgecdn.net/attachments/1470/4/example-map-quality-medium-png.png)
*   Low Quality map:

![alt text](https://media.forgecdn.net/attachments/1470/2/example-map-quality-low-png.png)

## Credits

This project was created to improve the exploration quality of life in Hytale.

*   **Created by:** Paralaxe and Theobosse
*   **Contributors:** Xytronix and DARKACE
*   **Team:** [Ninesliced](https://ninesliced.com/)

***

_Found a bug? Have a suggestion? Please report it in the comments or on our discord server!_ [Our discord link](https://discord.gg/8q7NCUm4xw)
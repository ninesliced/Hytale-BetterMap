# Hytale BetterMap Plugin

**Enhance your world exploration with saved map data, customizable zoom, waypoints, shared mapping, and performance-optimized rendering.**

## License

**ALL RIGHTS RESERVED.**

ASK US BEFORE IF YOU WANT TO PUBLISH A FORKED VERSION OF THIS PLUGIN.
YOU CAN FORK IT AS YOU WANT FOR A PRIVATE USE, OR CONTRIBUTE TO THE ORIGINAL PROJECT.

---

## What is this?

By default, the Hytale in-game map is fleeting. It only displays a small circular area around the player, and as soon as you walk away, the map "forgets" where you have been.

**Hytale BetterMap** changes this. This server plugin introduces a saved exploration feature. As you travel through the world, the plugin records the areas you have visited, effectively removing the "fog of war" permanently. Your map grows bigger the more you explore, allowing you to retrace your steps and navigate with ease.

## Key Features

* **Persistent Exploration:** The map retains all previously visited areas across sessions. Data is saved automatically to prevent loss during server crashes.
* **Waypoint System:** Never lose a location again. Open the waypoint menu to add markers at your current position, customize their names and colors, and share them with other players.
* **Teleport:** Players with permission can teleport directly to their saved waypoints.


* **Linked Exploration (Shared Map):** Optionally enable a shared map mode where all players contribute to a single global map, allowing you to see areas discovered by friends in real-time.
* **Compass Radar:** Easily locate other players nearby directly on your compass. The range can be customized or toggled off by admins.
* **Location Overlay:** Display your current coordinates and direction on-screen via a toggleable HUD (`/bm location`).
* **Customizable Zoom:** You are no longer locked to the default zoom. Set your own Minimum (zoom out) and Maximum (zoom in) scales. Settings are saved per player.
* **Multi-World Support:** Whitelist specific worlds for the mod to track, resolving compatibility issues with server hosts (like Apex) that change default world names.

## Performance & Optimization

* **Dynamic Chunk Loading:** The plugin intelligently manages memory by loading only the explored chunks nearest to the player and unloading distant ones.
* **Map Quality Settings:** Admins can balance visual fidelity and performance by choosing between `LOW`, `MEDIUM`, or `HIGH` quality.
* `LOW`: Loads up to 30,000 chunks (8x8 resolution).
* `MEDIUM`: Loads up to 10,000 chunks (16x16 resolution).
* `HIGH`: Loads up to 3,000 chunks (32x32 resolution). 

*Note: High quality strictly limits loaded chunks to prevent memory errors.*

You can also manually set the maximum number of loaded chunks via `/bm config maxchunk`, within recommended limits.


## Commands & Permissions

The command system has been updated to separate standard player features from administrative configuration.

You can either do `/op add <player>` to give full access or assign specific permissions as needed using `/perm group add Adventure <permission>`.

### Player Commands

**Permission:** `dev.ninesliced.bettermap.command.base`

* `/bettermap` (or `/bm`)
* Displays current map settings and status.


* `/bm waypoint` (or `/bm menu`)
* Opens the Waypoint UI to manage, share, or delete waypoints.


* `/bm location`
* Toggles the personal coordinate display HUD.


* `/bm min <value>`
* Sets your personal minimum zoom scale (default base is 32).


* `/bm max <value>`
* Sets your personal maximum zoom scale (default base is 256).



### Teleportation

**Permission:** `dev.ninesliced.bettermap.command.base.teleport`

* **Waypoint Teleport:** Allows the user to teleport to locations via the Waypoint UI buttons.

### Admin & Configuration Commands

**Permission:** `dev.ninesliced.bettermap.command.base.config`

* `/bm config radar <range>`
* Sets the radar range (use `-1` for infinite).


* `/bm config location`
* Toggles the server-wide default for the location HUD.


* `/bm config hideplayers`
* Hides player cursors on the map.

* `/bm config waypointteleport`
* Toggles waypoint teleports.

* `/bm config markerteleport`
* Toggles map marker teleports.


* `/bm config shareallexploration`
* Toggles "Linked Map" mode (shared exploration data).


* `/bm config track` / `untrack`
* Adds or removes the current world from the active whitelist.


* `/bm config maxchunk <number>`
* Manually overrides the maximum number of loaded chunks.


* `/bm config autosave <minutes>`
* Sets the interval for auto-saving map data.


* `/bm reload`
* Reloads the configuration file immediately.



## Configuration & Data Storage

All plugin files are located within the server's `mods` directory.

### Configuration File

You can modify the plugin settings in `mods/bettermap/config.json`.

*Note: Changing `mapQuality` or `maxChunksToLoad` requires a server restart to take effect.*

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
  "shareAllExploration": false,
  "maxChunksToLoad": 10000,
  "radarEnabled": true,
  "radarRange": -1,
  "hidePlayersOnMap": false,
  "allowWaypointTeleports": true,
  "allowMapMarkerTeleports": true,
  "autoSaveInterval": 5,
  "allowedWorlds": [
    "default",
    "world"
  ]
}

```

### Saved Exploration Data

Map data is saved per world. You can find the saved exploration files here: `mods/bettermap/data/`

## Credits

This project was created to improve the exploration quality of life in Hytale.

* **Created by:** Paralaxe and Theobosse
* **Team:** [Ninesliced](https://ninesliced.com/)

---

*Found a bug? Have a suggestion? Please report it in the comments!*
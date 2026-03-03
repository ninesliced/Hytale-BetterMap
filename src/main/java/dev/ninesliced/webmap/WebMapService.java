package dev.ninesliced.webmap;

import dev.ninesliced.BetterMap;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.webmap.auth.WebMapAuthService;
import dev.ninesliced.webmap.data.WorldDataCollector;
import dev.ninesliced.webmap.handlers.BatchTileHandler;
import dev.ninesliced.webmap.handlers.IconHandler;
import dev.ninesliced.webmap.handlers.PreloadHandler;
import dev.ninesliced.webmap.handlers.TileHandler;
import dev.ninesliced.webmap.handlers.WorldDataHandler;
import dev.ninesliced.webmap.preload.WebMapPreloadService;
import dev.ninesliced.webmap.tiles.TileManager;
import dev.ninesliced.webmap.tiles.TileQuality;

import java.util.logging.Logger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Coordinates web map server lifecycle, tile rendering, and realtime websocket broadcasting.
 */
public class WebMapService {
    private static final Logger LOGGER = Logger.getLogger(WebMapService.class.getName());

    private final TileManager tileManager;
    private final WorldDataCollector worldDataCollector;
    private final WebMapAuthService authService;
    private final WebMapPreloadService preloadService;
    private final LiveDataBroadcaster liveDataBroadcaster;
    private final WorldDataHandler worldDataHandler;
    private final IconHandler iconHandler;
    private final TileHandler tileHandler;
    private final BatchTileHandler batchTileHandler;
    private final PreloadHandler preloadHandler;
    private final WebServer webServer;

    public WebMapService(BetterMap plugin) {
        TileQuality defaultQuality = TileQuality.fromConfig(ModConfig.getInstance().getMapQuality());
        this.tileManager = new TileManager(plugin);
        this.worldDataCollector = new WorldDataCollector();
        this.authService = new WebMapAuthService();
        this.preloadService = new WebMapPreloadService(tileManager);
        this.liveDataBroadcaster = new LiveDataBroadcaster(worldDataCollector, preloadService);
        this.worldDataHandler = new WorldDataHandler(worldDataCollector);
        this.iconHandler = new IconHandler();
        this.tileHandler = new TileHandler(tileManager, defaultQuality);
        this.batchTileHandler = new BatchTileHandler(tileManager, defaultQuality);
        this.preloadHandler = new PreloadHandler(preloadService, defaultQuality);
        this.webServer = new WebServer(this);
    }

    public void start() {
        if (isRunning()) {
            return;
        }
        int port = ModConfig.getInstance().getWebMapPort();
        webServer.start(port);
        liveDataBroadcaster.start();
        LOGGER.info("WebMap server started on port " + port);
    }

    public void stop() {
        liveDataBroadcaster.shutdown();
        webServer.shutdown();
        LOGGER.info("WebMap server stopped");
    }

    public void shutdown() {
        stop();
        preloadService.shutdown();
        tileManager.shutdown();
    }

    public boolean isRunning() {
        return webServer.isRunning();
    }

    public void pushLiveUpdate() {
        liveDataBroadcaster.broadcastNow();
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + ModConfig.getInstance().getWebMapPort() + "/";
    }

    public String createLoginCode(UUID uuid, String username) {
        return authService.createLoginCode(uuid, username == null || username.isBlank() ? uuid.toString() : username);
    }

    public String createAutoLoginUrl(UUID uuid, String username) {
        String code = createLoginCode(uuid, username);
        return getBaseUrl() + "login?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
            + "&redirect_url=" + URLEncoder.encode("/", StandardCharsets.UTF_8);
    }

    HttpRequestHandler createHttpRequestHandler(boolean secure) {
        return new HttpRequestHandler(tileHandler, batchTileHandler, preloadHandler, worldDataHandler, iconHandler, liveDataBroadcaster, authService, secure);
    }
}

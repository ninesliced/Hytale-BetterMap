package dev.ninesliced.webmap;

import dev.ninesliced.webmap.auth.WebMapAuthService;
import dev.ninesliced.webmap.auth.WebMapViewer;
import dev.ninesliced.webmap.handlers.BatchTileHandler;
import dev.ninesliced.webmap.handlers.IconHandler;
import dev.ninesliced.webmap.handlers.PreloadHandler;
import dev.ninesliced.webmap.handlers.StaticHandler;
import dev.ninesliced.webmap.handlers.TileHandler;
import dev.ninesliced.webmap.handlers.WorldDataHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for HTTP routes, authentication, and websocket upgrade handling.
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final TileHandler tileHandler;
    private final BatchTileHandler batchTileHandler;
    private final PreloadHandler preloadHandler;
    private final WorldDataHandler worldDataHandler;
    private final IconHandler iconHandler;
    private final StaticHandler staticHandler;
    private final LiveDataBroadcaster broadcaster;
    private final WebMapAuthService authService;
    private final boolean secure;

    public HttpRequestHandler(TileHandler tileHandler,
                              BatchTileHandler batchTileHandler,
                              PreloadHandler preloadHandler,
                              WorldDataHandler worldDataHandler,
                              IconHandler iconHandler,
                              LiveDataBroadcaster broadcaster,
                              WebMapAuthService authService,
                              boolean secure) {
        this.tileHandler = tileHandler;
        this.batchTileHandler = batchTileHandler;
        this.preloadHandler = preloadHandler;
        this.worldDataHandler = worldDataHandler;
        this.iconHandler = iconHandler;
        this.broadcaster = broadcaster;
        this.authService = authService;
        this.staticHandler = new StaticHandler();
        this.secure = secure;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String uri = req.uri();
        String pathOnly = pathOnly(uri);

        WebMapViewer viewer = authService.authenticate(req);

        if ("/login".equals(pathOnly)) {
            handleLogin(ctx, req, viewer);
            return;
        }

        if ("/logout".equals(pathOnly)) {
            handleLogout(ctx, req);
            return;
        }

        if (viewer == null) {
            if (pathOnly.startsWith("/api/") || "/ws".equals(pathOnly)) {
                sendError(ctx, HttpResponseStatus.UNAUTHORIZED);
            } else {
                redirect(ctx, "/login?redirect_url=" + encodeForQuery(uri), null);
            }
            return;
        }

        if ("/ws".equals(pathOnly) && isWebSocketUpgrade(req)) {
            handleWebSocketUpgrade(ctx, req, viewer);
            return;
        }

        if ("/api/tiles/batch".equals(pathOnly)) {
            if (req.method() == HttpMethod.OPTIONS) {
                handleCorsPreflight(ctx);
            } else {
                batchTileHandler.handle(ctx, req, viewer);
            }
            return;
        }

        if ("/api/preload/start".equals(pathOnly)) {
            if (req.method() == HttpMethod.OPTIONS) {
                handleCorsPreflight(ctx);
            } else {
                preloadHandler.handleStart(ctx, req, viewer);
            }
            return;
        }

        if ("/api/preload/status".equals(pathOnly)) {
            if (req.method() == HttpMethod.OPTIONS) {
                handleCorsPreflight(ctx);
            } else {
                preloadHandler.handleStatus(ctx, req, viewer);
            }
            return;
        }

        if ("/api/preload/stop".equals(pathOnly)) {
            if (req.method() == HttpMethod.OPTIONS) {
                handleCorsPreflight(ctx);
            } else {
                preloadHandler.handleStop(ctx, req, viewer);
            }
            return;
        }

        if (pathOnly.startsWith("/api/tiles/")) {
            tileHandler.handle(ctx, req, viewer);
            return;
        }

        if ("/api/worlds".equals(pathOnly)) {
            worldDataHandler.handleWorlds(ctx, req);
            return;
        }

        if (pathOnly.startsWith("/api/worlds/") && pathOnly.endsWith("/snapshot")) {
            worldDataHandler.handleSnapshot(ctx, req, viewer);
            return;
        }

        if (pathOnly.startsWith("/api/icons/")) {
            iconHandler.handle(ctx, req);
            return;
        }

        staticHandler.handle(ctx, req);
    }

    private void handleLogin(ChannelHandlerContext ctx, FullHttpRequest req, WebMapViewer viewer) {
        if (req.method() == HttpMethod.GET) {
            if (viewer != null) {
                redirect(ctx, "/", null);
                return;
            }
            String queryCode = queryParam(req.uri(), "code");
            if (!queryCode.isBlank()) {
                WebMapViewer loggedIn = authService.consumeLoginCode(queryCode);
                if (loggedIn != null) {
                    String redirectTarget = sanitizeRedirect(queryParam(req.uri(), "redirect_url"));
                    String setCookie = authService.createSessionCookieHeader(loggedIn);
                    redirect(ctx, redirectTarget, setCookie);
                    return;
                }
            }
            String error = queryParam(req.uri(), "error");
            String redirectUrl = queryParam(req.uri(), "redirect_url");
            sendLoginPage(ctx, error, redirectUrl, HttpResponseStatus.OK);
            return;
        }

        if (req.method() != HttpMethod.POST) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        Map<String, String> form = parseForm(req.content().toString(StandardCharsets.UTF_8));
        String loginCode = valueOrEmpty(form.get("loginCode"));
        if (loginCode.isBlank()) {
            loginCode = valueOrEmpty(form.get("code"));
        }

        WebMapViewer loggedIn = authService.consumeLoginCode(loginCode);
        if (loggedIn == null) {
            String redirectUrl = valueOrEmpty(form.get("redirect_url"));
            sendLoginPage(ctx, "Login code invalid or expired.", redirectUrl, HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        String redirectTarget = sanitizeRedirect(valueOrEmpty(form.get("redirect_url")));
        String setCookie = authService.createSessionCookieHeader(loggedIn);
        redirect(ctx, redirectTarget, setCookie);
    }

    private void handleLogout(ChannelHandlerContext ctx, FullHttpRequest req) {
        String clearCookie = authService.clearSessionCookieHeader(req);
        redirect(ctx, "/login", clearCookie);
    }

    private boolean isWebSocketUpgrade(FullHttpRequest req) {
        String upgrade = req.headers().get(HttpHeaderNames.UPGRADE);
        return upgrade != null && upgrade.equalsIgnoreCase("websocket");
    }

    private void handleWebSocketUpgrade(ChannelHandlerContext ctx, FullHttpRequest req, WebMapViewer viewer) {
        String protocol = secure ? "wss" : "ws";
        String wsUrl = protocol + "://" + req.headers().get(HttpHeaderNames.HOST) + "/ws";
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(wsUrl, null, false);
        WebSocketServerHandshaker handshaker = factory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            return;
        }

        handshaker.handshake(ctx.channel(), req);
        broadcaster.addChannel(ctx.channel(), viewer);
        ctx.pipeline().replace(this, "websocket", new WebSocketHandler(broadcaster, handshaker));
    }

    private void sendLoginPage(ChannelHandlerContext ctx, String error, String redirectUrl, HttpResponseStatus status) {
        String safeError = escapeHtml(valueOrEmpty(error));
        String safeRedirect = escapeHtml(sanitizeRedirect(valueOrEmpty(redirectUrl)));

        String html = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
            + "<title>BetterMap Login</title>"
            + "<style>body{font-family:Arial,sans-serif;background:#111827;color:#e5e7eb;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;}"
            + ".card{background:#1f2937;padding:24px;border-radius:12px;max-width:420px;width:100%;box-sizing:border-box;}"
            + "input,button{width:100%;box-sizing:border-box;padding:10px;border-radius:8px;border:1px solid #374151;}"
            + "input{background:#111827;color:#e5e7eb;margin:8px 0 12px 0;}"
            + "button{background:#2563eb;color:white;cursor:pointer;border:none;}"
            + ".error{background:#7f1d1d;color:#fecaca;padding:8px;border-radius:8px;margin-bottom:12px;}"
            + "small{color:#9ca3af;display:block;margin-top:8px;}</style></head><body>"
            + "<div class=\"card\"><h2>BetterMap WebMap Login</h2>"
            + (safeError.isBlank() ? "" : "<div class=\"error\">" + safeError + "</div>")
            + "<form method=\"post\" action=\"/login\">"
            + "<input type=\"hidden\" name=\"redirect_url\" value=\"" + safeRedirect + "\">"
            + "<label for=\"loginCode\">Login Code</label>"
            + "<input id=\"loginCode\" name=\"loginCode\" type=\"text\" autocomplete=\"off\" required placeholder=\"ABCD-1234\">"
            + "<button type=\"submit\">Log In</button>"
            + "</form><small>Generate a code in-game via /bm webmapcode create</small></div></body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8")
            .set(HttpHeaderNames.CONTENT_LENGTH, bytes.length)
            .set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void redirect(ChannelHandlerContext ctx, String location, String setCookieHeader) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers()
            .set(HttpHeaderNames.LOCATION, location)
            .set(HttpHeaderNames.CONTENT_LENGTH, 0)
            .set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        if (setCookieHeader != null && !setCookieHeader.isBlank()) {
            response.headers().add(HttpHeaderNames.SET_COOKIE, setCookieHeader);
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void handleCorsPreflight(ChannelHandlerContext ctx) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers()
            .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS")
            .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type")
            .set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String pathOnly(String uri) {
        int queryIndex = uri.indexOf('?');
        return queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
    }

    private String queryParam(String uri, String key) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        List<String> values = decoder.parameters().get(key);
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.get(0);
    }

    private String sanitizeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/")) {
            return "/";
        }
        return redirect;
    }

    private String encodeForQuery(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        if (body == null || body.isBlank()) {
            return values;
        }

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private String escapeHtml(String raw) {
        return raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}

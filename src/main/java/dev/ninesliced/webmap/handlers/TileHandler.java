package dev.ninesliced.webmap.handlers;

import dev.ninesliced.webmap.tiles.TileManager;
import dev.ninesliced.webmap.tiles.TileQuality;
import dev.ninesliced.webmap.auth.WebMapAccessPolicy;
import dev.ninesliced.webmap.auth.WebMapViewer;
import dev.ninesliced.webmap.data.WebViewFilter;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles direct single-tile HTTP requests.
 */
public class TileHandler {
    private static final Pattern TILE_PATTERN = Pattern.compile("/api/tiles/([^/]+)/([^/]+)/(-?\\d+)/(-?\\d+)/(-?\\d+)\\.png");

    private final TileManager tileManager;
    private final TileQuality defaultQuality;

    public TileHandler(TileManager tileManager, TileQuality defaultQuality) {
        this.tileManager = tileManager;
        this.defaultQuality = defaultQuality;
    }

    public void handle(ChannelHandlerContext ctx, FullHttpRequest req, WebMapViewer viewer) {
        if (req.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        String uri = req.uri();
        String pathOnly = uri;
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            pathOnly = uri.substring(0, queryIndex);
        }

        Matcher matcher = TILE_PATTERN.matcher(pathOnly);
        if (!matcher.matches()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String worldName = matcher.group(1);
        TileQuality quality = TileQuality.parseOrDefault(matcher.group(2), defaultQuality);
        int zoom = Integer.parseInt(matcher.group(3));
        int x = Integer.parseInt(matcher.group(4));
        int z = Integer.parseInt(matcher.group(5));
        WebViewFilter requested = parseFilter(uri);
        WebViewFilter filter = WebMapAccessPolicy.enforceFilter(requested, viewer);
        boolean keepAlive = HttpUtil.isKeepAlive(req);

        tileManager.getTile(worldName, quality, zoom, x, z, filter.mode(), filter.playerUuid())
            .thenAccept(data -> {
                if (!ctx.channel().isActive()) {
                    return;
                }
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(data)
                );
                response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "image/png")
                    .set(HttpHeaderNames.CONTENT_LENGTH, data.length)
                    .set(HttpHeaderNames.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                    .set(HttpHeaderNames.PRAGMA, "no-cache")
                    .set(HttpHeaderNames.EXPIRES, "0")
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

                if (keepAlive) {
                    response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
                    ctx.writeAndFlush(response);
                } else {
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                }
            })
            .exceptionally(throwable -> {
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return null;
            });
    }

    private WebViewFilter parseFilter(String uri) {
        int queryIndex = uri.indexOf('?');
        if (queryIndex < 0 || queryIndex == uri.length() - 1) {
            return WebViewFilter.global();
        }

        String mode = null;
        String playerUuid = null;
        String[] params = uri.substring(queryIndex + 1).split("&");
        for (String param : params) {
            int eq = param.indexOf('=');
            if (eq <= 0 || eq >= param.length() - 1) {
                continue;
            }
            String key = param.substring(0, eq);
            String value = param.substring(eq + 1);
            if ("mode".equalsIgnoreCase(key)) {
                mode = value;
            } else if ("playerUuid".equalsIgnoreCase(key)) {
                playerUuid = value;
            }
        }
        return WebViewFilter.parse(mode, playerUuid);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0).set(HttpHeaderNames.CONNECTION, "close");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}

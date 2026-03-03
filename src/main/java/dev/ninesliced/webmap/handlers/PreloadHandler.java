package dev.ninesliced.webmap.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ninesliced.webmap.auth.WebMapViewer;
import dev.ninesliced.webmap.preload.WebMapPreloadService;
import dev.ninesliced.webmap.tiles.TileQuality;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;

/**
 * Handles preload control/status API endpoints.
 */
public class PreloadHandler {
    private static final Gson GSON = new Gson();

    private final WebMapPreloadService preloadService;
    private final TileQuality defaultQuality;

    public PreloadHandler(WebMapPreloadService preloadService, TileQuality defaultQuality) {
        this.preloadService = preloadService;
        this.defaultQuality = defaultQuality;
    }

    public void handleStart(ChannelHandlerContext ctx, FullHttpRequest req, WebMapViewer viewer) {
        if (req.method() != HttpMethod.POST) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        JsonObject body;
        try {
            body = JsonParser.parseString(req.content().toString(StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        if (!body.has("world")) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String worldName = body.get("world").getAsString();
        TileQuality quality = body.has("quality")
            ? TileQuality.parseOrDefault(body.get("quality").getAsString(), defaultQuality)
            : defaultQuality;

        sendJson(ctx, preloadService.start(viewer, worldName, quality));
    }

    public void handleStatus(ChannelHandlerContext ctx, FullHttpRequest req, WebMapViewer viewer) {
        if (req.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        sendJson(ctx, preloadService.status(viewer));
    }

    public void handleStop(ChannelHandlerContext ctx, FullHttpRequest req, WebMapViewer viewer) {
        if (req.method() != HttpMethod.POST) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        sendJson(ctx, preloadService.stop(viewer));
    }

    private void sendJson(ChannelHandlerContext ctx, Object payload) {
        byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bytes)
        );
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
            .set(HttpHeaderNames.CONTENT_LENGTH, bytes.length)
            .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS")
            .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}

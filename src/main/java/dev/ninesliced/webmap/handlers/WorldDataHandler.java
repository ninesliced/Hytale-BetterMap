package dev.ninesliced.webmap.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ninesliced.webmap.data.WorldDataCollector;
import dev.ninesliced.webmap.data.WebViewFilter;
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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles world metadata and snapshot API endpoints.
 */
public class WorldDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("/api/worlds/([^/]+)/snapshot");

    private final WorldDataCollector worldDataCollector;

    public WorldDataHandler(WorldDataCollector worldDataCollector) {
        this.worldDataCollector = worldDataCollector;
    }

    public void handleWorlds(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        sendJson(ctx, worldDataCollector.getWorlds());
    }

    public void handleSnapshot(ChannelHandlerContext ctx, FullHttpRequest req) {
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

        Matcher matcher = SNAPSHOT_PATTERN.matcher(pathOnly);
        if (!matcher.matches()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String worldName = matcher.group(1);
        Map<String, String> query = parseQuery(uri);
        WebViewFilter filter = WebViewFilter.parse(query.get("mode"), query.get("playerUuid"));
        sendJson(ctx, worldDataCollector.buildSnapshot(worldName, filter));
    }

    private Map<String, String> parseQuery(String uri) {
        Map<String, String> values = new HashMap<>();
        int index = uri.indexOf('?');
        if (index < 0 || index == uri.length() - 1) {
            return values;
        }
        String[] pairs = uri.substring(index + 1).split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq >= pair.length() - 1) {
                continue;
            }
            values.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return values;
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
            .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}

package dev.ninesliced.webmap.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ninesliced.webmap.data.WebViewFilter;
import dev.ninesliced.webmap.tiles.PngEncoder;
import dev.ninesliced.webmap.tiles.TileManager;
import dev.ninesliced.webmap.tiles.TileQuality;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

/**
 * Handles batched tile request payloads for efficient viewport loading.
 */
public class BatchTileHandler {
    private static final int MAX_BATCH_SIZE = 300;

    private final TileManager tileManager;
    private final TileQuality defaultQuality;

    public BatchTileHandler(TileManager tileManager, TileQuality defaultQuality) {
        this.tileManager = tileManager;
        this.defaultQuality = defaultQuality;
    }

    public void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() != HttpMethod.POST) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        JsonObject requestJson;
        try {
            requestJson = JsonParser.parseString(req.content().toString(StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        if (!requestJson.has("world") || !requestJson.has("tiles")) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String worldName = requestJson.get("world").getAsString();
        TileQuality quality = requestJson.has("quality")
            ? TileQuality.parseOrDefault(requestJson.get("quality").getAsString(), defaultQuality)
            : defaultQuality;
        WebViewFilter filter = WebViewFilter.parse(
            requestJson.has("mode") ? requestJson.get("mode").getAsString() : null,
            requestJson.has("playerUuid") ? requestJson.get("playerUuid").getAsString() : null
        );

        JsonArray tilesArray = requestJson.getAsJsonArray("tiles");
        if (tilesArray.size() > MAX_BATCH_SIZE) {
            sendError(ctx, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
            return;
        }

        List<TileCoord> coords = new ArrayList<>(tilesArray.size());
        for (int i = 0; i < tilesArray.size(); i++) {
            JsonObject tile = tilesArray.get(i).getAsJsonObject();
            int z = tile.get("z").getAsInt();
            int x = tile.get("x").getAsInt();
            int y = tile.get("y").getAsInt();
            coords.add(new TileCoord(z, x, y));
        }

        boolean keepAlive = HttpUtil.isKeepAlive(req);
        Map<String, CompletableFuture<byte[]>> futures = new LinkedHashMap<>();
        for (TileCoord coord : coords) {
            String key = coord.z + "/" + coord.x + "/" + coord.y;
            futures.put(key, tileManager.getTile(worldName, quality, coord.z, coord.x, coord.y, filter.mode(), filter.playerUuid()));
        }

        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
            .thenAccept(ignored -> {
                if (!ctx.channel().isActive()) {
                    return;
                }

                io.netty.buffer.ByteBuf payload = Unpooled.buffer(4096);
                byte[] emptyTileBytes = PngEncoder.encodeEmpty(quality.tileSize());
                int nonEmptyCount = 0;
                int emptyCount = 0;
                payload.writeByte(1);
                payload.writeInt(futures.size());
                for (Entry<String, CompletableFuture<byte[]>> entry : futures.entrySet()) {
                    String[] parts = entry.getKey().split("/");
                    int z = Integer.parseInt(parts[0]);
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);

                    byte[] data = entry.getValue().join();
                    payload.writeInt(z);
                    payload.writeInt(x);
                    payload.writeInt(y);
                    boolean isEmptyTile = data == null || data.length == 0 || Arrays.equals(data, emptyTileBytes);
                    if (isEmptyTile) {
                        emptyCount++;
                        payload.writeInt(0);
                    } else {
                        nonEmptyCount++;
                        payload.writeInt(data.length);
                        payload.writeBytes(data);
                    }
                }

                DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    payload
                );
                httpResponse.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream")
                    .set(HttpHeaderNames.CONTENT_LENGTH, payload.readableBytes())
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "POST, OPTIONS")
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type")
                    .set("X-Batch-Tiles", futures.size())
                    .set("X-Batch-NonEmpty", nonEmptyCount)
                    .set("X-Batch-Empty", emptyCount);

                if (keepAlive) {
                    httpResponse.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
                    ctx.writeAndFlush(httpResponse);
                } else {
                    ctx.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
                }
            })
            .exceptionally(throwable -> {
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return null;
            });
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0).set(HttpHeaderNames.CONNECTION, "close");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private record TileCoord(int z, int x, int y) {
    }
}

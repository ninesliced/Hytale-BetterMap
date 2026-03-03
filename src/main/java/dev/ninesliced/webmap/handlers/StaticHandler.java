package dev.ninesliced.webmap.handlers;

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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves static web map assets from classpath resources.
 */
public class StaticHandler {
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    public void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        String uri = req.uri();
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            uri = uri.substring(0, queryIndex);
        }
        if (uri.equals("/") || uri.isBlank()) {
            uri = "/index.html";
        }
        if (uri.contains("..")) {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }

        byte[] content = loadResource("/webmap" + uri);
        if (content == null) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        boolean keepAlive = HttpUtil.isKeepAlive(req);
        String cacheControl = uri.endsWith(".html") ? "no-cache" : "max-age=86400, immutable";

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(content)
        );
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, getContentType(uri))
            .set(HttpHeaderNames.CONTENT_LENGTH, content.length)
            .set(HttpHeaderNames.CACHE_CONTROL, cacheControl);

        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private byte[] loadResource(String path) {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                return null;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getContentType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0) {
            String ext = path.substring(dotIndex).toLowerCase();
            String mime = MIME_TYPES.get(ext);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0).set(HttpHeaderNames.CONNECTION, "close");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    static {
        MIME_TYPES.put(".html", "text/html; charset=UTF-8");
        MIME_TYPES.put(".css", "text/css; charset=UTF-8");
        MIME_TYPES.put(".js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put(".json", "application/json; charset=UTF-8");
        MIME_TYPES.put(".png", "image/png");
        MIME_TYPES.put(".svg", "image/svg+xml");
        MIME_TYPES.put(".ico", "image/x-icon");
    }
}

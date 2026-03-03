package dev.ninesliced.webmap.handlers;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Serves marker/player icon assets for the web map.
 */
public class IconHandler {
    private static final List<String> PLAYER_PREFIXES = List.of(
        "/Common/UI/Custom/Common/",
        "/Common/UI/Custom/Common"
    );

    private static final List<String> MARKER_PREFIXES = List.of(
        "/Common/UI/Custom/Common/",
        "/Common/UI/Custom/",
        "/Common/UI/",
        "/"
    );

    public void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        String uri = req.uri();
        String cleanUri = stripQuery(uri);

        boolean player = cleanUri.startsWith("/api/icons/player/");
        boolean marker = cleanUri.startsWith("/api/icons/marker/");
        if (!player && !marker) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String rawName = cleanUri.substring(cleanUri.lastIndexOf('/') + 1);
        String iconName = normalizeIconName(URLDecoder.decode(rawName, StandardCharsets.UTF_8));

        byte[] bytes = loadIcon(iconName, player ? PLAYER_PREFIXES : MARKER_PREFIXES);
        if (bytes == null) {
            bytes = loadIcon(fallbackPlayerIcon(iconName), PLAYER_PREFIXES);
        }
        if (bytes == null) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bytes)
        );
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "image/png")
            .set(HttpHeaderNames.CONTENT_LENGTH, bytes.length)
            .set(HttpHeaderNames.CACHE_CONTROL, "max-age=86400, immutable")
            .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private byte[] loadIcon(String iconName, List<String> prefixes) {
        String sanitized = normalizeIconName(iconName);
        for (String prefix : prefixes) {
            byte[] bytes = readResource(prefix + sanitized);
            if (bytes != null) {
                return bytes;
            }
        }

        int slash = sanitized.lastIndexOf('/');
        if (slash >= 0 && slash < sanitized.length() - 1) {
            String baseName = sanitized.substring(slash + 1);
            for (String prefix : prefixes) {
                byte[] bytes = readResource(prefix + baseName);
                if (bytes != null) {
                    return bytes;
                }
            }
        }

        byte[] direct = readResource("/" + sanitized);
        if (direct != null) {
            return direct;
        }
        return readResource(sanitized.startsWith("/") ? sanitized : "/" + sanitized);
    }

    private String normalizeIconName(String iconName) {
        String sanitized = iconName == null ? "" : iconName.replace("\\", "/").replace("..", "").trim();
        if (sanitized.isEmpty()) {
            sanitized = "UserA.png";
        }
        if (!sanitized.toLowerCase(Locale.ROOT).endsWith(".png")) {
            sanitized = sanitized + ".png";
        }
        while (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        return sanitized;
    }

    private String fallbackPlayerIcon(String requested) {
        String normalized = requested == null ? "" : requested.toLowerCase(Locale.ROOT);
        if (normalized.contains("userb")) return "UserB.png";
        if (normalized.contains("userc")) return "UserC.png";
        if (normalized.contains("userd")) return "UserD.png";
        if (normalized.contains("usere")) return "UserE.png";
        if (normalized.contains("userf")) return "UserF.png";
        return "UserA.png";
    }

    private byte[] readResource(String resourcePath) {
        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
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

    private String stripQuery(String uri) {
        int queryIndex = uri.indexOf('?');
        if (queryIndex < 0) {
            return uri;
        }
        return uri.substring(0, queryIndex);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}

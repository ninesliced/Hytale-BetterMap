package dev.ninesliced.webmap.auth;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory auth/session and one-time login code service for the web map.
 */
public class WebMapAuthService {
    private static final String SESSION_COOKIE = "bettermap_webmap_session";
    private static final Duration SESSION_VALIDITY = Duration.ofHours(12);
    private static final Duration CODE_VALIDITY = Duration.ofMinutes(5);

    private static final String ADMIN_PERMISSION = "bettermap.admin";
    private static final String ADMIN_COMMAND_PERMISSION = "bettermap.command.admin";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentMap<String, LoginCodeEntry> loginCodes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    public record LoginCodeEntry(long validUntilMs, UUID uuid, String username) {
    }

    private record SessionEntry(long validUntilMs, WebMapViewer viewer) {
    }

    @Nonnull
    public synchronized String createLoginCode(@Nonnull UUID uuid, @Nonnull String username) {
        long now = System.currentTimeMillis();
        loginCodes.entrySet().removeIf(entry -> {
            LoginCodeEntry value = entry.getValue();
            return value.validUntilMs < now || value.uuid.equals(uuid);
        });

        String code = generateLoginCode();
        loginCodes.put(code, new LoginCodeEntry(now + CODE_VALIDITY.toMillis(), uuid, username));
        return code;
    }

    @Nullable
    public synchronized WebMapViewer consumeLoginCode(@Nullable String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        LoginCodeEntry entry = loginCodes.remove(code.trim().toUpperCase());
        if (entry == null || entry.validUntilMs < System.currentTimeMillis()) {
            return null;
        }

        boolean isAdmin = isAdmin(entry.uuid);
        return new WebMapViewer(entry.uuid, entry.username, isAdmin);
    }

    @Nonnull
    public String createSessionCookieHeader(@Nonnull WebMapViewer viewer) {
        String token = generateSessionToken();
        sessions.put(token, new SessionEntry(System.currentTimeMillis() + SESSION_VALIDITY.toMillis(), viewer));

        DefaultCookie cookie = new DefaultCookie(SESSION_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(SESSION_VALIDITY.getSeconds());
        return ServerCookieEncoder.STRICT.encode(cookie);
    }

    @Nonnull
    public String clearSessionCookieHeader(@Nonnull FullHttpRequest req) {
        String token = readSessionToken(req);
        if (token != null) {
            sessions.remove(token);
        }

        DefaultCookie cookie = new DefaultCookie(SESSION_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        return ServerCookieEncoder.STRICT.encode(cookie);
    }

    @Nullable
    public WebMapViewer authenticate(@Nonnull FullHttpRequest req) {
        String token = readSessionToken(req);
        if (token == null) {
            return null;
        }

        SessionEntry entry = sessions.get(token);
        if (entry == null) {
            return null;
        }

        if (entry.validUntilMs < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }

        return entry.viewer;
    }

    @Nullable
    private String readSessionToken(@Nonnull FullHttpRequest req) {
        String rawCookie = req.headers().get(HttpHeaderNames.COOKIE);
        if (rawCookie == null || rawCookie.isBlank()) {
            return null;
        }

        Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(rawCookie);
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.name())) {
                return cookie.value();
            }
        }

        return null;
    }

    private boolean isAdmin(@Nonnull UUID uuid) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, ADMIN_PERMISSION) || perms.hasPermission(uuid, ADMIN_COMMAND_PERMISSION);
    }

    @Nonnull
    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Nonnull
    private String generateLoginCode() {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder builder = new StringBuilder(9);
        for (int i = 0; i < 4; i++) {
            builder.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        builder.append('-');
        for (int i = 0; i < 4; i++) {
            builder.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return builder.toString();
    }
}

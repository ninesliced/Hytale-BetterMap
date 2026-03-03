package dev.ninesliced.webmap.auth;

import java.util.UUID;

/**
 * Authenticated web map user context.
 */
public record WebMapViewer(UUID uuid, String username, boolean admin) {
}

package dev.ninesliced.webmap.auth;

import dev.ninesliced.webmap.data.WebViewFilter;

import javax.annotation.Nonnull;

/**
 * Access policy for web map mode/filter selection.
 */
public final class WebMapAccessPolicy {
    private WebMapAccessPolicy() {
    }

    @Nonnull
    public static WebViewFilter enforceFilter(@Nonnull WebViewFilter requested, @Nonnull WebMapViewer viewer) {
        if (viewer.admin()) {
            return requested;
        }
        return new WebViewFilter(WebViewFilter.Mode.PLAYER, viewer.uuid());
    }

    public static boolean allowGlobalMode(@Nonnull WebMapViewer viewer) {
        return viewer.admin();
    }
}

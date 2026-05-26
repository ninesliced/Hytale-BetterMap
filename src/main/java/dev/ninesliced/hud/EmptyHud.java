package dev.ninesliced.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * An empty HUD that renders nothing.
 * Used to clear the custom HUD slot in vanilla mode without passing null.
 */
public class EmptyHud extends CustomUIHud {

    public EmptyHud(@Nonnull PlayerRef playerRef) {
        this(playerRef, LocationHud.HUD_IDENTIFIER);
    }

    public EmptyHud(@Nonnull PlayerRef playerRef, @Nonnull String hudIdentifier) {
        super(playerRef, hudIdentifier);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        // Empty - renders nothing
    }
}

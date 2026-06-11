package dev.ninesliced.utils;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class PlayerRefUtil {
    private static final Logger LOGGER = Logger.getLogger(PlayerRefUtil.class.getName());

    private PlayerRefUtil() {
    }

    /**
     * Resolves the PlayerRef for a Player using the cached field.
     * This is safe to call from any thread (e.g. WorldMap thread).
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static PlayerRef resolve(@Nonnull Player player) {
        return player.getPlayerRef();
    }

    /**
     * Resolves the sending Player from a command context. Commands execute on the
     * ForkJoin common pool, so this must not touch the Store directly
     * (Store.getComponent asserts the world thread); see {@link #fromPlayerRef}.
     */
    @Nullable
    public static Player fromContext(@Nonnull CommandContext context) {
        if (context.sender() instanceof PlayerRef playerRef) {
            return fromPlayerRef(playerRef);
        }
        return null;
    }

    /**
     * Gets a Player component from a PlayerRef from any thread. The Holder only exists
     * while the player is detached from a world (PlayerRef.addedToStore nulls it), so for
     * an in-world player this reads the Store directly when already on the world thread,
     * and otherwise hops to the world thread and waits briefly — the same strategy as
     * vanilla PlayerRef.getComponent, minus its SEVERE log spam.
     */
    @Nullable
    public static Player fromPlayerRef(@Nonnull PlayerRef playerRef) {
        Holder<EntityStore> holder = playerRef.getHolder();
        if (holder != null) {
            return holder.getComponent(Player.getComponentType());
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        if (store.isInThread()) {
            return store.getComponent(ref, Player.getComponentType());
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                Ref<EntityStore> current = playerRef.getReference();
                return current != null && current.isValid()
                        ? store.getComponent(current, Player.getComponentType())
                        : null;
            }, store.getExternalData().getWorld()).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.fine("Could not resolve Player for " + playerRef.getUsername() + ": " + e.getMessage());
            return null;
        }
    }

    @Nonnull
    @SuppressWarnings("deprecation")
    public static String getUsername(@Nonnull Player player) {
        PlayerRef ref = player.getPlayerRef();
        return ref != null ? ref.getUsername() : "Unknown";
    }
}

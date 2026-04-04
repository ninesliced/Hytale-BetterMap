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

public final class PlayerRefUtil {

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

    @Nullable
    public static Player fromContext(@Nonnull CommandContext context) {
        Ref<EntityStore> ref = context.senderAsPlayerRef();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return null;
        }
        return store.getComponent(ref, Player.getComponentType());
    }

    /**
     * Gets a Player component from a PlayerRef using the thread-safe Holder.
     */
    @Nullable
    public static Player fromPlayerRef(@Nonnull PlayerRef playerRef) {
        Holder<EntityStore> holder = playerRef.getHolder();
        if (holder == null) {
            return null;
        }
        return holder.getComponent(Player.getComponentType());
    }

    @Nonnull
    @SuppressWarnings("deprecation")
    public static String getUsername(@Nonnull Player player) {
        PlayerRef ref = player.getPlayerRef();
        return ref != null ? ref.getUsername() : "Unknown";
    }
}

package dev.ninesliced.hud;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.zone.Zone;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the custom Head-Up Display (HUD) used for displaying location information to the player.
 * <p>
 * This HUD visualizes the player's current coordinates, world name, biome, and zone.
 * It is responsible for updating these values in real-time as the player moves through the world.
 * </p>
 */
public class LocationHud extends CustomUIHud {
    @Nullable
    private Vector3d playerPosition;
    @Nullable
    private String worldName;
    @Nullable
    private String biomeName;
    @Nullable
    private String zoneName;

    private boolean isEnabled = true;

    /**
     * Constructs a new LocationHud instance for the specified player.
     *
     * @param playerRef The reference to the player for whom this HUD is created.
     */
    public LocationHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    /**
     * Updates the HUD data based on the player's current state and surroundings.
     * <p>
     * This method retrieves the player's active position and world information, then triggers
     * an update of the biome and zone data.
     * </p>
     *
     * @param dt             The time delta since the last update.
     * @param index          The entity index within the archetype chunk.
     * @param archetypeChunk The chunk containing the entity's data.
     * @param store          The global entity store.
     * @param commandBuffer  The command buffer for scheduling updates.
     */
    @SuppressWarnings({"unchecked", "unused"})
    public void updateHud(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                         @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!this.isEnabled) return;

        Holder holder = EntityUtils.toHolder(index, archetypeChunk);
        Player player = (Player) holder.getComponent(Player.getComponentType());
        TransformComponent transformComponent = (TransformComponent) holder.getComponent(TransformComponent.getComponentType());

        if (player != null && transformComponent != null && player.getWorld() != null) {
            this.playerPosition = transformComponent.getPosition().clone();
            this.worldName = player.getWorld().getName();

            updateBiomeAndZone(player.getWorld(), this.playerPosition);
        }
    }

    /**
     * Calculates and updates the biome and zone information for a specific position in the world.
     *
     * @param world    The world instance the player is currently in.
     * @param position The position vector to check for biome and zone data.
     */
    private void updateBiomeAndZone(World world, Vector3d position) {
        if (world == null || position == null) return;

        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (worldGen instanceof ChunkGenerator generator) {
            int seed = (int) world.getWorldConfig().getSeed();
            int x = (int) position.getX();
            int z = (int) position.getZ();

            try {
                ZoneBiomeResult result = generator.generateZoneBiomeResultAt(seed, x, z);
                Biome biome = result.getBiome();
                Zone zone = result.getZoneResult().getZone();

                this.biomeName = biome != null ? biome.getName() : "Unknown Biome";
                this.zoneName = zone != null ? zone.name() : "Unknown Zone";
            } catch (Exception e) {
                 this.biomeName = "Error";
                 this.zoneName = "Error";
            }
        } else {
             this.biomeName = "N/A";
             this.zoneName = "N/A";
        }
    }

    /**
     * Builds the UI elements for this HUD using the provided command builder.
     * <p>
     * This method configures the initial state of the UI components, including the background,
     * content, and information labels. If valid player data is available, it populates the display.
     * </p>
     *
     * @param ui The UI command builder used to construct the HUD interface.
     */
    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        if (!this.isEnabled) {
            return;
        }

        ui.append("Hud/Location/LocationDisplay.ui");

        ui.set("#Location #Background #Content #InfoPanel #WorldNameLabel.TextSpans", Message.raw("World: Checking..."));
        ui.set("#Location #Background #Content #InfoPanel #BiomeNameLabel.TextSpans", Message.raw("Biome: Checking..."));
        ui.set("#Location #Background #Content #InfoPanel #ZoneNameLabel.TextSpans", Message.raw("Zone: Checking..."));
        ui.set("#Location #Background #Content #InfoPanel #PositionLabel.TextSpans", Message.raw("Checking..."));

        if (this.playerPosition != null) {
            updateInfoDisplay(ui);
        }
    }

    /**
     * Refreshes the information displayed on the HUD with the latest cached values.
     * <p>
     * This includes updating the position coordinates, world name, biome name, and zone name
     * on the respective UI labels.
     * </p>
     *
     * @param ui The UI command builder to update the UI elements.
     */
    private void updateInfoDisplay(@Nonnull UICommandBuilder ui) {

        if (!this.isEnabled) {
            return;
        }

        if (this.playerPosition == null) {
            return;
        }

        int x = (int) this.playerPosition.getX();
        int y = (int) this.playerPosition.getY();
        int z = (int) this.playerPosition.getZ();

        String positionText = String.format("Position: %d, %d, %d", x, y, z);
        ui.set("#Location #Background #Content #InfoPanel #PositionLabel.TextSpans", Message.raw(positionText));

        if (this.worldName != null) {
            ui.set("#Location #Background #Content #InfoPanel #WorldNameLabel.TextSpans", Message.raw("World: " + this.worldName));
        }

        if (this.biomeName != null) {
            ui.set("#Location #Background #Content #InfoPanel #BiomeNameLabel.TextSpans", Message.raw("Biome: " + this.biomeName));
        }

        if (this.zoneName != null) {
            ui.set("#Location #Background #Content #InfoPanel #ZoneNameLabel.TextSpans", Message.raw("Zone: " + this.zoneName));
        }
    }

    /**
     * Returns a string representation of the LocationHud state.
     *
     * @return A string containing the current player position and environment names.
     */
    @Override
    public String toString() {
        return "LocationHud{" +
                "playerPosition=" + playerPosition +
                ", worldName='" + worldName + '\'' +
                ", biomeName='" + biomeName + '\'' +
                ", zoneName='" + zoneName + '\'' +
                '}';
    }

    /**
     * Sets the enabled state of the HUD.
     *
     * @param enabled True to enable the HUD, false to disable it.
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
}


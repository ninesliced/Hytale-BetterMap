package dev.ninesliced.hud;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
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
import org.joml.Vector3d;

/**
 * Custom HUD displaying location information (coordinates, world, biome, zone).
 */
public class LocationHud extends CustomUIHud {

    private static final int HUD_WIDTH = 260;
    private static final int HUD_HEIGHT = 160;
    private static final int MARGIN = 20;

    @Nullable private Vector3d playerPosition;
    @Nullable private String worldName;
    @Nullable private String biomeName;
    @Nullable private String zoneName;

    private boolean isEnabled = true;
    private HudPosition position = HudPosition.TOP_LEFT;

    public LocationHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    public void setPosition(@Nonnull HudPosition position) {
        this.position = position;
    }

    public HudPosition getPosition() {
        return position;
    }

    @SuppressWarnings("unused")
    public void updateHud(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                          @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!this.isEnabled) return;

        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        TransformComponent transformComponent = archetypeChunk.getComponent(index, TransformComponent.getComponentType());

        if (player != null && transformComponent != null && player.getWorld() != null) {
            this.playerPosition = new Vector3d(transformComponent.getPosition());
            this.worldName = player.getWorld().getName();
            updateBiomeAndZone(player.getWorld(), this.playerPosition);
        }
    }

    private void updateBiomeAndZone(World world, Vector3d position) {
        if (world == null || position == null) return;

        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (worldGen instanceof ChunkGenerator generator) {
            int seed = (int) world.getWorldConfig().getSeed();
            int x = (int) position.x;
            int z = (int) position.z;

            try {
                ZoneBiomeResult result = generator.generateZoneBiomeResultAt(seed, x, z);
                Biome biome = result.getBiome();
                Zone zone = result.getZoneResult().getZone();

                this.biomeName = biome != null ? formatBiomeName(biome.getName()) : "Unknown Biome";
                this.zoneName = zone != null ? formatZoneName(zone.name()) : "Unknown Zone";
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
     * Formats a biome name by replacing underscores with spaces and capitalizing each word.
     * Example: "deep_forest_hills" -> "Deep Forest Hills"
     */
    private String formatBiomeName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "Unknown";
        }
        String[] words = rawName.replace("_", " ").split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (formatted.length() > 0) formatted.append(" ");
                formatted.append(Character.toUpperCase(word.charAt(0)))
                         .append(word.substring(1).toLowerCase());
            }
        }
        return formatted.toString();
    }

    /**
     * Formats a zone name from "Zone1_Tier2" format to "Zone 1 - Tier 2".
     * Example: "Zone1_Tier2" -> "Zone 1 - Tier 2"
     */
    private String formatZoneName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "Unknown";
        }

        // Handle Zone#_Tier# format
        if (rawName.matches("Zone\\d+_Tier\\d+")) {
            String[] parts = rawName.split("_");
            String zonePart = parts[0].replaceAll("(Zone)(\\d+)", "$1 $2");
            String tierPart = parts[1].replaceAll("(Tier)(\\d+)", "$1 $2");
            return zonePart + " - " + tierPart;
        }

        // Fallback: replace underscores with spaces and capitalize
        return formatBiomeName(rawName);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        if (!this.isEnabled) {
            return;
        }

        ui.append("Hud/Location/LocationDisplay.ui");
        ui.setObject("#LocationHudAnchor.Anchor", computeAnchor());

        ui.set("#LocationHudAnchor #Location #Background #Content #InfoPanel #WorldNameLabel.TextSpans", Message.raw("World: Checking..."));
        ui.set("#LocationHudAnchor #Location #Background #Content #InfoPanel #BiomeNameLabel.TextSpans", Message.raw("Biome: Checking..."));
        ui.set("#LocationHudAnchor #Location #Background #Content #InfoPanel #ZoneNameLabel.TextSpans", Message.raw("Zone: Checking..."));
        ui.set("#LocationHudAnchor #Location #Background #Content #InfoPanel #PositionLabel.TextSpans", Message.raw("Checking..."));

        if (this.playerPosition != null) {
            updateInfoDisplay(ui);
        }
    }

    private Anchor computeAnchor() {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(HUD_HEIGHT));

        switch (this.position) {
            case TOP_LEFT -> {
                anchor.setWidth(Value.of(HUD_WIDTH));
                anchor.setLeft(Value.of(MARGIN));
                anchor.setTop(Value.of(MARGIN));
            }
            case TOP_CENTER -> {
                anchor.setLeft(Value.of(0));
                anchor.setRight(Value.of(0));
                anchor.setTop(Value.of(MARGIN + 40));
            }
            case TOP_RIGHT -> {
                anchor.setWidth(Value.of(HUD_WIDTH));
                anchor.setRight(Value.of(MARGIN));
                anchor.setTop(Value.of(MARGIN));
            }
            case BOTTOM_LEFT -> {
                anchor.setWidth(Value.of(HUD_WIDTH));
                anchor.setLeft(Value.of(MARGIN));
                anchor.setBottom(Value.of(MARGIN));
            }
            case BOTTOM_CENTER -> {
                anchor.setLeft(Value.of(0));
                anchor.setRight(Value.of(0));
                anchor.setBottom(Value.of(MARGIN + 120));
            }
            case BOTTOM_RIGHT -> {
                anchor.setWidth(Value.of(HUD_WIDTH + 650));
                anchor.setRight(Value.of(MARGIN));
                anchor.setBottom(Value.of(MARGIN));
            }
        }

        return anchor;
    }

    private void updateInfoDisplay(@Nonnull UICommandBuilder ui) {
        if (!this.isEnabled || this.playerPosition == null) {
            return;
        }

        int x = (int) this.playerPosition.x;
        int y = (int) this.playerPosition.y;
        int z = (int) this.playerPosition.z;

        String positionText = String.format("Position: %d, %d, %d", x, y, z);
        ui.set("#LocationHudAnchor #Location #Background #Content #InfoPanel #PositionLabel.TextSpans", Message.raw(positionText));

        if (this.worldName != null) {
            ui.set("#LocationHudAnchor #Location #Background #Content #InfoPanel #WorldNameLabel.TextSpans", Message.raw("World: " + this.worldName));
        }

        if (this.biomeName != null) {
            ui.set("#LocationHudAnchor #Location #Background #Content #InfoPanel #BiomeNameLabel.TextSpans", Message.raw("Biome: " + this.biomeName));
        }

        if (this.zoneName != null) {
            ui.set("#LocationHudAnchor #Location #Background #Content #InfoPanel #ZoneNameLabel.TextSpans", Message.raw("Zone: " + this.zoneName));
        }
    }

    @Override
    public String toString() {
        return "LocationHud{" +
                "playerPosition=" + playerPosition +
                ", worldName='" + worldName + '\'' +
                ", biomeName='" + biomeName + '\'' +
                ", zoneName='" + zoneName + '\'' +
                ", position=" + position +
                '}';
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
}

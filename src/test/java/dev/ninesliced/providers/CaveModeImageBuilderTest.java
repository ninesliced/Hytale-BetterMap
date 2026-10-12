package dev.ninesliced.providers;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightDataBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CaveModeImageBuilderTest {
    @Test
    void readsBlocksAndLightAcrossSectionBoundaries() throws Exception {
        var builder = new CaveModeImageBuilder(0, 32, 32, null, 32, 12);
        var sections = new BlockSection[ChunkUtil.HEIGHT_SECTIONS];
        sections[0] = new BlockSection();
        sections[1] = new BlockSection();
        sections[0].set(3, 31, 5, 7, 0, 0);
        sections[1].set(3, 32, 5, 9, 0, 0);
        var light = new ChunkLightDataBuilder((short) 1);
        light.setBlockLight(3, 32, 5, (byte) 12, (byte) 2, (byte) 3);
        light.setSkyLight(3, 32, 5, (byte) 7);
        sections[1].setGlobalLight(light);
        var field = CaveModeImageBuilder.class.getDeclaredField("blockSections");
        field.setAccessible(true);
        field.set(builder, sections);

        Method blockAt = method("getBlockAt");
        Method lightAt = method("sampleLightLevel");
        assertEquals(7, blockAt.invoke(builder, 3, 31, 5));
        assertEquals(9, blockAt.invoke(builder, 3, 32, 5));
        assertEquals(12, lightAt.invoke(builder, 3, 32, 5));
        assertEquals(0, blockAt.invoke(builder, 3, -1, 5));
        assertEquals(0, blockAt.invoke(builder, 3, ChunkUtil.HEIGHT, 5));
        assertEquals(0, lightAt.invoke(builder, 3, ChunkUtil.HEIGHT, 5));
        assertEquals(0, lightAt.invoke(builder, 3, 64, 5));
    }

    private static Method method(String name) throws Exception {
        var method = CaveModeImageBuilder.class.getDeclaredMethod(name, int.class, int.class, int.class);
        method.setAccessible(true);
        return method;
    }
}

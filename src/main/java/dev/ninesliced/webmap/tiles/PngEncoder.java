package dev.ninesliced.webmap.tiles;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PNG encoding helpers for map tiles.
 */
public final class PngEncoder {
    private static final ConcurrentHashMap<Integer, byte[]> EMPTY_TILE_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<ImageWriter> PNG_WRITER = ThreadLocal.withInitial(() -> {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        return writers.hasNext() ? writers.next() : null;
    });

    private PngEncoder() {
    }

    public static byte[] encode(MapImage mapImage, int outputSize) {
        return encodeWithPixels(mapImage, outputSize).pngBytes;
    }

    public static TileData encodeWithPixels(MapImage mapImage, int outputSize) {
        int srcWidth = mapImage.width;
        int srcHeight = mapImage.height;
        int[] srcData = mapImage.data;
        int[] rgb = new int[outputSize * outputSize];

        float scaleX = (float) srcWidth / outputSize;
        float scaleY = (float) srcHeight / outputSize;

        for (int y = 0; y < outputSize; y++) {
            int destRowStart = y * outputSize;
            int srcY = Math.min((int) (y * scaleY), srcHeight - 1);
            int srcRowStart = srcY * srcWidth;

            for (int x = 0; x < outputSize; x++) {
                int srcX = Math.min((int) (x * scaleX), srcWidth - 1);
                int rgba = srcData[srcRowStart + srcX];
                int r = (rgba >> 24) & 0xFF;
                int g = (rgba >> 16) & 0xFF;
                int b = (rgba >> 8) & 0xFF;
                rgb[destRowStart + x] = (r << 16) | (g << 8) | b;
            }
        }

        BufferedImage image = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, outputSize, outputSize, rgb, 0, outputSize);

        return new TileData(encodeFast(image, outputSize), rgb, outputSize);
    }

    public static byte[] encodeEmpty(int size) {
        return EMPTY_TILE_CACHE.computeIfAbsent(size, ignored -> {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            return encodeFast(image, size);
        });
    }

    private static byte[] encodeFast(BufferedImage image, int outputSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(outputSize * outputSize / 2);
        ImageWriter writer = PNG_WRITER.get();
        if (writer == null) {
            try {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            } catch (IOException ignored) {
                return new byte[0];
            }
        }

        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(1.0f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            writer.reset();
            return out.toByteArray();
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    public record TileData(byte[] pngBytes, int[] pixels, int size) {
        public boolean isEmpty() {
            return pngBytes == null || pngBytes.length < 500;
        }
    }
}

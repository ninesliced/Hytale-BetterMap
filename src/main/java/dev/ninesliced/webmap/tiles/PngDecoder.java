package dev.ninesliced.webmap.tiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * PNG decoding helper for pixel-based composite tile generation.
 */
public final class PngDecoder {
    private PngDecoder() {
    }

    public static int[] decode(byte[] pngBytes, int expectedSize) {
        if (pngBytes == null || pngBytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(pngBytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            if (width != expectedSize || height != expectedSize) {
                BufferedImage scaled = new BufferedImage(expectedSize, expectedSize, BufferedImage.TYPE_INT_RGB);
                scaled.getGraphics().drawImage(image, 0, 0, expectedSize, expectedSize, null);
                image = scaled;
                width = expectedSize;
                height = expectedSize;
            }

            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);
            return pixels;
        } catch (Exception ignored) {
            return null;
        }
    }
}

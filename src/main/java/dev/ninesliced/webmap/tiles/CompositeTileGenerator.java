package dev.ninesliced.webmap.tiles;

import dev.ninesliced.webmap.data.WebViewFilter;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Builds lower zoom composite tiles by stitching higher zoom chunks.
 */
public class CompositeTileGenerator {
    private static final int TARGET_AXIS_ZOOM_NEG3 = 8;
    private static final int TARGET_AXIS_ZOOM_NEG4_PLUS = 4;

    private static final ThreadLocal<ImageWriter> PNG_WRITER = ThreadLocal.withInitial(() -> {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        return writers.hasNext() ? writers.next() : null;
    });

    private final TileManager tileManager;

    public CompositeTileGenerator(TileManager tileManager) {
        this.tileManager = tileManager;
    }

    public int getChunksPerAxis(int zoom) {
        return zoom >= 0 ? 1 : 1 << -zoom;
    }

    public CompletableFuture<byte[]> generateCompositeTile(String worldName,
                                                           TileQuality quality,
                                                           int zoom,
                                                           int tileX,
                                                           int tileZ,
                                                           WebViewFilter.Mode mode,
                                                           UUID playerUuid) {
        return generateCompositeTileWithPixels(worldName, quality, zoom, tileX, tileZ, mode, playerUuid)
            .thenApply(PngEncoder.TileData::pngBytes);
    }

    public CompletableFuture<PngEncoder.TileData> generateCompositeTileWithPixels(String worldName,
                                                                                   TileQuality quality,
                                                                                   int zoom,
                                                                                   int tileX,
                                                                                   int tileZ,
                                                                                   WebViewFilter.Mode mode,
                                                                                   UUID playerUuid) {
        if (zoom >= 0) {
            return tileManager.getBaseTileWithPixels(worldName, quality, tileX, tileZ, mode, playerUuid);
        }

        int chunksPerAxis = getChunksPerAxis(zoom);
        int targetAxis = targetTilesPerAxis(zoom, chunksPerAxis);
        int sourceTilesPerAxis = Math.max(1, Math.min(targetAxis, chunksPerAxis));
        int sourceZoom = zoom + Integer.numberOfTrailingZeros(sourceTilesPerAxis);
        int outputSize = quality.tileSize();

        int sourceBaseX = tileX * sourceTilesPerAxis;
        int sourceBaseZ = tileZ * sourceTilesPerAxis;

        List<CompletableFuture<TileWithPosition>> futures = new ArrayList<>(sourceTilesPerAxis * sourceTilesPerAxis);
        for (int dz = 0; dz < sourceTilesPerAxis; dz++) {
            for (int dx = 0; dx < sourceTilesPerAxis; dx++) {
                int sourceX = sourceBaseX + dx;
                int sourceZ = sourceBaseZ + dz;
                int posX = dx;
                int posZ = dz;
                CompletableFuture<TileWithPosition> future = tileManager
                    .getTileWithPixels(worldName, quality, sourceZoom, sourceX, sourceZ, mode, playerUuid)
                    .thenApply(data -> new TileWithPosition(data, posX, posZ));
                futures.add(future);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<TileWithPosition> tiles = new ArrayList<>(futures.size());
                for (CompletableFuture<TileWithPosition> future : futures) {
                    tiles.add(future.join());
                }
                return composeFromPixels(tiles, sourceTilesPerAxis, outputSize);
            });
    }

    private PngEncoder.TileData composeFromPixels(List<TileWithPosition> tiles, int chunksPerAxis, int outputSize) {
        int subTileSize = outputSize / chunksPerAxis;
        boolean hasAnyContent = false;
        BufferedImage image = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (TileWithPosition tile : tiles) {
            if (tile.data == null || tile.data.pixels() == null || tile.data.pixels().length == 0) {
                continue;
            }
            hasAnyContent = true;

            int[] srcPixels = tile.data.pixels();
            int srcSize = tile.data.size();
            int destX = tile.posX * subTileSize;
            int destY = tile.posZ * subTileSize;
            BufferedImage sourceImage = new BufferedImage(srcSize, srcSize, BufferedImage.TYPE_INT_RGB);
            sourceImage.setRGB(0, 0, srcSize, srcSize, srcPixels, 0, srcSize);
            graphics.drawImage(sourceImage, destX, destY, destX + subTileSize, destY + subTileSize, 0, 0, srcSize, srcSize, null);
        }

        graphics.dispose();

        if (!hasAnyContent) {
            return new PngEncoder.TileData(PngEncoder.encodeEmpty(outputSize), new int[0], outputSize);
        }
        int[] outputPixels = image.getRGB(0, 0, outputSize, outputSize, null, 0, outputSize);
        return new PngEncoder.TileData(encodeFast(image, outputSize), outputPixels, outputSize);
    }

    private int targetTilesPerAxis(int zoom, int chunksPerAxis) {
        if (zoom <= -4) {
            return Math.min(TARGET_AXIS_ZOOM_NEG4_PLUS, chunksPerAxis);
        }
        if (zoom == -3) {
            return Math.min(TARGET_AXIS_ZOOM_NEG3, chunksPerAxis);
        }
        return chunksPerAxis;
    }

    private byte[] encodeFast(BufferedImage image, int outputSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(outputSize * outputSize / 2);
        ImageWriter writer = PNG_WRITER.get();
        if (writer == null) {
            try {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            } catch (IOException ignored) {
                return PngEncoder.encodeEmpty(outputSize);
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
            return PngEncoder.encodeEmpty(outputSize);
        }
    }

    private record TileWithPosition(PngEncoder.TileData data, int posX, int posZ) {
    }
}

package com.makambox.studio.ui;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** HiDPI MakamLab name logo used in the application header. */
final class MakamWordmark extends JComponent {
    static final String BRAND_NAME = "MakamLab";
    private static final String RESOURCE = "/ui/MakamLab-Wordmark-v1.png";
    private static final BufferedImage ARTWORK = loadArtwork();

    MakamWordmark() {
        setOpaque(false);
        // Keep the wordmark at the same visual height as the application icon.
        // The large source artwork is still retained and rendered with bicubic
        // scaling, so the compact header remains crisp on HiDPI displays.
        setPreferredSize(new Dimension(100, 44));
        setMinimumSize(new Dimension(92, 40));
        setMaximumSize(new Dimension(110, 48));
        setToolTipText(BRAND_NAME);
        setName("makam-header-wordmark");
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        paintWordmark(g, getWidth(), getHeight());
        g.dispose();
    }

    /** Creates an exact high-resolution preview of the component artwork. */
    static BufferedImage renderPreview(int logicalWidth, int logicalHeight, int scale) {
        int safeScale = Math.max(1, scale);
        BufferedImage image = new BufferedImage(logicalWidth * safeScale,
                logicalHeight * safeScale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(safeScale, safeScale);
        paintWordmark(g, logicalWidth, logicalHeight);
        g.dispose();
        return image;
    }

    private static void paintWordmark(Graphics2D g, int width, int height) {
        if (width <= 2 || height <= 2) return;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (ARTWORK == null) {
            g.setColor(new Color(126, 10, 31));
            g.drawString(BRAND_NAME, 6, Math.max(18, height / 2));
            return;
        }
        double scale = Math.min(width / (double) ARTWORK.getWidth(),
                height / (double) ARTWORK.getHeight());
        int drawWidth = Math.max(1, (int) Math.round(ARTWORK.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.round(ARTWORK.getHeight() * scale));
        int x = (width - drawWidth) / 2;
        int y = (height - drawHeight) / 2;
        g.drawImage(ARTWORK, x, y, drawWidth, drawHeight, null);
    }

    private static BufferedImage loadArtwork() {
        try (InputStream input = MakamWordmark.class.getResourceAsStream(RESOURCE)) {
            if (input != null) return ImageIO.read(input);
        } catch (IOException ignored) {
            // Fall through to the source-tree asset for developer runs.
        }
        Path asset = Path.of("assets", "MakamLab-Wordmark-v1.png");
        if (Files.isRegularFile(asset)) {
            try {
                return ImageIO.read(asset.toFile());
            } catch (IOException ignored) {
                // The text fallback keeps the header usable if the asset is corrupt.
            }
        }
        return null;
    }
}

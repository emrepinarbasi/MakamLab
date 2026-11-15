package com.makambox.studio.ui;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lightweight HiDPI render check and optional visual-preview writer. */
public final class MakamWordmarkRegressionTest {
    private MakamWordmarkRegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        if (!"MakamLab".equals(MakamWordmark.BRAND_NAME)) {
            throw new AssertionError("Unexpected MakamLab brand name");
        }
        BufferedImage image = MakamWordmark.renderPreview(310, 110, 4);
        if (image.getWidth() != 1240 || image.getHeight() != 440) {
            throw new AssertionError("Unexpected wordmark preview size");
        }
        long visiblePixels = 0;
        long burgundyPixels = 0;
        long bluePixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if (((pixel >>> 24) & 0xff) > 8) visiblePixels++;
                int red = (pixel >>> 16) & 0xff;
                int green = (pixel >>> 8) & 0xff;
                int blue = pixel & 0xff;
                if (red > blue * 1.35 && red > green * 1.35 && red > 65) burgundyPixels++;
                if (blue > red * 1.25 && blue > green * 1.20 && blue > 65) bluePixels++;
            }
        }
        if (visiblePixels < 12_000) {
            throw new AssertionError("Wordmark render is unexpectedly empty");
        }
        if (burgundyPixels < 20_000 || bluePixels < 12_000) {
            throw new AssertionError("MakamLab red/blue artwork was not rendered");
        }
        if (arguments.length > 0 && !arguments[0].isBlank()) {
            Path output = Path.of(arguments[0]).toAbsolutePath();
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            BufferedImage preview = new BufferedImage(image.getWidth(), image.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = preview.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, preview.getWidth(), preview.getHeight());
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
            ImageIO.write(preview, "png", output.toFile());
            System.out.println("WORDMARK_PREVIEW=" + output);
        }
        System.out.println("MAKAM_WORDMARK_OK visiblePixels=" + visiblePixels);
    }
}

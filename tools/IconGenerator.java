import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

public final class IconGenerator {
    public static void main(String[] args) throws Exception {
        int size = 1024;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, size, size);
        g.setColor(new Color(22, 36, 52)); g.fillRoundRect(74, 74, 876, 876, 210, 210);
        g.setColor(new Color(13, 148, 136)); g.fillRoundRect(112, 112, 800, 800, 175, 175);
        g.setColor(new Color(11, 92, 102)); g.fillRoundRect(158, 158, 708, 708, 135, 135);
        for (int i = 0; i < 7; i++) {
            g.setColor(new Color(255, 255, 255, 30));
            int y = 285 + i * 68; g.drawLine(205, y, 820, y);
        }
        Path2D curve = new Path2D.Double();
        curve.moveTo(205, 650); curve.curveTo(330, 660, 360, 450, 485, 505);
        curve.curveTo(585, 550, 615, 315, 818, 350);
        g.setStroke(new BasicStroke(37, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 184, 77)); g.draw(curve);
        g.setColor(Color.WHITE); g.fillOval(451, 471, 68, 68);
        g.setFont(new Font("Helvetica Neue", Font.BOLD, 112));
        g.drawString("M", 205, 780);
        g.dispose();
        ImageIO.write(image, "png", Path.of(args[0]).toFile());
    }
}

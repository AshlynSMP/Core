package nl.ashlyn.core.web;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Draws a player's face and a flat front view of their body from a Minecraft skin,
 * with the outer layer on top. Works for classic and slim arms and for old 64x32 skins,
 * where a hat layer without any see-through pixels is left out, like Minecraft does.
 */
public class SkinRenderer {

    private static final int HEAD_SCALE = 16;
    private static final int BODY_SCALE = 8;

    /**
     * Draw the face with the hat layer
     * @param skin The skin image
     * @return A 128x128 PNG
     */
    public static byte[] head(BufferedImage skin) throws IOException {
        BufferedImage head = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = head.createGraphics();
        part(g, skin, 8, 8, 8, 8, 0, 0, false);
        if (hasHat(skin)) part(g, skin, 40, 8, 8, 8, 0, 0, false);
        g.dispose();
        return png(head, HEAD_SCALE);
    }

    /**
     * Draw the whole player from the front
     * @param skin The skin image
     * @param slim Whether the skin has 3 pixel wide arms
     * @return A 128x256 PNG
     */
    public static byte[] body(BufferedImage skin, boolean slim) throws IOException {
        boolean legacy = skin.getHeight() < 64;
        int arm = slim ? 3 : 4;
        BufferedImage body = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = body.createGraphics();

        part(g, skin, 8, 8, 8, 8, 4, 0, false);
        part(g, skin, 20, 20, 8, 12, 4, 8, false);
        part(g, skin, 44, 20, arm, 12, 4 - arm, 8, false);
        part(g, skin, 4, 20, 4, 12, 4, 20, false);
        if (legacy) {
            part(g, skin, 44, 20, arm, 12, 12, 8, true);
            part(g, skin, 4, 20, 4, 12, 8, 20, true);
        } else {
            part(g, skin, 36, 52, arm, 12, 12, 8, false);
            part(g, skin, 20, 52, 4, 12, 8, 20, false);
            part(g, skin, 20, 36, 8, 12, 4, 8, false);
            part(g, skin, 44, 36, arm, 12, 4 - arm, 8, false);
            part(g, skin, 52, 52, arm, 12, 12, 8, false);
            part(g, skin, 4, 36, 4, 12, 4, 20, false);
            part(g, skin, 4, 52, 4, 12, 8, 20, false);
        }
        if (hasHat(skin)) part(g, skin, 40, 8, 8, 8, 4, 0, false);
        g.dispose();
        return png(body, BODY_SCALE);
    }

    private static boolean hasHat(BufferedImage skin) {
        if (skin.getHeight() >= 64) return true;
        for (int y = 0; y < 32; y++) {
            for (int x = 32; x < 64; x++) {
                if (skin.getRGB(x, y) >>> 24 < 128) return true;
            }
        }
        return false;
    }

    private static void part(Graphics2D g, BufferedImage skin, int sx, int sy, int w, int h, int dx, int dy, boolean mirror) {
        if (sx + w > skin.getWidth() || sy + h > skin.getHeight()) return;
        if (mirror) g.drawImage(skin, dx, dy, dx + w, dy + h, sx + w, sy, sx, sy + h, null);
        else g.drawImage(skin, dx, dy, dx + w, dy + h, sx, sy, sx + w, sy + h, null);
    }

    private static byte[] png(BufferedImage image, int scale) throws IOException {
        BufferedImage scaled = new BufferedImage(image.getWidth() * scale, image.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(scaled, "png", out);
        return out.toByteArray();
    }
}

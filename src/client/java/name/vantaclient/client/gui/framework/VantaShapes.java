package name.vantaclient.client.gui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Anti-aliased rounded surfaces. Corners are pre-baked superellipse masks (see
 * {@code tools/ui-assets/gen_shapes.py}) blitted with GPU filtering at a resolution close to
 * the real device size, so edges stay smooth at every GUI scale instead of stair-stepping
 * in whole GUI pixels. Straight spans are plain rectangles.
 */
public final class VantaShapes {
    private static final int[] LEVELS = {16, 32, 64, 128};
    private static final int[] RING_DENOMINATORS = {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 20, 24, 32};
    private static final Identifier[] FILL = new Identifier[LEVELS.length];
    private static final Identifier[][] RING = new Identifier[LEVELS.length][RING_DENOMINATORS.length];

    static {
        for (int l = 0; l < LEVELS.length; l++) {
            FILL[l] = texture("fill_" + LEVELS[l]);
            for (int k = 0; k < RING_DENOMINATORS.length; k++) {
                RING[l][k] = texture("ring_" + LEVELS[l] + "_" + k);
            }
        }
    }

    private VantaShapes() {}

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("vanta", "textures/gui/shape/" + name + ".png");
    }

    /** Device pixels per local unit at the current pose. */
    private static double deviceScale(GuiGraphics g) {
        double gui = 1.0;
        try {
            gui = Minecraft.getInstance().getWindow().getGuiScale();
        } catch (RuntimeException ignored) {
            // Window not ready (very early screens): treat as 1:1.
        }
        var pose = g.pose();
        double pose2d = Math.hypot(pose.m00(), pose.m01());
        return gui * (pose2d > 0.0001 ? pose2d : 1.0);
    }

    private static int level(double devicePixels) {
        for (int i = 0; i < LEVELS.length; i++) {
            if (LEVELS[i] >= devicePixels) return i;
        }
        return LEVELS.length - 1;
    }

    private static void corner(GuiGraphics g, Identifier tex, int level, int qx, int qy,
                               int x, int y, int size, int color) {
        int s = LEVELS[level];
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, (float) (qx * s), (float) (qy * s),
                size, size, s, s, s * 2, s * 2, color);
    }

    /** Solid rounded rectangle with independently roundable left/right sides. */
    public static void fill(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        gradient(g, x, y, w, h, r, color, color, true, true);
    }

    /** Vertical gradient inside the rounded rectangle; corners take the colour at their row. */
    public static void gradient(GuiGraphics g, int x, int y, int w, int h, int r,
                                int top, int bottom, boolean roundLeft, boolean roundRight) {
        int level = level(r * deviceScale(g));
        Identifier tex = FILL[level];
        boolean flat = top == bottom;
        int topCorner = flat ? top : VantaUiTheme.blend(top, bottom, h <= 1 ? 0f : (r * 0.5f) / (h - 1));
        int bottomCorner = flat ? bottom : VantaUiTheme.blend(top, bottom, h <= 1 ? 1f : (h - r * 0.5f) / (h - 1));
        int rl = roundLeft ? r : 0;
        int rr = roundRight ? r : 0;

        // Middle band (full width) and the top/bottom caps between the corners.
        if (h > r * 2) {
            int midTop = VantaUiTheme.blend(top, bottom, h <= 1 ? 0f : r / (float) (h - 1));
            int midBottom = VantaUiTheme.blend(top, bottom, h <= 1 ? 1f : (h - r) / (float) (h - 1));
            if (flat) g.fill(x, y + r, x + w, y + h - r, top);
            else g.fillGradient(x, y + r, x + w, y + h - r, midTop, midBottom);
        }
        if (w > rl + rr) {
            g.fill(x + rl, y, x + w - rr, y + r, topCorner);
            g.fill(x + rl, y + h - r, x + w - rr, y + h, bottomCorner);
        }
        if (roundLeft) {
            corner(g, tex, level, 0, 0, x, y, r, topCorner);
            corner(g, tex, level, 0, 1, x, y + h - r, r, bottomCorner);
        } else {
            g.fill(x, y, x + r, y + r, topCorner);
            g.fill(x, y + h - r, x + r, y + h, bottomCorner);
        }
        if (roundRight) {
            corner(g, tex, level, 1, 0, x + w - r, y, r, topCorner);
            corner(g, tex, level, 1, 1, x + w - r, y + h - r, r, bottomCorner);
        } else {
            g.fill(x + w - r, y, x + w, y + r, topCorner);
            g.fill(x + w - r, y + h - r, x + w, y + h, bottomCorner);
        }
    }

    /** True when {@link #outline} can draw this thickness/radius pair. */
    public static boolean canOutline(int r, int t) {
        return r >= 2 && t >= 1 && t * 2 <= r;
    }

    /** Rounded outline: four straight edges plus four ring-texture corners. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int color, int t) {
        int level = level(r * deviceScale(g));
        double ratio = t / (double) r;
        int best = 0;
        double bestError = Double.MAX_VALUE;
        for (int k = 0; k < RING_DENOMINATORS.length; k++) {
            double error = Math.abs(1.0 / RING_DENOMINATORS[k] - ratio);
            if (error < bestError) {
                bestError = error;
                best = k;
            }
        }
        Identifier tex = RING[level][best];
        if (w > r * 2) {
            g.fill(x + r, y, x + w - r, y + t, color);
            g.fill(x + r, y + h - t, x + w - r, y + h, color);
        }
        if (h > r * 2) {
            g.fill(x, y + r, x + t, y + h - r, color);
            g.fill(x + w - t, y + r, x + w, y + h - r, color);
        }
        corner(g, tex, level, 0, 0, x, y, r, color);
        corner(g, tex, level, 1, 0, x + w - r, y, r, color);
        corner(g, tex, level, 0, 1, x, y + h - r, r, color);
        corner(g, tex, level, 1, 1, x + w - r, y + h - r, r, color);
    }
}

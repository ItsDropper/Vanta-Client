package name.vantaclient.client.gui.framework;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.compose.theme.VantaTheme;
import name.vantaclient.client.gui.compose.theme.VantaThemeFactory;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Central visual tokens for every Vanta-native UI component.
 *
 * Tokens are resolved at render time so Appearance changes are reflected
 * without rebuilding the screen tree.
 */
public final class VantaUiTheme {
    public static final int CANVAS = 0xF00A0D12;
    public static final int PANEL = 0xE80E1218;
    public static final int PANEL_RAISED = 0xED141A22;
    public static final int SURFACE = 0xFF1D242D;
    public static final int SURFACE_HOVER = 0xFF262F3A;
    public static final int SURFACE_SELECTED = 0xFF1D3552;
    public static final int BORDER = 0xFF28313D;
    public static final int BORDER_SUBTLE = 0xFF202832;
    public static final int BUTTON_OUTLINE = 0xFF354253;
    public static final int BUTTON_OUTLINE_HOVER = 0xFF628CC5;
    public static final int TEXT_PRIMARY = 0xFFF1F3F7;
    public static final int TEXT_SECONDARY = 0xFFB0B9C6;
    public static final int TEXT_MUTED = 0xFF7E8999;
    public static final int TRACK = 0xFF2A3441;
    public static final int INPUT_SURFACE = 0xF00E141C;
    public static final int INPUT_FOCUS_SURFACE = 0xF016202D;
    public static final int TOOLTIP_SURFACE = 0xF0121822;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int STATUS_INFO = 0xFF78AFFF;
    public static final int STATUS_SUCCESS = 0xFF65C99E;
    public static final int STATUS_WARNING = 0xFFE2B36C;
    public static final int STATUS_DANGER = 0xFFE27C83;

    private VantaTheme composeTheme = VantaThemeFactory.create();

    /** Refreshes Compose tokens once per frame, not once per component. */
    public void refreshComposeTheme() {
        composeTheme = VantaThemeFactory.create();
    }

    public VantaTheme composeTheme() {
        return composeTheme;
    }

    private static final int MAX_CACHED_SQUIRCLE_RADIUS = 32;
    /** Exact horizontal edge offset of a superellipse corner at the centre of each row. */
    private static final float[][] SQUIRCLE_EDGES = buildSquircleEdges();

    private static float[][] buildSquircleEdges() {
        float[][] edges = new float[MAX_CACHED_SQUIRCLE_RADIUS + 1][];
        edges[0] = new float[0];
        for (int radius = 1; radius <= MAX_CACHED_SQUIRCLE_RADIUS; radius++) {
            float[] rows = new float[radius];
            for (int row = 0; row < radius; row++) {
                double normalizedY = (radius - row - 0.5) / radius;
                double y4 = normalizedY * normalizedY * normalizedY * normalizedY;
                double normalizedX = Math.sqrt(Math.sqrt(Math.max(0.0, 1.0 - y4)));
                rows[row] = (float) Math.min(radius, radius * (1.0 - normalizedX));
            }
            edges[radius] = rows;
        }
        return edges;
    }

    private static float squircleEdge(int radius, int row) {
        if (radius <= 0) return 0.0f;
        int capped = Math.min(radius, MAX_CACHED_SQUIRCLE_RADIUS);
        return SQUIRCLE_EDGES[capped][Math.min(row, capped - 1)];
    }

    private static int squircleInset(int radius, int row) {
        return (int) Math.ceil(squircleEdge(radius, row));
    }

    /** Fills the single partially covered pixel just outside a corner row's solid span. */
    private static void edgePixels(GuiGraphics graphics, int x, int y, int width, int row,
                                   float edge, int color) {
        int solid = (int) Math.ceil(edge);
        float coverage = solid - edge;
        if (coverage <= 0.02f || solid <= 0) return;
        int alpha = Math.round(((color >>> 24) & 0xFF) * coverage);
        if (alpha <= 0) return;
        int soft = withAlpha(color, alpha);
        graphics.fill(x + solid - 1, y + row, x + solid, y + row + 1, soft);
        graphics.fill(x + width - solid, y + row, x + width - solid + 1, y + row + 1, soft);
    }

    public static final int SPACE_1 = 4;
    public static final int SPACE_2 = 8;
    public static final int SPACE_3 = 12;
    public static final int SPACE_4 = 16;
    public static final int SPACE_5 = 20;
    public static final int SPACE_6 = 24;

    public static final int CONTROL_HEIGHT = 30;
    public static final int ROW_HEIGHT = 38;
    public static final int SLIDER_HEIGHT = 48;

    public static final int RADIUS_SMALL = 4;
    public static final int RADIUS_MEDIUM = 7;
    public static final int RADIUS_LARGE = 10;

    public static final int TEXT_LINE_HEIGHT = 11;
    public static final int CARET_BLINK_TICKS = 10;
    public static final float HOVER_RATE = 16.0f;
    public static final float TOGGLE_RATE = 14.0f;

    public int accent() {
        return VantaClientPreferences.get().accent.color();
    }

    public boolean animationsEnabled() {
        return VantaClientPreferences.get().animations;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static int applyPanelOpacity(int color) {
        if (color != PANEL && color != PANEL_RAISED) return color;
        double opacity = VantaClientPreferences.get().panelOpacity;
        if (!Double.isFinite(opacity)) opacity = 0.85;
        opacity = Math.max(0.0, Math.min(1.0, opacity));
        int baseAlpha = (color >>> 24) & 0xFF;
        return withAlpha(color, (int) Math.round(baseAlpha * opacity));
    }

    /**
     * Superellipse (n=4) fill for Vanta surfaces. The corner profile stays flatter
     * along the sides than a circular radius, without a shader or per-frame allocation.
     */
    public static void fillSquircle(GuiGraphics graphics, int x, int y, int width,
                                    int height, int radius, int color) {
        if (graphics == null || width <= 0 || height <= 0 || color == 0) return;
        int r = Math.max(0, Math.min(Math.min(radius, MAX_CACHED_SQUIRCLE_RADIUS),
                Math.min(width / 2, height / 2)));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        if (height > r * 2) graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            float edge = squircleEdge(r, row);
            int inset = (int) Math.ceil(edge);
            int left = x + inset;
            int right = x + width - inset;
            if (right > left) {
                graphics.fill(left, y + row, right, y + row + 1, color);
                graphics.fill(left, y + height - row - 1, right, y + height - row, color);
            }
            edgePixels(graphics, x, y, width, row, edge, color);
            edgePixels(graphics, x, y + height - 2 * row - 1, width, row, edge, color);
        }
    }

    /** Vertical gradient inside the superellipse, drawn one row at a time. */
    public static void fillSquircleGradient(GuiGraphics graphics, int x, int y, int width,
                                            int height, int radius, int top, int bottom) {
        fillSquircleSides(graphics, x, y, width, height, radius, top, bottom, true, true);
    }

    /**
     * Gradient squircle whose left and right corners can be rounded independently, used for
     * panel halves such as a sidebar that shares its outer corners with a larger shell.
     */
    public static void fillSquircleSides(GuiGraphics graphics, int x, int y, int width, int height,
                                         int radius, int top, int bottom,
                                         boolean roundLeft, boolean roundRight) {
        if (graphics == null || width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(Math.min(radius, MAX_CACHED_SQUIRCLE_RADIUS),
                Math.min(width / 2, height / 2)));
        int midTop = r;
        int midBottom = height - r;
        if (midBottom > midTop) {
            float tTop = height <= 1 ? 0.0f : midTop / (float) (height - 1);
            float tBottom = height <= 1 ? 0.0f : midBottom / (float) (height - 1);
            graphics.fillGradient(x, y + midTop, x + width, y + midBottom,
                    blend(top, bottom, tTop), blend(top, bottom, tBottom));
        }
        for (int row = 0; row < r; row++) {
            float edge = squircleEdge(r, row);
            int inset = (int) Math.ceil(edge);
            float coverage = inset - edge;
            for (int side = 0; side < 2; side++) {
                int drawRow = side == 0 ? row : height - row - 1;
                float t = height <= 1 ? 0.0f : drawRow / (float) (height - 1);
                int color = blend(top, bottom, t);
                if ((color >>> 24) == 0) continue;
                int left = x + (roundLeft ? inset : 0);
                int right = x + width - (roundRight ? inset : 0);
                if (right > left) graphics.fill(left, y + drawRow, right, y + drawRow + 1, color);
                if (coverage > 0.02f && inset > 0) {
                    int soft = withAlpha(color, Math.round(((color >>> 24) & 0xFF) * coverage));
                    if (roundLeft) graphics.fill(x + inset - 1, y + drawRow, x + inset, y + drawRow + 1, soft);
                    if (roundRight) graphics.fill(x + width - inset, y + drawRow,
                            x + width - inset + 1, y + drawRow + 1, soft);
                }
            }
        }
    }

    private static int blend(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = Math.round(((from >>> 24) & 255) * (1.0f - t) + ((to >>> 24) & 255) * t);
        int r = Math.round(((from >>> 16) & 255) * (1.0f - t) + ((to >>> 16) & 255) * t);
        int g = Math.round(((from >>> 8) & 255) * (1.0f - t) + ((to >>> 8) & 255) * t);
        int b = Math.round((from & 255) * (1.0f - t) + (to & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** One-pixel superellipse outline for standard controls. */
    public static void outlineSquircle(GuiGraphics graphics, int x, int y, int width,
                                       int height, int radius, int color) {
        outlineSquircle(graphics, x, y, width, height, radius, color, 1);
    }

    /** One-pixel or multi-pixel superellipse outline matching {@link #fillSquircle}. */
    public static void outlineSquircle(GuiGraphics graphics, int x, int y, int width,
                                       int height, int radius, int color, int thickness) {
        if (graphics == null || width <= 1 || height <= 1 || color == 0 || thickness <= 0) return;
        int r = Math.max(0, Math.min(Math.min(radius, MAX_CACHED_SQUIRCLE_RADIUS),
                Math.min(width / 2, height / 2)));
        int t = Math.max(1, Math.min(thickness, Math.min(width / 2, height / 2)));
        for (int row = 0; row < height; row++) {
            int mirrored = row < r ? row : row >= height - r ? height - row - 1 : -1;
            int inset = mirrored >= 0 ? squircleInset(r, mirrored) : 0;
            int left = x + inset;
            int right = x + width - inset;
            if (right <= left) continue;
            if (row < t || row >= height - t) {
                graphics.fill(left, y + row, right, y + row + 1, color);
            } else {
                // Bridge to the previous corner row so steep arcs stay one connected stroke.
                int span = mirrored >= 0 && mirrored > 0
                        ? Math.max(t, squircleInset(r, mirrored - 1) - inset) : t;
                span = Math.min(span, (right - left) / 2 + 1);
                graphics.fill(left, y + row, left + span, y + row + 1, color);
                graphics.fill(right - span, y + row, right, y + row + 1, color);
            }
            if (mirrored >= 0) {
                float edge = squircleEdge(r, mirrored);
                int solid = (int) Math.ceil(edge);
                float coverage = solid - edge;
                if (coverage > 0.02f && solid > 0) {
                    int alpha = Math.round(((color >>> 24) & 0xFF) * coverage);
                    if (alpha > 0) {
                        int soft = withAlpha(color, alpha);
                        graphics.fill(x + solid - 1, y + row, x + solid, y + row + 1, soft);
                        graphics.fill(x + width - solid, y + row, x + width - solid + 1, y + row + 1, soft);
                    }
                }
            }
        }
    }

    /**
     * Compatibility alias for older Vanta widgets. All native controls now share
     * the same cached superellipse geometry as the retained component framework.
     */
    public static void outlineRounded(GuiGraphics graphics, int x, int y, int width,
                                      int height, int radius, int color) {
        outlineSquircle(graphics, x, y, width, height, radius, color, 1);
    }

    /** Compatibility alias retained for existing native widgets. */
    public static void fillRounded(GuiGraphics graphics, int x, int y, int width,
                                   int height, int radius, int color) {
        fillSquircle(graphics, x, y, width, height, radius, color);
    }

}

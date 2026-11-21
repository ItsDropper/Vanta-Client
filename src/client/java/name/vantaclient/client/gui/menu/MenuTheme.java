package name.vantaclient.client.gui.menu;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.compose.typography.VantaTypography;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Visual tokens and text helpers for the Vanta menu shell.
 *
 * Everything the menu draws by hand (cards, chips, the keyboard map) resolves colours,
 * fonts and the accent from here so the pages stay consistent with each other.
 */
public final class MenuTheme {
    private MenuTheme() {}

    public static final Identifier FONT_UI = Identifier.fromNamespaceAndPath("vanta", "ui");
    public static final Identifier FONT_MEDIUM = Identifier.fromNamespaceAndPath("vanta", "medium");
    public static final Identifier FONT_HEADING = Identifier.fromNamespaceAndPath("vanta", "heading");
    public static final Identifier FONT_SMALL = Identifier.fromNamespaceAndPath("vanta", "small");

    public static final int SIZE_UI = 13;
    public static final int SIZE_HEADING = 26;
    public static final int SIZE_SMALL = 11;

    public static final int PANEL = 0xB80E1320;
    public static final int SIDEBAR = 0x59000000;
    public static final int CARD = 0x9E10141D;
    public static final int CARD_HOVER = 0xB4181D29;
    public static final int ROW = 0x9210141D;
    public static final int FIELD = 0xB0141925;
    public static final int LINE = 0x2EFFFFFF;
    public static final int LINE_SOFT = 0x1AFFFFFF;
    public static final int TEXT = 0xFFF2F4F8;
    public static final int TEXT_DIM = 0xFFA3A9B5;
    public static final int TEXT_MUTED = 0xFF6F7581;
    public static final int NEUTRAL_DOT = 0xFF474B55;
    public static final int DANGER = 0xFFE5484D;

    /** Panel colour whose alpha follows the user's panel opacity preference. */
    public static int panel() {
        double opacity = VantaClientPreferences.get().panelOpacity;
        if (!Double.isFinite(opacity)) opacity = 0.85;
        opacity = Math.max(0.72, Math.min(0.98, opacity));
        // Preference 0.85 maps to the reference alpha; the range stays see-through enough to show the blur.
        int alpha = Math.round(((PANEL >>> 24) & 0xFF) * (float) (opacity / 0.85));
        return (PANEL & 0x00FFFFFF) | (Math.min(236, alpha) << 24);
    }

    /** Slightly deeper tint for the bottom of the shell so the glass reads as lit from above. */
    public static int panelBottom() {
        return mix(panel(), 0xFF05070C, 0.25f);
    }

    public static int accent() {
        return VantaClientPreferences.get().accent.color();
    }

    /** Linear blend between two ARGB colours. */
    public static int mix(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = Math.round(((from >>> 24) & 255) * (1.0f - t) + ((to >>> 24) & 255) * t);
        int r = Math.round(((from >>> 16) & 255) * (1.0f - t) + ((to >>> 16) & 255) * t);
        int g = Math.round(((from >>> 8) & 255) * (1.0f - t) + ((to >>> 8) & 255) * t);
        int b = Math.round((from & 255) * (1.0f - t) + (to & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int color, int alpha) {
        return VantaUiTheme.withAlpha(color, alpha);
    }

    /**
     * Top edge for text of {@code size} px whose capital letters are centred on {@code centerY}.
     * Minecraft puts the baseline of TTF text 7 units below the draw position, and Poppins
     * capitals are about 0.7 em tall, so the cap centre sits at {@code top + 7 - 0.35 * size}.
     */
    public static int textTop(int centerY, int size) {
        return centerY - 7 + Math.round(size * 0.35f);
    }

    public static int sizeOf(Identifier fontId) {
        if (FONT_HEADING.equals(fontId)) return SIZE_HEADING;
        if (FONT_SMALL.equals(fontId)) return SIZE_SMALL;
        return SIZE_UI;
    }

    public static int width(Font font, String text, Identifier fontId) {
        return VantaTypography.textWidth(font, text == null ? "" : text, fontId);
    }

    public static String fit(Font font, String text, int maxWidth, Identifier fontId) {
        if (text == null) return "";
        if (width(font, text, fontId) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisWidth = width(font, ellipsis, fontId);
        String clipped = VantaTypography.fitText(font, text, Math.max(0, maxWidth - ellipsisWidth), fontId);
        return clipped.isEmpty() ? "" : clipped.stripTrailing() + ellipsis;
    }

    /** Draws text with its left edge at {@code x}, vertically centred on {@code centerY}. */
    public static void text(GuiGraphics graphics, Font font, String text, int x, int centerY,
                            int color, Identifier fontId) {
        if (text == null || text.isEmpty()) return;
        graphics.drawString(font, VantaTypography.styledText(text, fontId),
                x, textTop(centerY, sizeOf(fontId)), color);
    }

    public static void textCentered(GuiGraphics graphics, Font font, String text, int centerX,
                                    int centerY, int color, Identifier fontId) {
        if (text == null || text.isEmpty()) return;
        text(graphics, font, text, centerX - width(font, text, fontId) / 2, centerY, color, fontId);
    }

    public static void textRight(GuiGraphics graphics, Font font, String text, int rightX,
                                 int centerY, int color, Identifier fontId) {
        if (text == null || text.isEmpty()) return;
        text(graphics, font, text, rightX - width(font, text, fontId), centerY, color, fontId);
    }

    public static void gradient(GuiGraphics graphics, int x, int y, int w, int h, int radius, int top, int bottom) {
        VantaUiTheme.fillSquircleGradient(graphics, x, y, w, h, radius, top, bottom);
    }

    /** Accent button fill: lighter at the top, deeper at the bottom, brighter on hover. */
    public static void accentFill(GuiGraphics graphics, int x, int y, int w, int h, int radius, float hover) {
        int accent = accent();
        int top = mix(accent, 0xFFFFFFFF, 0.12f + 0.12f * hover);
        int bottom = mix(accent, 0xFF000000, 0.16f - 0.10f * hover);
        gradient(graphics, x, y, w, h, radius, top, bottom);
    }

    public static void fill(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        VantaUiTheme.fillSquircle(graphics, x, y, w, h, radius, color);
    }

    public static void outline(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        VantaUiTheme.outlineSquircle(graphics, x, y, w, h, radius, color);
    }

    /** Filled circle approximated by the shared squircle primitive with a full radius. */
    public static void dot(GuiGraphics graphics, int cx, int cy, int diameter, int color) {
        int r = diameter / 2;
        VantaUiTheme.fillSquircle(graphics, cx - r, cy - r, diameter, diameter, r, color);
    }
}

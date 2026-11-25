package name.vantaclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Poppins text for Vanta's own screens. Components and measured widths are cached because the same
 * labels are drawn and measured every frame; sizes are in GUI pixels (the font is 13 units tall).
 */
public final class VantaText {
    public enum Weight {
        REGULAR("ui"), MEDIUM("medium"), BOLD("bold");

        private final Identifier id;
        final Map<String, Component>[] components;
        final Map<String, Integer> widths = new HashMap<>();

        @SuppressWarnings("unchecked")
        Weight(String name) {
            this.id = Identifier.fromNamespaceAndPath("vanta", name);
            this.components = new Map[5];
            for (int i = 0; i < components.length; i++) components[i] = new HashMap<>();
        }
    }

    private VantaText() {}

    private static Component component(String value, Weight weight, float devicePerUnit) {
        int index = VantaFonts.densityIndex(devicePerUnit);
        Map<String, Component> cache = weight.components[index];
        Component component = cache.get(value);
        if (component == null) {
            FontDescription font = new FontDescription.Resource(VantaFonts.variant(weight.id, devicePerUnit));
            component = Component.literal(value).withStyle(style -> style.withFont(font));
            if (cache.size() > 512) cache.clear();
            cache.put(value, component);
        }
        return component;
    }

    public static int width(String value, float size, Weight weight) {
        Integer base = weight.widths.get(value);
        if (base == null) {
            base = Minecraft.getInstance().font.width(component(value, weight, 4.0f));
            if (weight.widths.size() > 512) weight.widths.clear();
            weight.widths.put(value, base);
        }
        return Math.round(base * size / 13.0f);
    }

    /** Draws text with its vertical centre at {@code centerY}. */
    public static void draw(GuiGraphics g, String value, float x, float centerY, float size, int color, Weight weight) {
        if ((color >>> 24) == 0 || value.isEmpty()) return;
        float scale = size / 13.0f;
        float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
        var pose = g.pose();
        float poseScale = (float) Math.hypot(pose.m00(), pose.m01());
        if (poseScale <= 0.0001f) poseScale = 1.0f;
        // Snap the origin to whole device pixels so glyph quads never straddle pixel centres.
        float snappedX = (Math.round((pose.m00() * x + pose.m20()) * gui) / gui - pose.m20()) / pose.m00();
        float snappedY = (Math.round((pose.m11() * centerY + pose.m21()) * gui) / gui - pose.m21()) / pose.m11();
        pose.pushMatrix();
        pose.translate(Float.isFinite(snappedX) ? snappedX : x, Float.isFinite(snappedY) ? snappedY : centerY);
        pose.scale(scale, scale);
        g.drawString(Minecraft.getInstance().font, component(value, weight, gui * poseScale * scale), 0, -2, color, false);
        pose.popMatrix();
    }

    public static void drawCentered(GuiGraphics g, String value, float centerX, float centerY, float size, int color, Weight weight) {
        draw(g, value, centerX - width(value, size, weight) / 2.0f, centerY, size, color, weight);
    }

    public static String fit(String value, float size, Weight weight, int maxWidth) {
        if (width(value, size, weight) <= maxWidth) return value;
        String cut = value;
        while (cut.length() > 1 && width(cut + "…", size, weight) > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut.stripTrailing() + "…";
    }

    public static int withAlpha(int color, float factor) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, factor)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

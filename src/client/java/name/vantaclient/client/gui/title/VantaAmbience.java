package name.vantaclient.client.gui.title;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Lighting for the title screen: a gentle vignette, a warm top grade, slow light shafts, drifting
 * dust motes and a sheen that glides over the primary button. Everything is a handful of blits of
 * three soft textures, so it costs next to nothing, and each effect is slow and low contrast so it
 * reads as atmosphere rather than as motion competing with the UI.
 */
final class VantaAmbience {
    private static final Identifier VIGNETTE = tex("vignette");
    private static final Identifier GLOW = tex("glow");
    private static final Identifier SHAFT = tex("shaft");
    private static final int MOTES = 26;

    private VantaAmbience() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("vanta", "textures/gui/title/" + name + ".png");
    }

    private static int argb(int rgb, float alpha) {
        int a = Math.round(255.0f * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static float hash(int n) {
        int x = n * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Draws the full-screen lighting layer underneath the interface. */
    static void render(GuiGraphics g, int w, int h, long millis, int accent, float intro, float unit) {
        float t = millis / 1000.0f;

        // Warm key light from above, easing out before the middle so the world keeps its colour.
        g.fillGradient(0, 0, w, Math.round(h * 0.55f), argb(0xFFB56B, 0.10f * intro), argb(0xFFB56B, 0.0f));

        // Two slow shafts of light leaning in from the upper left.
        for (int i = 0; i < 2; i++) {
            float sway = (float) Math.sin(t * 0.18f + i * 2.1f);
            float shaftW = w * (0.20f + 0.05f * i);
            float cx = w * (0.16f + 0.24f * i) + sway * w * 0.012f;
            float alpha = (0.075f + 0.025f * (float) Math.sin(t * 0.31f + i * 1.7f)) * intro;
            g.pose().pushMatrix();
            g.pose().translate(cx, -h * 0.05f);
            g.pose().rotate(-0.42f);
            g.blit(RenderPipelines.GUI_TEXTURED, SHAFT, Math.round(-shaftW / 2), 0, 0.0f, 0.0f,
                    Math.round(shaftW), Math.round(h * 1.25f), 64, 256, 64, 256, argb(0xFFE3B4, alpha));
            g.pose().popMatrix();
        }

        // Dust motes drifting up through the light; each twinkles on its own phase.
        for (int i = 0; i < MOTES; i++) {
            float seedX = hash(i * 3 + 1);
            float seedY = hash(i * 3 + 2);
            float seedS = hash(i * 3 + 3);
            float speed = 0.012f + 0.02f * seedS;
            float px = ((seedX + t * speed * 0.6f + 0.08f * (float) Math.sin(t * 0.3f + i)) % 1.0f + 1.0f) % 1.0f * w;
            float py = (1.0f - ((seedY + t * speed) % 1.0f)) * h;
            float twinkle = 0.5f + 0.5f * (float) Math.sin(t * (0.7f + seedS) + i * 1.9f);
            float size = (3.0f + 5.0f * seedS) * unit;
            float edge = Math.min(1.0f, Math.min(py, h - py) / (h * 0.12f));
            float alpha = (0.10f + 0.34f * twinkle) * edge * intro;
            int color = i % 5 == 0 ? accent : 0xFFE9C4;
            int s = Math.max(2, Math.round(size));
            g.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(px - s / 2.0f), Math.round(py - s / 2.0f),
                    0.0f, 0.0f, s, s, 128, 128, 128, 128, argb(color, alpha));
        }

        // Vignette last, so it frames everything above it.
        g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0f, 0.0f, w, h, 192, 192, 192, 192,
                argb(0x02040A, 0.62f * intro));
    }

    /** A soft pool of coloured light, used behind cards. */
    static void glow(GuiGraphics g, int cx, int cy, int radius, int rgb, float alpha) {
        if (alpha <= 0.002f) return;
        g.blit(RenderPipelines.GUI_TEXTURED, GLOW, cx - radius, cy - radius, 0.0f, 0.0f,
                radius * 2, radius * 2, 128, 128, 128, 128, argb(rgb, alpha));
    }

    /** A band of light that periodically glides across a button; {@code boost} brightens it on hover. */
    static void sheen(GuiGraphics g, int x, int y, int w, int h, long millis, float boost) {
        float cycle = (millis % 5200L) / 5200.0f;
        float travel = cycle < 0.38f ? cycle / 0.38f : -1.0f;
        float base = 0.10f + 0.20f * boost;
        float pos = travel >= 0.0f ? travel : 0.5f;
        float strength = travel >= 0.0f ? (float) Math.sin(travel * Math.PI) : 0.0f;
        float alpha = base * (travel >= 0.0f ? strength : 0.0f) + 0.10f * boost;
        if (alpha <= 0.004f) return;
        int band = Math.round(w * 0.42f);
        int bx = Math.round(x - band + (w + band) * pos);
        g.enableScissor(x, y, x + w, y + h);
        g.blit(RenderPipelines.GUI_TEXTURED, GLOW, bx, y - h / 2, 0.0f, 0.0f, band, h * 2, 128, 128, 128, 128,
                argb(0xFFFFFF, alpha));
        g.disableScissor();
    }
}

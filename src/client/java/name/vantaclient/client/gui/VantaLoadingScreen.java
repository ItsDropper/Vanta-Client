package name.vantaclient.client.gui;

import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** The startup loading screen: the Vanta mark, a wordmark and a thin progress line on near-black. */
public final class VantaLoadingScreen {
    private static final Identifier MARK = Identifier.fromNamespaceAndPath("vanta", "textures/gui/title/mark.png");
    private static final VantaUiTheme THEME = new VantaUiTheme();
    private static float shown;
    private static long lastNanos;

    private VantaLoadingScreen() {}

    /**
     * @param progress    vanilla's reload progress, 0..1
     * @param fadeOutStart millisecond timestamp when the fade-out began, or a negative value while loading
     */
    public static void render(GuiGraphics g, float progress, long fadeOutStart) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000.0f);
        lastNanos = now;

        boolean fading = fadeOutStart >= 0;
        // Vanilla holds its own screen fully opaque for one second after loading finishes and only then
        // fades it over the next second. The fade has to follow the same clock (Util.getMillis is
        // System.nanoTime in milliseconds), otherwise the Mojang logo shows through while ours fades.
        float elapsed = fading ? (System.nanoTime() / 1_000_000L - fadeOutStart) / 1000.0f : -1.0f;
        float fade = elapsed >= 1.0f ? 1.0f - Math.max(0.0f, Math.min(1.0f, elapsed - 1.0f)) : 1.0f;
        if (fade <= 0.0f) return;

        float target = fading ? 1.0f : Math.max(0.0f, Math.min(1.0f, progress));
        if (!fading && target < shown) shown = target; // a new reload started a fresh overlay
        shown += (Math.max(shown, target) - shown) * (1.0f - (float) Math.exp(-dt * 7.0f));

        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, VantaText.withAlpha(0xFF070B12, fade));

        float unit = Math.max(28.0f, Math.min(72.0f, Math.min(w, h) * 0.13f));
        int mark = Math.round(unit);
        int cx = w / 2;
        int top = h / 2 - Math.round(unit * 1.05f);
        g.blit(RenderPipelines.GUI_TEXTURED, MARK, cx - mark / 2, top, 0.0f, 0.0f, mark, mark,
                256, 256, 256, 256, VantaText.withAlpha(0xFFFFFFFF, fade));

        float textSize = Math.max(15.0f, unit * 0.34f);
        VantaText.drawCentered(g, "Vanta Client", cx, top + mark + Math.round(unit * 0.42f), textSize,
                VantaText.withAlpha(0xFFFFFFFF, fade), VantaText.Weight.BOLD);

        int barW = Math.round(unit * 3.0f);
        int barH = Math.max(2, Math.round(unit / 20.0f));
        int barY = top + mark + Math.round(unit * 0.95f);
        int barX = cx - barW / 2;
        VantaUiTheme.fillSquircle(g, barX, barY, barW, barH, barH / 2, VantaText.withAlpha(0x26FFFFFF, fade));
        int filled = Math.max(barH, Math.round(barW * shown));
        VantaUiTheme.fillSquircle(g, barX, barY, filled, barH, barH / 2, VantaText.withAlpha(THEME.accent(), fade));
    }
}

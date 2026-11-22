package name.vantaclient.client.gui;

import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.gui.VantaText.Weight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Vanta's pause menu. The vanilla buttons stay in place as the real, focusable controls (so every
 * action and other mods' additions keep working); this class lays them out in one compact card and
 * paints them. The first button is the primary action, the last one is the destructive exit, and
 * everything between sits in a two-column grid.
 */
public final class VantaPauseUi implements Renderable {
    private static final Identifier MARK = Identifier.fromNamespaceAndPath("vanta", "textures/gui/title/mark.png");
    private static final VantaUiTheme THEME = new VantaUiTheme();
    private static final Set<Object> OWNED = Collections.newSetFromMap(new WeakHashMap<>());

    private static final int CARD = 0xE60A1018;
    private static final int CARD_EDGE = 0x2EFFFFFF;
    private static final int DANGER = 0xFFEF5D6B;

    private enum Kind { PRIMARY, NORMAL, DANGER }

    private static final class Entry {
        final AbstractButton button;
        final String label;
        final Kind kind;
        float hover;

        Entry(AbstractButton button, String label, Kind kind) {
            this.button = button;
            this.label = label;
            this.kind = kind;
        }
    }

    private final Screen screen;
    private final List<Entry> entries = new ArrayList<>();
    private final String title;
    private final String subtitle;
    private int cardX, cardY, cardW, cardH;
    private float scale = 1.0f;
    private long lastNanos = System.nanoTime();
    private float intro;

    private VantaPauseUi(Screen screen, List<AbstractButton> buttons, String subtitle) {
        this.screen = screen;
        this.title = "Game menu";
        this.subtitle = subtitle;
        for (int i = 0; i < buttons.size(); i++) {
            AbstractButton button = buttons.get(i);
            Kind kind = i == 0 ? Kind.PRIMARY : (i == buttons.size() - 1 && buttons.size() > 1 ? Kind.DANGER : Kind.NORMAL);
            String label = button.getMessage().getString();
            if (label.endsWith("...")) label = label.substring(0, label.length() - 3);
            entries.add(new Entry(button, label, kind));
            button.setMessage(Component.empty());
            OWNED.add(button);
        }
        layout();
    }

    /** True for vanilla buttons whose surface and label Vanta paints itself. */
    public static boolean owns(Object widget) {
        return OWNED.contains(widget);
    }

    public static VantaPauseUi create(Screen screen, List<AbstractButton> buttons, String subtitle) {
        return new VantaPauseUi(screen, buttons, subtitle);
    }

    private int s(float value) {
        return Math.round(value * scale);
    }

    private void layout() {
        scale = Math.max(0.55f, Math.min(1.0f, Math.min(screen.height / 330.0f, screen.width / 340.0f)));
        cardW = s(312);
        int pad = s(16);
        int headerH = s(64);
        int primaryH = s(40), rowH = s(34), exitH = s(38), gap = s(8);
        int middle = Math.max(0, entries.size() - 2);
        int rows = (middle + 1) / 2;
        cardH = pad + headerH + (entries.isEmpty() ? 0 : primaryH)
                + (rows > 0 ? gap + rows * rowH + (rows - 1) * gap : 0)
                + (entries.size() > 1 ? gap + s(6) + exitH : 0) + pad;
        cardX = (screen.width - cardW) / 2;
        cardY = Math.max(s(8), (screen.height - cardH) / 2);

        int innerW = cardW - pad * 2;
        int y = cardY + pad + headerH;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            AbstractButton b = entry.button;
            if (entry.kind == Kind.PRIMARY) {
                place(b, cardX + pad, y, innerW, primaryH);
                y += primaryH;
            } else if (entry.kind == Kind.DANGER) {
                y += gap + s(6);
                place(b, cardX + pad, y, innerW, exitH);
            } else {
                int index = i - 1;
                int column = index % 2;
                if (column == 0) y += gap;
                int colW = (innerW - gap) / 2;
                place(b, cardX + pad + column * (colW + gap), y, column == 0 && i == entries.size() - 2 ? innerW : colW, rowH);
                if (column == 1 || i == entries.size() - 2) y += rowH;
            }
        }
    }

    private static void place(AbstractButton button, int x, int y, int w, int h) {
        button.setX(x);
        button.setY(y);
        button.setWidth(w);
        button.setHeight(h);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000.0f);
        lastNanos = now;
        float blend = 1.0f - (float) Math.exp(-dt * 16.0f);
        intro += (1.0f - intro) * (1.0f - (float) Math.exp(-dt * 12.0f));
        if (cardX != (screen.width - cardW) / 2) layout();
        int accent = THEME.accent();
        float a = intro;
        int slide = Math.round((1.0f - a) * s(10));

        int x = cardX, y = cardY + slide;
        VantaUiTheme.fillSquircle(g, x, y, cardW, cardH, s(20), VantaText.withAlpha(CARD, a));
        VantaUiTheme.outlineSquircle(g, x, y, cardW, cardH, s(20), VantaText.withAlpha(CARD_EDGE, a));

        int pad = s(16);
        int mark = s(30);
        g.blit(RenderPipelines.GUI_TEXTURED, MARK, x + pad, y + pad + s(4), 0.0f, 0.0f, mark, mark,
                256, 256, 256, 256, VantaText.withAlpha(0xFFFFFFFF, a));
        int textX = x + pad + mark + s(12);
        VantaText.draw(g, title, textX, y + pad + s(13), s(17), VantaText.withAlpha(0xFFFFFFFF, a), Weight.BOLD);
        if (!subtitle.isEmpty()) {
            VantaText.draw(g, VantaText.fit(subtitle, s(12), Weight.REGULAR, cardW - pad * 2 - mark - s(12)),
                    textX, y + pad + s(33), s(12), VantaText.withAlpha(0xFFA3AFC3, a), Weight.REGULAR);
        }

        for (Entry entry : entries) {
            AbstractButton b = entry.button;
            float target = b.active && b.isHoveredOrFocused() ? 1.0f : 0.0f;
            entry.hover += (target - entry.hover) * blend;
            if (Math.abs(target - entry.hover) < 0.01f) entry.hover = target;
            int bx = b.getX(), by = b.getY() + slide, bw = b.getWidth(), bh = b.getHeight();
            int fill;
            int text;
            switch (entry.kind) {
                case PRIMARY -> {
                    fill = VantaText.mix(accent, 0xFFFFFFFF, 0.14f * entry.hover);
                    text = 0xFFFFFFFF;
                }
                case DANGER -> {
                    fill = VantaText.mix(0x1AEF5D6B, 0x40EF5D6B, entry.hover);
                    text = VantaText.mix(0xFFE6A3AA, 0xFFFFFFFF, entry.hover);
                }
                default -> {
                    fill = VantaText.mix(0x14FFFFFF, 0x2AFFFFFF, entry.hover);
                    text = VantaText.mix(0xFFC3CDDC, 0xFFFFFFFF, entry.hover);
                }
            }
            if (!b.active) {
                fill = VantaText.withAlpha(fill, 0.5f);
                text = VantaText.withAlpha(text, 0.45f);
            }
            VantaUiTheme.fillSquircle(g, bx, by, bw, bh, s(12), VantaText.withAlpha(fill, a));
            float size = entry.kind == Kind.PRIMARY ? s(15) : s(13);
            String label = VantaText.fit(entry.label, size, Weight.MEDIUM, bw - s(16));
            VantaText.drawCentered(g, label, bx + bw / 2.0f, by + bh / 2.0f, size,
                    VantaText.withAlpha(text, a), Weight.MEDIUM);
            if (b.isFocused()) {
                VantaUiTheme.outlineSquircle(g, bx - 1, by - 1, bw + 2, bh + 2, s(13),
                        VantaText.withAlpha(0xFFFFFFFF, 0.85f * a), 1);
            }
        }
    }
}

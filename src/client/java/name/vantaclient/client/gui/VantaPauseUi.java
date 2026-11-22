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
    private static final Identifier MARK = Identifier.fromNamespaceAndPath("vanta", "textures/gui/title/mark_small.png");
    private static final VantaUiTheme THEME = new VantaUiTheme();
    private static final Set<Object> OWNED = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Identifier SCRIM_RIGHT = Identifier.fromNamespaceAndPath("vanta", "textures/gui/title/scrim_right.png");
    private static final Identifier SCRIM_TOP = Identifier.fromNamespaceAndPath("vanta", "textures/gui/title/scrim_top.png");

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
    private int colX, colY, colW, headerH;
    private int dividerY;
    private float scale = 1.0f;
    private long lastNanos = System.nanoTime();
    private float intro;

    private VantaPauseUi(Screen screen, List<AbstractButton> buttons, String subtitle) {
        this.screen = screen;
        this.title = subtitle.isEmpty() ? "Game menu" : subtitle;
        this.subtitle = Minecraft.getInstance().getSingleplayerServer() != null ? "Singleplayer"
                : Minecraft.getInstance().getCurrentServer() != null ? Minecraft.getInstance().getCurrentServer().ip : "";
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

    /**
     * One column on the right over a darkened world: eyebrow, world name, the primary "back to game"
     * action, a quiet list of rows and the exit at the bottom, separated by a divider.
     */
    private void layout() {
        int middle = Math.max(0, entries.size() - 2);
        float requestedScale = Math.min(1.0f, Math.min(screen.height / 360.0f, screen.width / 560.0f));
        int designHeight = 78 + 46 + 10 + middle * 40 + (entries.size() > 1 ? 16 + 40 : 0);
        float verticalFit = Math.max(0.1f, (screen.height - 24.0f) / Math.max(1.0f, designHeight));
        scale = Math.max(0.1f, Math.min(requestedScale, verticalFit));
        colW = Math.max(1, Math.min(s(360), Math.max(1, screen.width - s(24))));
        int rightInset = Math.max(s(12), screen.width / 14);
        colX = Math.max(0, screen.width - colW - rightInset);
        int primaryH = s(46), rowH = s(40), gap = s(10);
        headerH = s(78);
        int exitH = entries.size() > 1 ? rowH : 0;
        int total = headerH + primaryH + gap + middle * rowH + (entries.size() > 1 ? s(16) + exitH : 0);
        colY = Math.max(s(12), Math.min(Math.max(0, screen.height - total), (screen.height - total) / 2));
        int y = colY + headerH;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.kind == Kind.PRIMARY) {
                place(entry.button, colX, y, colW, primaryH);
                y += primaryH + gap;
            } else if (entry.kind == Kind.DANGER) {
                dividerY = y + s(7);
                y += s(16);
                place(entry.button, colX, y, colW, exitH);
            } else {
                place(entry.button, colX, y, colW, rowH);
                y += rowH;
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
        intro += (1.0f - intro) * (1.0f - (float) Math.exp(-dt * 10.0f));
        if (colX + colW + Math.max(s(48), screen.width / 14) != screen.width) layout();
        int accent = THEME.accent();
        float a = intro;
        int slide = Math.round((1.0f - a) * s(14));
        int w = screen.width, h = screen.height;

        // Darkening from the right keeps the column readable while the world stays visible on the left.
        int shadeW = Math.min(w, Math.round(w * 0.62f));
        g.blit(RenderPipelines.GUI_TEXTURED, SCRIM_RIGHT, w - shadeW, 0, 0.0f, 0.0f, shadeW, h, 256, 4, 256, 4,
                VantaText.withAlpha(0xFFFFFFFF, a));
        g.blit(RenderPipelines.GUI_TEXTURED, SCRIM_TOP, 0, 0, 0.0f, 0.0f, w, Math.min(h, s(120)), 4, 256, 4, 256,
                VantaText.withAlpha(0xFFFFFFFF, a * 0.9f));

        // Brand and player, top bar.
        int margin = s(28);
        int mark = s(24);
        g.blit(RenderPipelines.GUI_TEXTURED, MARK, margin, s(20), 0.0f, 0.0f, mark, mark, 96, 96, 96, 96,
                VantaText.withAlpha(0xFFFFFFFF, a));
        VantaText.draw(g, "Vanta", margin + mark + s(10), s(20) + mark / 2.0f, s(17), VantaText.withAlpha(0xFFFFFFFF, a), Weight.BOLD);
        String name = Minecraft.getInstance().getUser().getName();
        int accountWidth = Math.max(0, w - margin * 2 - s(72));
        if (accountWidth >= s(28)) {
            String shownName = VantaText.fit(name, s(13), Weight.MEDIUM, accountWidth);
            VantaText.draw(g, shownName, w - margin - VantaText.width(shownName, s(13), Weight.MEDIUM),
                    s(20) + mark / 2.0f, s(13), VantaText.withAlpha(0xFFC3CDDC, a), Weight.MEDIUM);
        }

        int x = colX, y = colY + slide;
        VantaText.draw(g, "Paused", x, y + s(8), s(12), VantaText.withAlpha(accent, a), Weight.MEDIUM);
        VantaText.draw(g, VantaText.fit(title, s(24), Weight.BOLD, colW), x, y + s(34), s(24),
                VantaText.withAlpha(0xFFFFFFFF, a), Weight.BOLD);
        if (!subtitle.isEmpty()) {
            VantaText.draw(g, VantaText.fit(subtitle, s(12), Weight.REGULAR, colW), x, y + s(58), s(12),
                    VantaText.withAlpha(0xFFA3AFC3, a), Weight.REGULAR);
        }
        if (entries.size() > 1) g.fill(x, dividerY + slide, x + colW, dividerY + slide + 1, VantaText.withAlpha(0x26FFFFFF, a));

        for (Entry entry : entries) {
            AbstractButton b = entry.button;
            float target = b.active && b.isHoveredOrFocused() ? 1.0f : 0.0f;
            entry.hover += (target - entry.hover) * blend;
            if (Math.abs(target - entry.hover) < 0.01f) entry.hover = target;
            int bx = b.getX(), by = b.getY() + slide, bw = b.getWidth(), bh = b.getHeight();
            int text;
            if (entry.kind == Kind.PRIMARY) {
                int fill = VantaText.mix(accent, 0xFFFFFFFF, 0.14f * entry.hover);
                VantaUiTheme.fillSquircle(g, bx, by, bw, bh, s(14), VantaText.withAlpha(fill, a));
                text = 0xFFFFFFFF;
                VantaText.drawCentered(g, VantaText.fit(entry.label, s(15), Weight.BOLD, bw - s(16)), bx + bw / 2.0f,
                        by + bh / 2.0f, s(15), VantaText.withAlpha(text, a), Weight.BOLD);
            } else {
                boolean danger = entry.kind == Kind.DANGER;
                if (entry.hover > 0.0f) {
                    int tint = danger ? 0x30EF5D6B : 0x1CFFFFFF;
                    VantaUiTheme.fillSquircle(g, bx, by + s(2), bw, bh - s(4), s(11),
                            VantaText.withAlpha(tint, a * entry.hover));
                }
                text = danger ? VantaText.mix(0xFFE6A3AA, 0xFFFF8F9A, entry.hover)
                        : VantaText.mix(0xFFB9C4D6, 0xFFFFFFFF, entry.hover);
                if (!b.active) text = VantaText.withAlpha(text, 0.4f);
                int inset = s(14) + Math.round(s(4) * entry.hover);
                VantaText.draw(g, VantaText.fit(entry.label, s(14), Weight.MEDIUM, bw - inset - s(14)), bx + inset,
                        by + bh / 2.0f, s(14), VantaText.withAlpha(text, a), Weight.MEDIUM);
            }
            if (b.isFocused()) {
                VantaUiTheme.outlineSquircle(g, bx - 1, by - 1, bw + 2, bh + 2, s(13),
                        VantaText.withAlpha(0xFFFFFFFF, 0.85f * a), 1);
            }
        }
    }
}

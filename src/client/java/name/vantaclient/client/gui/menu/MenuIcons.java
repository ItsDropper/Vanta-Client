package name.vantaclient.client.gui.menu;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Small outline icon set drawn from line and arc primitives on a 16-unit grid.
 * Icons scale with the requested size and use a stroke of roughly 1.4 grid units.
 */
public final class MenuIcons {
    private MenuIcons() {}

    public enum Icon {
        MODS, PROFILES, KEYBINDS, THEMES, PREFERENCES,
        ALL, STAR, STAR_FILLED, GAUGE, HUD, WRENCH, SPARKLE, DOTS,
        CHEVRON_DOWN, CHEVRON_UP, CHEVRON_LEFT, CHEVRON_RIGHT,
        ARROW_LEFT, ARROW_RIGHT, CLOSE, SEARCH, INFO, BELL, PLUS, FOLDER,
        GRID, LIST, MOUSE, CHECK, SLIDERS, EDIT_HUD, TRASH, RESET
    }

    public static void draw(GuiGraphics g, Icon icon, int x, int y, int size, int color) {
        if (g == null || icon == null || size <= 0) return;
        float u = size / 16.0f;
        float t = Math.max(1.0f, u * 1.35f);
        switch (icon) {
            case MODS, GRID, ALL -> {
                box(g, x + 2 * u, y + 2 * u, 5 * u, 5 * u, color, t);
                box(g, x + 9 * u, y + 2 * u, 5 * u, 5 * u, color, t);
                box(g, x + 2 * u, y + 9 * u, 5 * u, 5 * u, color, t);
                box(g, x + 9 * u, y + 9 * u, 5 * u, 5 * u, color, t);
            }
            case PROFILES -> {
                ring(g, x + 8 * u, y + 5.2f * u, 2.8f * u, color, t);
                arc(g, x + 8 * u, y + 15 * u, 5.6f * u, 200, 340, color, t);
            }
            case KEYBINDS -> {
                box(g, x + 1.5f * u, y + 3.5f * u, 13 * u, 9 * u, color, t);
                for (int col = 0; col < 4; col++) {
                    dotAt(g, x + (4 + col * 2.7f) * u, y + 6.3f * u, color, t);
                }
                line(g, x + 4.5f * u, y + 10 * u, x + 11.5f * u, y + 10 * u, color, t);
            }
            case THEMES -> {
                ring(g, x + 8 * u, y + 8 * u, 6 * u, color, t);
                dotAt(g, x + 5.4f * u, y + 6 * u, color, t * 1.2f);
                dotAt(g, x + 8.4f * u, y + 4.6f * u, color, t * 1.2f);
                dotAt(g, x + 10.8f * u, y + 7 * u, color, t * 1.2f);
                dotAt(g, x + 6.2f * u, y + 10.2f * u, color, t * 1.2f);
            }
            case PREFERENCES, SLIDERS -> {
                line(g, x + 2 * u, y + 4 * u, x + 14 * u, y + 4 * u, color, t);
                line(g, x + 2 * u, y + 8 * u, x + 14 * u, y + 8 * u, color, t);
                line(g, x + 2 * u, y + 12 * u, x + 14 * u, y + 12 * u, color, t);
                disc(g, x + 5.5f * u, y + 4 * u, 1.9f * u, color);
                disc(g, x + 10.5f * u, y + 8 * u, 1.9f * u, color);
                disc(g, x + 6.5f * u, y + 12 * u, 1.9f * u, color);
            }
            case STAR -> star(g, x, y, u, color, t, false);
            case STAR_FILLED -> star(g, x, y, u, color, t, true);
            case GAUGE -> {
                arc(g, x + 8 * u, y + 10 * u, 6.5f * u, 180, 360, color, t);
                line(g, x + 8 * u, y + 10 * u, x + 11.4f * u, y + 5.6f * u, color, t);
                disc(g, x + 8 * u, y + 10 * u, 1.3f * u, color);
            }
            case HUD -> {
                line(g, x + 2 * u, y + 6 * u, x + 2 * u, y + 2 * u, color, t);
                line(g, x + 2 * u, y + 2 * u, x + 6 * u, y + 2 * u, color, t);
                line(g, x + 10 * u, y + 2 * u, x + 14 * u, y + 2 * u, color, t);
                line(g, x + 14 * u, y + 2 * u, x + 14 * u, y + 6 * u, color, t);
                line(g, x + 2 * u, y + 10 * u, x + 2 * u, y + 14 * u, color, t);
                line(g, x + 2 * u, y + 14 * u, x + 6 * u, y + 14 * u, color, t);
                line(g, x + 10 * u, y + 14 * u, x + 14 * u, y + 14 * u, color, t);
                line(g, x + 14 * u, y + 10 * u, x + 14 * u, y + 14 * u, color, t);
            }
            case EDIT_HUD -> {
                line(g, x + 2 * u, y + 6 * u, x + 2 * u, y + 2 * u, color, t);
                line(g, x + 2 * u, y + 2 * u, x + 6 * u, y + 2 * u, color, t);
                line(g, x + 10 * u, y + 2 * u, x + 14 * u, y + 2 * u, color, t);
                line(g, x + 14 * u, y + 2 * u, x + 14 * u, y + 6 * u, color, t);
                line(g, x + 2 * u, y + 10 * u, x + 2 * u, y + 14 * u, color, t);
                line(g, x + 2 * u, y + 14 * u, x + 6 * u, y + 14 * u, color, t);
                line(g, x + 6 * u, y + 8 * u, x + 10 * u, y + 8 * u, color, t);
                line(g, x + 8 * u, y + 6 * u, x + 8 * u, y + 10 * u, color, t);
            }
            case WRENCH -> {
                line(g, x + 3 * u, y + 13 * u, x + 9 * u, y + 7 * u, color, t * 1.3f);
                ring(g, x + 11 * u, y + 5 * u, 2.9f * u, color, t);
            }
            case SPARKLE -> {
                line(g, x + 8 * u, y + 2 * u, x + 8 * u, y + 14 * u, color, t);
                line(g, x + 2 * u, y + 8 * u, x + 14 * u, y + 8 * u, color, t);
                line(g, x + 4.2f * u, y + 4.2f * u, x + 11.8f * u, y + 11.8f * u, color, t * 0.8f);
                line(g, x + 11.8f * u, y + 4.2f * u, x + 4.2f * u, y + 11.8f * u, color, t * 0.8f);
            }
            case DOTS -> {
                disc(g, x + 3 * u, y + 8 * u, 1.4f * u, color);
                disc(g, x + 8 * u, y + 8 * u, 1.4f * u, color);
                disc(g, x + 13 * u, y + 8 * u, 1.4f * u, color);
            }
            case CHEVRON_DOWN -> {
                line(g, x + 3.5f * u, y + 6 * u, x + 8 * u, y + 10.5f * u, color, t);
                line(g, x + 8 * u, y + 10.5f * u, x + 12.5f * u, y + 6 * u, color, t);
            }
            case CHEVRON_UP -> {
                line(g, x + 3.5f * u, y + 10.5f * u, x + 8 * u, y + 6 * u, color, t);
                line(g, x + 8 * u, y + 6 * u, x + 12.5f * u, y + 10.5f * u, color, t);
            }
            case CHEVRON_LEFT -> {
                line(g, x + 10.5f * u, y + 3.5f * u, x + 6 * u, y + 8 * u, color, t);
                line(g, x + 6 * u, y + 8 * u, x + 10.5f * u, y + 12.5f * u, color, t);
            }
            case CHEVRON_RIGHT -> {
                line(g, x + 5.5f * u, y + 3.5f * u, x + 10 * u, y + 8 * u, color, t);
                line(g, x + 10 * u, y + 8 * u, x + 5.5f * u, y + 12.5f * u, color, t);
            }
            case ARROW_LEFT -> {
                line(g, x + 2.5f * u, y + 8 * u, x + 13.5f * u, y + 8 * u, color, t);
                line(g, x + 2.5f * u, y + 8 * u, x + 7.5f * u, y + 3.5f * u, color, t);
                line(g, x + 2.5f * u, y + 8 * u, x + 7.5f * u, y + 12.5f * u, color, t);
            }
            case ARROW_RIGHT -> {
                line(g, x + 2.5f * u, y + 8 * u, x + 13.5f * u, y + 8 * u, color, t);
                line(g, x + 13.5f * u, y + 8 * u, x + 8.5f * u, y + 3.5f * u, color, t);
                line(g, x + 13.5f * u, y + 8 * u, x + 8.5f * u, y + 12.5f * u, color, t);
            }
            case CLOSE -> {
                line(g, x + 3.5f * u, y + 3.5f * u, x + 12.5f * u, y + 12.5f * u, color, t);
                line(g, x + 12.5f * u, y + 3.5f * u, x + 3.5f * u, y + 12.5f * u, color, t);
            }
            case SEARCH -> {
                ring(g, x + 7 * u, y + 7 * u, 4.4f * u, color, t);
                line(g, x + 10.4f * u, y + 10.4f * u, x + 14 * u, y + 14 * u, color, t);
            }
            case INFO -> {
                ring(g, x + 8 * u, y + 8 * u, 6.2f * u, color, t);
                line(g, x + 8 * u, y + 7 * u, x + 8 * u, y + 11.5f * u, color, t);
                dotAt(g, x + 8 * u, y + 4.8f * u, color, t * 1.1f);
            }
            case BELL -> {
                arc(g, x + 8 * u, y + 7.5f * u, 4.2f * u, 180, 360, color, t);
                line(g, x + 3.8f * u, y + 7.5f * u, x + 3 * u, y + 11.5f * u, color, t);
                line(g, x + 12.2f * u, y + 7.5f * u, x + 13 * u, y + 11.5f * u, color, t);
                line(g, x + 3 * u, y + 11.5f * u, x + 13 * u, y + 11.5f * u, color, t);
                disc(g, x + 8 * u, y + 13.6f * u, 1.1f * u, color);
            }
            case PLUS -> {
                line(g, x + 8 * u, y + 2.5f * u, x + 8 * u, y + 13.5f * u, color, t);
                line(g, x + 2.5f * u, y + 8 * u, x + 13.5f * u, y + 8 * u, color, t);
            }
            case FOLDER -> {
                line(g, x + 1.5f * u, y + 4 * u, x + 6 * u, y + 4 * u, color, t);
                line(g, x + 6 * u, y + 4 * u, x + 7.5f * u, y + 5.8f * u, color, t);
                box(g, x + 1.5f * u, y + 5.8f * u, 13 * u, 7.7f * u, color, t);
            }
            case LIST -> {
                for (int i = 0; i < 3; i++) {
                    float row = 3.5f + i * 4.5f;
                    dotAt(g, x + 2.5f * u, y + row * u, color, t);
                    line(g, x + 5.5f * u, y + row * u, x + 14 * u, y + row * u, color, t);
                }
            }
            case MOUSE -> {
                box(g, x + 4 * u, y + 1.8f * u, 8 * u, 12.4f * u, color, t);
                line(g, x + 8 * u, y + 2 * u, x + 8 * u, y + 7 * u, color, t);
                line(g, x + 4 * u, y + 7 * u, x + 12 * u, y + 7 * u, color, t);
            }
            case CHECK -> {
                line(g, x + 3 * u, y + 8.5f * u, x + 6.5f * u, y + 12 * u, color, t);
                line(g, x + 6.5f * u, y + 12 * u, x + 13 * u, y + 4.5f * u, color, t);
            }
            case TRASH -> {
                line(g, x + 2.5f * u, y + 4 * u, x + 13.5f * u, y + 4 * u, color, t);
                line(g, x + 6 * u, y + 4 * u, x + 6 * u, y + 2 * u, color, t);
                line(g, x + 6 * u, y + 2 * u, x + 10 * u, y + 2 * u, color, t);
                line(g, x + 10 * u, y + 2 * u, x + 10 * u, y + 4 * u, color, t);
                box(g, x + 4 * u, y + 4 * u, 8 * u, 9.5f * u, color, t);
            }
            case RESET -> {
                arc(g, x + 8 * u, y + 8 * u, 5.4f * u, 40, 330, color, t);
                line(g, x + 12.6f * u, y + 2.6f * u, x + 12.6f * u, y + 6.2f * u, color, t);
                line(g, x + 12.6f * u, y + 6.2f * u, x + 9 * u, y + 6.2f * u, color, t);
            }
        }
    }

    // ---- primitives -------------------------------------------------------------------------

    private static void plot(GuiGraphics g, float px, float py, int color, float thickness) {
        int size = Math.max(1, Math.round(thickness));
        int left = Math.round(px - thickness / 2.0f);
        int top = Math.round(py - thickness / 2.0f);
        g.fill(left, top, left + size, top + size, color);
    }

    private static void line(GuiGraphics g, float x1, float y1, float x2, float y2,
                             int color, float thickness) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(1, (int) Math.ceil(length * 1.5f));
        for (int i = 0; i <= steps; i++) {
            float amount = i / (float) steps;
            plot(g, x1 + (x2 - x1) * amount, y1 + (y2 - y1) * amount, color, thickness);
        }
    }

    private static void box(GuiGraphics g, float x, float y, float w, float h, int color, float thickness) {
        line(g, x, y, x + w, y, color, thickness);
        line(g, x + w, y, x + w, y + h, color, thickness);
        line(g, x + w, y + h, x, y + h, color, thickness);
        line(g, x, y + h, x, y, color, thickness);
    }

    private static void arc(GuiGraphics g, float cx, float cy, float radius,
                            int fromDegrees, int toDegrees, int color, float thickness) {
        float previousX = cx + (float) Math.cos(Math.toRadians(fromDegrees)) * radius;
        float previousY = cy + (float) Math.sin(Math.toRadians(fromDegrees)) * radius;
        for (int angle = fromDegrees + 10; angle <= toDegrees; angle += 10) {
            float nextX = cx + (float) Math.cos(Math.toRadians(angle)) * radius;
            float nextY = cy + (float) Math.sin(Math.toRadians(angle)) * radius;
            line(g, previousX, previousY, nextX, nextY, color, thickness);
            previousX = nextX;
            previousY = nextY;
        }
    }

    private static void ring(GuiGraphics g, float cx, float cy, float radius, int color, float thickness) {
        arc(g, cx, cy, radius, 0, 360, color, thickness);
    }

    private static void disc(GuiGraphics g, float cx, float cy, float radius, int color) {
        int r = Math.max(1, Math.round(radius));
        int icx = Math.round(cx);
        int icy = Math.round(cy);
        for (int row = -r; row <= r; row++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0.0, (double) r * r - (double) row * row)));
            g.fill(icx - half, icy + row, icx + half + 1, icy + row + 1, color);
        }
    }

    private static void dotAt(GuiGraphics g, float cx, float cy, int color, float thickness) {
        disc(g, cx, cy, Math.max(0.8f, thickness * 0.6f), color);
    }

    private static void star(GuiGraphics g, int x, int y, float u, int color, float t, boolean filled) {
        float cx = x + 8 * u;
        float cy = y + 8.6f * u;
        float[] px = new float[10];
        float[] py = new float[10];
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            float radius = (i % 2 == 0 ? 6.6f : 2.8f) * u;
            px[i] = cx + (float) Math.cos(angle) * radius;
            py[i] = cy + (float) Math.sin(angle) * radius;
        }
        if (filled) {
            int top = Math.round(cy - 6.6f * u);
            int bottom = Math.round(cy + 6.0f * u);
            for (int row = top; row <= bottom; row++) {
                float minX = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE;
                for (int i = 0; i < 10; i++) {
                    int j = (i + 1) % 10;
                    float y1 = py[i];
                    float y2 = py[j];
                    if ((row + 0.5f >= Math.min(y1, y2)) && (row + 0.5f <= Math.max(y1, y2)) && y1 != y2) {
                        float amount = (row + 0.5f - y1) / (y2 - y1);
                        float crossing = px[i] + (px[j] - px[i]) * amount;
                        minX = Math.min(minX, crossing);
                        maxX = Math.max(maxX, crossing);
                    }
                }
                if (minX <= maxX) g.fill(Math.round(minX), row, Math.round(maxX) + 1, row + 1, color);
            }
        }
        for (int i = 0; i < 10; i++) {
            int j = (i + 1) % 10;
            line(g, px[i], py[i], px[j], py[j], color, t);
        }
    }
}

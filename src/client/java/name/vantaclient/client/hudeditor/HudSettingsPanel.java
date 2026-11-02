package name.vantaclient.client.hudeditor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.hud.ArmorHudElement;

public final class HudSettingsPanel {
    private static HudElement selected;
    private static boolean open;
    private static boolean draggingSlider;
    private static float openProgress;

    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 310;
    private static final int TEXT = 0xFFF0F3FA;
    private static final int SUBTEXT = 0xFF8995A8;
    private static final int SURFACE = 0xFF171D27;
    private static final int INSET = 0xFF202834;
    private static final int BORDER = 0xFF303A49;

    private HudSettingsPanel() {}

    private static int getPanelX() {
        Minecraft mc = Minecraft.getInstance();
        int max = mc.getWindow().getGuiScaledWidth() - PANEL_WIDTH - 10;
        int preferred = selected == null ? 10 : selected.getX() + selected.getScaledWidth() + 18;
        return Math.max(10, Math.min(preferred, Math.max(10, max)));
    }

    private static int getPanelY() {
        Minecraft mc = Minecraft.getInstance();
        int max = mc.getWindow().getGuiScaledHeight() - PANEL_HEIGHT - 10;
        int preferred = selected == null ? 10 : selected.getY() - 8;
        return Math.max(10, Math.min(preferred, Math.max(10, max)));
    }

    public static HudElement getSelected() {
        return selected;
    }

    public static boolean isOpen() {
        return open;
    }

    public static void setSelected(HudElement element) {
        selected = element;
        open = true;
        draggingSlider = false;
    }

    public static void close() {
        open = false;
        draggingSlider = false;
    }

    public static void clear() {
        open = false;
        selected = null;
        draggingSlider = false;
        openProgress = 0.0f;
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selected == null) return;

        float target = open ? 1.0f : 0.0f;
        openProgress += (target - openProgress) * 0.24f;
        if (Math.abs(target - openProgress) < 0.012f) openProgress = target;
        if (!open && openProgress <= 0.012f) {
            selected = null;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int alpha = Math.round(255.0f * openProgress);
        int x = getPanelX();
        int y = getPanelY() + Math.round((1.0f - openProgress) * 12.0f);
        int accent = VantaClientPreferences.get().accent.color();

        fillRounded(graphics, x + 4, y + 5, PANEL_WIDTH, PANEL_HEIGHT, 18, withAlpha(0x99000000, alpha));
        fillRounded(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, 17, withAlpha(BORDER, alpha));
        fillRounded(graphics, x + 1, y + 1, PANEL_WIDTH - 2, PANEL_HEIGHT - 2, 16,
                withAlpha(SURFACE, alpha));
        fillRounded(graphics, x + 8, y + 8, PANEL_WIDTH - 16, 42, 11,
                withAlpha(INSET, alpha));
        fillRounded(graphics, x + 10, y + 47, PANEL_WIDTH - 20, 2, 1, withAlpha(accent, alpha));

        drawText(graphics, mc, "HUD ELEMENT", x + 16, y + 17, TEXT, alpha);
        drawText(graphics, mc, getDisplayName(selected), x + 16, y + 31, SUBTEXT, alpha);
        boolean closeHover = inside(mouseX, mouseY, x + PANEL_WIDTH - 36, y + 15, 22, 22);
        fillRounded(graphics, x + PANEL_WIDTH - 38, y + 13, 26, 26, 8,
                withAlpha(closeHover ? 0xFF394557 : 0xFF28313E, alpha));
        drawCentered(graphics, mc, "×", x + PANEL_WIDTH - 25, y + 21,
                withAlpha(TEXT, alpha));

        if (selected instanceof ArmorHudElement armor) {
            sectionLabel(graphics, mc, "LAYOUT", x + 16, y + 66, alpha);
            boolean horizontal = armor.isHorizontal();
            drawSegment(graphics, mc, "Vertical", x + 14, y + 82, 106, 28,
                    !horizontal, mouseX, mouseY, accent, alpha);
            drawSegment(graphics, mc, "Horizontal", x + 128, y + 82, 108, 28,
                    horizontal, mouseX, mouseY, accent, alpha);
        }

        int scaleLabelY = y + (selected instanceof ArmorHudElement ? 128 : 82);
        int sliderY = scaleLabelY + 26;
        sectionLabel(graphics, mc, "SIZE", x + 16, scaleLabelY, alpha);
        drawText(graphics, mc, String.format("%.1fx", selected.getScale()),
                x + PANEL_WIDTH - 48, scaleLabelY, TEXT, alpha);
        drawSlider(graphics, x + 16, sliderY, PANEL_WIDTH - 32, selected.getScale(),
                accent, alpha);

        int controlsY = y + (selected instanceof ArmorHudElement ? 183 : 137);
        fillRounded(graphics, x + 12, controlsY - 10, PANEL_WIDTH - 24, 1, 1,
                withAlpha(BORDER, alpha));
        sectionLabel(graphics, mc, "ALIGNMENT", x + 16, controlsY, alpha);
        drawPreferenceRow(graphics, mc, "Snap to grid",
                VantaClientPreferences.get().hudSnapToGrid,
                x + 14, controlsY + 17, mouseX, mouseY, accent, alpha);
        drawPreferenceRow(graphics, mc, "Show grid",
                VantaClientPreferences.get().hudShowGrid,
                x + 14, controlsY + 48, mouseX, mouseY, accent, alpha);

        sectionLabel(graphics, mc, "GRID SPACING", x + 16, controlsY + 84, alpha);
        int[] sizes = {8, 16, 24, 32};
        for (int i = 0; i < sizes.length; i++) {
            drawSegment(graphics, mc, sizes[i] + " px", x + 14 + i * 57,
                    controlsY + 99, 52, 25,
                    VantaClientPreferences.get().hudGridSize == sizes[i],
                    mouseX, mouseY, accent, alpha);
        }

        fillRounded(graphics, x + 12, y + PANEL_HEIGHT - 43, PANEL_WIDTH - 24, 1, 1,
                withAlpha(BORDER, alpha));
        drawSegment(graphics, mc, "Done", x + 14, y + PANEL_HEIGHT - 34,
                PANEL_WIDTH - 28, 24, true, mouseX, mouseY, accent, alpha);
    }

    private static void sectionLabel(GuiGraphics graphics, Minecraft mc,
                                     String label, int x, int y, int alpha) {
        drawText(graphics, mc, label, x, y, SUBTEXT, alpha);
    }

    private static void drawPreferenceRow(GuiGraphics graphics, Minecraft mc, String label,
                                          boolean enabled, int x, int y, int mouseX,
                                          int mouseY, int accent, int alpha) {
        boolean hovered = inside(mouseX, mouseY, x, y, PANEL_WIDTH - 28, 27);
        fillRounded(graphics, x, y, PANEL_WIDTH - 28, 27, 8,
                withAlpha(hovered ? 0xFF252F3D : 0xFF1C2430, alpha));
        drawText(graphics, mc, label, x + 10, y + 9, TEXT, alpha);
        int sx = x + PANEL_WIDTH - 28 - 43;
        fillRounded(graphics, sx, y + 5, 34, 17, 9,
                withAlpha(enabled ? accent : 0xFF3A4554, alpha));
        fillRounded(graphics, sx + (enabled ? 19 : 3), y + 8, 11, 11, 6,
                withAlpha(0xFFFFFFFF, alpha));
    }

    private static void drawSegment(GuiGraphics graphics, Minecraft mc, String label,
                                    int x, int y, int width, int height, boolean active,
                                    int mouseX, int mouseY, int accent, int alpha) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        int background = active ? accent : hovered ? 0xFF2C3747 : 0xFF202834;
        int border = active ? accent : hovered ? 0xFF46566B : BORDER;
        fillRounded(graphics, x, y, width, height, 8, withAlpha(border, alpha));
        fillRounded(graphics, x + 1, y + 1, width - 2, height - 2, 7,
                withAlpha(background, alpha));
        graphics.drawCenteredString(mc.font, label, x + width / 2, y + (height - 8) / 2,
                withAlpha(TEXT, alpha));
    }

    private static void drawSlider(GuiGraphics graphics, int x, int y, int width,
                                   float value, int accent, int alpha) {
        float progress = Math.max(0.0f, Math.min(1.0f, (value - 0.25f) / 2.75f));
        int filled = Math.round(width * progress);
        fillRounded(graphics, x, y, width, 6, 3, withAlpha(0xFF354050, alpha));
        if (filled > 0) fillRounded(graphics, x, y, filled, 6, 3, withAlpha(accent, alpha));
        fillRounded(graphics, x + filled - 5, y - 4, 10, 14, 6, withAlpha(0xFFFFFFFF, alpha));
    }

    private static String getDisplayName(HudElement element) {
        return switch (element.getClass().getSimpleName()) {
            case "ArmorHudElement" -> "Armor HUD";
            case "DebugHudElement" -> "Debug HUD";
            case "TotemCounterHudElement" -> "Totem Counter";
            case "PotionEffectsHudElement" -> "Potion Effects";
            default -> element.getClass().getSimpleName();
        };
    }

    public static boolean mouseClicked(double mouseX, double mouseY) {
        if (!open || selected == null) return false;
        int x = getPanelX();
        int y = getPanelY();

        if (!inside(mouseX, mouseY, x, y, PANEL_WIDTH, PANEL_HEIGHT)) return false;
        if (inside(mouseX, mouseY, x + PANEL_WIDTH - 40, y + 12, 32, 28)
                || inside(mouseX, mouseY, x + 12, y + PANEL_HEIGHT - 40, PANEL_WIDTH - 24, 34)) {
            close();
            return true;
        }

        if (selected instanceof ArmorHudElement armor) {
            if (inside(mouseX, mouseY, x + 14, y + 82, 106, 28)) {
                armor.setHorizontal(false);
                return true;
            }
            if (inside(mouseX, mouseY, x + 128, y + 82, 108, 28)) {
                armor.setHorizontal(true);
                return true;
            }
        }

        int scaleLabelY = y + (selected instanceof ArmorHudElement ? 128 : 82);
        int sliderY = scaleLabelY + 26;
        if (inside(mouseX, mouseY, x + 10, sliderY - 7, PANEL_WIDTH - 20, 22)) {
            draggingSlider = true;
            updateSizeSlider(mouseX, x + 16);
            return true;
        }

        int controlsY = y + (selected instanceof ArmorHudElement ? 183 : 137);
        if (inside(mouseX, mouseY, x + 14, controlsY + 17, PANEL_WIDTH - 28, 27)) {
            VantaClientPreferences.get().hudSnapToGrid =
                    !VantaClientPreferences.get().hudSnapToGrid;
            VantaClientPreferences.save();
            return true;
        }
        if (inside(mouseX, mouseY, x + 14, controlsY + 48, PANEL_WIDTH - 28, 27)) {
            VantaClientPreferences.get().hudShowGrid =
                    !VantaClientPreferences.get().hudShowGrid;
            VantaClientPreferences.save();
            return true;
        }
        if (inside(mouseX, mouseY, x + 14, controlsY + 99, 228, 25)) {
            int index = Math.max(0, Math.min(3, (int) ((mouseX - (x + 14)) / 57)));
            VantaClientPreferences.get().hudGridSize = new int[]{8, 16, 24, 32}[index];
            VantaClientPreferences.save();
            return true;
        }
        return true;
    }

    public static void mouseReleased() {
        draggingSlider = false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (!draggingSlider || selected == null) return false;
        updateSizeSlider(mouseX, getPanelX() + 16);
        return true;
    }

    private static void updateSizeSlider(double mouseX, int sliderX) {
        float percent = (float) ((mouseX - sliderX) / (PANEL_WIDTH - 32.0));
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        selected.setScale(0.25f + percent * 2.75f);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.round(((color >>> 24) & 255) * alpha / 255.0f) << 24);
    }

    private static void drawText(GuiGraphics graphics, Minecraft mc, String value,
                                 int x, int y, int color, int alpha) {
        graphics.drawString(mc.font, value, x, y, withAlpha(color, alpha));
    }

    private static void drawCentered(GuiGraphics graphics, Minecraft mc, String value,
                                     int x, int y, int color) {
        graphics.drawCenteredString(mc.font, value, x, y, color);
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y, int width,
                                    int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            } else if (row >= height - r) {
                double dy = row - (height - r) + 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }
}

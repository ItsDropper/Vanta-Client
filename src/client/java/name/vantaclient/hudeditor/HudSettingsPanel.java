package name.vantaclient.client.hudeditor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import name.vantaclient.client.hud.ArmorHudElement;


public class HudSettingsPanel {

    private static HudElement selected;
    private static boolean open = false;
    private static boolean draggingSlider = false;

    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_HEIGHT = 200;

    private static final int PANEL_BG = 0xF018181C;
    private static final int HEADER_BG = 0xFF202024;

    private static final int BORDER = 0xFF34343B;
    private static final int DIVIDER = 0xFF303038;

    private static final int BUTTON_BG = 0xFF25252B;
    private static final int BUTTON_HOVER = 0xFF303038;
    private static final int BUTTON_SELECTED = 0xFF556BFF;

    private static final int SLIDER_BG = 0xFF34343B;
    private static final int SLIDER_FILL = 0xFF6677FF;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTEXT = 0xFF9A9AA3;
    private static final int ACCENT = 0xFF6677FF;


    private static int getPanelX() {

        Minecraft mc = Minecraft.getInstance();

        if (selected == null) {
            return 10;
        }

        int x = selected.getX() + 100;

        return Math.max(
                5,
                Math.min(
                        x,
                        mc.getWindow().getGuiScaledWidth() - PANEL_WIDTH - 5
                )
        );
    }


    private static int getPanelY() {

        Minecraft mc = Minecraft.getInstance();

        if (selected == null) {
            return 10;
        }

        int y = selected.getY();

        return Math.max(
                5,
                Math.min(
                        y,
                        mc.getWindow().getGuiScaledHeight() - PANEL_HEIGHT - 5
                )
        );
    }


    public static void setSelected(HudElement element) {

        selected = element;
        open = true;
        draggingSlider = false;
    }

    public static void close() {

        open = false;
        selected = null;
    }


    public static void clear() {
        selected = null;
    }


    public static void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {

        if (!open || selected == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        int x = getPanelX();
        int y = getPanelY();

        // ─────────────────────────────────────
        // Shadow
        // ─────────────────────────────────────

        graphics.fill(
                x + 3,
                y + 3,
                x + PANEL_WIDTH + 4,
                y + PANEL_HEIGHT + 4,
                0x55000000
        );

        // ─────────────────────────────────────
        // Panel
        // ─────────────────────────────────────

        graphics.fill(
                x,
                y,
                x + PANEL_WIDTH,
                y + PANEL_HEIGHT,
                PANEL_BG
        );

        // Border
        graphics.fill(
                x,
                y,
                x + PANEL_WIDTH,
                y + 1,
                BORDER
        );

        graphics.fill(
                x,
                y + PANEL_HEIGHT - 1,
                x + PANEL_WIDTH,
                y + PANEL_HEIGHT,
                BORDER
        );

        graphics.fill(
                x,
                y,
                x + 1,
                y + PANEL_HEIGHT,
                BORDER
        );

        graphics.fill(
                x + PANEL_WIDTH - 1,
                y,
                x + PANEL_WIDTH,
                y + PANEL_HEIGHT,
                BORDER
        );

        // ─────────────────────────────────────
        // Header
        // ─────────────────────────────────────

        graphics.fill(
                x + 1,
                y + 1,
                x + PANEL_WIDTH - 1,
                y + 38,
                HEADER_BG
        );

        // Accent
        graphics.fill(
                x + 1,
                y + 37,
                x + PANEL_WIDTH - 1,
                y + 39,
                ACCENT
        );

        graphics.drawString(
                mc.font,
                getDisplayName(selected),
                x + 12,
                y + 13,
                TEXT
        );

        // Close X
        boolean closeHovered =
                mouseX >= x + PANEL_WIDTH - 28 &&
                        mouseX <= x + PANEL_WIDTH - 8 &&
                        mouseY >= y + 9 &&
                        mouseY <= y + 29;

        graphics.fill(
                x + PANEL_WIDTH - 29,
                y + 9,
                x + PANEL_WIDTH - 8,
                y + 29,
                closeHovered
                        ? BUTTON_HOVER
                        : HEADER_BG
        );

        graphics.drawCenteredString(
                mc.font,
                "×",
                x + PANEL_WIDTH - 18,
                y + 12,
                closeHovered
                        ? TEXT
                        : SUBTEXT
        );

        // ─────────────────────────────────────
        // Content
        // ─────────────────────────────────────

        if (selected instanceof ArmorHudElement armor) {

            graphics.drawString(
                    mc.font,
                    "LAYOUT",
                    x + 12,
                    y + 56,
                    SUBTEXT
            );

            boolean vertical =
                    !armor.isHorizontal();

            drawOptionButton(
                    graphics,
                    mc,
                    "Vertical",
                    x + 10,
                    y + 70,
                    77,
                    24,
                    mouseX,
                    mouseY,
                    vertical
            );

            drawOptionButton(
                    graphics,
                    mc,
                    "Horizontal",
                    x + 92,
                    y + 70,
                    78,
                    24,
                    mouseX,
                    mouseY,
                    !vertical
            );
        }

        // ─────────────────────────────────────
        // Size
        // ─────────────────────────────────────

        int labelY;

        int sliderY;

        if (selected instanceof ArmorHudElement) {
            labelY = y + 107;
            sliderY = y + 128;
        } else {
            labelY = y + 58;
            sliderY = y + 81;
        }

        float scale = selected.getScale();

        graphics.drawString(
                mc.font,
                "SIZE",
                x + 12,
                labelY,
                SUBTEXT
        );

        graphics.drawString(
                mc.font,
                String.format("%.1fx", scale),
                x + PANEL_WIDTH - 42,
                labelY,
                TEXT
        );

        // Slider track
        int sliderX = x + 12;
        int sliderWidth = PANEL_WIDTH - 24;

        graphics.fill(
                sliderX,
                sliderY,
                sliderX + sliderWidth,
                sliderY + 4,
                SLIDER_BG
        );

        float progress =
                (scale - 0.25f) / 2.75f;

        progress = Math.max(
                0.0f,
                Math.min(1.0f, progress)
        );

        int filledWidth =
                (int) (sliderWidth * progress);

        graphics.fill(
                sliderX,
                sliderY,
                sliderX + filledWidth,
                sliderY + 4,
                SLIDER_FILL
        );

        // Slider knob
        int knobX =
                sliderX + filledWidth;

        graphics.fill(
                knobX - 4,
                sliderY - 4,
                knobX + 4,
                sliderY + 8,
                TEXT
        );

        // ─────────────────────────────────────
        // Bottom divider
        // ─────────────────────────────────────

        graphics.fill(
                x + 12,
                y + PANEL_HEIGHT - 48,
                x + PANEL_WIDTH - 12,
                y + PANEL_HEIGHT - 47,
                DIVIDER
        );

        // Close button
        drawButton(
                graphics,
                mc,
                "Close",
                x + 12,
                y + PANEL_HEIGHT - 36,
                PANEL_WIDTH - 24,
                24,
                mouseX,
                mouseY
        );
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

        if (!open) {
            return false;
        }


        int panelWidth = PANEL_WIDTH;
        int panelHeight = PANEL_HEIGHT;

        int x = getPanelX();
        int y = getPanelY();

        if (selected instanceof ArmorHudElement armor) {

            if (mouseX >= x + 10 &&
                    mouseX <= x + 85 &&
                    mouseY >= y + 70 &&
                    mouseY <= y + 92) {

                armor.setHorizontal(false);
                return true;
            }


            if (mouseX >= x + 95 &&
                    mouseX <= x + 170 &&
                    mouseY >= y + 70 &&
                    mouseY <= y + 92) {

                armor.setHorizontal(true);
                return true;
            }
        }

        int sliderX = x + 10;

        int sliderY;

        if (selected instanceof ArmorHudElement) {
            sliderY = y + 125;
        } else {
            sliderY = y + 80;
        }

        if (mouseX >= sliderX &&
                mouseX <= sliderX + 120 &&
                mouseY >= sliderY - 5 &&
                mouseY <= sliderY + 10) {

            draggingSlider = true;

            updateSizeSlider(
                    mouseX,
                    sliderX
            );

            return true;
        }


        if (mouseX >= x + 10 &&
                mouseX <= x + 90 &&
                mouseY >= y + panelHeight - 35 &&
                mouseY <= y + panelHeight - 11) {

            close();
            return true;
        }


        return false;
    }

    public static void mouseReleased() {
        draggingSlider = false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {

        if (!draggingSlider || selected == null) {
            return false;
        }

        int sliderX = getPanelX() + 10;

        updateSizeSlider(
                mouseX,
                sliderX
        );

        return true;
    }

    private static void updateSizeSlider(
            double mouseX,
            int sliderX
    ) {

        float percent =
                (float) ((mouseX - sliderX) / 120.0);

        percent = Math.max(0, Math.min(1, percent));

        float value =
                0.25f + percent * 2.75f;

        selected.setScale(value);
    }

    private static void drawButton(
            GuiGraphics graphics,
            Minecraft mc,
            String text,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {

        boolean hovered =
                mouseX >= x &&
                        mouseX <= x + width &&
                        mouseY >= y &&
                        mouseY <= y + height;


        int color = hovered ? BUTTON_HOVER : BUTTON_BG;

// middle parts
        graphics.fill(
                x + 3,
                y,
                x + width - 3,
                y + height,
                color
        );

        graphics.fill(
                x,
                y + 3,
                x + width,
                y + height - 3,
                color
        );


        graphics.drawString(
                mc.font,
                text,
                x + 8,
                y + 6,
                TEXT
        );
    }

    private static void drawOptionButton(
            GuiGraphics graphics,
            Minecraft mc,
            String text,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean selected
    ) {

        boolean hovered =
                mouseX >= x &&
                        mouseX <= x + width &&
                        mouseY >= y &&
                        mouseY <= y + height;

        int background;

        if (selected) {
            background = BUTTON_SELECTED;
        } else if (hovered) {
            background = BUTTON_HOVER;
        } else {
            background = BUTTON_BG;
        }

        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                background
        );

        if (selected) {
            graphics.fill(
                    x,
                    y + height - 2,
                    x + width,
                    y + height,
                    0xFF7C8CFF
            );
        }

        graphics.drawCenteredString(
                mc.font,
                text,
                x + width / 2,
                y + 8,
                TEXT
        );
    }
}
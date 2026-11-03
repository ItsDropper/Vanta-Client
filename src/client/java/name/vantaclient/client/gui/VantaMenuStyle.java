package name.vantaclient.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Shared glass-blue treatment for vanilla Minecraft buttons and title branding. */
public final class VantaMenuStyle {
    private static final Identifier VANTA_MARK = Identifier.fromNamespaceAndPath(
            "dropperutils", "textures/gui/vanta_logo_transparent.png");
    private static final int MARK_TEXTURE_WIDTH = 962;
    private static final int MARK_TEXTURE_HEIGHT = 702;

    private VantaMenuStyle() {}

    /** Replaces only the vanilla button surface; vanilla labels, icons and actions remain intact. */
    public static void renderButtonSurface(GuiGraphics graphics, AbstractButton button) {
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        if (width < 4 || height < 4) return;

        boolean enabled = button.isActive();
        boolean highlighted = button.isHoveredOrFocused();

        int border = !enabled ? 0x80506A82
                : highlighted ? 0xF08DDBFF : 0xD05C9BCB;
        int surface = !enabled ? 0xA5101D2B
                : highlighted ? 0xE2266396 : 0xD2163858;
        fillRounded(graphics, x, y, width, height, 7, border);
        fillRounded(graphics, x + 1, y + 1, width - 2, height - 2, 6, surface);

        if (width > 16) {
            graphics.fill(x + 7, y + 2, x + width - 7, y + 3,
                    enabled ? 0x56E3F6FF : 0x2A9CB7D0);
            graphics.fill(x + 7, y + height - 2, x + width - 7, y + height - 1,
                    enabled ? 0x67439DE8 : 0x35436B8B);
        }
        if (highlighted && width > 7 && height > 8) {
            fillRounded(graphics, x + 2, y + 4, 2, height - 8, 1,
                    enabled ? 0xFF8CE1FF : 0x806C849A);
        }
    }

    /**
     * Adds Vanta's custom V mark and wordmark immediately below the original Minecraft logo.
     * The vanilla title screen, panorama, button layout and copyright remain untouched.
     */
    public static void renderTitleBranding(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (screenWidth < 190 || screenHeight < 150) return;

        int panelWidth = Math.min(224, screenWidth - 20);
        int panelHeight = 24;
        int x = (screenWidth - panelWidth) / 2;
        int y = Math.max(5, Math.min(screenHeight - panelHeight - 5, screenHeight / 4 + 16));

        fillRounded(graphics, x, y, panelWidth, panelHeight, 7, 0xD75B9FCF);
        fillRounded(graphics, x + 1, y + 1, panelWidth - 2, panelHeight - 2,
                6, 0xD5102741);
        graphics.fill(x + 8, y + 2, x + panelWidth - 8, y + 3, 0x70DDF6FF);

        int markWidth = 22;
        int markHeight = 16;
        int markX = x + 9;
        int markY = y + (panelHeight - markHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, VANTA_MARK, markX, markY,
                0.0f, 0.0f, markWidth, markHeight,
                MARK_TEXTURE_WIDTH, MARK_TEXTURE_HEIGHT,
                MARK_TEXTURE_WIDTH, MARK_TEXTURE_HEIGHT);

        graphics.fill(x + 38, y + 5, x + 39, y + panelHeight - 5, 0x805FA9D8);
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.drawString(font, "ANTA CLIENT", x + 47, y + 8, 0xFFF0F8FF, false);
        graphics.drawString(font, "CLIENT EDITION", x + panelWidth - 76, y + 8,
                0xFF9FC7E6, false);
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y,
                                    int width, int height, int radius, int color) {
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

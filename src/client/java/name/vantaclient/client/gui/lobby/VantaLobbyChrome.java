package name.vantaclient.client.gui.lobby;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import name.vantaclient.client.gui.VantaMenuStyle;
import name.vantaclient.client.gui.framework.VantaUiTheme;

/** Glass lobby chrome composed around the live Minecraft title world. */
public final class VantaLobbyChrome {
    private VantaLobbyChrome() {}

    public static void renderPlayPanel(GuiGraphics graphics, int width, int height) {
        if (graphics == null || width < 360 || height < 240) return;

        boolean compact = height < 420 || width < 520;
        int left = VantaMenuStyle.menuLeft(width);
        int top = VantaMenuStyle.menuTop(height);
        int buttonWidth = VantaMenuStyle.menuButtonWidth(width);
        int buttonHeight = VantaMenuStyle.menuButtonHeight(height);
        int gap = VantaMenuStyle.menuButtonGap(height);
        int secondaryHeight = compact ? 20 : 27;
        int settingsHeight = compact ? 18 : 24;
        int stackHeight = 3 * (buttonHeight + gap) + (compact ? 4 : 8)
                + secondaryHeight + (compact ? 3 : 6) + settingsHeight;
        int panelX = Math.max(6, left - (compact ? 12 : 18));
        int panelY = Math.max(54, top - (compact ? 26 : 34));
        int panelWidth = Math.min(width - panelX - 8, buttonWidth + (compact ? 24 : 36));
        int panelHeight = Math.min(height - panelY - 28, stackHeight + (compact ? 38 : 54));
        if (panelWidth <= 0 || panelHeight <= 0) return;

        VantaUiTheme.fillSquircle(graphics, panelX + 2, panelY + 6,
                panelWidth, panelHeight, compact ? 10 : 16, 0x35000000);
        VantaUiTheme.fillSquircle(graphics, panelX, panelY,
                panelWidth, panelHeight, compact ? 10 : 16, 0xC4141B27);
        VantaUiTheme.outlineSquircle(graphics, panelX, panelY,
                panelWidth, panelHeight, compact ? 10 : 16, 0x668D9BB0);

        int accent = new VantaUiTheme().accent();
        int inset = compact ? 10 : 16;
        int headerY = panelY + (compact ? 8 : 13);
        graphics.fill(panelX + inset, headerY + 1, panelX + inset + 3,
                headerY + (compact ? 12 : 15), VantaUiTheme.withAlpha(accent, 0xF0));
        graphics.drawString(Minecraft.getInstance().font, "PLAY",
                panelX + inset + 10, headerY, 0xFFF3F6FB, false);
        if (!compact) {
            graphics.drawString(Minecraft.getInstance().font, "Choose where to play",
                    panelX + inset, headerY + 20, 0xFFAAB6C7, false);
            graphics.fill(panelX + inset, top - 8, panelX + panelWidth - inset,
                    top - 7, 0x455E7189);
        }
    }

    /** Persistent left rail: status and system telemetry, never fabricated playback or ping. */
    public static void renderSideWidgets(GuiGraphics graphics, int width, int height) {
        if (graphics == null || width < 420 || height < 260) return;
        Minecraft mc = Minecraft.getInstance();
        boolean compact = height < 420 || width < 600;
        int x = 14;
        int cardWidth = Math.min(compact ? 176 : 230, Math.max(150, width / 3));
        int bottom = height - (compact ? 54 : 62);
        int cardHeight = compact ? 54 : 66;
        int gap = 7;

        panel(graphics, x, bottom - cardHeight * 2 - gap, cardWidth, cardHeight, compact);
        graphics.drawString(mc.font, "NOTHING PLAYING", x + 12, bottom - cardHeight * 2 - gap + 10,
                0xFFE7ECF4, false);
        graphics.drawString(mc.font, "Play something in another app", x + 12,
                bottom - cardHeight * 2 - gap + 28, 0xFF9BA8BA, false);

        panel(graphics, x, bottom - cardHeight, cardWidth, cardHeight, compact);
        graphics.drawString(mc.font, "PERFORMANCE", x + 12, bottom - cardHeight + 9,
                0xFFB7C3D2, false);
        graphics.drawString(mc.font, mc.getFps() + " FPS", x + 12,
                bottom - cardHeight + 29, 0xFFF0F4FA, false);

        panel(graphics, x, bottom + 7, cardWidth, compact ? 26 : 32, compact);
        graphics.drawString(mc.font, "Press Enter to open Social", x + 12, bottom + 16,
                0xFFB7C3D2, false);
    }

    public static void renderTopNavigation(GuiGraphics graphics, int width, int height) {
        if (graphics == null || width < 420 || height < 180) return;
        Minecraft mc = Minecraft.getInstance();
        int barHeight = height < 420 ? 40 : 48;
        graphics.fill(0, 0, width, barHeight, 0xB90A1019);
        graphics.fill(0, barHeight - 1, width, barHeight, 0x283C4B60);

        String[] items = {"Play", "Wardrobe", "Marketplace", "Social", "Stats", "News"};
        int x = Math.max(180, width / 3);
        int y = (barHeight - mc.font.lineHeight) / 2;
        for (int i = 0; i < items.length; i++) {
            int color = i == 0 ? 0xFFF4F6FA : 0xFF8793A4;
            mc.font.drawInBatch(items[i], x, y, color, false,
                    graphics.pose().last().pose(), graphics.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            int itemWidth = mc.font.width(items[i]);
            if (i == 0) graphics.fill(x, barHeight - 3, x + itemWidth, barHeight - 1,
                    new VantaUiTheme().accent());
            x += itemWidth + (width < 700 ? 12 : 26);
            if (x > width - 190) break;
        }
    }

    public static void renderSceneAtmosphere(GuiGraphics graphics, int width, int height) {
        if (graphics == null || width <= 0 || height <= 0) return;
        graphics.fill(0, 0, width, height, 0x28070A11);
        graphics.fillGradient(0, 0, width, Math.max(1, height * 34 / 100),
                0x79070B14, 0x00070B14);
        graphics.fillGradient(0, height * 56 / 100, width, height,
                0x00070B14, 0xB8070B14);
        int sideWidth = Math.max(1, width / 18);
        graphics.fill(0, 0, sideWidth, height, 0x18070A11);
        graphics.fill(width - sideWidth, 0, width, height, 0x18070A11);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height, boolean compact) {
        VantaUiTheme.fillSquircle(graphics, x + 1, y + 3, width, height,
                compact ? 8 : 11, 0x28000000);
        VantaUiTheme.fillSquircle(graphics, x, y, width, height,
                compact ? 8 : 11, 0xB9141B27);
        VantaUiTheme.outlineSquircle(graphics, x, y, width, height,
                compact ? 8 : 11, 0x3DFFFFFF);
    }
}

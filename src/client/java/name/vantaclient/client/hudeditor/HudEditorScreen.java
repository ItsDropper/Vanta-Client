package name.vantaclient.client.hudeditor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

public class HudEditorScreen extends Screen {
    private float entrance = 0.0f;
    private float selectionPulse = 0.0f;
    private boolean panelPointer;
    private boolean toolbarPointer;
    private VantaUiRuntime toolbarRuntime;
    private final net.minecraft.client.gui.screens.Screen parent;

    public HudEditorScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buildToolbar();
    }

    private void buildToolbar() {
        if (toolbarRuntime != null) toolbarRuntime.dispose();
        VantaUiNode root = VantaUiNode.row(0, 0).height(52);
        root.add(VantaUiNode.spacer(1).flex(1));
        VantaUiNode toolbar = VantaUiNode.row(10, 10).width(430).height(48)
                .background(VantaUiTheme.PANEL).align(VantaUiNode.Alignment.START);
        toolbar.add(VantaUiNode.icon(VantaUiNode.Icon.BRAND).size(22, 22)
                .iconColor(VantaUiTheme.TEXT_PRIMARY).align(VantaUiNode.Alignment.CENTER));
        VantaUiNode labels = VantaUiNode.column(2, 0).align(VantaUiNode.Alignment.CENTER);
        labels.add(VantaUiNode.eyebrow("VANTA / HUD EDITOR"));
        labels.add(VantaUiNode.text("Drag elements to position", VantaUiTheme.TEXT_SECONDARY));
        toolbar.add(labels);
        toolbar.add(VantaUiNode.spacer(1).flex(1));
        toolbar.add(VantaUiNode.toggle("Snap",
                () -> VantaClientPreferences.get().hudSnapToGrid,
                value -> {
                    VantaClientPreferences.get().hudSnapToGrid = value;
                    VantaClientPreferences.save();
                }).width(84).tooltip("Snap moved elements to the alignment grid")
                .align(VantaUiNode.Alignment.CENTER));
        toolbar.add(VantaUiNode.button("Done", this::onClose).height(30)
                .align(VantaUiNode.Alignment.CENTER));
        root.add(toolbar);
        root.add(VantaUiNode.spacer(1).flex(1));
        toolbarRuntime = new VantaUiRuntime(root);
        toolbarRuntime.setScale((float) VantaClientPreferences.get().uiScale);
        toolbarRuntime.layout(font, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        VantaClientPreferences preferences = VantaClientPreferences.get();
        boolean animations = preferences.animations;
        entrance = animations ? entrance + (1.0f - entrance) * 0.18f : 1.0f;
        if (Math.abs(1.0f - entrance) < 0.01f) entrance = 1.0f;

        graphics.fill(0, 0, width, height, 0x99080C13);
        if (preferences.hudShowGrid) drawGrid(graphics, preferences.hudGridSize, entrance);

        HudMouseHandler.updateHover(mouseX, mouseY);
        HudManager.render(graphics);
        drawElementHitboxes(graphics, preferences.accent.color());

        HudElement focused = HudSettingsPanel.getSelected();
        if (focused == null) focused = HudMouseHandler.getHovered();
        if (focused != null) {
            selectionPulse = animations
                    ? selectionPulse + (1.0f - selectionPulse) * 0.22f
                    : 1.0f;
            drawSelection(graphics, focused, preferences.accent.color(), selectionPulse);
            drawElementTag(graphics, mc, focused, preferences.accent.color());
        } else {
            selectionPulse = 0.0f;
        }

        if (toolbarRuntime != null) {
            toolbarRuntime.setScale((float) preferences.uiScale);
            toolbarRuntime.render(graphics, font, width, height, mouseX, mouseY);
        }
        HudSettingsPanel.render(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawGrid(GuiGraphics graphics, int spacing, float opacity) {
        int color = (Math.round(65.0f * opacity) << 24) | 0x005D82B8;
        for (int y = 12; y < height; y += spacing) {
            for (int x = 12; x < width; x += spacing) {
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    private void drawToolbar(GuiGraphics graphics, Minecraft mc,
                             VantaClientPreferences preferences, float opacity) {
        int accent = preferences.accent.color();
        int x = (width - 430) / 2;
        int y = 18 - Math.round((1.0f - opacity) * 10.0f);
        fillRounded(graphics, x + 3, y + 4, 430, 52, 15, 0x66000000);
        fillRounded(graphics, x, y, 430, 52, 14, 0xFF303A49);
        fillRounded(graphics, x + 1, y + 1, 428, 50, 13, 0xF0161D28);
        fillRounded(graphics, x + 12, y + 10, 32, 32, 9, accent);
        graphics.drawCenteredString(mc.font, "V", x + 28, y + 22, 0xFFFFFFFF);
        graphics.drawString(mc.font, "HUD Editor", x + 54, y + 11, 0xFFF0F3FA);
        graphics.drawString(mc.font, "Drag elements to position · Click to customize",
                x + 54, y + 27, 0xFF8995A8);

        String snap = preferences.hudSnapToGrid ? "SNAP ON" : "SNAP OFF";
        int snapWidth = mc.font.width(snap) + 18;
        fillRounded(graphics, x + 430 - snapWidth - 12, y + 15, snapWidth, 22, 8,
                preferences.hudSnapToGrid ? accent : 0xFF303A49);
        graphics.drawCenteredString(mc.font, snap,
                x + 430 - snapWidth / 2 - 12, y + 22, 0xFFFFFFFF);
    }

    private void drawElementHitboxes(GuiGraphics graphics, int accent) {
        for (HudElement element : HudManager.getElements()) {
            int x = element.getX() - 3;
            int y = element.getY() - 3;
            int w = Math.max(8, element.getScaledWidth() + 6);
            int h = Math.max(8, element.getScaledHeight() + 6);
            boolean selected = element == HudSettingsPanel.getSelected();
            int fill = selected ? 0x263F8DFF : 0x182A3A50;
            int border = selected ? accent : 0xA8798CA8;
            fillRounded(graphics, x, y, w, h, 5, fill);
            drawRoundedOutline(graphics, x, y, w, h, 5, border, selected ? 2 : 1);
            if (!selected) {
                String name = element.getClass().getSimpleName()
                        .replace("HudElement", "").replaceAll("([a-z])([A-Z])", "$1 $2");
                graphics.fill(x, y - 13, x + Minecraft.getInstance().font.width(name) + 10,
                        y - 2, 0xCC111A27);
                graphics.drawString(Minecraft.getInstance().font, name, x + 5, y - 11,
                        0xFFDCE6F5, false);
            }
        }
    }

    private void drawSelection(GuiGraphics graphics, HudElement element,
                               int accent, float pulse) {
        int x = element.getX() - 4;
        int y = element.getY() - 4;
        int w = Math.max(12, element.getScaledWidth() + 8);
        int h = Math.max(12, element.getScaledHeight() + 8);
        int color = (Math.round(150.0f + 75.0f * pulse) << 24)
                | (accent & 0x00FFFFFF);
        drawRoundedOutline(graphics, x, y, w, h, 8, color, 2);
    }

    private void drawElementTag(GuiGraphics graphics, Minecraft mc,
                                HudElement element, int accent) {
        String label = switch (element.getClass().getSimpleName()) {
            case "ArmorHudElement" -> "ARMOR HUD";
            case "DebugHudElement" -> "DEBUG HUD";
            case "TotemCounterHudElement" -> "TOTEM COUNTER";
            case "PotionEffectsHudElement" -> "POTION EFFECTS";
            default -> "HUD ELEMENT";
        };
        int tagWidth = mc.font.width(label) + 16;
        int x = Math.max(8, Math.min(element.getX(), width - tagWidth - 8));
        int y = Math.max(76, element.getY() - 25);
        fillRounded(graphics, x, y, tagWidth, 18, 7, accent);
        graphics.drawCenteredString(mc.font, label, x + tagWidth / 2, y + 5, 0xFFFFFFFF);
    }

    private static void drawRoundedOutline(GuiGraphics graphics, int x, int y,
                                            int width, int height, int radius,
                                            int color, int thickness) {
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
            if (row < thickness || row >= height - thickness) {
                graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            } else {
                graphics.fill(x + inset, y + row, x + inset + thickness, y + row + 1, color);
                graphics.fill(x + width - inset - thickness, y + row,
                        x + width - inset, y + row + 1, color);
            }
        }
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y,
                                     int width, int height, int radius, int color) {
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

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (HudSettingsPanel.mouseClicked(mouseX, mouseY, event.button())) {
            panelPointer = true;
            return true;
        }
        if (toolbarRuntime != null
                && toolbarRuntime.mouseClicked(mouseX, mouseY, event.button(), font)) {
            toolbarPointer = true;
            return true;
        }
        if (event.button() == 0) {
            HudElement hovered = HudMouseHandler.getHovered();
            if (hovered != null) {
                HudMouseHandler.startDragging(hovered, mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        boolean moved = HudMouseHandler.hasMoved();
        HudMouseHandler.release();
        HudSettingsPanel.mouseReleased();
        if (toolbarRuntime != null) toolbarRuntime.mouseReleased(event.button());
        boolean consumedByPanel = panelPointer || toolbarPointer;
        panelPointer = false;
        toolbarPointer = false;
        if (!moved && !consumedByPanel) {
            HudElement hovered = HudMouseHandler.getHovered();
            if (hovered != null) HudSettingsPanel.setSelected(hovered);
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
                                double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (panelPointer) {
            HudSettingsPanel.mouseDragged(mouseX, mouseY);
            return true;
        }
        if (toolbarPointer) return true;
        if (event.button() == 0) {
            HudMouseHandler.drag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (HudSettingsPanel.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (HudSettingsPanel.keyPressed(event.key(), shiftDown)) return true;
        if (toolbarRuntime != null && toolbarRuntime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        if (toolbarRuntime != null) toolbarRuntime.dispose();
        HudSettingsPanel.clear();
        super.removed();
    }

    @Override
    public void onClose() {
        HudMouseHandler.release();
        panelPointer = false;
        HudSettingsPanel.clear();
        if (parent != null) Minecraft.getInstance().setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

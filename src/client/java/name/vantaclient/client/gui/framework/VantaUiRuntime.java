package name.vantaclient.client.gui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Owns one screen's retained node tree, focus, pointer capture and layout bounds. */
public final class VantaUiRuntime {
    private final VantaUiNode root;
    private final VantaUiTheme theme = new VantaUiTheme();

    private VantaUiNode focused;
    private VantaUiNode activeSlider;
    private VantaUiNode hovered;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public VantaUiRuntime(VantaUiNode root) {
        if (root == null) throw new IllegalArgumentException("Root node cannot be null.");
        this.root = root;
    }

    public void layout(Font font, int screenWidth, int screenHeight) {
        if (screenWidth == lastWidth && screenHeight == lastHeight) return;
        lastWidth = screenWidth;
        lastHeight = screenHeight;
        root.arrange(22, 22, Math.max(0, screenWidth - 44),
                Math.max(0, screenHeight - 44), font);
        clearFocusIfDetached();
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       int mouseX, int mouseY) {
        layout(font, screenWidth, screenHeight);
        root.clearHover();
        hovered = root.hitTest(mouseX, mouseY);
        if (hovered != null) hovered.setHovered(true);

        root.render(graphics, font, theme, mouseX, mouseY);
        renderTooltip(graphics, font, screenWidth, screenHeight);
    }

    public void tick() {
        root.tick(theme);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        VantaUiNode target = root.hitTest(mouseX, mouseY);
        setFocused(target);
        if (target == null) return false;
        target.click(mouseX, button);
        if (target.kind() == VantaUiNode.Kind.SLIDER && button == 0) {
            activeSlider = target;
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (activeSlider == null || button != 0) return false;
        activeSlider.drag(mouseX);
        return true;
    }

    public boolean mouseReleased() {
        boolean handled = activeSlider != null;
        activeSlider = null;
        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        boolean handled = root.scrollAt(mouseX, mouseY, amount);
        if (handled) lastWidth = -1;
        return handled;
    }

    public boolean keyPressed(int keyCode, boolean shiftDown) {
        if (keyCode == 258) {
            cycleFocus(shiftDown ? -1 : 1);
            return true;
        }
        if (focused == null) return false;
        if (focused.editKey(keyCode)) return true;

        switch (keyCode) {
            case 257, 32 -> focused.activate();
            case 263, 265 -> focused.adjust(-1);
            case 262, 264 -> focused.adjust(1);
            default -> { return false; }
        }
        return true;
    }

    public boolean charTyped(int codepoint) {
        return focused != null && focused.charTyped(codepoint);
    }

    public void dispose() {
        activeSlider = null;
        focused = null;
        hovered = null;
    }

    private void setFocused(VantaUiNode node) {
        if (focused == node) return;
        if (focused != null) focused.setFocused(false);
        focused = node != null && node.isInteractive() ? node : null;
        if (focused != null) focused.setFocused(true);
    }

    private void cycleFocus(int direction) {
        List<VantaUiNode> focusable = new ArrayList<>();
        root.collectFocusable(focusable);
        if (focusable.isEmpty()) {
            setFocused(null);
            return;
        }

        if (focused == null) {
            setFocused(focusable.get(direction > 0 ? 0 : focusable.size() - 1));
            return;
        }
        int index = focusable.indexOf(focused);
        if (index < 0) {
            setFocused(focusable.get(direction > 0 ? 0 : focusable.size() - 1));
            return;
        }
        int next = Math.floorMod(index + direction, focusable.size());
        setFocused(focusable.get(next));
    }

    private void clearFocusIfDetached() {
        if (focused == null) return;
        List<VantaUiNode> focusable = new ArrayList<>();
        root.collectFocusable(focusable);
        if (!focusable.contains(focused)) setFocused(null);
    }

    private void renderTooltip(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (hovered == null || hovered.tooltip().isBlank()) return;
        String tooltip = font.plainSubstrByWidth(hovered.tooltip(), Math.max(80, screenWidth - 24));
        int boxWidth = font.width(tooltip) + 14;
        int boxHeight = 22;
        int tooltipX = Math.max(6, Math.min(screenWidth - boxWidth - 6, hovered.x()));
        int tooltipY = hovered.y() - boxHeight - 7;
        if (tooltipY < 6) tooltipY = Math.min(screenHeight - boxHeight - 6, hovered.y() + hovered.height() + 7);

        graphics.fill(tooltipX, tooltipY, tooltipX + boxWidth, tooltipY + boxHeight,
                VantaUiTheme.TOOLTIP_SURFACE);
        graphics.fill(tooltipX, tooltipY, tooltipX + boxWidth, tooltipY + 1, theme.accent());
        graphics.fill(tooltipX, tooltipY + boxHeight - 1,
                tooltipX + boxWidth, tooltipY + boxHeight, VantaUiTheme.BORDER);
        graphics.drawString(font, tooltip, tooltipX + 7, tooltipY + 7,
                VantaUiTheme.TEXT_PRIMARY);
    }
}

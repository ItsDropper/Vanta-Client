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
    private VantaUiNode pendingFocusReveal;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private long lastFrameNanos;
    private long lastUiRenderNanos;
    private long averageUiRenderNanos;
    private float uiScale = 1.0f;

    public VantaUiRuntime(VantaUiNode root) {
        if (root == null) throw new IllegalArgumentException("Root node cannot be null.");
        this.root = root;
    }

    /** Applies the user's shared interface scale and invalidates placement when it changes. */
    public void setScale(float scale) {
        float bounded = Math.max(0.8f, Math.min(1.2f, scale));
        if (Float.compare(uiScale, bounded) == 0) return;
        uiScale = bounded;
        invalidateLayout();
    }

    /** Marks measurements and placement dirty after content or size constraints change. */
    public void invalidateLayout() {
        lastWidth = -1;
    }

    public void layout(Font font, int screenWidth, int screenHeight) {
        if (screenWidth == lastWidth && screenHeight == lastHeight) return;
        lastWidth = screenWidth;
        lastHeight = screenHeight;
        int layoutWidth = Math.max(0, Math.round(screenWidth / uiScale));
        int layoutHeight = Math.max(0, Math.round(screenHeight / uiScale));
        root.arrange(22, 22, Math.max(0, layoutWidth - 44),
                Math.max(0, layoutHeight - 44), font);
        clearFocusIfDetached();
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       int mouseX, int mouseY) {
        layout(font, screenWidth, screenHeight);
        if (pendingFocusReveal != null) {
            root.revealDescendant(pendingFocusReveal, pendingFocusReveal.height());
            pendingFocusReveal = null;
            lastWidth = -1;
            layout(font, screenWidth, screenHeight);
        }
        long now = System.nanoTime();
        float deltaSeconds = lastFrameNanos == 0L
                ? 1.0f / 60.0f
                : Math.max(0.0f, Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000.0f));
        lastFrameNanos = now;
        float offsetX = screenWidth * 0.5f * (1.0f - uiScale);
        float offsetY = screenHeight * 0.5f * (1.0f - uiScale);
        int layoutWidth = Math.max(0, Math.round(screenWidth / uiScale));
        int layoutHeight = Math.max(0, Math.round(screenHeight / uiScale));
        int logicalMouseX = Math.round((mouseX - offsetX) / uiScale);
        int logicalMouseY = Math.round((mouseY - offsetY) / uiScale);

        root.clearHover();
        hovered = root.hitTest(logicalMouseX, logicalMouseY);
        if (hovered != null) hovered.setHovered(true);
        boolean animationsEnabled = theme.animationsEnabled();
        float hoverFactor = 1.0f;
        float toggleFactor = 1.0f;
        if (animationsEnabled) {
            hoverFactor = 1.0f - (float) Math.exp(-VantaUiTheme.HOVER_RATE * deltaSeconds);
            toggleFactor = 1.0f - (float) Math.exp(-VantaUiTheme.TOGGLE_RATE * deltaSeconds);
        }
        root.animate(animationsEnabled, hoverFactor, toggleFactor);

        long renderStart = System.nanoTime();
        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY);
        graphics.pose().scale(uiScale, uiScale);
        try {
            root.render(graphics, font, theme, logicalMouseX, logicalMouseY);
            renderTooltip(graphics, font, layoutWidth, layoutHeight);
        } finally {
            graphics.pose().popMatrix();
        }
        lastUiRenderNanos = Math.max(0L, System.nanoTime() - renderStart);
        averageUiRenderNanos = averageUiRenderNanos == 0L
                ? lastUiRenderNanos
                : averageUiRenderNanos + (lastUiRenderNanos - averageUiRenderNanos) / 8L;
    }

    /** Last CPU-side UI command submission duration; excludes GPU completion. */
    public long lastUiRenderNanos() {
        return lastUiRenderNanos;
    }

    /** Smoothed CPU-side UI command submission duration; excludes GPU completion. */
    public long averageUiRenderNanos() {
        return averageUiRenderNanos;
    }

    public void tick() {
        root.tick();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, Font font) {
        if (button != 0) return false;
        double offsetX = lastWidth * 0.5 * (1.0 - uiScale);
        double offsetY = lastHeight * 0.5 * (1.0 - uiScale);
        mouseX = (mouseX - offsetX) / uiScale;
        mouseY = (mouseY - offsetY) / uiScale;
        VantaUiNode target = root.hitTest(mouseX, mouseY);
        boolean dropdownClosed = root.closeDropdownsExcept(target);
        setFocused(target);
        if (target == null) {
            if (dropdownClosed) lastWidth = -1;
            return dropdownClosed;
        }
        target.click(mouseX, mouseY, button, font);
        if (target.kind() == VantaUiNode.Kind.SLIDER) activeSlider = target;

        int layoutWidth = lastWidth;
        int layoutHeight = lastHeight;
        lastWidth = -1;
        if (target.isDropdownOpen() && layoutWidth > 0 && layoutHeight > 0) {
            layout(font, layoutWidth, layoutHeight);
            root.revealDescendant(target, target.height());
            lastWidth = -1;
            layout(font, layoutWidth, layoutHeight);
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (activeSlider == null || button != 0) return false;
        double offsetX = lastWidth * 0.5 * (1.0 - uiScale);
        activeSlider.drag((mouseX - offsetX) / uiScale);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0 || activeSlider == null) return false;
        activeSlider = null;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double offsetX = lastWidth * 0.5 * (1.0 - uiScale);
        double offsetY = lastHeight * 0.5 * (1.0 - uiScale);
        boolean handled = root.scrollAt((mouseX - offsetX) / uiScale,
                (mouseY - offsetY) / uiScale, amount);
        if (handled) lastWidth = -1;
        return handled;
    }

    public boolean keyPressed(int keyCode, boolean shiftDown) {
        if (keyCode == 258) {
            if (root.closeDropdownsExcept(null)) lastWidth = -1;
            cycleFocus(shiftDown ? -1 : 1);
            return true;
        }
        if (focused == null) return false;
        if (keyCode == 256 && focused.closeDropdown()) {
            lastWidth = -1;
            return true;
        }
        if (focused.editKey(keyCode)) return true;

        switch (keyCode) {
            case 257, 32 -> focused.activate();
            case 263, 265 -> focused.adjust(-1);
            case 262, 264 -> focused.adjust(1);
            default -> { return false; }
        }
        if (focused.kind() == VantaUiNode.Kind.DROPDOWN) {
            lastWidth = -1;
            if (focused.isDropdownOpen()) pendingFocusReveal = focused;
        }
        return true;
    }

    public boolean charTyped(int codepoint) {
        return focused != null && focused.charTyped(codepoint);
    }

    public void dispose() {
        activeSlider = null;
        if (focused != null) focused.setFocused(false);
        focused = null;
        hovered = null;
        pendingFocusReveal = null;
        root.closeDropdownsExcept(null);
        root.clearHover();
        lastFrameNanos = 0L;
        lastUiRenderNanos = 0L;
        averageUiRenderNanos = 0L;
    }

    private void setFocused(VantaUiNode node) {
        pendingFocusReveal = null;
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
        pendingFocusReveal = focused;
    }

    private void clearFocusIfDetached() {
        if (focused == null) return;
        List<VantaUiNode> focusable = new ArrayList<>();
        root.collectFocusable(focusable);
        if (!focusable.contains(focused)) setFocused(null);
    }

    private void renderTooltip(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (hovered == null || hovered.tooltip().isBlank()) return;
        String tooltip = VantaUiNode.fitText(font, hovered.tooltip(), Math.max(80, screenWidth - 24), VantaUiNode.UI_FONT);
        int boxWidth = VantaUiNode.textWidth(font, tooltip, VantaUiNode.UI_FONT) + 14;
        int boxHeight = 22;
        int tooltipX = Math.max(6, Math.min(screenWidth - boxWidth - 6, hovered.x()));
        int tooltipY = hovered.y() - boxHeight - 7;
        if (tooltipY < 6) tooltipY = Math.min(screenHeight - boxHeight - 6, hovered.y() + hovered.height() + 7);

        graphics.fill(tooltipX, tooltipY, tooltipX + boxWidth, tooltipY + boxHeight,
                VantaUiTheme.TOOLTIP_SURFACE);
        graphics.fill(tooltipX, tooltipY, tooltipX + boxWidth, tooltipY + 1, theme.accent());
        graphics.fill(tooltipX, tooltipY + boxHeight - 1,
                tooltipX + boxWidth, tooltipY + boxHeight, VantaUiTheme.BORDER);
        graphics.drawString(font, VantaUiNode.styledText(tooltip, VantaUiNode.UI_FONT), tooltipX + 7, tooltipY + 7,
                VantaUiTheme.TEXT_PRIMARY);
    }
}

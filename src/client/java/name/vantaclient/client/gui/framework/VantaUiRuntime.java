package name.vantaclient.client.gui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import name.vantaclient.client.gui.compose.interaction.VantaFocusController;
import name.vantaclient.client.gui.compose.interaction.VantaPointerCapture;

/** Owns one screen's retained node tree, focus, pointer capture and layout bounds. */
public final class VantaUiRuntime {
    private final VantaUiNode root;
    private final VantaUiTheme theme = new VantaUiTheme();

    private final VantaFocusController focusController;
    private final VantaPointerCapture pointerCapture = new VantaPointerCapture();
    private VantaUiNode hovered;
    private VantaUiNode pendingFocusReveal;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int viewportWidth = -1;
    private int viewportHeight = -1;
    private long lastFrameNanos;
    private long lastUiRenderNanos;
    private long averageUiRenderNanos;
    private float uiScale = 1.0f;

    public VantaUiRuntime(VantaUiNode root) {
        if (root == null) throw new IllegalArgumentException("Root node cannot be null.");
        this.root = root;
        this.focusController = new VantaFocusController(root);
    }

    /** Applies the user's shared interface scale and invalidates placement when it changes. */
    public void setScale(float scale) {
        float bounded = Float.isFinite(scale) ? Math.max(0.8f, Math.min(1.2f, scale)) : 1.0f;
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
        viewportWidth = screenWidth;
        viewportHeight = screenHeight;
        int layoutWidth = Math.max(0, Math.round(screenWidth / uiScale));
        int layoutHeight = Math.max(0, Math.round(screenHeight / uiScale));
        // Keep a small physical frame at every Minecraft GUI scale. The
        // logical viewport is centered inside the scaled coordinate system;
        // omitting that origin shift clips and misaligns enlarged interfaces.
        int physicalMargin = Math.max(8, Math.min(22,
                Math.round(Math.min(screenWidth, screenHeight) * 0.06f)));
        int margin = Math.max(1, Math.round(physicalMargin / uiScale));
        int logicalLeft = Math.round((screenWidth - layoutWidth) * 0.5f);
        int logicalTop = Math.round((screenHeight - layoutHeight) * 0.5f);
        root.arrange(logicalLeft + margin, logicalTop + margin,
                Math.max(0, layoutWidth - margin * 2),
                Math.max(0, layoutHeight - margin * 2), font);
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
        float deltaSeconds = VantaUiMotion.frameDeltaSeconds(lastFrameNanos, now);
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
        float hoverFactor = VantaUiMotion.interpolationFactor(
                VantaUiTheme.HOVER_RATE, deltaSeconds, animationsEnabled);
        float toggleFactor = VantaUiMotion.interpolationFactor(
                VantaUiTheme.TOGGLE_RATE, deltaSeconds, animationsEnabled);
        root.animate(animationsEnabled, hoverFactor, toggleFactor, deltaSeconds);

        theme.refreshComposeTheme();
        long renderStart = System.nanoTime();
        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY);
        graphics.pose().scale(uiScale, uiScale);
        try {
            root.render(graphics, font, theme, logicalMouseX, logicalMouseY);
            root.renderDropdownPopup(graphics, font, theme,
                    logicalMouseX, logicalMouseY, layoutWidth, layoutHeight);
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

    /** Returns the current floating dropdown bounds in logical GUI coordinates, if open. */
    public VantaUiNode.DropdownPopupBounds dropdownPopupBounds() {
        return root.dropdownPopupBounds(
                Math.max(1, lastWidth), Math.max(1, lastHeight));
    }

    public void tick() {
        root.tick();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, Font font) {
        if (button != 0) return false;
        double offsetX = viewportWidth * 0.5 * (1.0 - uiScale);
        double offsetY = viewportHeight * 0.5 * (1.0 - uiScale);
        mouseX = (mouseX - offsetX) / uiScale;
        mouseY = (mouseY - offsetY) / uiScale;

        VantaUiNode popupTarget = root.dropdownPopupAt(
                mouseX, mouseY, Math.max(1, lastWidth), Math.max(1, lastHeight));
        if (popupTarget != null) {
            setFocused(popupTarget);
            int option = popupTarget.dropdownOptionAt(
                    mouseY, Math.max(1, lastWidth), Math.max(1, lastHeight));
            popupTarget.selectDropdownOption(option);
            lastWidth = -1;
            return true;
        }

        VantaUiNode scrollTarget = root.beginScrollbarDrag(mouseX, mouseY);
        if (scrollTarget != null) {
            if (root.closeDropdownsExcept(null)) lastWidth = -1;
            setFocused(null);
            pointerCapture.captureScrollbar(scrollTarget);
            return true;
        }

        VantaUiNode target = root.hitTest(mouseX, mouseY);
        boolean dropdownClosed = root.closeDropdownsExcept(target);
        setFocused(target);
        if (target == null) {
            if (dropdownClosed) lastWidth = -1;
            return dropdownClosed;
        }
        target.click(mouseX, mouseY, button, font);
        if (target.kind() == VantaUiNode.Kind.SLIDER) pointerCapture.captureSlider(target);

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
        if (button != 0) return false;
        double offsetX = viewportWidth * 0.5 * (1.0 - uiScale);
        double offsetY = viewportHeight * 0.5 * (1.0 - uiScale);
        if (!pointerCapture.drag(mouseX, mouseY, offsetX, offsetY, uiScale)) return false;
        lastWidth = -1;
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0) return false;
        return pointerCapture.release(button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double offsetX = lastWidth * 0.5 * (1.0 - uiScale);
        double offsetY = lastHeight * 0.5 * (1.0 - uiScale);
        double logicalMouseX = (mouseX - offsetX) / uiScale;
        double logicalMouseY = (mouseY - offsetY) / uiScale;
        if (root.scrollDropdownAt(logicalMouseX, logicalMouseY, amount,
                Math.max(1, lastWidth), Math.max(1, lastHeight))) return true;
        boolean popupClosed = root.closeDropdownsExcept(null);
        boolean handled = root.scrollAt(logicalMouseX, logicalMouseY, amount);
        if (handled || popupClosed) lastWidth = -1;
        return handled || popupClosed;
    }

    public boolean keyPressed(int keyCode, boolean shiftDown) {
        if (keyCode == 258) {
            if (root.closeDropdownsExcept(null)) lastWidth = -1;
            cycleFocus(shiftDown ? -1 : 1);
            return true;
        }
        VantaUiNode focused = focusController.focused();
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
        VantaUiNode focused = focusController.focused();
        return focused != null && focused.charTyped(codepoint);
    }

    public void dispose() {
        pointerCapture.cancel();
        focusController.clear();
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
        focusController.setFocused(node);
    }

    private void cycleFocus(int direction) {
        VantaUiNode next = focusController.cycle(direction);
        pendingFocusReveal = next;
    }

    private void clearFocusIfDetached() {
        if (focusController.clearIfDetached()) pendingFocusReveal = null;
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

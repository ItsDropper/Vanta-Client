package name.vantaclient.client.gui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import name.vantaclient.client.gui.compose.interaction.VantaInteractionController;

/** Owns one screen's retained node tree, focus, pointer capture and layout bounds. */
public final class VantaUiRuntime {
    private final VantaUiNode root;
    private final VantaUiTheme theme = new VantaUiTheme();

    private final VantaInteractionController interactions;
    private VantaUiNode hovered;
    private VantaUiNode pendingFocusReveal;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int viewportWidth = -1;
    private int viewportHeight = -1;
    private long lastFrameNanos;
    private long lastUiRenderNanos;
    private long averageUiRenderNanos;
    private float preferredScale = 1.0f;
    private float uiScale = 1.0f;

    private static final float DESIGN_WIDTH = 640.0f;
    private static final float DESIGN_HEIGHT = 360.0f;

    public VantaUiRuntime(VantaUiNode root) {
        if (root == null) throw new IllegalArgumentException("Root node cannot be null.");
        this.root = root;
        this.interactions = new VantaInteractionController(root);
    }

    /**
     * Sets the user's preferred UI scale. The runtime additionally fits a
     * 640×360 design viewport to the available Minecraft GUI coordinates so
     * the same layout occupies the same screen-relative position at every
     * resolution and GUI scale.
     */
    public void setScale(float scale) {
        float bounded = Float.isFinite(scale) ? Math.max(0.8f, Math.min(1.2f, scale)) : 1.0f;
        if (Float.compare(preferredScale, bounded) == 0) return;
        preferredScale = bounded;
        invalidateLayout();
    }

    /** Uniform scale needed to fit the reference design viewport in a screen. */
    public static float viewportFitScale(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return 1.0f;
        return Math.min(screenWidth / DESIGN_WIDTH, screenHeight / DESIGN_HEIGHT);
    }

    /** Width expressed in the reference design coordinate system. */
    public static float designWidth(int screenWidth, int screenHeight) {
        return screenWidth / viewportFitScale(screenWidth, screenHeight);
    }

    /** Marks measurements and placement dirty after content or size constraints change. */
    public void invalidateLayout() {
        lastWidth = -1;
    }

    public void layout(Font font, int screenWidth, int screenHeight) {
        float fitScale = viewportFitScale(screenWidth, screenHeight);
        float effectiveScale = preferredScale * fitScale;
        if (!Float.isFinite(effectiveScale) || effectiveScale <= 0.0f) effectiveScale = 1.0f;
        if (Float.compare(uiScale, effectiveScale) != 0) {
            uiScale = effectiveScale;
            lastWidth = -1;
        }
        if (screenWidth == lastWidth && screenHeight == lastHeight) return;
        lastWidth = screenWidth;
        lastHeight = screenHeight;
        viewportWidth = screenWidth;
        viewportHeight = screenHeight;
        int layoutWidth = Math.max(0, Math.round(screenWidth / uiScale));
        int layoutHeight = Math.max(0, Math.round(screenHeight / uiScale));

        // Insets are percentages of the fitted design viewport, not fixed
        // Minecraft GUI pixels. This keeps the frame anchored consistently
        // across GUI scales, window sizes, and the user's scale preference.
        int marginX = Math.max(1, Math.round(layoutWidth * 0.025f));
        int marginY = Math.max(1, Math.round(layoutHeight * 0.035f));
        int logicalLeft = Math.round((screenWidth - layoutWidth) * 0.5f);
        int logicalTop = Math.round((screenHeight - layoutHeight) * 0.5f);
        root.arrange(logicalLeft + marginX, logicalTop + marginY,
                Math.max(0, layoutWidth - marginX * 2),
                Math.max(0, layoutHeight - marginY * 2), font);
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
        return root.dropdownPopupBounds(logicalViewportWidth(), logicalViewportHeight());
    }

    private int logicalViewportWidth() {
        return Math.max(1, Math.round(viewportWidth / uiScale));
    }

    private int logicalViewportHeight() {
        return Math.max(1, Math.round(viewportHeight / uiScale));
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
                mouseX, mouseY, logicalViewportWidth(), logicalViewportHeight());
        if (popupTarget != null) {
            setFocused(popupTarget);
            int option = popupTarget.dropdownOptionAt(
                    mouseY, logicalViewportWidth(), logicalViewportHeight());
            popupTarget.selectDropdownOption(option);
            lastWidth = -1;
            return true;
        }

        VantaUiNode scrollTarget = root.beginScrollbarDrag(mouseX, mouseY);
        if (scrollTarget != null) {
            if (root.closeDropdownsExcept(null)) lastWidth = -1;
            setFocused(null);
            interactions.captureScrollbar(scrollTarget);
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
        if (target.kind() == VantaUiNode.Kind.SLIDER) interactions.captureSlider(target);

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
        if (!interactions.dragPointer(mouseX, mouseY, offsetX, offsetY, uiScale)) return false;
        lastWidth = -1;
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0) return false;
        return interactions.releasePointer(button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double offsetX = lastWidth * 0.5 * (1.0 - uiScale);
        double offsetY = lastHeight * 0.5 * (1.0 - uiScale);
        double logicalMouseX = (mouseX - offsetX) / uiScale;
        double logicalMouseY = (mouseY - offsetY) / uiScale;
        if (root.scrollDropdownAt(logicalMouseX, logicalMouseY, amount,
                logicalViewportWidth(), logicalViewportHeight())) return true;
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
        VantaUiNode focused = interactions.focused();
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
        VantaUiNode focused = interactions.focused();
        return focused != null && focused.charTyped(codepoint);
    }

    public void dispose() {
        interactions.clear();
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
        interactions.focus(node);
    }

    private void cycleFocus(int direction) {
        VantaUiNode next = interactions.cycleFocus(direction);
        pendingFocusReveal = next;
    }

    private void clearFocusIfDetached() {
        if (interactions.validateFocus()) pendingFocusReveal = null;
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

package name.vantaclient.client.gui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import name.vantaclient.client.gui.compose.interaction.VantaInteractionController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Owns one screen's retained node tree, focus, pointer capture and layout bounds. */
public final class VantaUiRuntime {
    private final VantaUiNode root;
    private final VantaUiTheme theme = new VantaUiTheme();

    private final VantaInteractionController interactions;
    private final List<AutoCloseable> stateSubscriptions = new ArrayList<>();
    private final AtomicLong invalidationRevision = new AtomicLong();
    private final AtomicLong layoutInvalidationRevision = new AtomicLong();
    private long appliedLayoutInvalidationRevision;
    private volatile long renderedRevision = -1L;
    private VantaUiNode hovered;
    private VantaUiNode pendingFocusReveal;
    private int lastHitTestX = Integer.MIN_VALUE;
    private int lastHitTestY = Integer.MIN_VALUE;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int viewportWidth = -1;
    private int viewportHeight = -1;
    private long lastFrameNanos;
    private long lastUiRenderNanos;
    private long averageUiRenderNanos;
    private float preferredScale = 1.0f;
    private float uiScale = 1.0f;
    private float zoom = 1.0f;
    private int maxViewportWidth = Integer.MAX_VALUE;
    private int maxViewportHeight = Integer.MAX_VALUE;

    private static final float DESIGN_WIDTH = 640.0f;
    private static final float DESIGN_HEIGHT = 360.0f;

    public VantaUiRuntime(VantaUiNode root) {
        if (root == null) throw new IllegalArgumentException("Root node cannot be null.");
        this.root = root;
        this.interactions = new VantaInteractionController(root);
        List<VantaUiNode.StateBinding> bindings = new ArrayList<>();
        root.collectStateBindings(bindings);
        for (VantaUiNode.StateBinding binding : bindings) {
            stateSubscriptions.add(binding.state().subscribe(ignored -> {
                invalidateRender();
                if (binding.affectsLayout()) layoutInvalidationRevision.incrementAndGet();
            }));
        }
    }

    /** Sets the user's preferred physical UI scale, independent of Minecraft's GUI scale. */
    public void setScale(float scale) {
        float bounded = Float.isFinite(scale) ? Math.max(0.8f, Math.min(1.2f, scale)) : 1.0f;
        if (Float.compare(preferredScale, bounded) == 0) return;
        preferredScale = bounded;
        invalidateLayout();
    }

    /**
     * Extra multiplier on top of the preferred scale, used by full-window menus that size
     * themselves to the window instead of staying a fixed number of physical pixels.
     */
    public void setZoom(float value) {
        float bounded = Float.isFinite(value) ? Math.max(0.5f, Math.min(3.0f, value)) : 1.0f;
        if (Float.compare(zoom, bounded) == 0) return;
        zoom = bounded;
        invalidateLayout();
    }

    /** Uniform scale needed to fit the reference design viewport in a screen. */
    public static float viewportFitScale(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return 1.0f;
        return Math.min(screenWidth / DESIGN_WIDTH, screenHeight / DESIGN_HEIGHT);
    }

    /** Width of Minecraft's actual GUI coordinate viewport (not framebuffer pixels). */
    public static float designWidth(int screenWidth, int screenHeight) {
        return Math.max(0, screenWidth);
    }

    /** Height of Minecraft's actual GUI coordinate viewport (not framebuffer pixels). */
    public static float designHeight(int screenWidth, int screenHeight) {
        return Math.max(0, screenHeight);
    }

    /**
     * Bounds this runtime's centered layout in physical-pixel-equivalent design units.
     * The content remains responsive to the actual Minecraft GUI coordinate viewport,
     * while this limit prevents large framebuffer resolutions from stretching a menu
     * across the entire display.
     */
    public void setMaxViewportSize(int maximumWidth, int maximumHeight) {
        int boundedWidth = maximumWidth > 0 ? maximumWidth : Integer.MAX_VALUE;
        int boundedHeight = maximumHeight > 0 ? maximumHeight : Integer.MAX_VALUE;
        if (maxViewportWidth == boundedWidth && maxViewportHeight == boundedHeight) return;
        maxViewportWidth = boundedWidth;
        maxViewportHeight = boundedHeight;
        invalidateLayout();
    }

    private static float minecraftGuiScale() {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return Double.isFinite(scale) && scale >= 1.0 ? (float) scale : 1.0f;
    }

    /** Marks measurements and placement dirty after content or size constraints change. */
    public void invalidateLayout() {
        layoutInvalidationRevision.incrementAndGet();
        lastWidth = -1;
        invalidateRender();
    }

    /** Marks the component tree's visual output stale after an external state change. */
    public void invalidateRender() {
        invalidationRevision.incrementAndGet();
    }

    /** Monotonic revision used by screen hosts and tests to observe UI state changes. */
    public long invalidationRevision() {
        return invalidationRevision.get();
    }

    /** True when a state write or handled interaction occurred after the last render. */
    public boolean isRenderDirty() {
        return renderedRevision != invalidationRevision.get();
    }

    /** Read-only semantic root shared by every retained-mode screen. */
    public VantaUiComponent componentTree() {
        return root;
    }

    /** Currently focused component, if any, for narration and accessibility adapters. */
    public VantaUiComponent focusedComponent() {
        return interactions.focused();
    }

    public void layout(Font font, int screenWidth, int screenHeight) {
        long requestedLayoutRevision = layoutInvalidationRevision.get();
        if (requestedLayoutRevision != appliedLayoutInvalidationRevision) {
            lastWidth = -1;
            appliedLayoutInvalidationRevision = requestedLayoutRevision;
        }
        // Screen dimensions are already divided by Minecraft's GUI scale.
        // Compensate here so a 16px design control remains 16 physical pixels
        // at GUI scales 1–4 instead of growing with the vanilla coordinate scale.
        float effectiveScale = preferredScale * zoom / minecraftGuiScale();
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

        // Preserve a small edge inset on constrained screens, then center the
        // resulting coordinate-space window. A maximum viewport is expressed in
        // design units so it remains the same physical size at every GUI scale.
        int marginX = Math.max(1, Math.round(layoutWidth * 0.025f));
        int marginY = Math.max(1, Math.round(layoutHeight * 0.035f));
        int arrangedWidth = Math.min(Math.max(0, layoutWidth - marginX * 2), maxViewportWidth);
        int arrangedHeight = Math.min(Math.max(0, layoutHeight - marginY * 2), maxViewportHeight);
        int logicalLeft = Math.round((screenWidth - arrangedWidth) * 0.5f);
        int logicalTop = Math.round((screenHeight - arrangedHeight) * 0.5f);
        root.arrange(logicalLeft, logicalTop, arrangedWidth, arrangedHeight, font);
        clearFocusIfDetached();
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       int mouseX, int mouseY) {
        long frameRevision = invalidationRevision.get();
        int previousWidth = lastWidth;
        int previousHeight = lastHeight;
        float previousScale = uiScale;
        layout(font, screenWidth, screenHeight);
        boolean layoutChanged = previousWidth != lastWidth || previousHeight != lastHeight
                || Float.compare(previousScale, uiScale) != 0;
        if (pendingFocusReveal != null) {
            root.revealDescendant(pendingFocusReveal, pendingFocusReveal.height());
            pendingFocusReveal = null;
            lastWidth = -1;
            layout(font, screenWidth, screenHeight);
            layoutChanged = true;
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

        // Only the previous hover target can be hovered. Clearing the entire retained
        // tree every frame was redundant O(n) traversal on large mods/keybind pages.
        if (layoutChanged || logicalMouseX != lastHitTestX || logicalMouseY != lastHitTestY) {
            if (hovered != null) hovered.setHovered(false);
            hovered = root.hitTest(logicalMouseX, logicalMouseY);
            if (hovered != null) hovered.setHovered(true);
            lastHitTestX = logicalMouseX;
            lastHitTestY = logicalMouseY;
        }
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
        renderedRevision = frameRevision;
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
            invalidateRender();
            return true;
        }

        VantaUiNode scrollTarget = root.beginScrollbarDrag(mouseX, mouseY);
        if (scrollTarget != null) {
            if (root.closeDropdownsExcept(null)) lastWidth = -1;
            setFocused(null);
            interactions.captureScrollbar(scrollTarget);
            invalidateRender();
            return true;
        }

        VantaUiNode target = root.hitTest(mouseX, mouseY);
        boolean dropdownClosed = root.closeDropdownsExcept(target);
        setFocused(target);
        if (target == null) {
            if (dropdownClosed) {
                lastWidth = -1;
                invalidateRender();
            }
            return dropdownClosed;
        }
        target.click(mouseX, mouseY, button, font);
        invalidateRender();
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
        invalidateRender();
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0) return false;
        boolean handled = interactions.releasePointer(button);
        if (handled) invalidateRender();
        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double offsetX = viewportWidth * 0.5 * (1.0 - uiScale);
        double offsetY = viewportHeight * 0.5 * (1.0 - uiScale);
        double logicalMouseX = (mouseX - offsetX) / uiScale;
        double logicalMouseY = (mouseY - offsetY) / uiScale;
        if (root.scrollDropdownAt(logicalMouseX, logicalMouseY, amount,
                logicalViewportWidth(), logicalViewportHeight())) {
            invalidateRender();
            return true;
        }
        boolean popupClosed = root.closeDropdownsExcept(null);
        boolean handled = root.scrollAt(logicalMouseX, logicalMouseY, amount);
        if (handled || popupClosed) {
            lastWidth = -1;
            invalidateRender();
        }
        return handled || popupClosed;
    }

    public boolean keyPressed(int keyCode, boolean shiftDown) {
        if (keyCode == 258) {
            if (root.closeDropdownsExcept(null)) lastWidth = -1;
            cycleFocus(shiftDown ? -1 : 1);
            invalidateRender();
            return true;
        }
        VantaUiNode focused = interactions.focused();
        if (focused == null) return false;
        if (keyCode == 256 && focused.closeDropdown()) {
            lastWidth = -1;
            invalidateRender();
            return true;
        }
        if (focused.editKey(keyCode)) {
            invalidateRender();
            return true;
        }

        switch (keyCode) {
            case 257, 32 -> focused.activate();
            case 263, 265 -> focused.adjust(-1);
            case 262, 264 -> focused.adjust(1);
            default -> { return false; }
        }
        invalidateRender();
        if (focused.kind() == VantaUiNode.Kind.DROPDOWN) {
            lastWidth = -1;
            if (focused.isDropdownOpen()) pendingFocusReveal = focused;
        }
        return true;
    }

    public boolean charTyped(int codepoint) {
        VantaUiNode focused = interactions.focused();
        boolean handled = focused != null && focused.charTyped(codepoint);
        if (handled) invalidateRender();
        return handled;
    }

    public void dispose() {
        interactions.clear();
        for (AutoCloseable subscription : stateSubscriptions) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // A failed listener removal must not prevent the rest of screen teardown.
            }
        }
        stateSubscriptions.clear();
        hovered = null;
        pendingFocusReveal = null;
        lastHitTestX = Integer.MIN_VALUE;
        lastHitTestY = Integer.MIN_VALUE;
        root.closeDropdownsExcept(null);
        root.clearHover();
        lastFrameNanos = 0L;
        lastUiRenderNanos = 0L;
        averageUiRenderNanos = 0L;
    }

    /** Moves keyboard focus to {@code node}; used to keep a search field focused across rebuilds. */
    public void focus(VantaUiNode node) {
        setFocused(node);
    }

    private void setFocused(VantaUiNode node) {
        pendingFocusReveal = null;
        interactions.focus(node);
        invalidateRender();
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

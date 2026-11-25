package name.vantaclient.api.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A HUD element contributed by an integrating mod.
 *
 * Rendering is performed in the element's local coordinate space with its origin at (0, 0).
 * Vanta Client applies position and editor scale around that origin. Positions are normalized
 * to the GUI viewport (0.0 to 1.0), not physical framebuffer pixels.
 */
public interface VantaHudElement {
    /** Stable ID unique within the registering mod. */
    String id();

    /** Human-readable name shown by Vanta's HUD editor. */
    String displayName();

    /** Unscaled local render width, in GUI pixels. */
    int getWidth();

    /** Unscaled local render height, in GUI pixels. */
    int getHeight();

    /** Render at local origin (0, 0); Vanta applies the element's position and scale. */
    void render(GuiGraphics graphics);

    /** Whether the element is normally visible. The editor still previews disabled elements. */
    default boolean isEnabled() { return true; }

    /** Initial horizontal position as a fraction of the GUI viewport. */
    default float initialX() { return 0.05f; }

    /** Initial vertical position as a fraction of the GUI viewport. */
    default float initialY() { return 0.05f; }

    /** Initial editor scale. */
    default float initialScale() { return 1.0f; }

    /** Called when the user moves the element; persist the normalized position if desired. */
    default void onPositionChanged(float x, float y) {}

    /** Called when the user changes the element's editor scale; persist it if desired. */
    default void onScaleChanged(float scale) {}
}

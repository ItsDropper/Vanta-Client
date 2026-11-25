package name.vantaclient.client.hudeditor;

import name.vantaclient.api.RegisteredHud;
import name.vantaclient.api.hud.VantaHudElement;
import name.vantaclient.client.feature.HudEditorFeature;
import net.minecraft.client.gui.GuiGraphics;

/** Adapts a public API HUD into the same positioning and editor pipeline as native elements. */
final class RegisteredHudElement extends HudElement {
    private final RegisteredHud registration;
    private final String backgroundId;

    RegisteredHudElement(RegisteredHud registration) {
        super(normalize(registration.element().initialX()),
                normalize(registration.element().initialY()));
        this.registration = registration;
        this.backgroundId = "mod." + registration.modId() + "." + registration.element().id();
        this.scale = clampScale(registration.element().initialScale());
    }

    VantaHudElement element() {
        return registration.element();
    }

    boolean represents(RegisteredHud candidate) {
        return registration.modId().equals(candidate.modId())
                && registration.element() == candidate.element();
    }

    @Override
    public String getDisplayName() {
        return registration.element().displayName();
    }

    @Override
    public String getBackgroundId() {
        return backgroundId;
    }

    @Override
    public boolean shouldRender() {
        return registration.element().isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {
        if (registration.element().isEnabled() || HudEditorFeature.INSTANCE.isEnabled()) {
            registration.element().render(graphics);
        }
    }

    @Override
    public int getWidth() {
        return Math.max(1, registration.element().getWidth());
    }

    @Override
    public int getHeight() {
        return Math.max(1, registration.element().getHeight());
    }

    @Override
    public void savePosition() {
        registration.element().onPositionChanged(xPercent, yPercent);
    }

    @Override
    public void setScale(float value) {
        super.setScale(value);
        registration.element().onScaleChanged(scale);
    }

    private static float normalize(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.05f;
    }
}

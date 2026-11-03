package name.vantaclient.client.hudeditor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public abstract class HudElement {

    protected float xPercent;
    protected float yPercent;

    protected float scale = 1.0f;

    protected final int defaultX;
    protected final int defaultY;

    public HudElement(float x, float y) {
        this.xPercent = x;
        this.yPercent = y;

        this.defaultX = 0;
        this.defaultY = 0;
    }

    public void move(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int maxX = Math.max(0, screenWidth - getScaledWidth());
        int maxY = Math.max(0, screenHeight - getScaledHeight());
        int targetX = Math.max(0, Math.min(mouseX, maxX));
        int targetY = Math.max(0, Math.min(mouseY, maxY));

        name.vantaclient.client.config.VantaClientPreferences preferences =
                name.vantaclient.client.config.VantaClientPreferences.get();
        if (preferences.hudSnapToGrid) {
            int grid = preferences.hudGridSize;
            targetX = Math.round(targetX / (float) grid) * grid;
            targetY = Math.round(targetY / (float) grid) * grid;
            targetX = Math.max(0, Math.min(targetX, maxX));
            targetY = Math.max(0, Math.min(targetY, maxY));
        }

        xPercent = screenWidth <= 0 ? 0.0f : targetX / (float) screenWidth;
        yPercent = screenHeight <= 0 ? 0.0f : targetY / (float) screenHeight;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= getX() &&
                mouseX <= getX() + getScaledWidth() &&
                mouseY >= getY() &&
                mouseY <= getY() + getScaledHeight();
    }

    public int getX() {
        Minecraft mc = Minecraft.getInstance();
        return (int) (xPercent * mc.getWindow().getGuiScaledWidth());
    }

    public int getY() {
        Minecraft mc = Minecraft.getInstance();
        return (int) (yPercent * mc.getWindow().getGuiScaledHeight());
    }

    public float getScale() {
        return scale;
    }

    /** Stable preference key used by the optional per-element background. */
    public String getBackgroundId() {
        return getClass().getSimpleName();
    }

    public void setScale(float value) {
        scale = clampScale(value);
    }

    protected static float clampScale(float value) {
        return Float.isFinite(value) ? Math.max(0.25f, Math.min(value, 3.0f)) : 1.0f;
    }

    public int getScaledWidth() {
        return (int) (getWidth() * scale);
    }

    public int getScaledHeight() {
        return (int) (getHeight() * scale);
    }

    /**
     * Whether this element needs to be included in the per-frame HUD render pass.
     * The manager rebuilds this list at tick rate, not during rendering.
     */
    public boolean shouldRender() {
        return true;
    }

    public final void render(GuiGraphics graphics) {
        renderScaled(graphics);
    }

    protected abstract void renderElement(GuiGraphics graphics);

    public abstract int getWidth();

    public abstract int getHeight();

    public void savePosition() {
    }

    /** Place this element in the center of the current GUI viewport and persist it. */
    public final void centerOnScreen() {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(1, mc.getWindow().getGuiScaledHeight());
        int targetX = Math.max(0, (screenWidth - getScaledWidth()) / 2);
        int targetY = Math.max(0, (screenHeight - getScaledHeight()) / 2);
        xPercent = targetX / (float) screenWidth;
        yPercent = targetY / (float) screenHeight;
        savePosition();
    }

    public final void renderScaled(GuiGraphics graphics) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(getX(), getY());
            graphics.pose().scale(scale);
            renderElement(graphics);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}

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
        int leftOffset = scaledOffset(getHitboxLeftOffset());
        int topOffset = scaledOffset(getHitboxTopOffset());
        int maxX = Math.max(-leftOffset, screenWidth - leftOffset - getHitboxWidth());
        int maxY = Math.max(-topOffset, screenHeight - topOffset - getHitboxHeight());
        int targetX = Math.max(-leftOffset, Math.min(mouseX, maxX));
        int targetY = Math.max(-topOffset, Math.min(mouseY, maxY));

        name.vantaclient.client.config.VantaClientPreferences preferences =
                name.vantaclient.client.config.VantaClientPreferences.get();
        if (preferences.hudSnapToGrid) {
            int grid = preferences.hudGridSize;
            targetX = Math.round(targetX / (float) grid) * grid;
            targetY = Math.round(targetY / (float) grid) * grid;
            targetX = Math.max(-leftOffset, Math.min(targetX, maxX));
            targetY = Math.max(-topOffset, Math.min(targetY, maxY));
        }

        xPercent = screenWidth <= 0 ? 0.0f : targetX / (float) screenWidth;
        yPercent = screenHeight <= 0 ? 0.0f : targetY / (float) screenHeight;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= getHitboxX() &&
                mouseX <= getHitboxX() + getHitboxWidth() &&
                mouseY >= getHitboxY() &&
                mouseY <= getHitboxY() + getHitboxHeight();
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

    /** Local-pixel expansion beyond the element's normal rendered rectangle. */
    protected int getHitboxLeftOffset() { return 0; }
    protected int getHitboxTopOffset() { return 0; }
    protected int getHitboxRightOffset() { return 0; }
    protected int getHitboxBottomOffset() { return 0; }

    private int scaledOffset(int offset) {
        return Math.round(offset * scale);
    }

    public int getHitboxX() { return getX() + scaledOffset(getHitboxLeftOffset()); }
    public int getHitboxY() { return getY() + scaledOffset(getHitboxTopOffset()); }

    public int getHitboxWidth() {
        return Math.max(1, getScaledWidth()
                + scaledOffset(getHitboxRightOffset() - getHitboxLeftOffset()));
    }

    public int getHitboxHeight() {
        return Math.max(1, getScaledHeight()
                + scaledOffset(getHitboxBottomOffset() - getHitboxTopOffset()));
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
        int targetX = Math.max(-scaledOffset(getHitboxLeftOffset()),
                (screenWidth - getHitboxWidth()) / 2 - scaledOffset(getHitboxLeftOffset()));
        int targetY = Math.max(-scaledOffset(getHitboxTopOffset()),
                (screenHeight - getHitboxHeight()) / 2 - scaledOffset(getHitboxTopOffset()));
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

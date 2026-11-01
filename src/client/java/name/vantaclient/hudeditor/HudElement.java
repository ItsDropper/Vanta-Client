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

        xPercent =
                (float) mouseX /
                        mc.getWindow().getGuiScaledWidth();

        yPercent =
                (float) mouseY /
                        mc.getWindow().getGuiScaledHeight();
    }

    public boolean isHovered(double mouseX, double mouseY) {

        return mouseX >= getX() &&
                mouseX <= getX() + getScaledWidth() &&
                mouseY >= getY() &&
                mouseY <= getY() + getScaledHeight();
    }

    public int getX() {

        Minecraft mc = Minecraft.getInstance();

        return (int) (
                xPercent *
                        mc.getWindow().getGuiScaledWidth()
        );
    }

    public int getY() {

        Minecraft mc = Minecraft.getInstance();

        return (int) (
                yPercent *
                        mc.getWindow().getGuiScaledHeight()
        );
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float value) {
        scale = Math.max(
                0.25f,
                Math.min(value, 3.0f)
        );
    }

    public int getScaledWidth() {
        return (int) (getWidth() * scale);
    }

    public int getScaledHeight() {
        return (int) (getHeight() * scale);
    }

    public final void render(GuiGraphics graphics) {

        graphics.pose().pushMatrix();

        graphics.pose().translate(
                getX(),
                getY()
        );

        graphics.pose().scale(scale);

        renderElement(graphics);

        graphics.pose().popMatrix();
    }

    protected abstract void renderElement(
            GuiGraphics graphics
    );

    public abstract int getWidth();

    public abstract int getHeight();

    public void savePosition() {
    }

    public final void renderScaled(GuiGraphics graphics) {

        graphics.pose().pushMatrix();

        graphics.pose().translate(getX(), getY());
        graphics.pose().scale(scale);

        renderElement(graphics);

        graphics.pose().popMatrix();
    }

}
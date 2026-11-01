package name.vantaclient.client.gui.components;

import name.vantaclient.client.config.VantaClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class FeatureSliderWidget extends AbstractSliderButton {

    private final String name;

    protected FeatureSliderWidget(
            int x,
            int y,
            int width,
            int height,
            String name,
            double value
    ) {
        super(
                x,
                y,
                width,
                height,
                Component.literal(name),
                value
        );

        this.name = name;
    }

    protected abstract String getValueText();

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int hitboxTop = getY() + 4;
        return mouseX >= getX()
                && mouseX < getX() + width
                && mouseY >= hitboxTop
                && mouseY < hitboxTop + height;
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(name));
    }

    @Override
    public void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        boolean hovered = isHovered();
        int accent = VantaClientPreferences.get().accent.color();

        graphics.drawString(minecraft.font, name, getX(), getY(), 0xFFE8ECF4);
        String valueText = getValueText();
        int valueWidth = minecraft.font.width(valueText);
        graphics.drawString(minecraft.font, valueText,
                getX() + width - valueWidth, getY(), 0xFF9BA6B8);

        int trackY = getY() + 15;
        int trackLeft = getX();
        int trackRight = getX() + width;
        int filledWidth = (int) ((width - 8) * Math.max(0.0, Math.min(1.0, value)));
        graphics.fill(trackLeft, trackY, trackRight, trackY + 3, 0xFF303744);
        graphics.fill(trackLeft, trackY, trackLeft + filledWidth + 4, trackY + 3, accent);

        int knobX = Math.max(trackLeft, Math.min(trackRight - 8, trackLeft + filledWidth));
        int knobColor = hovered ? 0xFFFFFFFF : 0xFFE1E7F1;
        graphics.fill(knobX - 1, trackY - 4, knobX + 9, trackY + 7, 0x552A3342);
        graphics.fill(knobX, trackY - 3, knobX + 8, trackY + 6, knobColor);
    }
}
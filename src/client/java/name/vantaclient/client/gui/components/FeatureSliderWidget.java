package name.vantaclient.client.gui.components;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiTheme;
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

        graphics.drawString(minecraft.font, name, getX(), getY(), VantaUiTheme.TEXT_PRIMARY);
        String valueText = getValueText();
        int valueWidth = minecraft.font.width(valueText);
        graphics.drawString(minecraft.font, valueText,
                getX() + width - valueWidth, getY(), VantaUiTheme.TEXT_SECONDARY);

        int trackY = getY() + 15;
        int trackLeft = getX();
        int trackRight = getX() + width;
        int filledWidth = (int) ((width - 8) * Math.max(0.0, Math.min(1.0, value)));
        VantaUiTheme.fillRounded(graphics, trackLeft, trackY, trackRight - trackLeft, 4, 2,
                VantaUiTheme.TRACK);
        VantaUiTheme.fillRounded(graphics, trackLeft, trackY, Math.max(4, filledWidth + 4), 4, 2, accent);

        int knobX = Math.max(trackLeft, Math.min(trackRight - 8, trackLeft + filledWidth));
        int knobColor = hovered ? VantaUiTheme.WHITE : 0xFFE1E7F1;
        VantaUiTheme.fillRounded(graphics, knobX, trackY - 3, 8, 8, 4, knobColor);
    }
}
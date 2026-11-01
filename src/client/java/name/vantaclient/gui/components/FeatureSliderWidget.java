package name.vantaclient.client.gui.components;

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

        // Label
        graphics.drawString(
                minecraft.font,
                name,
                getX(),
                getY(),
                0xFFFFFFFF
        );

        // Value
        String valueText = getValueText();

        int valueWidth = minecraft.font.width(valueText);

        graphics.drawString(
                minecraft.font,
                valueText,
                getX() + width - valueWidth,
                getY(),
                0xFFAAAAAA
        );

        // Slider track
        int trackY = getY() + 14;

        graphics.fill(
                getX(),
                trackY,
                getX() + width,
                trackY + 3,
                0xFF38383F
        );

        // Filled part
        int filledWidth = (int) (width * value);

        graphics.fill(
                getX(),
                trackY,
                getX() + filledWidth,
                trackY + 3,
                0xFF6677FF
        );

        // Knob
        int knobX = getX() + filledWidth - 4;

        graphics.fill(
                knobX,
                trackY - 3,
                knobX + 8,
                trackY + 7,
                hovered
                        ? 0xFFFFFFFF
                        : 0xFFDDDDDD
        );
    }
}
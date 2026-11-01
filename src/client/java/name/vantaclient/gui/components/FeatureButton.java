package name.vantaclient.client.gui.components;

import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.gui.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class FeatureButton extends AbstractWidget {

    private final ClickGuiScreen screen;
    private final Feature feature;
    private final Runnable action;
    private final BooleanSupplier enabled;

    private float toggleAnimation;

    public FeatureButton(
            ClickGuiScreen screen,
            int x,
            int y,
            Feature feature,
            Runnable action,
            BooleanSupplier enabled
    ) {
        super(
                x,
                y,
                130,
                24,
                Component.literal(feature.getName())
        );

        this.screen = screen;
        this.feature = feature;
        this.action = action;
        this.enabled = enabled;

        this.toggleAnimation = enabled.getAsBoolean()
                ? 1.0f
                : 0.0f;
    }

    @Override
    public void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        boolean isEnabled = enabled.getAsBoolean();
        boolean hovered = isHovered();

        float target = isEnabled ? 1.0f : 0.0f;

        toggleAnimation +=
                (target - toggleAnimation) * 0.25f;

        if (Math.abs(target - toggleAnimation) < 0.01f) {
            toggleAnimation = target;
        }

        /*
         * Main background.
         */
        int backgroundColor = hovered
                ? 0xFF29292F
                : 0xFF202024;

        graphics.fill(
                getX(),
                getY(),
                getX() + width,
                getY() + height,
                backgroundColor
        );

        /*
         * Feature name.
         */
        graphics.drawString(
                minecraft.font,
                feature.getName(),
                getX() + 8,
                getY() + 7,
                0xFFFFFFFF
        );

        /*
         * Toggle.
         */
        int toggleWidth = 24;
        int toggleHeight = 12;

        int toggleX =
                getX() + width - toggleWidth - 8;

        int toggleY =
                getY() + 6;

        int offColor = 0xFF444449;
        int onColor = 0xFF5577FF;

        int background = interpolateColor(
                offColor,
                onColor,
                toggleAnimation
        );

        graphics.fill(
                toggleX,
                toggleY,
                toggleX + toggleWidth,
                toggleY + toggleHeight,
                background
        );

        /*
         * Toggle knob.
         */
        int knobSize = 8;

        float knobPosition =
                toggleAnimation * 12.0f;

        int knobX =
                (int) (toggleX + 2 + knobPosition);

        graphics.fill(
                knobX,
                toggleY + 2,
                knobX + knobSize,
                toggleY + 10,
                0xFFFFFFFF
        );
    }

    private int interpolateColor(
            int from,
            int to,
            float progress
    ) {
        progress = Math.max(
                0.0f,
                Math.min(1.0f, progress)
        );

        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;

        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;

        int r = (int) (
                fromR + (toR - fromR) * progress
        );

        int g = (int) (
                fromG + (toG - fromG) * progress
        );

        int b = (int) (
                fromB + (toB - fromB) * progress
        );

        return 0xFF000000
                | (r << 16)
                | (g << 8)
                | b;
    }

    @Override
    public boolean mouseClicked(
            MouseButtonEvent event,
            boolean bl
    ) {
        if (!isActive()) {
            return false;
        }

        if (!isMouseOver(event.x(), event.y())) {
            return false;
        }

        int toggleWidth = 24;
        int toggleHeight = 12;

        int toggleX =
                getX() + width - toggleWidth - 8;

        int toggleY =
                getY() + 6;

        boolean clickedToggle =
                event.x() >= toggleX &&
                        event.x() <= toggleX + toggleWidth &&
                        event.y() >= toggleY &&
                        event.y() <= toggleY + toggleHeight;

        /*
         * Only toggle when the actual switch is clicked.
         */
        if (!clickedToggle) {
            return false;
        }

        playDownSound(
                Minecraft.getInstance()
                        .getSoundManager()
        );

        action.run();

        return true;
    }

    @Override
    protected void updateWidgetNarration(
            NarrationElementOutput narration
    ) {
        narration.add(
                NarratedElementType.TITLE,
                Component.literal(
                        feature.getName()
                                + (
                                enabled.getAsBoolean()
                                        ? " enabled"
                                        : " disabled"
                        )
                )
        );
    }

    public Feature getFeature() {
        return feature;
    }
}
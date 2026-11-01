package name.vantaclient.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class CloseButton extends AbstractWidget {

    private final Runnable action;

    public CloseButton(
            int x,
            int y,
            Runnable action
    ) {
        super(
                x,
                y,
                20,
                20,
                Component.literal("X")
        );

        this.action = action;
    }

    @Override
    public void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        boolean hovered = isHovered();

        int background = hovered
                ? 0xFF303038
                : 0xFF202024;

        graphics.fill(
                getX(),
                getY(),
                getX() + width,
                getY() + height,
                background
        );

        graphics.drawCenteredString(
                Minecraft.getInstance().font,
                "×",
                getX() + width / 2,
                getY() + 5,
                0xFFFFFFFF
        );
    }

    @Override
    public boolean mouseClicked(
            MouseButtonEvent event,
            boolean bl
    ) {
        if (!isActive() || !isMouseOver(event.x(), event.y())) {
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
                Component.literal("Close")
        );
    }
}
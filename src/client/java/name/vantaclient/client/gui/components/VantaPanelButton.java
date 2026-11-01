package name.vantaclient.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Dark, accent-aware action control used by the Vanta settings panel. */
public final class VantaPanelButton extends AbstractWidget {
    private final Runnable action;
    private final BooleanSupplier selected;
    private final IntSupplier accent;
    private String label;

    public VantaPanelButton(int x, int y, int width, int height, String label,
                            Runnable action, BooleanSupplier selected, IntSupplier accent) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.action = action;
        this.selected = selected;
        this.accent = accent;
    }

    public void setLabel(String label) {
        this.label = label;
        setMessage(Component.literal(label));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        boolean active = selected.getAsBoolean();
        int background = active ? 0xFF292E3A : hovered ? 0xFF252A34 : 0xFF1D2129;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
        if (active) {
            graphics.fill(getX(), getY(), getX() + 2, getY() + height, accent.getAsInt());
        }
        int textColor = active ? 0xFFF5F6FA : hovered ? 0xFFE3E6ED : 0xFFB2B7C3;
        graphics.drawString(Minecraft.getInstance().font, label,
                getX() + 8, getY() + (height - 8) / 2, textColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !isMouseOver(event.x(), event.y())) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        action.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.literal(label));
    }
}

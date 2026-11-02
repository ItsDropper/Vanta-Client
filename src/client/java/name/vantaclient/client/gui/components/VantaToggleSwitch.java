package name.vantaclient.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Compact rounded toggle control for Vanta's settings rows. */
public final class VantaToggleSwitch extends AbstractWidget {
    private final BooleanSupplier value;
    private final Consumer<Boolean> setter;
    private float transition;
    private float hover;

    public VantaToggleSwitch(int x, int y, BooleanSupplier value, Consumer<Boolean> setter) {
        super(VantaPanelButton.layoutX(x), VantaPanelButton.layoutY(y),
                VantaPanelButton.layoutSize(46), VantaPanelButton.layoutSize(22),
                Component.literal("Toggle"));
        this.value = value;
        this.setter = setter;
        this.transition = value.getAsBoolean() ? 1.0f : 0.0f;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = value.getAsBoolean();
        float target = enabled ? 1.0f : 0.0f;
        transition += (target - transition) * 0.28f;
        if (Math.abs(target - transition) < 0.01f) transition = target;
        float hoverTarget = isHovered() ? 1.0f : 0.0f;
        hover += (hoverTarget - hover) * 0.24f;

        int accent = name.vantaclient.client.config.VantaClientPreferences.get().accent.color();
        int track = mix(0xFF303744, 0xFF3B4658, hover * 0.7f);
        track = mix(track, accent, transition * 0.92f);
        int border = mix(0xFF414B5A, accent, transition * 0.65f);
        rounded(graphics, getX(), getY(), width, height, height / 2, border);
        rounded(graphics, getX() + 1, getY() + 1, width - 2, height - 2,
                Math.max(1, height / 2 - 1), track);

        int knobSize = Math.max(8, height - 8);
        int travel = width - knobSize - 8;
        int knobX = getX() + 4 + Math.round(travel * transition);
        int knobY = getY() + (height - knobSize) / 2;
        rounded(graphics, knobX, knobY, knobSize, knobSize, knobSize / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !isMouseOver(event.x(), event.y())) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        setter.accept(!value.getAsBoolean());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE,
                Component.literal(value.getAsBoolean() ? "Enabled" : "Disabled"));
    }

    private static void rounded(GuiGraphics graphics, int x, int y, int width,
                                int height, int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            } else if (row >= height - r) {
                double dy = row - (height - r) + 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static int mix(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = Math.round(((from >>> 24) & 255) * (1.0f - t) + ((to >>> 24) & 255) * t);
        int r = Math.round(((from >>> 16) & 255) * (1.0f - t) + ((to >>> 16) & 255) * t);
        int g = Math.round(((from >>> 8) & 255) * (1.0f - t) + ((to >>> 8) & 255) * t);
        int b = Math.round((from & 255) * (1.0f - t) + (to & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

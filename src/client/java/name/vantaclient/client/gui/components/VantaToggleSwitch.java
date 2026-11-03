package name.vantaclient.client.gui.components;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiTheme;
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
    private long lastFrameNanos;

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
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1.0f / 60.0f
                : Math.max(0.0f, Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000.0f));
        lastFrameNanos = now;
        float hoverTarget = isHovered() ? 1.0f : 0.0f;
        if (!VantaClientPreferences.get().animations) {
            transition = target;
            hover = hoverTarget;
        } else {
            float factor = 1.0f - (float) Math.exp(-17.0f * delta);
            transition += (target - transition) * factor;
            hover += (hoverTarget - hover) * factor;
            if (Math.abs(target - transition) < 0.01f) transition = target;
            if (Math.abs(hoverTarget - hover) < 0.01f) hover = hoverTarget;
        }

        int accent = VantaClientPreferences.get().accent.color();
        int track = mix(VantaUiTheme.TRACK, VantaUiTheme.SURFACE_HOVER, hover * 0.75f);
        track = mix(track, accent, transition * 0.90f);
        VantaUiTheme.fillRounded(graphics, getX(), getY(), width, height, height / 2, track);

        int knobSize = Math.max(8, height - 8);
        int travel = width - knobSize - 8;
        int knobX = getX() + 4 + Math.round(travel * transition);
        int knobY = getY() + (height - knobSize) / 2;
        VantaUiTheme.fillRounded(graphics, knobX, knobY, knobSize, knobSize,
                knobSize / 2, VantaUiTheme.WHITE);
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



    private static int mix(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = Math.round(((from >>> 24) & 255) * (1.0f - t) + ((to >>> 24) & 255) * t);
        int r = Math.round(((from >>> 16) & 255) * (1.0f - t) + ((to >>> 16) & 255) * t);
        int g = Math.round(((from >>> 8) & 255) * (1.0f - t) + ((to >>> 8) & 255) * t);
        int b = Math.round((from & 255) * (1.0f - t) + (to & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

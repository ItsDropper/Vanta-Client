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
import java.util.function.IntSupplier;

/** Dark, accent-aware action control used throughout the Vanta settings panel. */
public final class VantaPanelButton extends AbstractWidget {
    private final Consumer<VantaPanelButton> action;
    private final BooleanSupplier selected;
    private final IntSupplier accent;
    private String label;
    private float hoverAmount;
    private long lastFrameNanos;
    private final boolean accentSurface;
    private final boolean accentWhenSelected;

    public VantaPanelButton(int x, int y, int width, int height, String label,
                            Runnable action, BooleanSupplier selected, IntSupplier accent) {
        this(x, y, width, height, label, button -> action.run(), selected, accent, false, true);
    }

    public VantaPanelButton(int x, int y, int width, int height, String label,
                            Runnable action, BooleanSupplier selected, IntSupplier accent,
                            boolean accentSurface) {
        this(x, y, width, height, label, button -> action.run(), selected, accent, accentSurface, false);
    }

    public VantaPanelButton(int x, int y, int width, int height, String label,
                            Runnable action, BooleanSupplier selected, IntSupplier accent,
                            boolean accentSurface, boolean accentWhenSelected) {
        super(layoutX(x), layoutY(y), layoutSize(width), layoutSize(height), Component.literal(label));
        this.label = label;
        this.action = button -> action.run();
        this.selected = selected;
        this.accent = accent;
        this.accentSurface = accentSurface;
        this.accentWhenSelected = accentWhenSelected;
    }

    public static float layoutScale() {
        Minecraft minecraft = Minecraft.getInstance();
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        float fit = Math.min(1.0f, Math.min((guiWidth - 32.0f) / 920.0f,
                (guiHeight - 32.0f) / 540.0f));
        float preference = (float) VantaClientPreferences.get().uiScale;
        return Math.max(0.25f, Math.min(1.0f, fit * preference));
    }

    public static int layoutOffsetX() {
        Minecraft minecraft = Minecraft.getInstance();
        return Math.round((minecraft.getWindow().getGuiScaledWidth() - 920.0f * layoutScale()) / 2.0f);
    }

    public static int layoutOffsetY() {
        Minecraft minecraft = Minecraft.getInstance();
        return Math.round((minecraft.getWindow().getGuiScaledHeight() - 540.0f * layoutScale()) / 2.0f);
    }

    public static int layoutX(int x) { return layoutOffsetX() + Math.round(x * layoutScale()); }
    public static int layoutY(int y) { return layoutOffsetY() + Math.round(y * layoutScale()); }
    public static int layoutSize(int size) { return Math.max(1, Math.round(size * layoutScale())); }
    public static int unlayoutX(double x) { return Math.round((float) (x - layoutOffsetX()) / layoutScale()); }
    public static int unlayoutY(double y) { return Math.round((float) (y - layoutOffsetY()) / layoutScale()); }

    private VantaPanelButton(int x, int y, int width, int height, String label,
                             Consumer<VantaPanelButton> action, BooleanSupplier selected, IntSupplier accent,
                             boolean accentSurface, boolean accentWhenSelected) {
        super(layoutX(x), layoutY(y), layoutSize(width), layoutSize(height), Component.literal(label));
        this.label = label;
        this.action = action;
        this.selected = selected;
        this.accent = accent;
        this.accentSurface = accentSurface;
        this.accentWhenSelected = accentWhenSelected;
    }

    public static Builder builder(Component message, Consumer<VantaPanelButton> action) {
        return new Builder(message, action);
    }

    public void setLabel(String label) {
        setMessage(Component.literal(label));
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.label = message.getString();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        boolean activeSelection = selected.getAsBoolean();
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1.0f / 60.0f
                : Math.max(0.0f, Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000.0f));
        lastFrameNanos = now;
        if (!VantaClientPreferences.get().animations) {
            hoverAmount = hovered ? 1.0f : 0.0f;
        } else {
            float factor = 1.0f - (float) Math.exp(-18.0f * delta);
            float target = hovered ? 1.0f : 0.0f;
            hoverAmount += (target - hoverAmount) * factor;
            if (Math.abs(target - hoverAmount) < 0.01f) hoverAmount = target;
        }

        int accentColor = accent.getAsInt();
        boolean filledAccent = accentSurface || (accentWhenSelected && activeSelection);
        if (filledAccent) {
            VantaUiTheme.fillRounded(graphics, getX(), getY(), width, height, 9,
                    VantaUiTheme.withAlpha(mixColor(accentColor, 0xFFFFFFFF, hoverAmount * 0.10f),
                            isActive() ? 226 : 110));
        } else if (activeSelection) {
            VantaUiTheme.fillRounded(graphics, getX(), getY(), width, height, 9,
                    VantaUiTheme.withAlpha(mixColor(VantaUiTheme.PANEL_RAISED, accentColor, 0.16f), 148));
        } else if (hovered) {
            VantaUiTheme.fillRounded(graphics, getX(), getY(), width, height, 9,
                    VantaUiTheme.withAlpha(VantaUiTheme.SURFACE_HOVER,
                            Math.round(110.0f + 45.0f * hoverAmount)));
        }

        int textColor = !isActive() ? VantaUiTheme.TEXT_MUTED
                : filledAccent ? VantaUiTheme.WHITE
                : activeSelection || hovered ? VantaUiTheme.TEXT_PRIMARY
                : VantaUiTheme.TEXT_SECONDARY;
        String visibleLabel = Minecraft.getInstance().font.plainSubstrByWidth(label, Math.max(0, width - 16));
        int textX = getX() + Math.max(8,
                (width - Minecraft.getInstance().font.width(visibleLabel)) / 2);
        graphics.drawString(Minecraft.getInstance().font,
                Component.literal(visibleLabel).withStyle(style ->
                        style.withBold(activeSelection || filledAccent)),
                textX, getY() + (height - 8) / 2, textColor);
    }



    private static int mixColor(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = Math.round(((from >>> 24) & 255) * (1.0f - t) + ((to >>> 24) & 255) * t);
        int r = Math.round(((from >>> 16) & 255) * (1.0f - t) + ((to >>> 16) & 255) * t);
        int g = Math.round(((from >>> 8) & 255) * (1.0f - t) + ((to >>> 8) & 255) * t);
        int b = Math.round((from & 255) * (1.0f - t) + (to & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !isMouseOver(event.x(), event.y())) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        action.accept(this);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.literal(label));
    }

    public static final class Builder {
        private final Component message;
        private final Consumer<VantaPanelButton> action;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        private Builder(Component message, Consumer<VantaPanelButton> action) {
            this.message = message;
            this.action = action;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public VantaPanelButton build() {
            return new VantaPanelButton(x, y, width, height, message.getString(), action,
                    () -> false, () -> VantaClientPreferences.get().accent.color(), false, false);
        }
    }
}

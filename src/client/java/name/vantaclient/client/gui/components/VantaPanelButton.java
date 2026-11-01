package name.vantaclient.client.gui.components;

import name.vantaclient.client.config.VantaClientPreferences;
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

    public VantaPanelButton(int x, int y, int width, int height, String label,
                            Runnable action, BooleanSupplier selected, IntSupplier accent) {
        this(x, y, width, height, label, button -> action.run(), selected, accent);
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
                             Consumer<VantaPanelButton> action, BooleanSupplier selected, IntSupplier accent) {
        super(layoutX(x), layoutY(y), layoutSize(width), layoutSize(height), Component.literal(label));
        this.label = label;
        this.action = action;
        this.selected = selected;
        this.accent = accent;
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
        boolean active = selected.getAsBoolean();
        int background = active ? 0xFF292E3A : hovered ? 0xFF252A34 : 0xFF1D2129;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
        if (active) {
            graphics.fill(getX(), getY(), getX() + 2, getY() + height, accent.getAsInt());
        }
        int textColor = active ? 0xFFF5F6FA : hovered ? 0xFFE3E6ED : 0xFFB2B7C3;
        String visibleLabel = Minecraft.getInstance().font.plainSubstrByWidth(label, Math.max(0, width - 16));
        graphics.drawString(Minecraft.getInstance().font, visibleLabel,
                getX() + 8, getY() + (height - 8) / 2, textColor);
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
                    () -> false, () -> VantaClientPreferences.get().accent.color());
        }
    }
}

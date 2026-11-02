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
    private float hoverAmount;
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
        boolean active = selected.getAsBoolean();
        float target = hovered ? 1.0f : 0.0f;
        hoverAmount += (target - hoverAmount) * 0.24f;
        if (Math.abs(target - hoverAmount) < 0.015f) hoverAmount = target;

        boolean filledAccent = accentSurface || (accentWhenSelected && active);
        int background = filledAccent
                ? mixColor(accent.getAsInt(), 0xFF6599FF, hoverAmount * 0.22f)
                : mixColor(0xFF171C26, 0xFF252F40, hoverAmount);
        if (active && !filledAccent) background = mixColor(background, 0xFF1D304D, 0.82f);
        int border = filledAccent ? mixColor(accent.getAsInt(), 0xFF8DB2FF, hoverAmount * 0.35f)
                : mixColor(0xFF2A3040, accent.getAsInt(), active ? 0.52f : hoverAmount * 0.36f);
        fillRounded(graphics, getX(), getY(), width, height, 10, border);
        fillRounded(graphics, getX() + 1, getY() + 1, width - 2, height - 2, 9, background);

        int accentColor = accent.getAsInt();
        if (active && !filledAccent) {
            fillRounded(graphics, getX() + 2, getY() + 3, 3, height - 6, 2, accentColor);
        } else if (!filledAccent && hoverAmount > 0.08f) {
            fillRounded(graphics, getX() + 2, getY() + 5, 2, height - 10, 1,
                    mixColor(0xFF252D3B, accentColor, hoverAmount * 0.7f));
        }

        int textColor = active ? 0xFFF7F9FF : hovered ? 0xFFF0F4FC : 0xFFAAB4C5;
        String visibleLabel = Minecraft.getInstance().font.plainSubstrByWidth(label, Math.max(0, width - 16));
        int textX = accentSurface
                ? getX() + (width - Minecraft.getInstance().font.width(visibleLabel)) / 2
                : getX() + 10;
        graphics.drawString(Minecraft.getInstance().font, visibleLabel,
                textX, getY() + (height - 8) / 2, filledAccent ? 0xFFFFFFFF : textColor);
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y, int width,
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

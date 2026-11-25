package name.vantaclient.client.gui.components.settings;

import name.vantaclient.client.gui.ClickGuiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Persistent boolean feature option rendered as a compact toggle button. */
public final class BooleanSetting implements Setting {
    private final String name;
    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;
    private Button button;

    public BooleanSetting(String name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        this.name = name;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void addWidgets(ClickGuiScreen screen, int x, int y) {
        button = Button.builder(message(), widget -> {
            setter.accept(!getter.get());
            widget.setMessage(message());
        }).bounds(x, y, 150, 22).build();
        screen.addButton(button);
    }

    public String getName() { return name; }
    public Supplier<Boolean> getGetter() { return getter; }
    public Consumer<Boolean> getSetter() { return setter; }

    private Component message() {
        return Component.literal(name + ": " + (getter.get() ? "On" : "Off"));
    }

    @Override
    public void updateWidgets(int x, int y) {
        if (button != null) {
            button.setX(x);
            button.setY(y);
            button.setMessage(message());
        }
    }

    @Override
    public void render(GuiGraphics graphics, ClickGuiScreen screen, int x, int y) {}

    @Override
    public void clearWidgets(ClickGuiScreen screen) {
        if (button != null) {
            screen.removeWidgetPublic(button);
            button = null;
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (button != null) button.visible = visible;
    }

    @Override
    public int getHeight() {
        return 30;
    }
}

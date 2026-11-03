package name.vantaclient.client.gui.components.settings;

import name.vantaclient.client.gui.ClickGuiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A compact cyclic choice setting shared by the legacy and retained settings UIs. */
public final class ChoiceSetting implements Setting {
    private final String name;
    private final List<String> options;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private Button button;

    public ChoiceSetting(String name, List<String> options,
                         Supplier<String> getter, Consumer<String> setter) {
        this.name = name;
        this.options = List.copyOf(options);
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() { return name; }
    public List<String> getOptions() { return options; }
    public Supplier<String> getGetter() { return getter; }
    public Consumer<String> getSetter() { return setter; }

    private Component message() {
        return Component.literal(name + ": " + getter.get());
    }

    @Override
    public void addWidgets(ClickGuiScreen screen, int x, int y) {
        button = Button.builder(message(), widget -> {
            int index = options.indexOf(getter.get());
            setter.accept(options.get((index + 1 + options.size()) % options.size()));
            widget.setMessage(message());
        }).bounds(x, y, 150, 22).build();
        screen.addButton(button);
    }

    @Override public void updateWidgets(int x, int y) {
        if (button != null) {
            button.setX(x);
            button.setY(y);
            button.setMessage(message());
        }
    }

    @Override public void render(GuiGraphics graphics, ClickGuiScreen screen, int x, int y) {}

    @Override public void clearWidgets(ClickGuiScreen screen) {
        if (button != null) {
            screen.removeWidgetPublic(button);
            button = null;
        }
    }

    @Override public void setVisible(boolean visible) {
        if (button != null) button.visible = visible;
    }

    @Override public int getHeight() { return 30; }
}

package name.vantaclient.client.gui.components.settings;

import name.vantaclient.client.gui.ClickGuiScreen;
import name.vantaclient.client.gui.components.FeatureSlider;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SliderSetting implements Setting {

    private final String name;
    private final String unit;
    private final double min;
    private final double max;

    private final Supplier<Double> getter;
    private final Consumer<Double> setter;

    private FeatureSlider slider;

    public SliderSetting(
            String name,
            double min,
            double max,
            String unit,
            Supplier<Double> getter,
            Consumer<Double> setter
    ) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() { return name; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public String getUnit() { return unit; }
    public Supplier<Double> getGetter() { return getter; }
    public Consumer<Double> getSetter() { return setter; }

    @Override
    public void addWidgets(
            ClickGuiScreen screen,
            int x,
            int y
    ) {


        slider = new FeatureSlider(
                x,
                y,
                name,
                min,
                max,
                unit,
                getter,
                setter
        );

        screen.addSlider(slider.getSlider());
    }

    @Override
    public void updateWidgets(int x, int y) {

        if (slider != null) {
            slider.setPosition(x, y);
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            ClickGuiScreen screen,
            int x,
            int y
    ) {
    }

    @Override
    public void clearWidgets(ClickGuiScreen screen) {

        if (slider != null) {
            screen.removeWidgetPublic(
                    slider.getSlider()
            );

            slider = null;
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (slider != null) {
            slider.getSlider().visible = visible;
        }
    }

    @Override
    public int getHeight() {
        return 30;
    }
}
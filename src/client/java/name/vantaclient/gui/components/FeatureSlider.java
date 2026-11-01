package name.vantaclient.client.gui.components;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FeatureSlider {

    private final FeatureSliderWidget slider;

    private final String name;
    private final double min;
    private final double max;

    private final Supplier<Double> valueGetter;
    private final Consumer<Double> valueSetter;

    private final String unit;

    public FeatureSlider(
            int x,
            int y,
            String name,
            double min,
            double max,
            String unit,
            Supplier<Double> valueGetter,
            Consumer<Double> valueSetter
    ) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.unit = unit;

        this.valueGetter = valueGetter;
        this.valueSetter = valueSetter;

        double current = valueGetter.get();

        this.slider = new FeatureSliderWidget(
                x,
                y,
                150,
                24,
                name,
                (current - min) / (max - min)
        ) {

            @Override
            protected String getValueText() {

                double value =
                        min + this.value * (max - min);

                if (unit.equals("%")) {
                    return String.format(
                            "%.0f%%",
                            value
                    );
                }

                if (!unit.isEmpty()) {
                    return String.format(
                            "%.0f %s",
                            value,
                            unit
                    );
                }

                return String.format(
                        "%.2f",
                        value
                );
            }

            @Override
            protected void applyValue() {

                double newValue =
                        min + this.value * (max - min);

                valueSetter.accept(newValue);
            }
        };
    }

    public FeatureSliderWidget getSlider() {
        return slider;
    }

    public void setPosition(int x, int y) {
        slider.setX(x);
        slider.setY(y);
    }
}
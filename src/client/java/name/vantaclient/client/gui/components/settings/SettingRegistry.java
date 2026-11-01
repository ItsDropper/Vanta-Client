package name.vantaclient.client.gui.components.settings;

import name.vantaclient.client.feature.*;
import name.vantaclient.client.config.DropperUtilsConfig;

import java.util.ArrayList;
import java.util.List;

public class SettingRegistry {

    public static List<Setting> getSettings(Feature feature) {

        List<Setting> settings = new ArrayList<>();

        if (feature == ZoomFeature.INSTANCE) {

            settings.add(
                    new SliderSetting(
                            "Zoom FOV",
                            1,
                            50,
                            "",
                            () -> (double) ZoomFeature.getZoomFov(),
                            value -> ZoomFeature.setZoomFov(
                                    value.intValue()
                            )
                    )
            );

            settings.add(
                    new SliderSetting(
                            "Zoom Speed",
                            0.01,
                            0.5,
                            "",
                            () -> (double) ZoomFeature.getZoomSpeed(),
                            value -> ZoomFeature.setZoomSpeed(
                                    value.floatValue()
                            )
                    )
            );
        }

        if (feature == MotionBlurFeature.INSTANCE) {
            settings.add(new SliderSetting(
                    "Blur Strength", 0.05, 0.85, "",
                    () -> (double) MotionBlurFeature.getStrength(),
                    value -> MotionBlurFeature.setStrength(value.floatValue())
            ));
            settings.add(new SliderSetting(
                    "Samples", 2, 12, "",
                    () -> (double) MotionBlurFeature.getSamples(),
                    value -> MotionBlurFeature.setSamples(value.intValue())
            ));
            settings.add(new SliderSetting(
                    "Sensitivity", 0.25, 2.0, "x",
                    () -> (double) MotionBlurFeature.getSensitivity(),
                    value -> MotionBlurFeature.setSensitivity(value.floatValue())
            ));
        }

        if (feature == LowFireFeature.INSTANCE) {
            settings.add(
                    new SliderSetting(
                            "Fire Height",
                            0,
                            100,
                            "%",
                            () -> 100.0 - (
                                    DropperUtilsConfig.get().lowFireHeight * 100.0
                            ),
                            value -> {
                                DropperUtilsConfig.get().lowFireHeight =
                                        1.0f - (value.floatValue() / 100.0f);

                                DropperUtilsConfig.save();
                            }
                    )
            );
        }

        if (feature == FullbrightFeature.INSTANCE) {

            settings.add(
                    new SliderSetting(
                            "Brightness",
                            0,
                            100,
                            "%",
                            () -> (double) FullbrightFeature.getBrightness(),
                            value -> FullbrightFeature.setBrightness(
                                    value.floatValue()
                            )
                    )
            );
        }

        if (feature == AnchorOptimizerFeature.INSTANCE) {
            settings.add(
                    new SliderSetting(
                            "Scan Interval",
                            5,
                            100,
                            "ticks",
                            () -> (double) AnchorOptimizerFeature.getScanInterval(),
                            value -> AnchorOptimizerFeature.setScanInterval(
                                    value.intValue()
                            )
                    )
            );

            settings.add(
                    new SliderSetting(
                            "Scan Range",
                            1,
                            8,
                            "blocks",
                            () -> (double) AnchorOptimizerFeature.getScanRange(),
                            value -> AnchorOptimizerFeature.setScanRange(
                                    value.intValue()
                            )
                    )
            );
        }


        return settings;
    }
}


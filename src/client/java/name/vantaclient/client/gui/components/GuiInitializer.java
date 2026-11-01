package name.vantaclient.client.gui.components;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.Category;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.gui.ClickGuiScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class GuiInitializer {

    public static void setup(ClickGuiScreen screen) {

        /*
         * Layout
         *
         * Sidebar:
         * x = 20
         * width = 130
         *
         * Feature area:
         * x = 180
         */

        int sidebarX = 20;
        int sidebarY = 70;

        int featureX = 180;
        int featureY = 70;

        /*
         * Categories
         */

        screen.addButton(
                new CategoryButton(
                        sidebarX,
                        sidebarY,
                        Category.HUD,
                        () -> screen.selectCategory(Category.HUD),
                        () -> screen.getSelectedCategory() == Category.HUD
                )
        );

        screen.addButton(
                new CategoryButton(
                        sidebarX,
                        sidebarY + 36,
                        Category.VISUAL,
                        () -> screen.selectCategory(Category.VISUAL),
                        () -> screen.getSelectedCategory() == Category.VISUAL
                )
        );

        screen.addButton(
                new CategoryButton(
                        sidebarX,
                        sidebarY + 72,
                        Category.COMBAT,
                        () -> screen.selectCategory(Category.COMBAT),
                        () -> screen.getSelectedCategory() == Category.COMBAT
                )
        );

        screen.addButton(
                new CategoryButton(
                        sidebarX,
                        sidebarY + 108,
                        Category.MISC,
                        () -> screen.selectCategory(Category.MISC),
                        () -> screen.getSelectedCategory() == Category.MISC
                )
        );

        screen.addButton(
                Button.builder(
                        Component.literal("Settings"),
                        button -> screen.openIntegrations()
                ).bounds(sidebarX, sidebarY + 144, 130, 22).build()
        );

        /*
         * Feature panel
         */

        FeaturePanel panel = new FeaturePanel(
                featureX,
                featureY
        );

        for (Feature feature : FeatureRegistry.FEATURES) {
            panel.addFeature(feature);
        }

        screen.setFeaturePanel(panel);

        /*
         * Features
         */

        int y = featureY;

        for (Feature feature : panel.getFeatures()) {

            if (feature.getCategory() != screen.getSelectedCategory()) {
                continue;
            }

            final int currentY = y;

            FeatureButton featureButton = new FeatureButton(
                    screen,
                    panel.getX(),
                    currentY, // ✅ use the current feature's Y
                    feature,
                    () -> {
                        feature.toggle();
                        feature.saveConfig();
                        DropperUtilsConfig.save();
                    },
                    feature::isEnabled
            );

            screen.addButton(featureButton);

            /*
             * Settings
             */

            screen.addButton(
                    new SettingsButton(
                            featureX + 155,
                            currentY,
                            () -> screen.openSettings(
                                    feature,
                                    featureX + 175,
                                    currentY
                            )
                    )
            );

            y += 36;
        }
    }
}
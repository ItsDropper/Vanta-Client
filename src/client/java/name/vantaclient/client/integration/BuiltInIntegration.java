package name.vantaclient.client.integration;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.api.setting.SettingDefinition;
import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;

/** Exposes the original built-in feature set through the same API used by addons. */
public final class BuiltInIntegration {

    private BuiltInIntegration() {
    }

    public static void register() {
        ModDescriptor.Builder descriptor = ModDescriptor.builder(
                "dropperutils",
                "Vanta Client"
        ).description("Built-in Vanta Client features");

        for (Feature feature : FeatureRegistry.FEATURES) {
            descriptor.setting(SettingDefinition.bool(
                    settingId(feature),
                    feature.getName(),
                    feature.getDescription(),
                    feature.isEnabled(),
                    feature::isEnabled,
                    enabled -> {
                        feature.setEnabled(enabled);
                        feature.saveConfig();
                        DropperUtilsConfig.save();
                    }
            ));
        }

        VantaClientApi.registerMod(descriptor.build());
    }

    private static String settingId(Feature feature) {
        return feature.getName()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}

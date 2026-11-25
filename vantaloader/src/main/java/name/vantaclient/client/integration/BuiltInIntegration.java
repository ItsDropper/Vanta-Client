package name.vantaclient.client.integration;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.api.setting.SettingDefinition;
import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Exposes the original built-in feature set through the same API used by addons. */
public final class BuiltInIntegration {

    private BuiltInIntegration() {
    }

    public static void register() {
        ModDescriptor.Builder descriptor = ModDescriptor.builder(
                "dropperutils",
                "Vanta Client"
        ).description("Built-in Vanta Client features");

        Set<String> settingIds = new HashSet<>();
        for (Feature feature : FeatureRegistry.FEATURES) {
            descriptor.setting(SettingDefinition.bool(
                    settingId(feature, settingIds),
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

    private static String settingId(Feature feature, Set<String> usedIds) {
        String base = feature.getName()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "feature";
        }

        String candidate = base;
        int suffix = 2;
        while (!usedIds.add(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }
}

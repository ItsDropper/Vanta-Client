package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.FarTerrainRenderer;
import name.vantaclient.client.util.FarViewController;
import name.vantaclient.client.platform.ClientPlatform;

import java.util.Map;
import java.util.stream.Collectors;

/** Adds local-only Vanta context to Minecraft crash reports. */
public final class CrashPatchFeature extends Feature {
    public static final CrashPatchFeature INSTANCE = new CrashPatchFeature();

    private CrashPatchFeature() {
        super("CrashPatch Diagnostics",
                "Adds Vanta runtime and feature diagnostics to local crash reports. No data is uploaded.",
                Category.MISC);
    }

    public static Map<String, String> collectDiagnostics() {
        return Map.of(
                "Vanta Client Version", safe(CrashPatchFeature::getVersion),
                "Vanta Enabled Features", safe(() -> FeatureRegistry.FEATURES.stream()
                        .filter(Feature::isEnabled)
                        .map(Feature::getName)
                        .sorted()
                        .collect(Collectors.joining(", "))),
                "Vanta FarView Controller", safe(FarViewController::getRendererStatus),
                "Vanta FarView Renderer", safe(FarTerrainRenderer::getStatusLine),
                "Vanta Runtime Memory", safe(() -> {
                    Runtime runtime = Runtime.getRuntime();
                    long used = runtime.totalMemory() - runtime.freeMemory();
                    return formatMiB(used) + " used / " + formatMiB(runtime.maxMemory()) + " max";
                }),
                "Vanta Runtime Processors", safe(() -> Integer.toString(
                        Runtime.getRuntime().availableProcessors())),
                "Vanta Loaded Mod Count", safe(() -> Integer.toString(
                        ClientPlatform.isFabric() ? "available" : "not exposed by VantaLoader"))
        );
    }

    private static String getVersion() {
        return ClientPlatform.clientVersion();
    }

    private static String safe(java.util.function.Supplier<String> supplier) {
        try {
            String value = supplier.get();
            return value == null || value.isBlank() ? "unavailable" : value;
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private static String formatMiB(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / 1048576.0);
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().crashPatchDiagnostics = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().crashPatchDiagnostics = false;
        DropperUtilsConfig.save();
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().crashPatchDiagnostics = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().crashPatchDiagnostics);
    }
}

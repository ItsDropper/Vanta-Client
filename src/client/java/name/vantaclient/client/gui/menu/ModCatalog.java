package name.vantaclient.client.gui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.client.feature.AnchorOptimizerFeature;
import name.vantaclient.client.feature.ChatTimestampsFeature;
import name.vantaclient.client.feature.CrashPatchFeature;
import name.vantaclient.client.feature.FarViewFeature;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.NoExplosionEffectsFeature;
import name.vantaclient.client.feature.OgAnimationsFeature;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModConfigDiscovery;
import name.vantaclient.client.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;

/**
 * Collects everything the Mods page lists: Vanta's own features, mods that registered
 * settings through the Vanta API and every mod JAR found in the mods folder.
 *
 * The JAR scan runs off the render thread; icon textures are decoded and registered on the
 * client thread once the immutable inventory arrives.
 */
public final class ModCatalog {
    /** A registered mod icon and its pixel size, needed to blit the whole image. */
    public record IconTexture(Identifier id, int width, int height) {}

    private final Minecraft minecraft;
    private final Map<String, IconTexture> icons = new HashMap<>();
    private List<ExperimentalModHotLoader.AvailableMod> installed = List.of();
    private List<ModEntry> entries;
    private boolean scanning;
    private boolean released;

    public ModCatalog(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.entries = buildEntries(List.of(), Map.of());
    }

    public List<ModEntry> entries() { return entries; }

    public boolean isScanning() { return scanning; }

    public IconTexture icon(String modId) { return icons.get(modId); }

    public ModEntry find(String id) {
        if (id == null) return null;
        for (ModEntry entry : entries) {
            if (entry.id().equals(id)) return entry;
        }
        return null;
    }

    /** Rescans the mods folder asynchronously and calls {@code onChanged} on the client thread. */
    public void refresh(Runnable onChanged) {
        if (scanning || released) return;
        if (!ClientPlatform.isFabric()) {
            entries = buildEntries(List.of(), Map.of());
            return;
        }
        scanning = true;
        CompletableFuture.supplyAsync(() -> {
            List<ExperimentalModHotLoader.AvailableMod> mods = ExperimentalModHotLoader.getAvailableMods();
            Map<String, Object> factories = ModConfigDiscovery.discover();
            return new Object[] {mods, factories};
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            scanning = false;
            if (released) return;
            if (error == null && result != null) {
                @SuppressWarnings("unchecked")
                List<ExperimentalModHotLoader.AvailableMod> mods =
                        (List<ExperimentalModHotLoader.AvailableMod>) result[0];
                @SuppressWarnings("unchecked")
                Map<String, Object> factories = (Map<String, Object>) result[1];
                installed = List.copyOf(mods);
                entries = buildEntries(installed, factories);
                reloadIcons();
            } else if (error != null) {
                System.err.println("[Vanta Client] Could not scan the mods folder: " + error.getMessage());
            }
            if (onChanged != null) onChanged.run();
        }));
    }

    /** Releases icon textures. Call from the screen's removed() hook. */
    public void release() {
        released = true;
        releaseIcons();
    }

    /** Re-registers icons after a child screen released the GPU textures. */
    public void restoreIcons() {
        released = false;
        if (icons.isEmpty() && !installed.isEmpty()) reloadIcons();
    }

    private void releaseIcons() {
        for (IconTexture icon : icons.values()) {
            minecraft.getTextureManager().release(icon.id());
        }
        icons.clear();
    }

    private void reloadIcons() {
        releaseIcons();
        for (ExperimentalModHotLoader.AvailableMod mod : installed) {
            loadIcon(mod.id(), mod.path(), mod.iconPath());
        }
    }

    private void loadIcon(String modId, Path jarPath, String iconPath) {
        if (iconPath == null || iconPath.isBlank() || iconPath.startsWith("/")
                || iconPath.contains("..") || iconPath.contains("\\")) return;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(iconPath);
            if (entry == null || entry.getSize() <= 0 || entry.getSize() > 1024 * 1024) return;
            byte[] bytes;
            try (InputStream input = jar.getInputStream(entry)) {
                bytes = input.readNBytes(1024 * 1024 + 1);
            }
            if (bytes.length < 24 || bytes.length > 1024 * 1024
                    || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                    || bytes[2] != 0x4E || bytes[3] != 0x47
                    || bytes[4] != 0x0D || bytes[5] != 0x0A
                    || bytes[6] != 0x1A || bytes[7] != 0x0A) return;
            int declaredWidth = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
                    | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
            int declaredHeight = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16)
                    | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
            if (declaredWidth <= 0 || declaredHeight <= 0 || declaredWidth > 512 || declaredHeight > 512) return;
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            int width = image.getWidth();
            int height = image.getHeight();
            if (width > 512 || height > 512 || width <= 0 || height <= 0) {
                image.close();
                return;
            }
            Identifier texture = Identifier.fromNamespaceAndPath("vantaclient", "menu_icons/" + modId);
            minecraft.getTextureManager().register(texture,
                    new DynamicTexture(() -> "Vanta menu icon " + modId, image));
            icons.put(modId, new IconTexture(texture, width, height));
        } catch (Exception | LinkageError ignored) {
            // Missing, malformed or oversized optional icons fall back to the mod's initial.
        }
    }

    // ---- entry construction ------------------------------------------------------------------

    private static List<ModEntry> buildEntries(
            List<ExperimentalModHotLoader.AvailableMod> installedMods, Map<String, Object> factories) {
        List<ModEntry> result = new ArrayList<>();
        for (Feature feature : FeatureRegistry.FEATURES) {
            if (feature == FarViewFeature.INSTANCE || feature instanceof HudEditorFeature) continue;
            result.add(new ModEntry(builtInId(feature), feature.getName(), feature.getDescription(),
                    "", groupOf(feature), feature, null, null, null, null));
        }

        Map<String, ModDescriptor> descriptors = new HashMap<>();
        for (ModDescriptor descriptor : VantaClientApi.getRegisteredMods()) {
            descriptors.put(descriptor.id(), descriptor);
        }
        List<String> listed = new ArrayList<>();
        for (ExperimentalModHotLoader.AvailableMod mod : installedMods) {
            ModDescriptor descriptor = descriptors.get(mod.id());
            String description = descriptor == null ? "" : descriptor.description();
            result.add(new ModEntry(mod.id(), mod.name(), description, mod.version(),
                    ModEntry.Group.OTHER, null, descriptor, factories.get(mod.id()),
                    mod.path(), mod.iconPath()));
            listed.add(mod.id());
        }
        for (ModDescriptor descriptor : descriptors.values()) {
            if (listed.contains(descriptor.id())) continue;
            result.add(new ModEntry(descriptor.id(), descriptor.displayName(), descriptor.description(),
                    descriptor.version(), ModEntry.Group.OTHER, null, descriptor,
                    factories.get(descriptor.id()), null, null));
        }
        result.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(result);
    }

    public static String builtInId(Feature feature) {
        return "vanta:" + feature.getClass().getSimpleName()
                .replaceFirst("Feature$", "").toLowerCase(Locale.ROOT);
    }

    private static ModEntry.Group groupOf(Feature feature) {
        if (feature instanceof PerformanceOptimizerFeature || feature instanceof CrashPatchFeature
                || feature instanceof AnchorOptimizerFeature || feature instanceof NoExplosionEffectsFeature) {
            return ModEntry.Group.PERFORMANCE;
        }
        if (feature instanceof ChatTimestampsFeature || feature instanceof OgAnimationsFeature) {
            return ModEntry.Group.QUALITY_OF_LIFE;
        }
        return switch (feature.getCategory()) {
            case HUD -> ModEntry.Group.HUD;
            case VISUAL -> ModEntry.Group.QUALITY_OF_LIFE;
            case COMBAT, MISC -> ModEntry.Group.UTILITY;
        };
    }
}

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
    private record DecodedIcon(String modId, NativeImage image, int width, int height) {}

    private final Minecraft minecraft;
    private final Map<String, IconTexture> icons = new HashMap<>();
    private Map<String, byte[]> iconBytes = Map.of();
    private int iconLoadGeneration;
    private List<ExperimentalModHotLoader.AvailableMod> installed = List.of();
    private List<ModEntry> entries;
    private Map<String, ModEntry> entriesById;
    private boolean scanning;
    private boolean released;

    public ModCatalog(Minecraft minecraft) {
        this.minecraft = minecraft;
        setEntries(buildEntries(List.of(), Map.of()));
    }

    public List<ModEntry> entries() { return entries; }

    public boolean isScanning() { return scanning; }

    public IconTexture icon(String modId) { return icons.get(modId); }

    public ModEntry find(String id) {
        return id == null ? null : entriesById.get(id);
    }

    private void setEntries(List<ModEntry> nextEntries) {
        Map<String, ModEntry> index = new HashMap<>(nextEntries.size());
        for (ModEntry entry : nextEntries) index.putIfAbsent(entry.id(), entry);
        entries = nextEntries;
        entriesById = Map.copyOf(index);
    }

    /** Rescans the mods folder asynchronously and calls {@code onChanged} on the client thread. */
    public void refresh(Runnable onChanged) {
        if (scanning || released) return;
        if (!ClientPlatform.isFabric()) {
            setEntries(buildEntries(List.of(), Map.of()));
            return;
        }
        scanning = true;
        CompletableFuture.supplyAsync(() -> {
            List<ExperimentalModHotLoader.AvailableMod> mods = ExperimentalModHotLoader.getAvailableMods();
            Map<String, Object> factories = ModConfigDiscovery.discover();
            Map<String, byte[]> iconData = readIconBytes(mods);
            return new Object[] {mods, factories, iconData};
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
                @SuppressWarnings("unchecked")
                Map<String, byte[]> loadedIcons = (Map<String, byte[]>) result[2];
                iconBytes = Map.copyOf(loadedIcons);
                setEntries(buildEntries(installed, factories));
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
        if (icons.isEmpty() && !iconBytes.isEmpty()) reloadIcons();
    }

    private void releaseIcons() {
        iconLoadGeneration++;
        for (IconTexture icon : icons.values()) {
            minecraft.getTextureManager().release(icon.id());
        }
        icons.clear();
    }

    /**
     * Reads PNG payloads and decodes them away from the render thread. Only texture
     * registration is dispatched back to Minecraft's client thread.
     */
    private void reloadIcons() {
        releaseIcons();
        if (iconBytes.isEmpty() || released) return;
        int generation = iconLoadGeneration;
        Map<String, byte[]> source = iconBytes;
        CompletableFuture.supplyAsync(() -> decodeIcons(source)).thenAccept(decoded ->
                minecraft.execute(() -> {
                    if (released || generation != iconLoadGeneration) {
                        closeDecodedIcons(decoded);
                        return;
                    }
                    for (DecodedIcon icon : decoded) {
                        try {
                            Identifier texture = Identifier.fromNamespaceAndPath(
                                    "vantaclient", "menu_icons/" + icon.modId());
                            minecraft.getTextureManager().register(texture,
                                    new DynamicTexture(() -> "Vanta menu icon " + icon.modId(), icon.image()));
                            icons.put(icon.modId(), new IconTexture(texture, icon.width(), icon.height()));
                        } catch (Exception | LinkageError error) {
                            icon.image().close();
                        }
                    }
                }));
    }

    private static Map<String, byte[]> readIconBytes(List<ExperimentalModHotLoader.AvailableMod> mods) {
        Map<String, byte[]> result = new HashMap<>();
        for (ExperimentalModHotLoader.AvailableMod mod : mods) {
            String iconPath = mod.iconPath();
            if (iconPath == null || iconPath.isBlank() || iconPath.startsWith("/")
                    || iconPath.contains("..") || iconPath.contains("\\\\")) continue;
            try (JarFile jar = new JarFile(mod.path().toFile())) {
                var entry = jar.getJarEntry(iconPath);
                if (entry == null || entry.getSize() <= 0 || entry.getSize() > 1024 * 1024) continue;
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    bytes = input.readNBytes(1024 * 1024 + 1);
                }
                if (bytes.length < 24 || bytes.length > 1024 * 1024
                        || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                        || bytes[2] != 0x4E || bytes[3] != 0x47
                        || bytes[4] != 0x0D || bytes[5] != 0x0A
                        || bytes[6] != 0x1A || bytes[7] != 0x0A) continue;
                int width = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
                        | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
                int height = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16)
                        | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
                if (width > 0 && height > 0 && width <= 512 && height <= 512) {
                    result.put(mod.id(), bytes);
                }
            } catch (Exception ignored) {
                // Missing or malformed optional icons use the mod's initial instead.
            }
        }
        return result;
    }

    private static List<DecodedIcon> decodeIcons(Map<String, byte[]> source) {
        List<DecodedIcon> result = new ArrayList<>(source.size());
        for (Map.Entry<String, byte[]> entry : source.entrySet()) {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(entry.getValue()));
                int width = image.getWidth();
                int height = image.getHeight();
                if (width > 0 && height > 0 && width <= 512 && height <= 512) {
                    result.add(new DecodedIcon(entry.getKey(), image, width, height));
                } else {
                    image.close();
                }
            } catch (Exception | LinkageError ignored) {
                // Invalid optional icon; keep the text fallback.
            }
        }
        return result;
    }

    private static void closeDecodedIcons(List<DecodedIcon> decoded) {
        for (DecodedIcon icon : decoded) icon.image().close();
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
        java.util.Set<String> listed = new java.util.HashSet<>();
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
        result.sort(Comparator.comparing(ModEntry::normalizedName));
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

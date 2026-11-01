package name.vantaclient.api;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Stable, Minecraft-independent entry point for third-party integrations. */
public final class VantaClientApi {
    public static final int API_VERSION = 1;
    private static final ConcurrentMap<String, ModDescriptor> MODS = new ConcurrentHashMap<>();
    private VantaClientApi() {}

    /** Registers one integration. Duplicate mod ids are rejected, never overwritten. */
    public static void registerMod(ModDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (MODS.putIfAbsent(descriptor.id(), descriptor) != null)
            throw new IllegalArgumentException(
                    "A Vanta Client integration is already registered for mod id: "
                            + descriptor.id());
    }

    /** Returns an immutable snapshot in deterministic mod-id order. */
    public static List<ModDescriptor> getRegisteredMods() {
        return MODS.values().stream()
                .sorted(java.util.Comparator.comparing(ModDescriptor::id))
                .toList();
    }

    /** Intended for controlled lifecycle cleanup and tests. */
    public static boolean unregisterMod(String modId) {
        return modId != null && !modId.isBlank() && MODS.remove(modId) != null;
    }
}

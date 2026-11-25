package name.vantaclient.api;

import java.util.List;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Public registration entry point for third-party settings and HUD integrations. */
public final class VantaClientApi {
    public static final int API_VERSION = 1;
    private static final ConcurrentMap<String, ModDescriptor> MODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, RegisteredHud> HUDS = new ConcurrentHashMap<>();
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final AtomicLong HUD_REVISION = new AtomicLong();
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

    /**
     * Registers a HUD element for a loaded mod. Element IDs are unique per mod;
     * duplicate registrations fail rather than silently replacing an existing HUD.
     */
    public static void registerHud(String modId, name.vantaclient.api.hud.VantaHudElement element) {
        Objects.requireNonNull(element, "element");
        String owner = requireId(modId, "modId");
        String elementId = requireId(element.id(), "element id");
        if (element.displayName() == null || element.displayName().isBlank()) {
            throw new IllegalArgumentException("HUD display name must not be blank");
        }
        String key = owner + ":" + elementId;
        RegisteredHud registration = new RegisteredHud(owner, element);
        if (HUDS.putIfAbsent(key, registration) != null) {
            throw new IllegalArgumentException("A Vanta HUD is already registered: " + key);
        }
        HUD_REVISION.incrementAndGet();
    }

    /** Removes one previously registered HUD element. */
    public static void unregisterHud(String modId, String elementId) {
        if (HUDS.remove(requireId(modId, "modId") + ":" + requireId(elementId, "elementId")) != null) {
            HUD_REVISION.incrementAndGet();
        }
    }

    /** Removes all HUD elements contributed by a mod. */
    public static void unregisterHuds(String modId) {
        String prefix = requireId(modId, "modId") + ":";
        if (HUDS.keySet().removeIf(key -> key.startsWith(prefix))) {
            HUD_REVISION.incrementAndGet();
        }
    }

    /** Monotonic revision for efficient client-thread synchronization of the HUD registry. */
    public static long getHudRegistryRevision() { return HUD_REVISION.get(); }

    /** Returns an immutable, deterministic snapshot of all registered HUD elements. */
    public static List<RegisteredHud> getRegisteredHuds() {
        return HUDS.values().stream()
                .sorted(java.util.Comparator.comparing(RegisteredHud::modId)
                        .thenComparing(registration -> registration.element().id()))
                .toList();
    }

    private static String requireId(String value, String field) {
        if (value == null || !ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
        return value;
    }
}

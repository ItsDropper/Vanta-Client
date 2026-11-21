package name.vantaclient.client.gui.menu;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.client.feature.Feature;

import java.nio.file.Path;
import java.util.Locale;

/** One tile in the Mods page: a Vanta feature, an API integration or an installed mod JAR. */
public final class ModEntry {
    /** Filter groups shown as chips above the grid (All and Favorites are virtual). */
    public enum Group {
        PERFORMANCE("Performance"),
        HUD("HUD"),
        UTILITY("Utility"),
        QUALITY_OF_LIFE("Quality of Life"),
        OTHER("Other");

        private final String label;
        Group(String label) { this.label = label; }
        public String label() { return label; }
    }

    private final String id;
    private final String name;
    private final String description;
    private final String version;
    private final Group group;
    private final Feature feature;
    private final ModDescriptor descriptor;
    private final Object configFactory;
    private final Path jar;
    private final String iconPath;

    ModEntry(String id, String name, String description, String version, Group group,
             Feature feature, ModDescriptor descriptor, Object configFactory,
             Path jar, String iconPath) {
        this.id = id;
        this.name = name == null || name.isBlank() ? id : name;
        this.description = description == null ? "" : description;
        this.version = version == null ? "" : version;
        this.group = group;
        this.feature = feature;
        this.descriptor = descriptor;
        this.configFactory = configFactory;
        this.jar = jar;
        this.iconPath = iconPath;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String version() { return version; }
    public Group group() { return group; }
    /** The built-in Vanta feature behind this tile, or null for third-party mods. */
    public Feature feature() { return feature; }
    /** Settings contributed through the Vanta API, or null. */
    public ModDescriptor descriptor() { return descriptor; }
    /** Mod Menu config screen factory exposed by the mod itself, or null. */
    public Object configFactory() { return configFactory; }
    public Path jar() { return jar; }
    public String iconPath() { return iconPath; }

    public boolean isBuiltIn() { return feature != null; }

    /**
     * True when the mod's options can be changed directly inside Vanta's own UI
     * (the blue dot): built-in features and mods that registered settings through the Vanta API.
     */
    public boolean configurableInVanta() {
        return feature != null || (descriptor != null && !descriptor.settings().isEmpty());
    }

    /** True when the mod ships its own config screen that Vanta can open for it. */
    public boolean hasOwnConfigScreen() { return configFactory != null; }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return name.toLowerCase(Locale.ROOT).contains(needle)
                || id.toLowerCase(Locale.ROOT).contains(needle)
                || description.toLowerCase(Locale.ROOT).contains(needle);
    }
}

package name.vantaclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/** Persistent presentation preferences for the Vanta Client interface. */
public final class VantaClientPreferences {
    public enum Accent {
        BLUE(0xFF3B82FF),
        VIOLET(0xFF9A7BFF),
        EMERALD(0xFF45D6A0),
        CYAN(0xFF48C9E8);

        private final int color;
        Accent(int color) { this.color = color; }
        public int color() { return color; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("vanta-client.json");
    private static VantaClientPreferences instance;

    public Accent accent = Accent.BLUE;
    public boolean animations = true;
    public boolean compactMode = false;
    public boolean showDescriptions = true;
    public double uiScale = 1.0;
    public double panelOpacity = 0.92;
    public Set<String> favoriteMods = new HashSet<>();

    private VantaClientPreferences() {}

    public static VantaClientPreferences get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.isRegularFile(FILE)) {
                try (var reader = Files.newBufferedReader(FILE)) {
                    instance = GSON.fromJson(reader, VantaClientPreferences.class);
                }
            }
        } catch (Exception exception) {
            System.err.println("[Vanta Client] Could not load interface preferences: " + exception.getMessage());
        }

        if (instance == null) instance = new VantaClientPreferences();
        if (instance.accent == null) instance.accent = Accent.BLUE;
        if (!Double.isFinite(instance.uiScale)) instance.uiScale = 1.0;
        instance.uiScale = Math.max(0.8, Math.min(1.2, instance.uiScale));
        if (!Double.isFinite(instance.panelOpacity)) instance.panelOpacity = 0.92;
        instance.panelOpacity = Math.max(0.72, Math.min(0.98, instance.panelOpacity));
        if (instance.favoriteMods == null) instance.favoriteMods = new HashSet<>();
    }

    public static void save() {
        VantaClientPreferences current = get();
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            try (var writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(current, writer);
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.err.println("[Vanta Client] Could not save interface preferences: " + exception.getMessage());
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }
}

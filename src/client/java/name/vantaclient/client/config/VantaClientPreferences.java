package name.vantaclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    /** Normalized screen position and scale for a movable vanilla HUD component. */
    public static final class HudPosition {
        public float x;
        public float y;
        public float scale = 1.0f;

        public HudPosition() {}

        public HudPosition(float x, float y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("vanta-client.json");
    private static VantaClientPreferences instance;

    public Accent accent = Accent.BLUE;
    public boolean animations = true;
    public boolean compactMode = false;
    public boolean showDescriptions = true;
    public boolean hudSnapToGrid = true;
    public boolean hudShowGrid = true;
    public int hudGridSize = 16;
    public double uiScale = 1.0;
    public double panelOpacity = 0.85;
    /** User-owned HTTPS origin for remote world sharing; authentication tokens are never persisted. */
    public String remoteRelayEndpoint = "";
    /** Restrict WebRTC ICE to TURN relay candidates to avoid exposing the peer's home IP. */
    public boolean remoteMaximumIpPrivacy = false;
    /** Approved Minecraft profile UUIDs, keyed by the normalized save-root path. */
    public Map<String, Set<String>> remoteWorldWhitelists = new HashMap<>();
    public Set<String> favoriteMods = new HashSet<>();
    public Map<String, HudPosition> vanillaHudPositions = new HashMap<>();
    /** Enabled background keys are stored sparsely; absent keys are disabled. */
    public Map<String, Boolean> hudBackgrounds = new HashMap<>();
    /** Merge backgrounds whose hitboxes overlap or are separated by only a few pixels. */
    public boolean connectHudBackgrounds = true;
    /** Maximum empty space bridged between adjacent HUD backgrounds, in GUI pixels. */
    public int hudBackgroundConnectionGap = 4;

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
        if (instance.remoteRelayEndpoint == null || instance.remoteRelayEndpoint.length() > 256) {
            instance.remoteRelayEndpoint = "";
        } else {
            instance.remoteRelayEndpoint = instance.remoteRelayEndpoint.trim();
        }
        if (!Double.isFinite(instance.uiScale)) instance.uiScale = 1.0;
        instance.uiScale = Math.max(0.8, Math.min(1.2, instance.uiScale));
        if (!Double.isFinite(instance.panelOpacity) || Math.abs(instance.panelOpacity - 0.92) < 0.0001) {
            instance.panelOpacity = 0.85;
        }
        // Keep config-load bounds aligned with the Appearance slider (0.72–0.98).
        instance.panelOpacity = Math.max(0.72, Math.min(0.98, instance.panelOpacity));
        if (instance.hudGridSize != 8 && instance.hudGridSize != 16
                && instance.hudGridSize != 24 && instance.hudGridSize != 32) {
            instance.hudGridSize = 16;
        }
        if (instance.remoteWorldWhitelists == null) {
            instance.remoteWorldWhitelists = new HashMap<>();
        }
        instance.remoteWorldWhitelists.entrySet().removeIf(entry ->
                entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        for (Set<String> profileIds : instance.remoteWorldWhitelists.values()) {
            Set<String> normalized = new HashSet<>();
            for (String profileId : profileIds) {
                if (profileId != null && profileId.matches(
                        "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                    normalized.add(profileId.toLowerCase(java.util.Locale.ROOT));
                }
            }
            profileIds.clear();
            profileIds.addAll(normalized);
        }
        if (instance.favoriteMods == null) instance.favoriteMods = new HashSet<>();
        if (instance.vanillaHudPositions == null) instance.vanillaHudPositions = new HashMap<>();
        if (instance.hudBackgrounds == null) instance.hudBackgrounds = new HashMap<>();
        instance.hudBackgrounds.entrySet().removeIf(entry ->
                entry.getKey() == null || entry.getKey().isBlank()
                        || !Boolean.TRUE.equals(entry.getValue()));
        instance.hudBackgroundConnectionGap =
                Math.max(0, Math.min(24, instance.hudBackgroundConnectionGap));
        instance.vanillaHudPositions.entrySet().removeIf(entry -> entry.getValue() == null);
        for (HudPosition position : instance.vanillaHudPositions.values()) {
            if (!Float.isFinite(position.x)) position.x = 0.0f;
            if (!Float.isFinite(position.y)) position.y = 0.0f;
            if (!Float.isFinite(position.scale)) position.scale = 1.0f;
            position.x = Math.max(0.0f, Math.min(1.0f, position.x));
            position.y = Math.max(0.0f, Math.min(1.0f, position.y));
            position.scale = Math.max(0.25f, Math.min(3.0f, position.scale));
        }
    }

    public boolean isHudBackgroundEnabled(String backgroundId) {
        return backgroundId != null && Boolean.TRUE.equals(hudBackgrounds.get(backgroundId));
    }

    public void setHudBackgroundEnabled(String backgroundId, boolean enabled) {
        if (backgroundId == null || backgroundId.isBlank()) return;
        if (enabled) hudBackgrounds.put(backgroundId, true);
        else hudBackgrounds.remove(backgroundId);
        save();
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

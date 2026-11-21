package name.vantaclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.platform.ClientPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Named snapshots of Vanta's feature configuration and interface preferences.
 * Each profile is one JSON file in {@code config/vanta-profiles}.
 */
public final class ProfileManager {
    public record Profile(String id, String name, boolean favorite, long updatedAt) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_NAME = 32;

    private ProfileManager() {}

    public static Path directory() {
        return ClientPlatform.configDirectory().resolve("vanta-profiles");
    }

    public static List<Profile> list() {
        List<Profile> result = new ArrayList<>();
        Path dir = directory();
        if (!Files.isDirectory(dir)) return result;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject json = read(file);
                if (json == null) continue;
                String fileName = file.getFileName().toString();
                String id = fileName.substring(0, fileName.length() - 5);
                String name = json.has("name") ? json.get("name").getAsString() : id;
                boolean favorite = json.has("favorite") && json.get("favorite").getAsBoolean();
                long updated = json.has("updatedAt") ? json.get("updatedAt").getAsLong() : 0L;
                result.add(new Profile(id, name, favorite, updated));
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Vanta Client] Could not list profiles: " + exception.getMessage());
        }
        result.sort(Comparator.comparing((Profile profile) -> profile.name().toLowerCase(Locale.ROOT)));
        return result;
    }

    /** Captures the current state under a new name; returns null if the name is unusable or saving fails. */
    public static Profile create(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME).trim();
        if (name.isEmpty()) return null;
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isEmpty()) base = "profile";
        String id = base;
        int suffix = 2;
        while (Files.exists(directory().resolve(id + ".json"))) id = base + "-" + suffix++;
        if (!write(id, name, false)) return null;
        VantaClientPreferences.get().activeProfile = id;
        VantaClientPreferences.save();
        return new Profile(id, name, false, System.currentTimeMillis());
    }

    /** Overwrites a profile with the current settings. */
    public static boolean update(String id) {
        JsonObject existing = read(directory().resolve(id + ".json"));
        if (existing == null) return false;
        String name = existing.has("name") ? existing.get("name").getAsString() : id;
        boolean favorite = existing.has("favorite") && existing.get("favorite").getAsBoolean();
        boolean written = write(id, name, favorite);
        if (written) {
            VantaClientPreferences.get().activeProfile = id;
            VantaClientPreferences.save();
        }
        return written;
    }

    public static boolean apply(String id) {
        JsonObject json = read(directory().resolve(id + ".json"));
        if (json == null || !json.has("config") || !json.has("preferences")) return false;
        if (!DropperUtilsConfig.restore(json.get("config"))) return false;
        VantaClientPreferences.applyProfileSnapshot(json.getAsJsonObject("preferences"));
        FeatureRegistry.loadConfigs();
        VantaClientPreferences.get().activeProfile = id;
        VantaClientPreferences.save();
        return true;
    }

    public static boolean toggleFavorite(String id) {
        Path file = directory().resolve(id + ".json");
        JsonObject json = read(file);
        if (json == null) return false;
        boolean favorite = !(json.has("favorite") && json.get("favorite").getAsBoolean());
        json.addProperty("favorite", favorite);
        return writeJson(file, json);
    }

    public static boolean delete(String id) {
        try {
            boolean removed = Files.deleteIfExists(directory().resolve(id + ".json"));
            if (removed && id.equals(VantaClientPreferences.get().activeProfile)) {
                VantaClientPreferences.get().activeProfile = "";
                VantaClientPreferences.save();
            }
            return removed;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean write(String id, String name, boolean favorite) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("favorite", favorite);
        json.addProperty("updatedAt", System.currentTimeMillis());
        json.add("config", DropperUtilsConfig.snapshot());
        json.add("preferences", VantaClientPreferences.get().profileSnapshot());
        return writeJson(directory().resolve(id + ".json"), json);
    }

    private static boolean writeJson(Path file, JsonObject json) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(json, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            System.err.println("[Vanta Client] Could not save profile: " + exception.getMessage());
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            return false;
        }
    }

    private static JsonObject read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (var reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }
}

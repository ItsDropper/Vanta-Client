package name.vantaclient.client.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Opt-in, best-effort loader for late-added, simple client-only Fabric JARs.
 *
 * This deliberately does not pretend to integrate a JAR into Fabric Loader:
 * Mixins, access wideners, language adapters, metadata registration, and
 * Fabric-managed dependency resolution are unsupported.
 */
public final class ExperimentalModHotLoader {
    private static final long MAX_JAR_SIZE = 128L * 1024L * 1024L;
    private static final Set<String> ATTEMPTED = new HashSet<>();
    private static final List<URLClassLoader> RETAINED_LOADERS = new ArrayList<>();

    private ExperimentalModHotLoader() {}

    public record Result(int loaded, int skipped, int failed, String summary) {}

    public static synchronized Result scanAndLoad() {
        Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods");
        if (!Files.isDirectory(modsDirectory)) {
            return new Result(0, 0, 0, "Mods directory was not found.");
        }

        int loaded = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();

        try (var paths = Files.list(modsDirectory)) {
            for (Path jar : paths.filter(path -> path.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                String key = jar.toAbsolutePath().normalize().toString();
                if (ATTEMPTED.contains(key)) {
                    skipped++;
                    continue;
                }

                URLClassLoader classLoader = null;
                try {
                    if (!Files.isRegularFile(jar) || Files.size(jar) <= 0
                            || Files.size(jar) > MAX_JAR_SIZE) {
                        skipped++;
                        continue;
                    }

                    JsonObject metadata;
                    try (JarFile archive = new JarFile(jar.toFile())) {
                        var entry = archive.getJarEntry("fabric.mod.json");
                        if (entry == null) {
                            skipped++;
                            continue;
                        }
                        try (InputStream input = archive.getInputStream(entry)) {
                            metadata = JsonParser.parseReader(
                                    new java.io.InputStreamReader(input,
                                            java.nio.charset.StandardCharsets.UTF_8))
                                    .getAsJsonObject();
                        }
                    }

                    String id = string(metadata, "id");
                    if (id.isBlank() || FabricLoader.getInstance().isModLoaded(id)) {
                        skipped++;
                        ATTEMPTED.add(key);
                        continue;
                    }

                    if (!isSupportedMetadata(metadata, id)) {
                        skipped++;
                        details.add(id + ": unsupported Fabric metadata");
                        ATTEMPTED.add(key);
                        continue;
                    }

                    List<String> entrypoints = clientEntrypoints(metadata);
                    if (entrypoints.isEmpty()) {
                        skipped++;
                        details.add(id + ": no simple client entrypoint");
                        ATTEMPTED.add(key);
                        continue;
                    }

                    classLoader = new URLClassLoader(
                            new java.net.URL[]{jar.toUri().toURL()},
                            ExperimentalModHotLoader.class.getClassLoader());
                    boolean initialized = false;
                    for (String entrypoint : entrypoints) {
                        Class<?> type = Class.forName(entrypoint, true, classLoader);
                        if (!ClientModInitializer.class.isAssignableFrom(type)) {
                            throw new IllegalArgumentException(
                                    entrypoint + " does not implement ClientModInitializer");
                        }
                        var constructor = type.getDeclaredConstructor();
                        if (!java.lang.reflect.Modifier.isPublic(constructor.getModifiers())
                                || !java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
                            throw new IllegalArgumentException(
                                    entrypoint + " must have a public no-argument constructor");
                        }
                        ClientModInitializer initializer =
                                (ClientModInitializer) constructor.newInstance();
                        initializer.onInitializeClient();
                        initialized = true;
                    }

                    if (initialized) {
                        RETAINED_LOADERS.add(classLoader);
                        loaded++;
                        details.add(id + ": client entrypoint invoked (best effort)");
                    } else {
                        classLoader.close();
                        skipped++;
                    }
                    ATTEMPTED.add(key);
                } catch (Exception | LinkageError throwable) {
                    if (classLoader != null) {
                        try { classLoader.close(); } catch (IOException ignored) {}
                    }
                    failed++;
                    ATTEMPTED.add(key);
                    details.add(jar.getFileName() + ": "
                            + throwable.getClass().getSimpleName() + " — "
                            + safeMessage(throwable));
                }
            }
        } catch (IOException exception) {
            failed++;
            details.add("Could not scan mods directory: " + safeMessage(exception));
        }

        String summary = "Attempted new JARs: " + loaded + " initialized, "
                + skipped + " skipped, " + failed + " failed.";
        if (!details.isEmpty()) {
            summary += " " + String.join(" | ", details.stream().limit(3).toList());
        }
        return new Result(loaded, skipped, failed, summary);
    }

    private static boolean isSupportedMetadata(JsonObject metadata, String ownId) {
        if (metadata.has("mixins") || metadata.has("accessWidener")
                || metadata.has("languageAdapters")) return false;

        String environment = string(metadata, "environment");
        if (!environment.isBlank() && !environment.equals("*")
                && !environment.equals("client")) return false;

        JsonElement dependsElement = metadata.get("depends");
        if (dependsElement != null && dependsElement.isJsonObject()) {
            for (var dependency : dependsElement.getAsJsonObject().entrySet()) {
                String dependencyId = dependency.getKey();
                if (!dependencyId.equals(ownId)
                        && !FabricLoader.getInstance().isModLoaded(dependencyId)
                        && !dependencyId.equals("minecraft")) return false;
            }
        }
        return true;
    }

    private static List<String> clientEntrypoints(JsonObject metadata) {
        JsonElement entrypointsElement = metadata.get("entrypoints");
        if (entrypointsElement == null || !entrypointsElement.isJsonObject()) return List.of();
        JsonElement client = entrypointsElement.getAsJsonObject().get("client");
        if (client == null || !client.isJsonArray()) return List.of();

        List<String> result = new ArrayList<>();
        for (JsonElement item : client.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                return List.of(); // Fabric adapters/object declarations need Loader support.
            }
            result.add(item.getAsString());
        }
        return List.copyOf(result);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) return "no diagnostic message";
        return message.length() > 140 ? message.substring(0, 140) : message;
    }
}

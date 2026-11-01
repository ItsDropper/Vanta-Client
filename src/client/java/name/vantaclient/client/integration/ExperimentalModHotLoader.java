package name.vantaclient.client.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Opt-in, best-effort loader for late-added, simple client-only Fabric JARs.
 *
 * This is deliberately not a replacement for Fabric Loader. It cannot apply
 * Mixins/access wideners, register Fabric mod containers, or reproduce Loader's
 * complete dependency, entrypoint-adapter, and resource semantics.
 */
public final class ExperimentalModHotLoader {
    private static final long MAX_JAR_SIZE = 128L * 1024L * 1024L;
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final Set<String> ATTEMPTED = new HashSet<>();
    private static final Set<String> ATTEMPTED_MOD_IDS = new HashSet<>();
    private static final Map<String, HotLoadedMod> HOT_LOADED_MODS = new LinkedHashMap<>();
    private static final List<URLClassLoader> RETAINED_LOADERS = new ArrayList<>();
    private static final Set<String> BUILT_IN_DEPENDENCIES =
            Set.of("minecraft", "fabricloader", "java");

    private ExperimentalModHotLoader() {}

    public record Result(int loaded, int skipped, int failed, String summary) {}
    public record HotLoadedMod(String id, String name, String version, String jarName) {}

    private record Candidate(
            String id, String name, String version, Path path, String identity,
            List<String> entrypoints, Set<String> dependencies) {}

    public static synchronized List<HotLoadedMod> getHotLoadedMods() {
        return List.copyOf(HOT_LOADED_MODS.values());
    }

    public static synchronized Result scanAndLoad() {
        Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods");
        if (!Files.isDirectory(modsDirectory)) {
            return new Result(0, 0, 0, "Mods directory was not found.");
        }

        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Set<String> duplicateIds = new HashSet<>();

        try (var paths = Files.list(modsDirectory)) {
            for (Path jar : paths
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList()) {
                String identity;
                try {
                    if (!Files.isRegularFile(jar)) {
                        skipped++;
                        continue;
                    }
                    long size = Files.size(jar);
                    if (size <= 0 || size > MAX_JAR_SIZE) {
                        skipped++;
                        details.add(jar.getFileName() + ": JAR size is outside the supported limit");
                        continue;
                    }
                    identity = jar.toAbsolutePath().normalize() + ":" + size + ":"
                            + Files.getLastModifiedTime(jar).toMillis();
                } catch (IOException exception) {
                    failed++;
                    details.add(jar.getFileName() + ": " + safeMessage(exception));
                    continue;
                }

                if (ATTEMPTED.contains(identity)) {
                    skipped++;
                    continue;
                }

                try {
                    JsonObject metadata = readMetadata(jar);
                    if (metadata == null) {
                        skipped++;
                        ATTEMPTED.add(identity);
                        continue;
                    }

                    String id = string(metadata, "id");
                    if (!id.matches("[a-z0-9][a-z0-9_.-]{0,63}")) {
                        skipped++;
                        details.add(jar.getFileName() + ": invalid or missing mod id");
                        ATTEMPTED.add(identity);
                        continue;
                    }
                    if (FabricLoader.getInstance().isModLoaded(id)
                            || HOT_LOADED_MODS.containsKey(id) || ATTEMPTED_MOD_IDS.contains(id)) {
                        skipped++;
                        ATTEMPTED.add(identity);
                        continue;
                    }

                    String unsupported = unsupportedReason(metadata);
                    if (unsupported != null) {
                        skipped++;
                        details.add(id + ": " + unsupported);
                        ATTEMPTED.add(identity);
                        continue;
                    }

                    List<String> entrypoints = clientEntrypoints(metadata);
                    if (entrypoints.isEmpty()) {
                        skipped++;
                        details.add(id + ": no supported string-based client entrypoint");
                        ATTEMPTED.add(identity);
                        continue;
                    }

                    if (duplicateIds.contains(id)) {
                        skipped++;
                        ATTEMPTED.add(identity);
                        continue;
                    }
                    String name = string(metadata, "name");
                    String version = string(metadata, "version");
                    Candidate candidate = new Candidate(id, name.isBlank() ? id : name,
                            version, jar, identity, entrypoints, requiredDependencies(metadata));
                    Candidate previous = candidates.putIfAbsent(id, candidate);
                    if (previous != null) {
                        candidates.remove(id);
                        duplicateIds.add(id);
                        ATTEMPTED.add(previous.identity());
                        ATTEMPTED.add(identity);
                        skipped += 2;
                        details.add(id + ": multiple JARs declare this mod id; both were skipped");
                    }
                } catch (Exception | LinkageError exception) {
                    failed++;
                    ATTEMPTED.add(identity);
                    details.add(jar.getFileName() + ": " + safeMessage(exception));
                }
            }
        } catch (IOException exception) {
            failed++;
            details.add("Could not scan mods directory: " + safeMessage(exception));
            return result(0, skipped, failed, details);
        }

        Set<String> loadedByFabric = new HashSet<>();
        FabricLoader.getInstance().getAllMods()
                .forEach(mod -> loadedByFabric.add(mod.getMetadata().getId()));
        loadedByFabric.addAll(HOT_LOADED_MODS.keySet());

        // Remove candidates with unavailable required dependencies, then order the rest.
        boolean changed;
        do {
            changed = false;
            var iterator = candidates.entrySet().iterator();
            while (iterator.hasNext()) {
                Candidate candidate = iterator.next().getValue();
                String missing = candidate.dependencies().stream()
                        .filter(dependency -> !BUILT_IN_DEPENDENCIES.contains(dependency)
                                && !loadedByFabric.contains(dependency)
                                && !candidates.containsKey(dependency))
                        .findFirst().orElse(null);
                if (missing != null) {
                    iterator.remove();
                    skipped++;
                    details.add(candidate.id() + ": required dependency '" + missing
                            + "' is not loaded or available for this batch");
                    changed = true;
                }
            }
        } while (changed);

        List<Candidate> ordered = dependencyOrder(candidates, loadedByFabric);
        Set<String> orderedIds = new HashSet<>();
        ordered.forEach(candidate -> orderedIds.add(candidate.id()));
        for (Candidate candidate : candidates.values()) {
            if (!orderedIds.contains(candidate.id())) {
                skipped++;
                ATTEMPTED.add(candidate.identity());
                details.add(candidate.id() + ": dependency cycle detected");
            }
        }
        if (ordered.isEmpty()) return result(0, skipped, failed, details);

        URLClassLoader classLoader = null;
        boolean initializerInvoked = false;
        int loaded = 0;
        try {
            URL[] urls = new URL[ordered.size()];
            for (int i = 0; i < ordered.size(); i++) {
                urls[i] = ordered.get(i).path().toUri().toURL();
            }
            // One shared loader per scan batch allows candidate mods to share classes.
            ClassLoader parent = RETAINED_LOADERS.isEmpty()
                    ? ExperimentalModHotLoader.class.getClassLoader()
                    : RETAINED_LOADERS.get(RETAINED_LOADERS.size() - 1);
            classLoader = new URLClassLoader(urls, parent);

            Set<String> successfullyInitialized = new HashSet<>();
            for (Candidate candidate : ordered) {
                String failedDependency = candidate.dependencies().stream()
                        .filter(dependency -> candidates.containsKey(dependency)
                                && !successfullyInitialized.contains(dependency))
                        .findFirst().orElse(null);
                if (failedDependency != null) {
                    skipped++;
                    ATTEMPTED.add(candidate.identity());
                    details.add(candidate.id() + ": dependency '" + failedDependency
                            + "' did not initialize successfully");
                    continue;
                }

                ATTEMPTED_MOD_IDS.add(candidate.id());
                boolean modSucceeded = true;
                for (String entrypoint : candidate.entrypoints()) {
                    try {
                        Class<?> type = Class.forName(entrypoint, false, classLoader);
                        if (!ClientModInitializer.class.isAssignableFrom(type)) {
                            throw new IllegalArgumentException(
                                    entrypoint + " does not implement ClientModInitializer");
                        }
                        if (!Modifier.isPublic(type.getModifiers())) {
                            throw new IllegalArgumentException(entrypoint + " is not public");
                        }
                        var constructor = type.getConstructor();
                        if (!Modifier.isPublic(constructor.getModifiers())) {
                            throw new IllegalArgumentException(
                                    entrypoint + " has no public no-argument constructor");
                        }
                        ClientModInitializer initializer =
                                (ClientModInitializer) constructor.newInstance();
                        initializerInvoked = true;
                        initializer.onInitializeClient();
                    } catch (InvocationTargetException exception) {
                        Throwable cause = exception.getCause() == null
                                ? exception : exception.getCause();
                        details.add(candidate.id() + ": entrypoint failed — " + safeMessage(cause));
                        modSucceeded = false;
                        break;
                    } catch (Exception | LinkageError exception) {
                        details.add(candidate.id() + ": entrypoint failed — "
                                + safeMessage(exception));
                        modSucceeded = false;
                        break;
                    }
                }

                ATTEMPTED.add(candidate.identity());
                if (modSucceeded) {
                    successfullyInitialized.add(candidate.id());
                    HOT_LOADED_MODS.put(candidate.id(), new HotLoadedMod(candidate.id(),
                            candidate.name(), candidate.version(),
                            candidate.path().getFileName().toString()));
                    loaded++;
                    details.add(candidate.id() + ": client entrypoint(s) invoked");
                } else {
                    failed++;
                }
            }
        } catch (Exception | LinkageError exception) {
            failed++;
            details.add("Hot-load batch failed: " + safeMessage(exception));
        } finally {
            if (classLoader != null) {
                if (initializerInvoked) {
                    // Closing a loader after arbitrary initializer code ran can break
                    // resources that the mod expects to access later.
                    RETAINED_LOADERS.add(classLoader);
                } else {
                    try {
                        classLoader.close();
                    } catch (IOException ignored) {
                        // No classes were initialized by this batch.
                    }
                }
            }
        }

        return result(loaded, skipped, failed, details);
    }

    private static JsonObject readMetadata(Path jar) throws IOException {
        try (JarFile archive = new JarFile(jar.toFile())) {
            var entry = archive.getJarEntry("fabric.mod.json");
            if (entry == null) return null;
            try (InputStream input = archive.getInputStream(entry)) {
                byte[] bytes = input.readNBytes(MAX_METADATA_BYTES + 1);
                if (bytes.length > MAX_METADATA_BYTES) {
                    throw new IOException("fabric.mod.json exceeds 1 MiB");
                }
                JsonElement parsed = JsonParser.parseString(
                        new String(bytes, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) throw new IOException("fabric.mod.json is not an object");
                return parsed.getAsJsonObject();
            }
        }
    }

    private static String unsupportedReason(JsonObject metadata) {
        if (hasNonEmpty(metadata.get("mixins"))) return "declares Mixins, which cannot be applied after startup";
        if (hasNonBlankString(metadata.get("accessWidener"))) {
            return "declares an access widener, which cannot be applied after startup";
        }
        if (hasNonEmpty(metadata.get("languageAdapters"))) return "declares custom language adapters";
        if (hasNonEmpty(metadata.get("jars"))) return "contains nested JAR declarations";
        String environment = string(metadata, "environment");
        if (!environment.isBlank() && !environment.equals("*") && !environment.equals("client")) {
            return "is not declared client-compatible";
        }

        JsonElement entrypoints = metadata.get("entrypoints");
        if (entrypoints != null && entrypoints.isJsonObject()) {
            JsonObject object = entrypoints.getAsJsonObject();
            if (hasNonEmpty(object.get("main")) || hasNonEmpty(object.get("server"))) {
                return "requires common or server entrypoints that cannot be safely initialized late";
            }
        }
        return null;
    }

    private static List<String> clientEntrypoints(JsonObject metadata) {
        JsonElement allEntrypoints = metadata.get("entrypoints");
        if (allEntrypoints == null || !allEntrypoints.isJsonObject()) return List.of();
        JsonElement client = allEntrypoints.getAsJsonObject().get("client");
        if (client == null || !client.isJsonArray() || client.getAsJsonArray().isEmpty()) {
            return List.of();
        }

        // Accept Fabric's plain Java entrypoint form, including the equivalent
        // object form when it explicitly selects the default language adapter.
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement item : client.getAsJsonArray()) {
            String value;
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                value = item.getAsString();
            } else if (item.isJsonObject()) {
                JsonObject declaration = item.getAsJsonObject();
                value = string(declaration, "value");
                String adapter = string(declaration, "adapter");
                if (!adapter.isBlank() && !adapter.equals("default")) return List.of();
            } else {
                return List.of();
            }
            if (value.isBlank()) return List.of();
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static Set<String> requiredDependencies(JsonObject metadata) {
        JsonElement depends = metadata.get("depends");
        if (depends == null || !depends.isJsonObject()) return Set.of();
        return new LinkedHashSet<>(depends.getAsJsonObject().keySet());
    }

    private static List<Candidate> dependencyOrder(
            Map<String, Candidate> candidates, Set<String> loadedByFabric) {
        List<Candidate> result = new ArrayList<>();
        Set<String> emitted = new HashSet<>(loadedByFabric);
        emitted.addAll(BUILT_IN_DEPENDENCIES);
        Set<String> remaining = new LinkedHashSet<>(candidates.keySet());

        while (!remaining.isEmpty()) {
            boolean progressed = false;
            for (String id : List.copyOf(remaining)) {
                Candidate candidate = candidates.get(id);
                if (emitted.containsAll(candidate.dependencies())) {
                    result.add(candidate);
                    emitted.add(id);
                    remaining.remove(id);
                    progressed = true;
                }
            }
            if (!progressed) break;
        }
        return result;
    }

    private static boolean hasNonEmpty(JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (value.isJsonArray()) return !value.getAsJsonArray().isEmpty();
        if (value.isJsonObject()) return !value.getAsJsonObject().entrySet().isEmpty();
        return value.isJsonPrimitive() && !value.getAsString().isBlank();
    }

    private static boolean hasNonBlankString(JsonElement value) {
        return value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static Result result(int loaded, int skipped, int failed, List<String> details) {
        String summary = "Attempted new JARs: " + loaded + " initialized, "
                + skipped + " skipped, " + failed + " failed.";
        if (!details.isEmpty()) {
            summary += " " + String.join(" | ", details.stream().limit(10).toList());
        }
        return new Result(loaded, skipped, failed, summary);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) return "no diagnostic message";
        return message.length() > 140 ? message.substring(0, 140) : message;
    }
}

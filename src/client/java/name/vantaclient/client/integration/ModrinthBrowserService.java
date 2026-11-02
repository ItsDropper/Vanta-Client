package name.vantaclient.client.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Small client-side Modrinth API client for the in-game library.
 * Installs Fabric-compatible versions into the active game's mods directory.
 */
public final class ModrinthBrowserService {
    private static final String API = "https://api.modrinth.com/v2";
    private static final long MAX_DOWNLOAD_BYTES = 128L * 1024L * 1024L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ModrinthBrowserService() {}

    public record Project(String id, String title, String description,
                          String author, String iconUrl, int downloads) {}
    public record InstallResult(List<Path> installed, String summary) {}

    public static List<Project> search(String query) throws IOException, InterruptedException {
        String minecraft = FabricLoader.getInstance().getModContainer("minecraft")
                .orElseThrow(() -> new IOException("Could not determine Minecraft version."))
                .getMetadata().getVersion().getFriendlyString();
        String encoded = URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8);
        String facets = URLEncoder.encode("[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:"
                + minecraft + "\"]]", StandardCharsets.UTF_8);
        JsonObject root = getJson(API + "/search?query=" + encoded + "&facets=" + facets + "&limit=30&index=relevance");
        JsonArray hits = root.getAsJsonArray("hits");
        List<Project> projects = new ArrayList<>();
        if (hits == null) return List.of();
        for (JsonElement element : hits) {
            if (!element.isJsonObject()) continue;
            JsonObject hit = element.getAsJsonObject();
            String id = string(hit, "project_id");
            if (id.isBlank()) continue;
            projects.add(new Project(id, fallback(string(hit, "title"), id),
                    string(hit, "description"), string(hit, "author"),
                    string(hit, "icon_url"), integer(hit, "downloads")));
        }
        return List.copyOf(projects);
    }

    public static InstallResult install(String projectId) throws IOException, InterruptedException {
        if (projectId == null || !projectId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IOException("Invalid Modrinth project ID.");
        }
        String minecraft = FabricLoader.getInstance().getModContainer("minecraft")
                .orElseThrow(() -> new IOException("Could not determine Minecraft version."))
                .getMetadata().getVersion().getFriendlyString();
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        Files.createDirectories(mods);
        List<Path> installed = new ArrayList<>();
        installRecursive(projectId, minecraft, mods, new HashSet<>(), installed);
        return new InstallResult(List.copyOf(installed),
                "Installed " + installed.size() + " compatible JAR(s). Use Enable* to attempt session loading where supported; otherwise restart Minecraft.");
    }

    private static void installRecursive(String projectId, String minecraft, Path mods,
                                         Set<String> visiting, List<Path> installed)
            throws IOException, InterruptedException {
        if (!visiting.add(projectId)) return;
        try {
            JsonArray versions = getJson(API + "/project/" + projectId + "/version?loaders="
                    + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
                    + "&game_versions=" + URLEncoder.encode("[\"" + minecraft + "\"]", StandardCharsets.UTF_8))
                    .getAsJsonArray("unused");
        } catch (UnsupportedOperationException ignored) {
            // No-op; the actual version response is an array and is parsed below.
        }
        JsonArray versions = getArray(API + "/project/" + projectId + "/version?loaders="
                + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
                + "&game_versions=" + URLEncoder.encode("[\"" + minecraft + "\"]", StandardCharsets.UTF_8));
        if (versions.isEmpty()) throw new IOException("No Fabric version compatible with Minecraft " + minecraft + " for " + projectId);
        JsonObject selected = versions.get(0).getAsJsonObject();
        JsonArray dependencies = selected.getAsJsonArray("dependencies");
        if (dependencies != null) {
            for (JsonElement element : dependencies) {
                if (!element.isJsonObject()) continue;
                JsonObject dependency = element.getAsJsonObject();
                String type = string(dependency, "dependency_type");
                String id = string(dependency, "project_id");
                if ("required".equals(type) && !id.isBlank()) installRecursive(id, minecraft, mods, visiting, installed);
            }
        }
        JsonArray files = selected.getAsJsonArray("files");
        if (files == null || files.isEmpty()) throw new IOException("Modrinth returned no files for " + projectId);
        JsonObject primary = files.get(0).getAsJsonObject();
        for (JsonElement fileElement : files) {
            if (fileElement.isJsonObject() && fileElement.getAsJsonObject().has("primary")
                    && fileElement.getAsJsonObject().get("primary").getAsBoolean()) {
                primary = fileElement.getAsJsonObject();
                break;
            }
        }
        String url = string(primary, "url");
        String filename = string(primary, "filename");
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Modrinth download URL.", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !uri.getHost().equalsIgnoreCase("cdn.modrinth.com")) {
            throw new IOException("Refusing an untrusted Modrinth download host.");
        }
        filename = Path.of(filename).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".jar")) throw new IOException("Selected Modrinth file is not a JAR.");
        Path destination = mods.resolve(filename).normalize();
        if (!destination.getParent().equals(mods.normalize())) throw new IOException("Invalid download filename.");
        if (Files.isRegularFile(destination) && Files.size(destination) > 0) {
            installed.add(destination);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "VantaClient/1.0 (https://github.com/ItsDropper/Vanta-Client)")
                .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("Modrinth download failed with HTTP " + response.statusCode());
        }
        Path temporary = Files.createTempFile(mods, ".vanta-download-", ".part");
        try (InputStream input = response.body(); var output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DOWNLOAD_BYTES) throw new IOException("Mod JAR exceeds the 128 MiB safety limit.");
                output.write(buffer, 0, read);
            }
            if (total == 0) throw new IOException("Modrinth returned an empty file.");
            try (java.util.jar.JarFile ignored = new java.util.jar.JarFile(temporary.toFile())) {
                if (ignored.getJarEntry("fabric.mod.json") == null) throw new IOException("Downloaded file is not a Fabric mod JAR.");
            }
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            installed.add(destination);
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        JsonElement value = requestJson(url);
        if (!value.isJsonObject()) throw new IOException("Unexpected Modrinth API response.");
        return value.getAsJsonObject();
    }

    private static JsonArray getArray(String url) throws IOException, InterruptedException {
        JsonElement value = requestJson(url);
        if (!value.isJsonArray()) throw new IOException("Unexpected Modrinth version response.");
        return value.getAsJsonArray();
    }

    private static JsonElement requestJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", "VantaClient/1.0 (https://github.com/ItsDropper/Vanta-Client)")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Modrinth API returned HTTP " + response.statusCode());
        }
        try { return JsonParser.parseString(response.body()); }
        catch (RuntimeException exception) { throw new IOException("Modrinth returned invalid JSON.", exception); }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
    private static int integer(JsonObject object, String key) {
        try { return object.has(key) ? object.get(key).getAsInt() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }
    private static String fallback(String value, String fallback) { return value.isBlank() ? fallback : value; }
}

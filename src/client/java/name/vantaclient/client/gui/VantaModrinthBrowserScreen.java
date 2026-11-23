package name.vantaclient.client.gui;

import name.vantaclient.client.gui.compose.VantaCompose;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import com.mojang.blaze3d.platform.NativeImage;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModrinthBrowserService;
import name.vantaclient.client.platform.ClientPlatform;
import name.vantaclient.client.gui.menu.MenuComponents;
import name.vantaclient.client.gui.menu.MenuIcons;
import name.vantaclient.client.gui.menu.MenuTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Modrinth library rendered entirely through Vanta's retained UI framework.
 * Network and disk work remain off the render thread; texture ownership is scoped to this screen.
 */
public final class VantaModrinthBrowserScreen extends Screen implements VantaCompose.Screen {
    private static final int PAGE_SIZE = 6;
    private static final int WIDE_LAYOUT_THRESHOLD = 760;
    private static final int ICON_MAX_BYTES = 512 * 1024;
    private static final int ICON_MAX_DIMENSION = 512;

    private final Screen parent;
    /** Optional deterministic data source used by the real-client UI screenshot test. */
    private final List<ModrinthBrowserService.Project> initialProjects;
    private final boolean previewBusy;
    private VantaUiRuntime runtime;
    private List<ModrinthBrowserService.Project> projects = List.of();
    private String searchQuery = "";
    private String status = "Search Modrinth for Fabric mods compatible with this Minecraft version.";
    private boolean busy;
    private int page;
    private final Map<String, Identifier> projectIconTextures = new HashMap<>();
    private final Set<String> requestedIcons = new HashSet<>();
    private long iconGeneration;
    private boolean installedView;
    private List<ExperimentalModHotLoader.AvailableMod> installedMods = List.of();
    private Set<String> fabricLoadedIds = Set.of();
    private Set<String> sessionLoadedIds = Set.of();
    private String enableModId;
    private boolean closed;
    private long operationGeneration;

    public VantaModrinthBrowserScreen(Screen parent) {
        this(parent, null);
    }

    VantaModrinthBrowserScreen(Screen parent,
                               List<ModrinthBrowserService.Project> initialProjects) {
        this(parent, initialProjects, false);
    }

    VantaModrinthBrowserScreen(Screen parent,
                               List<ModrinthBrowserService.Project> initialProjects,
                               boolean previewBusy) {
        super(Component.literal("Vanta Modrinth Library"));
        this.parent = parent;
        this.initialProjects = initialProjects == null ? null : List.copyOf(initialProjects);
        this.previewBusy = previewBusy;
    }

    @Override
    protected void init() {
        closed = false;
        if (initialProjects == null) {
            rebuildUi();
            search();
        } else {
            projects = initialProjects;
            busy = previewBusy;
            status = previewBusy
                    ? "Installing preview mod and resolving dependencies…"
                    : projects.size() + " local preview projects. Network access is disabled for this UI test.";
            rebuildUi();
        }
    }

    private static final int MAX_WIDTH = 1040;
    private static final int MAX_HEIGHT = 640;

    private float zoom() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        if (!Float.isFinite(guiScale) || guiScale <= 0) guiScale = 1.0f;
        float userScale = (float) VantaClientPreferences.get().uiScale;
        if (!Float.isFinite(userScale) || userScale <= 0) userScale = 1.0f;
        float fit = Math.min(width * guiScale * 0.94f / MAX_WIDTH, height * guiScale * 0.90f / MAX_HEIGHT) / userScale;
        float stepped = Math.round(fit * 8.0f) / 8.0f;
        return Math.max(0.5f, Math.min(3.0f, stepped));
    }

    private boolean compactLayout() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        if (!Float.isFinite(guiScale) || guiScale < 1.0f) guiScale = 1.0f;
        return width * guiScale / (float) Math.max(0.5, VantaClientPreferences.get().uiScale) < 860.0f;
    }

    private void rebuildUi() {
        if (runtime != null) runtime.dispose();
        boolean compact = compactLayout();

        VantaUiNode shell = VantaUiNode.stack(0);
        shell.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.fill(g, x - 3, y + 4, w + 6, h + 4, 22, 0x1E000000);
            MenuTheme.gradient(g, x, y, w, h, 18, MenuTheme.panel(), MenuTheme.panelBottom());
            MenuTheme.outline(g, x, y, w, h, 18, 0x38FFFFFF);
        }));

        VantaUiNode layout = VantaUiNode.column(0, 0);
        VantaUiNode bar = VantaUiNode.row(compact ? 6 : 10, compact ? 12 : 22)
                .height(compact ? 64 : 84).align(VantaUiNode.Alignment.CENTER);
        bar.add(MenuComponents.iconButton(MenuIcons.Icon.ARROW_LEFT, "Back", true, this::onClose));
        bar.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.text(g, f, "Mod library", x + 6, y + h / 2 - 8, MenuTheme.TEXT, MenuTheme.FONT_HEADING);
            MenuTheme.text(g, f, "Fabric mods for Minecraft " + ClientPlatform.minecraftVersion(),
                    x + 6, y + h / 2 + 16, MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
        }).flex(1).height(48));
        bar.add(MenuComponents.button(font, "Installed", null, installedView, this::showInstalled)
                .size(compact ? 92 : 112, 38).enabled(!busy));
        bar.add(MenuComponents.button(font, "Discover", null, !installedView, this::showDiscover)
                .size(compact ? 92 : 112, 38).enabled(!busy));
        layout.add(bar);

        VantaUiNode content = VantaUiNode.column(12, compact ? 12 : 22).flex(1);
        if (!installedView) {
            VantaUiNode search = VantaUiNode.row(10, 0).height(48).align(VantaUiNode.Alignment.CENTER);
            VantaUiNode field = VantaUiNode.row(0, 0).height(48).flex(1)
                    .background(MenuTheme.FIELD).radius(13).border(0x33FFFFFF);
            field.add(VantaUiNode.textField("Search Fabric mods", () -> searchQuery,
                    value -> searchQuery = value, 100).menuStyle().height(48).flex(1));
            search.add(field);
            search.add(MenuComponents.button(font, "Search", MenuIcons.Icon.SEARCH, true, this::search)
                    .size(120, 44).enabled(!busy));
            content.add(search);
        }
        content.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> MenuTheme.text(g, f,
                MenuTheme.fit(f, busy ? "Working…" : status, w - 4, MenuTheme.FONT_SMALL), x + 2, y + h / 2,
                MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL)).height(20));
        if (busy) content.add(VantaUiNode.progressIndicator("Working…").height(26));

        VantaUiNode cards = VantaUiNode.column(12, 0).flex(1).scrollable();
        if (busy && visibleCount() == 0) {
            cards.add(emptyPanel(installedView ? "Scanning installed mods" : "Searching Modrinth",
                    "This runs in the background. The interface stays responsive."));
        } else if (visibleCount() == 0) {
            cards.add(emptyPanel(emptyTitle(), emptyDescription()));
        } else {
            addVisibleCards(cards);
        }
        content.add(cards);

        boolean showSessionAction = enableModId != null;
        VantaUiNode pagination = VantaUiNode.row(10, 0).height(40).align(VantaUiNode.Alignment.CENTER);
        pagination.add(MenuComponents.button(font, "Previous", null, false, () -> changePage(-1))
                .size(100, 36).enabled(!busy && page > 0));
        pagination.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> MenuTheme.textCentered(g, f,
                (page + 1) + " of " + pageCount(), x + w / 2, y + h / 2, MenuTheme.TEXT_DIM,
                MenuTheme.FONT_SMALL)).width(70).height(36));
        pagination.add(MenuComponents.button(font, "Next", null, false, () -> changePage(1))
                .size(100, 36).enabled(!busy && page + 1 < pageCount()));
        pagination.add(VantaUiNode.spacer(1).flex(1));
        if (showSessionAction) {
            pagination.add(MenuComponents.button(font, "Enable now", null, true, this::attemptSessionLoad)
                    .size(compact ? 124 : 140, 38).enabled(!busy)
                    .tooltip("Loads the mod into this session. Experimental; a restart is safest."));
        }
        content.add(pagination);
        layout.add(content);
        shell.add(layout);

        runtime = new VantaUiRuntime(shell);
        runtime.setScale((float) VantaClientPreferences.get().uiScale);
        runtime.setZoom(zoom());
        runtime.setMaxViewportSize(MAX_WIDTH, MAX_HEIGHT);
        if (width > 0 && height > 0) runtime.layout(font, width, height);
    }

    private VantaUiNode emptyPanel(String title, String detail) {
        return VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.fill(g, x, y, w, h, 16, 0x5C141926);
            MenuTheme.outline(g, x, y, w, h, 16, 0x1FFFFFFF);
            MenuTheme.textCentered(g, f, title, x + w / 2, y + h / 2 - 12, MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            MenuTheme.textCentered(g, f, MenuTheme.fit(f, detail, w - 40, MenuTheme.FONT_SMALL),
                    x + w / 2, y + h / 2 + 14, MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
        }).height(140);
    }

    private void addVisibleCards(VantaUiNode cards) {
        int count = visibleCount();
        int start = Math.min(page * PAGE_SIZE, Math.max(0, count - 1));
        int end = Math.min(count, start + PAGE_SIZE);
        int columns = compactLayout() ? 1 : 2;

        for (int index = start; index < end; index += columns) {
            VantaUiNode row = VantaUiNode.row(12, 0).height(96).align(VantaUiNode.Alignment.STRETCH);
            row.add(installedView
                    ? buildInstalledCard(installedMods.get(index))
                    : buildProjectCard(projects.get(index)));
            if (columns == 2 && index + 1 < end) {
                row.add(installedView
                        ? buildInstalledCard(installedMods.get(index + 1))
                        : buildProjectCard(projects.get(index + 1)));
            } else if (columns == 2) {
                row.add(VantaUiNode.spacer(1).flex(1));
            }
            cards.add(row);
        }
    }

    private static VantaUiNode letterTile(String initial) {
        return VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            int accent = MenuTheme.accent();
            MenuTheme.gradient(g, x, y, w, h, 14, MenuTheme.alpha(accent, 58), MenuTheme.alpha(accent, 24));
            MenuTheme.outline(g, x, y, w, h, 14, MenuTheme.alpha(accent, 96));
            MenuTheme.textCentered(g, f, initial, x + w / 2, y + h / 2, MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
        }).size(56, 56);
    }

    private VantaUiNode cardShell() {
        return VantaUiNode.row(14, 16).flex(1).height(96)
                .background(0x8C141926).border(0x22FFFFFF).radius(16)
                .align(VantaUiNode.Alignment.CENTER);
    }

    private VantaUiNode buildProjectCard(ModrinthBrowserService.Project project) {
        VantaUiNode card = cardShell();
        String initial = project.title().isBlank() ? "?" :
                project.title().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        card.add(VantaUiNode.dynamicImage(() -> projectIconTextures.get(project.id()), initial)
                .size(56, 56).background(MenuTheme.alpha(MenuTheme.accent(), 40)).radius(14));
        String meta = project.author() + "  ·  " + compact(project.downloads()) + " downloads";
        card.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.text(g, f, MenuTheme.fit(f, project.title(), w, MenuTheme.FONT_MEDIUM), x, y + 16,
                    MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            MenuTheme.text(g, f, MenuTheme.fit(f, meta, w, MenuTheme.FONT_SMALL), x, y + 38,
                    MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
            MenuTheme.text(g, f, MenuTheme.fit(f, project.description(), w, MenuTheme.FONT_SMALL), x, y + 58,
                    MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
        }).flex(1).height(72));
        card.add(MenuComponents.button(font, "Install", null, true, () -> install(project))
                .size(92, 38).enabled(!busy));
        return card;
    }

    private VantaUiNode buildInstalledCard(ExperimentalModHotLoader.AvailableMod mod) {
        VantaUiNode card = cardShell();
        String initial = mod.name().isBlank() ? "?" :
                mod.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        card.add(letterTile(initial));
        boolean fabricLoaded = fabricLoadedIds.contains(mod.id());
        boolean sessionLoaded = sessionLoadedIds.contains(mod.id());
        String state = fabricLoaded ? "Enabled by Fabric"
                : sessionLoaded ? "Loaded this session (experimental)"
                : "Installed, restart to load";
        int stateColor = fabricLoaded ? 0xFF3DD68C : sessionLoaded ? 0xFFF2C14E : 0xFF8B94A6;
        String meta = mod.id() + "  ·  " + mod.version();
        card.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.text(g, f, MenuTheme.fit(f, mod.name(), w, MenuTheme.FONT_MEDIUM), x, y + 16,
                    MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            MenuTheme.text(g, f, MenuTheme.fit(f, meta, w, MenuTheme.FONT_SMALL), x, y + 38,
                    MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
            MenuTheme.dot(g, x + 5, y + 58, 8, stateColor);
            MenuTheme.text(g, f, MenuTheme.fit(f, state, w - 18, MenuTheme.FONT_SMALL), x + 16, y + 58,
                    MenuTheme.mix(stateColor, 0xFFFFFFFF, 0.35f), MenuTheme.FONT_SMALL);
        }).flex(1).height(72));
        return card;
    }

    private void changePage(int delta) {
        if (busy) return;
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        if (!installedView) requestVisibleIcons();
        rebuildUi();
    }

    private int visibleCount() {
        return installedView ? installedMods.size() : projects.size();
    }

    private int pageCount() {
        return Math.max(1, (visibleCount() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private String emptyTitle() {
        if (busy) return installedView ? "Scanning installed mods" : "Searching Modrinth";
        if (status.startsWith("Search failed")) return "Could not load Modrinth results";
        if (installedView) return "No external Fabric mods found";
        return "No matching mods found";
    }

    private String emptyDescription() {
        if (status.startsWith("Search failed")) return trim(status, 150);
        if (installedView) return "Install a mod from Discover, or add a JAR and restart Minecraft.";
        return "Try another search, or clear the query to browse popular Fabric mods.";
    }

    private void showDiscover() {
        if (busy) return;
        installedView = false;
        enableModId = null;
        page = 0;
        search();
    }

    private void showInstalled() {
        if (busy) return;
        installedView = true;
        enableModId = null;
        page = 0;
        busy = true;
        status = "Scanning the local mods folder…";
        rebuildUi();
        long operation = ++operationGeneration;
        CompletableFuture.supplyAsync(ExperimentalModHotLoader::getAvailableMods)
                .whenComplete((result, error) -> minecraft.execute(() -> {
                    if (closed || operation != operationGeneration) return;
                    busy = false;
                    if (error != null) {
                        installedMods = List.of();
                        status = "Could not scan installed mods: " + safeMessage(error);
                        VantaNotificationManager.error("Mod scan failed",
                                "Could not read the mods folder. Check folder permissions.");
                    } else {
                        installedMods = result;
                        refreshLoadedStates();
                        status = result.size() + " installed mod JAR(s). Fabric-loaded mods are enabled; other JARs need a restart.";
                    }
                    page = 0;
                    rebuildUi();
                }));
    }

    private void refreshLoadedStates() {
        Set<String> fabricLoaded = new HashSet<>();
        if (ClientPlatform.isFabric()) {
            // Fabric-only discovery; VantaLoader's native mod list is not a Fabric mod list.
            try {
                Class<?> loaderType = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object loader = loaderType.getMethod("getInstance").invoke(null);
                Object mods = loaderType.getMethod("getAllMods").invoke(loader);
                if (mods instanceof Iterable<?> iterable) {
                    for (Object container : iterable) {
                        Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
                        Object id = metadata.getClass().getMethod("getId").invoke(metadata);
                        fabricLoaded.add(String.valueOf(id));
                    }
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Optional Fabric metadata is unavailable; leave the set empty.
            }
        }
        Set<String> sessionLoaded = new HashSet<>();
        for (ExperimentalModHotLoader.HotLoadedMod mod : ExperimentalModHotLoader.getHotLoadedMods()) {
            sessionLoaded.add(mod.id());
        }
        fabricLoadedIds = Set.copyOf(fabricLoaded);
        sessionLoadedIds = Set.copyOf(sessionLoaded);
    }

    private void search() {
        if (busy) return;
        installedView = false;
        page = 0;
        enableModId = null;
        busy = true;
        status = searchQuery.isBlank() ? "Loading popular Fabric mods…" : "Searching Modrinth…";
        rebuildUi();
        long operation = ++operationGeneration;
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModrinthBrowserService.search(searchQuery.trim());
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            if (closed || operation != operationGeneration) return;
            busy = false;
            if (error != null) {
                status = "Search failed: " + safeMessage(error);
                projects = List.of();
                pruneProjectIcons();
                VantaNotificationManager.error("Modrinth search failed",
                        "Could not reach Modrinth. Check your connection and retry.");
            } else {
                projects = result == null ? List.of() : List.copyOf(result);
                page = 0;
                pruneProjectIcons();
                status = projects.size()
                        + " compatible results. Select Install to download a mod and its required dependencies.";
                requestVisibleIcons();
            }
            rebuildUi();
        }));
    }

    private void install(ModrinthBrowserService.Project project) {
        if (busy || project == null) return;
        busy = true;
        enableModId = null;
        status = "Installing " + project.title() + " and resolving required dependencies…";
        rebuildUi();
        long operation = ++operationGeneration;
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModrinthBrowserService.install(project.id());
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            if (closed || operation != operationGeneration) return;
            busy = false;
            if (error != null) {
                status = "Install failed: " + safeMessage(error);
                VantaNotificationManager.error("Installation failed",
                        "Could not finish the download. Check your connection and retry.");
                rebuildUi();
                return;
            }

            status = result.summary();
            if (!result.installed().isEmpty()) {
                VantaNotificationManager.success("Download complete",
                        project.title() + " was added. Restart Minecraft to load it.");
                Path primary = result.installed().get(result.installed().size() - 1);
                try {
                    enableModId = ModrinthBrowserService.readFabricModId(primary);
                    status += " " + enableModId + " is ready for an experimental session-load attempt.";
                } catch (Exception exception) {
                    status += " Enable unavailable: " + safeMessage(exception);
                    VantaNotificationManager.error("Mod metadata unavailable",
                            "The downloaded JAR does not contain valid Fabric metadata.");
                }
            }
            rebuildUi();
        }));
    }

    private void attemptSessionLoad() {
        if (busy || enableModId == null) return;
        ExperimentalModHotLoader.Result result = ExperimentalModHotLoader.scanAndLoad(enableModId);
        status = result.summary();
        if (result.loaded() > 0 && result.failed() == 0) {
            VantaNotificationManager.success("Session load complete",
                    "Loaded " + result.loaded() + " mod(s). This remains experimental.");
        } else if (result.failed() > 0) {
            VantaNotificationManager.error("Session load failed",
                    "Some entrypoints failed. Restart Minecraft before trying again.");
        } else {
            VantaNotificationManager.warning("Mod not loaded",
                    "This JAR is not eligible for experimental session loading.");
        }
        rebuildUi();
    }

    private void pruneProjectIcons() {
        iconGeneration++;
        Set<String> retained = projects.stream()
                .map(ModrinthBrowserService.Project::id)
                .collect(java.util.stream.Collectors.toSet());
        var iterator = projectIconTextures.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!retained.contains(entry.getKey())) {
                minecraft.getTextureManager().release(entry.getValue());
                iterator.remove();
            }
        }
        requestedIcons.retainAll(retained);
    }

    private void requestVisibleIcons() {
        long generation = iconGeneration;
        int start = Math.min(page * PAGE_SIZE, projects.size());
        int end = Math.min(projects.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            ModrinthBrowserService.Project project = projects.get(index);
            if (project.iconUrl() == null || project.iconUrl().isBlank()
                    || projectIconTextures.containsKey(project.id())
                    || !requestedIcons.add(project.id())) continue;

            CompletableFuture.supplyAsync(() -> {
                try {
                    return ModrinthBrowserService.downloadIcon(project.iconUrl());
                } catch (Exception ignored) {
                    return new byte[0];
                }
            }).thenAccept(bytes -> minecraft.execute(() -> {
                if (closed || generation != iconGeneration
                        || projects.stream().noneMatch(candidate -> candidate.id().equals(project.id()))) return;
                registerProjectIcon(project.id(), bytes);
            }));
        }
    }

    private boolean registerProjectIcon(String projectId, byte[] bytes) {
        if (closed || bytes == null || bytes.length < 24 || bytes.length > ICON_MAX_BYTES
                || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                || bytes[2] != 0x4E || bytes[3] != 0x47
                || bytes[4] != 0x0D || bytes[5] != 0x0A
                || bytes[6] != 0x1A || bytes[7] != 0x0A) return false;

        int imageWidth = readPngDimension(bytes, 16);
        int imageHeight = readPngDimension(bytes, 20);
        if (imageWidth <= 0 || imageHeight <= 0
                || imageWidth > ICON_MAX_DIMENSION || imageHeight > ICON_MAX_DIMENSION) return false;

        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > ICON_MAX_DIMENSION
                    || image.getHeight() > ICON_MAX_DIMENSION) {
                image.close();
                return false;
            }

            String textureKey = java.util.UUID.nameUUIDFromBytes(
                    projectId.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString().replace("-", "");
            Identifier texture = Identifier.fromNamespaceAndPath(
                    "vantaclient", "modrinth/" + textureKey);
            minecraft.getTextureManager().register(texture,
                    new DynamicTexture(() -> "Vanta Modrinth icon " + projectId, image));
            projectIconTextures.put(projectId, texture);
            return true;
        } catch (Exception | LinkageError ignored) {
            // Invalid optional icons fall back to the project's initial.
            return false;
        }
    }

    private static int readPngDimension(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private String safeMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName() : message;
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private String compact(int value) {
        if (value >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000) {
            return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Integer.toString(value);
    }

    @Override
    public void tick() {
        super.tick();
        if (runtime != null) runtime.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VantaCompose.renderScrim(graphics, width, height);
        if (runtime != null) {
            runtime.setScale((float) VantaClientPreferences.get().uiScale);
            runtime.render(graphics, font, width, height, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (runtime != null && runtime.mouseClicked(event.x(), event.y(), event.button(), font)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = runtime != null && runtime.mouseReleased(event.button());
        return handled || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (runtime != null && runtime.mouseDragged(event.x(), event.y(), event.button())) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (runtime != null && runtime.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (runtime != null && runtime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (runtime != null && runtime.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        closed = true;
        operationGeneration++;
        iconGeneration++;
        if (runtime != null) runtime.dispose();
        for (Identifier texture : projectIconTextures.values()) {
            minecraft.getTextureManager().release(texture);
        }
        projectIconTextures.clear();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

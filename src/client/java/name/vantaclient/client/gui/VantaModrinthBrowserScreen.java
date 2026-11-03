package name.vantaclient.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModrinthBrowserService;
import net.fabricmc.loader.api.FabricLoader;
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
public final class VantaModrinthBrowserScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int WIDE_LAYOUT_THRESHOLD = 760;
    private static final int ICON_MAX_BYTES = 512 * 1024;
    private static final int ICON_MAX_DIMENSION = 512;

    private final Screen parent;
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
        super(Component.literal("Vanta Modrinth Library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        closed = false;
        rebuildUi();
        search();
    }

    private void rebuildUi() {
        if (runtime != null) runtime.dispose();

        VantaUiNode root = VantaUiNode.column(12, 14)
                .background(VantaUiTheme.applyPanelOpacity(VantaUiTheme.PANEL))
                .padding(14)
                .clipToBounds();

        VantaUiNode header = VantaUiNode.row(12, 0)
                .height(44)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode identity = VantaUiNode.column(2, 0).flex(1);
        identity.add(VantaUiNode.eyebrow("VANTA CLIENT / LIBRARY"));
        identity.add(VantaUiNode.title("Modrinth"));
        header.add(identity);
        if (width >= WIDE_LAYOUT_THRESHOLD) {
            header.add(VantaUiNode.text("Minecraft "
                    + FabricLoader.getInstance().getModContainer("minecraft")
                            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                            .orElse("unknown"),
                    VantaUiTheme.TEXT_MUTED));
        }
        header.add(VantaUiNode.button("Back", VantaUiNode.Icon.OVERVIEW,
                this::onClose).size(82, 30));
        root.add(header);

        if (!installedView) {
            VantaUiNode search = VantaUiNode.row(8, 8)
                    .height(46)
                    .background(VantaUiTheme.INPUT_SURFACE)
                    .align(VantaUiNode.Alignment.CENTER);
            search.add(VantaUiNode.icon(VantaUiNode.Icon.SEARCH));
            search.add(VantaUiNode.textField("Search Fabric mods…",
                    () -> searchQuery,
                    value -> searchQuery = value,
                    100).flex(1).minSize(54, 30));
            search.add(VantaUiNode.button("Search", VantaUiNode.Icon.SEARCH,
                    this::search).size(66, 30).enabled(!busy));
            search.add(VantaUiNode.button("Clear", () -> {
                if (busy) return;
                searchQuery = "";
                search();
            }).size(46, 30).enabled(!busy));
            root.add(search);
        }

        VantaUiNode body = VantaUiNode.row(12, 0).flex(1);
        VantaUiNode sidebar = VantaUiNode.column(8, 10)
                .width(142)
                .background(VantaUiTheme.applyPanelOpacity(VantaUiTheme.PANEL_RAISED))
                .padding(10);
        sidebar.add(VantaUiNode.eyebrow("LIBRARY"));
        sidebar.add(VantaUiNode.button("Installed", VantaUiNode.Icon.OVERVIEW,
                this::showInstalled).height(34).selected(installedView).enabled(!busy));
        sidebar.add(VantaUiNode.button("Discover", VantaUiNode.Icon.SEARCH,
                this::showDiscover).height(34).selected(!installedView).enabled(!busy));
        sidebar.add(VantaUiNode.spacer(4));
        sidebar.add(VantaUiNode.button("Vanta Client", VantaUiNode.Icon.BRAND,
                this::onClose).height(34));
        sidebar.add(VantaUiNode.spacer(1).flex(1));
        sidebar.add(VantaUiNode.eyebrow("INSTALLATION"));
        sidebar.add(VantaUiNode.paragraph(
                "Fabric-compatible files only. Most new mods require a Minecraft restart.",
                VantaUiTheme.TEXT_MUTED, 4));
        body.add(sidebar);

        VantaUiNode main = VantaUiNode.column(8, 10)
                .flex(1)
                .background(VantaUiTheme.applyPanelOpacity(VantaUiTheme.PANEL_RAISED))
                .padding(10);
        VantaUiNode heading = VantaUiNode.row(8, 0)
                .height(24)
                .align(VantaUiNode.Alignment.CENTER);
        heading.add(VantaUiNode.eyebrow(installedView ? "INSTALLED MODS" : "DISCOVER MODS"));
        heading.add(VantaUiNode.spacer(1).flex(1));
        heading.add(VantaUiNode.dynamicText(
                () -> busy ? "WORKING…" : trim(status, 24),
                VantaUiTheme.TEXT_MUTED).flex(1).align(VantaUiNode.Alignment.END));
        main.add(heading);

        VantaUiNode cards = VantaUiNode.column(8, 8)
                .flex(1)
                .background(VantaUiTheme.INPUT_SURFACE)
                .padding(8)
                .scrollable();
        if (busy && visibleCount() == 0) {
            VantaUiNode loading = VantaUiNode.column(8, 16)
                    .height(132)
                    .background(VantaUiTheme.PANEL_RAISED)
                    .align(VantaUiNode.Alignment.CENTER);
            loading.add(VantaUiNode.icon(VantaUiNode.Icon.SEARCH).iconColor(VantaUiTheme.TEXT_MUTED));
            loading.add(VantaUiNode.title(installedView ? "Scanning installed mods" : "Searching Modrinth"));
            loading.add(VantaUiNode.text("This runs asynchronously. The interface remains responsive.",
                    VantaUiTheme.TEXT_MUTED));
            cards.add(loading);
        } else if (visibleCount() == 0) {
            VantaUiNode empty = VantaUiNode.column(8, 16)
                    .height(140)
                    .background(VantaUiTheme.PANEL_RAISED)
                    .align(VantaUiNode.Alignment.CENTER);
            empty.add(VantaUiNode.icon(VantaUiNode.Icon.OVERVIEW));
            empty.add(VantaUiNode.title(emptyTitle()));
            empty.add(VantaUiNode.paragraph(emptyDescription(), VantaUiTheme.TEXT_MUTED, 2));
            cards.add(empty);
        } else {
            addVisibleCards(cards);
        }
        main.add(cards);

        boolean compactViewport = width < WIDE_LAYOUT_THRESHOLD;
        boolean showSessionAction = enableModId != null;
        VantaUiNode footer = VantaUiNode.column(4, 0)
                .height(showSessionAction && compactViewport ? 64 : 32);
        VantaUiNode pagination = VantaUiNode.row(8, 0)
                .height(30)
                .align(VantaUiNode.Alignment.CENTER);
        pagination.add(VantaUiNode.button("Previous", () -> changePage(-1))
                .size(64, 30).enabled(!busy && page > 0));
        pagination.add(VantaUiNode.dynamicText(
                () -> (page + 1) + " / " + pageCount(),
                VantaUiTheme.TEXT_MUTED).width(34));
        pagination.add(VantaUiNode.button("Next", () -> changePage(1))
                .size(50, 30).enabled(!busy && page + 1 < pageCount()));
        pagination.add(VantaUiNode.spacer(1).flex(1));
        if (showSessionAction && !compactViewport) {
            pagination.add(VantaUiNode.button("Enable now*",
                    VantaUiNode.Icon.SPARK, this::attemptSessionLoad)
                    .size(116, 30).enabled(!busy));
        }
        footer.add(pagination);
        if (showSessionAction && compactViewport) {
            VantaUiNode actionRow = VantaUiNode.row(8, 0)
                    .height(30)
                    .align(VantaUiNode.Alignment.CENTER);
            actionRow.add(VantaUiNode.spacer(1).flex(1));
            actionRow.add(VantaUiNode.button("Enable now*",
                    VantaUiNode.Icon.SPARK, this::attemptSessionLoad)
                    .size(116, 30).enabled(!busy));
            footer.add(actionRow);
        }
        main.add(footer);
        body.add(main);
        root.add(body);

        runtime = new VantaUiRuntime(root);
        runtime.setScale((float) VantaClientPreferences.get().uiScale);
        if (width > 0 && height > 0) runtime.layout(font, width, height);
    }

    private void addVisibleCards(VantaUiNode cards) {
        int count = visibleCount();
        int start = Math.min(page * PAGE_SIZE, Math.max(0, count - 1));
        int end = Math.min(count, start + PAGE_SIZE);
        int columns = width >= WIDE_LAYOUT_THRESHOLD ? 2 : 1;

        for (int index = start; index < end; index += columns) {
            VantaUiNode row = VantaUiNode.row(8, 0)
                    .height(132)
                    .align(VantaUiNode.Alignment.STRETCH);
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

    private VantaUiNode buildProjectCard(ModrinthBrowserService.Project project) {
        VantaUiNode card = VantaUiNode.column(6, 10)
                .flex(1)
                .height(132)
                .background(VantaUiTheme.PANEL_RAISED)
                .padding(10);

        VantaUiNode top = VantaUiNode.row(8, 0)
                .height(42)
                .align(VantaUiNode.Alignment.CENTER);
        String initial = project.title().isBlank() ? "?" :
                project.title().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        top.add(VantaUiNode.dynamicImage(
                () -> projectIconTextures.get(project.id()), initial)
                .size(38, 38)
                .background(VantaUiTheme.SURFACE_SELECTED)
                .align(VantaUiNode.Alignment.CENTER));
        VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
        labels.add(VantaUiNode.text(trim(project.title(), 32), VantaUiTheme.TEXT_PRIMARY));
        labels.add(VantaUiNode.text(trim(project.author(), 22), VantaUiTheme.TEXT_MUTED));
        top.add(labels);
        card.add(top);
        card.add(VantaUiNode.paragraph(trim(project.description(), 180),
                VantaUiTheme.TEXT_SECONDARY, 2).flex(1));

        VantaUiNode footer = VantaUiNode.row(8, 0)
                .height(30)
                .align(VantaUiNode.Alignment.CENTER);
        footer.add(VantaUiNode.text(compact(project.downloads()) + " downloads",
                VantaUiTheme.TEXT_MUTED).flex(1));
        footer.add(VantaUiNode.button("Install", VantaUiNode.Icon.SPARK,
                () -> install(project)).height(28).enabled(!busy));
        card.add(footer);
        return card;
    }

    private VantaUiNode buildInstalledCard(ExperimentalModHotLoader.AvailableMod mod) {
        VantaUiNode card = VantaUiNode.column(6, 10)
                .flex(1)
                .height(132)
                .background(VantaUiTheme.PANEL_RAISED)
                .padding(10);
        VantaUiNode top = VantaUiNode.row(8, 0)
                .height(42)
                .align(VantaUiNode.Alignment.CENTER);
        String initial = mod.name().isBlank() ? "?" :
                mod.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        top.add(VantaUiNode.text(initial, VantaUiTheme.TEXT_PRIMARY)
                .size(38, 38)
                .background(VantaUiTheme.SURFACE_SELECTED)
                .align(VantaUiNode.Alignment.CENTER));
        VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
        labels.add(VantaUiNode.text(trim(mod.name(), 32), VantaUiTheme.TEXT_PRIMARY));
        labels.add(VantaUiNode.text(trim(mod.id() + "  ·  " + mod.version(), 34),
                VantaUiTheme.TEXT_MUTED));
        top.add(labels);
        card.add(top);

        boolean fabricLoaded = fabricLoadedIds.contains(mod.id());
        boolean sessionLoaded = sessionLoadedIds.contains(mod.id());
        String state = fabricLoaded ? "Enabled by Fabric"
                : sessionLoaded ? "Session-loaded · experimental"
                : "Installed · restart to load";
        int stateColor = fabricLoaded ? 0xFF78D6A0
                : sessionLoaded ? 0xFFFFC978 : VantaUiTheme.TEXT_SECONDARY;
        card.add(VantaUiNode.text(state, stateColor));
        card.add(VantaUiNode.paragraph(fabricLoaded
                        ? "Fabric loaded this mod at startup."
                        : sessionLoaded
                        ? "This mod was loaded experimentally in the current session."
                        : "The JAR is present, but it has not been loaded by Fabric.",
                VantaUiTheme.TEXT_MUTED, 2));
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
        for (var container : FabricLoader.getInstance().getAllMods()) {
            fabricLoaded.add(container.getMetadata().getId());
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
        graphics.fill(0, 0, width, height, 0x90070D16);
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

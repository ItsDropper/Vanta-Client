package name.vantaclient.client.gui;

import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModrinthBrowserService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import name.vantaclient.client.gui.components.VantaPanelButton;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

/** In-game Modrinth browser. Network and disk work never runs on the render thread. */
public final class VantaModrinthBrowserScreen extends Screen {
    private static final int PANEL_WIDTH = 920;
    private static final int PANEL_HEIGHT = 540;
    private final Screen parent;
    private EditBox searchBox;
    private List<ModrinthBrowserService.Project> projects = List.of();
    private String status = "Search Modrinth for Fabric mods compatible with this Minecraft version.";
    private boolean busy;
    private int page;
    private final List<VantaPanelButton> cardButtons = new java.util.ArrayList<>();
    private final Map<String, Identifier> projectIconTextures = new HashMap<>();
    private final java.util.Set<String> requestedIcons = new java.util.HashSet<>();
    private VantaPanelButton enableButton;
    private boolean installedView;
    private List<ExperimentalModHotLoader.AvailableMod> installedMods = List.of();
    private Set<String> fabricLoadedIds = Set.of();
    private Set<String> sessionLoadedIds = Set.of();
    private boolean closed;

    public VantaModrinthBrowserScreen(Screen parent) {
        super(Component.literal("Vanta Modrinth Library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = 0;
        int top = 0;
        searchBox = new EditBox(font, VantaPanelButton.layoutX(260), VantaPanelButton.layoutY(76),
                VantaPanelButton.layoutSize(420), VantaPanelButton.layoutSize(30), Component.literal("Search Modrinth"));
        searchBox.setMaxLength(100);
        searchBox.setHint(Component.literal("Search mods..."));
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFE6EDF8);
        addRenderableWidget(searchBox);
        addPanelButton("Search", 694, 76, 94, 30, this::search, true);
        addPanelButton("Clear", 796, 76, 44, 30, this::clearSearch, false);
        addPanelButton("Installed", 34, 132, 184, 30, this::showInstalled, true);
        addPanelButton("Discover", 34, 170, 184, 30, this::showDiscover, false);
        addPanelButton("Back to Vanta Client", 34, 208, 184, 30, this::onClose, false);
        addPanelButton("Back", 844, 24, 54, 26, this::onClose, false);
        addPanelButton("Previous", 252, 492, 66, 26, () -> {
            page = Math.max(0, page - 1);
            requestVisibleIcons();
            rebuildCards();
        }, false);
        addPanelButton("Next", 324, 492, 58, 26, () -> {
            page = Math.min(Math.max(0, (projects.size() - 1) / 6), page + 1);
            requestVisibleIcons();
            rebuildCards();
        }, false);
        rebuildCards();
        search();
    }

    private void loadPopular() {
        if (searchBox != null) searchBox.setValue("");
        page = 0;
        search();
    }

    private void showDiscover() {
        installedView = false;
        if (searchBox != null) searchBox.visible = true;
        if (enableButton != null) {
            removeWidget(enableButton);
            enableButton = null;
        }
        page = 0;
        search();
    }

    private void showInstalled() {
        if (busy) return;
        installedView = true;
        if (searchBox != null) searchBox.visible = false;
        if (enableButton != null) {
            removeWidget(enableButton);
            enableButton = null;
        }
        busy = true;
        page = 0;
        status = "Scanning the local mods folder…";
        rebuildCards();
        CompletableFuture.supplyAsync(ExperimentalModHotLoader::getAvailableMods)
                .whenComplete((result, error) -> minecraft.execute(() -> {
                    if (closed) return;
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
                    rebuildCards();
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

    private void clearSearch() {
        if (searchBox != null) {
            searchBox.setValue("");
            searchBox.setFocused(true);
        }
        page = 0;
        search();
    }

    private void search() {
        if (busy) return;
        installedView = false;
        if (searchBox != null) searchBox.visible = true;
        String query = searchBox.getValue().trim();
        busy = true;
        setCardButtonsActive(false);
        status = query.isEmpty() ? "Loading popular Fabric mods…" : "Searching Modrinth…";
        CompletableFuture.supplyAsync(() -> {
            try { return ModrinthBrowserService.search(query); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) {
                status = "Search failed: " + safeMessage(error);
                projects = List.of();
                VantaNotificationManager.error("Modrinth search failed",
                        "Could not reach Modrinth. Check your connection and retry.");
            } else {
                projects = result;
                page = 0;
                pruneProjectIcons();
                status = result.size() + " compatible results. Select Install to download a mod and its required dependencies.";
                requestVisibleIcons();
            }
            rebuildCards();
        }));
    }

    private void rebuildCards() {
        for (VantaPanelButton button : cardButtons) removeWidget(button);
        cardButtons.clear();
        if (installedView) return;
        int start = page * 6;
        int end = Math.min(projects.size(), start + 6);
        for (int i = start; i < end; i++) {
            ModrinthBrowserService.Project project = projects.get(i);
            int row = i - start;
            int x = 252 + (row % 2) * 326;
            int y = 132 + (row / 2) * 112;
            VantaPanelButton installButton = new VantaPanelButton(x + 226, y + 66, 82, 24,
                    "Install", () -> install(project), () -> false,
                    () -> 0xFF438BFF, true);
            installButton.active = !busy;
            cardButtons.add(addRenderableWidget(installButton));
        }
    }

    private void setCardButtonsActive(boolean active) {
        for (VantaPanelButton button : cardButtons) button.active = active;
    }

    private void pruneProjectIcons() {
        java.util.Set<String> retained = projects.stream()
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

    private void install(ModrinthBrowserService.Project project) {
        if (busy) return;
        busy = true;
        setCardButtonsActive(false);
        status = "Installing " + project.title() + " and resolving required dependencies…";
        CompletableFuture.supplyAsync(() -> {
            try { return ModrinthBrowserService.install(project.id()); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) {
                status = "Install failed: " + safeMessage(error);
                VantaNotificationManager.error("Installation failed",
                        "Could not finish the download. Check your connection and retry.");
                rebuildCards();
                return;
            }
            status = result.summary();
            if (!result.installed().isEmpty()) {
                VantaNotificationManager.success("Download complete",
                        project.title() + " was added. Restart Minecraft to load it.");
                Path primary = result.installed().get(result.installed().size() - 1);
                String modId;
                try {
                    modId = ModrinthBrowserService.readFabricModId(primary);
                } catch (Exception exception) {
                    status += " Enable unavailable: " + safeMessage(exception);
                    VantaNotificationManager.error("Mod metadata unavailable",
                            "The downloaded JAR does not contain valid Fabric metadata.");
                    rebuildCards();
                    return;
                }
                if (enableButton != null) removeWidget(enableButton);
                enableButton = addPanelButton("Enable now*", 736, 458, 162, 26, () -> {
                    ExperimentalModHotLoader.Result hotLoad = ExperimentalModHotLoader.scanAndLoad(modId);
                    status = hotLoad.summary();
                    if (hotLoad.loaded() > 0 && hotLoad.failed() == 0) {
                        VantaNotificationManager.success("Session load complete",
                                "Loaded " + hotLoad.loaded() + " mod(s). This remains experimental.");
                    } else if (hotLoad.failed() > 0) {
                        VantaNotificationManager.error("Session load failed",
                                "Some entrypoints failed. Restart Minecraft before trying again.");
                    } else {
                        VantaNotificationManager.warning("Mod not loaded",
                                "This JAR is not eligible for experimental session loading.");
                    }
                }, true);
                status += " " + modId + " is ready for an experimental session-load attempt.";
            }
            rebuildCards();
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = VantaPanelButton.layoutX(0);
        int top = VantaPanelButton.layoutY(0);
        int panelWidth = VantaPanelButton.layoutSize(PANEL_WIDTH);
        int panelHeight = VantaPanelButton.layoutSize(PANEL_HEIGHT);
        graphics.fill(0, 0, width, height, 0x90070D16);
        rounded(graphics, left + 3, top + 5, panelWidth - 6, panelHeight - 8, 18, 0x44000000);
        rounded(graphics, left, top, panelWidth, panelHeight, 18,
                VantaUiTheme.applyPanelOpacity(VantaUiTheme.PANEL));
        rounded(graphics, VantaPanelButton.layoutX(20), VantaPanelButton.layoutY(112),
                VantaPanelButton.layoutSize(214), VantaPanelButton.layoutSize(404), 15,
                VantaUiTheme.applyPanelOpacity(VantaUiTheme.PANEL_RAISED));

        graphics.drawString(font, "VANTA", VantaPanelButton.layoutX(28), VantaPanelButton.layoutY(24),
                0xFF82B4FF, false);
        graphics.drawString(font, "MODRINTH LIBRARY", VantaPanelButton.layoutX(86), VantaPanelButton.layoutY(24),
                0xFFF1F5FC, false);
        graphics.drawString(font, "Discover Fabric mods that match your Minecraft version",
                VantaPanelButton.layoutX(28), VantaPanelButton.layoutY(46), 0xFF8998AD, false);
        graphics.drawString(font, "LIBRARY", VantaPanelButton.layoutX(42), VantaPanelButton.layoutY(116),
                0xFF64758D, false);
        graphics.drawString(font, "DISCOVER", VantaPanelButton.layoutX(42), VantaPanelButton.layoutY(254),
                0xFF64758D, false);
        graphics.drawString(font, "Compatible with Minecraft "
                        + net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft")
                        .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(""),
                VantaPanelButton.layoutX(42), VantaPanelButton.layoutY(238), 0xFF8292A8, false);

        rounded(graphics, VantaPanelButton.layoutX(24), VantaPanelButton.layoutY(68),
                VantaPanelButton.layoutSize(884), VantaPanelButton.layoutSize(46), 12,
                VantaUiTheme.INPUT_SURFACE);
        if (installedView) {
            graphics.drawString(font, "INSTALLED MODS", VantaPanelButton.layoutX(42),
                    VantaPanelButton.layoutY(84), 0xFFE6EDF8, false);
            graphics.drawString(font, "Fabric status is authoritative; session-loaded entries are experimental",
                    VantaPanelButton.layoutX(252), VantaPanelButton.layoutY(84), 0xFF8998AD, false);
            for (int i = page * 6; i < Math.min(installedMods.size(), page * 6 + 6); i++) {
                ExperimentalModHotLoader.AvailableMod mod = installedMods.get(i);
                int row = i - page * 6;
                int x = 252 + (row % 2) * 326;
                int y = 132 + (row / 2) * 112;
                int sx = VantaPanelButton.layoutX(x);
                int sy = VantaPanelButton.layoutY(y);
                int sw = VantaPanelButton.layoutSize(310);
                int sh = VantaPanelButton.layoutSize(100);
                boolean hovered = mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh;
                rounded(graphics, sx, sy + 2, sw, sh, 13, 0xFF090D15);
                rounded(graphics, sx, sy, sw, sh, 13, hovered ? VantaUiTheme.SURFACE_HOVER : VantaUiTheme.PANEL_RAISED);
                rounded(graphics, sx + VantaPanelButton.layoutSize(1), sy + VantaPanelButton.layoutSize(1),
                        sw - VantaPanelButton.layoutSize(2), sh - VantaPanelButton.layoutSize(2), 12,
                        hovered ? VantaUiTheme.SURFACE_HOVER : VantaUiTheme.PANEL_RAISED);
                String initial = mod.name().isBlank() ? "?" :
                        mod.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                graphics.drawCenteredString(font, initial, sx + VantaPanelButton.layoutSize(33),
                        sy + VantaPanelButton.layoutSize(27), 0xFF9BC2FF);
                graphics.drawString(font, trim(mod.name(), 32), sx + VantaPanelButton.layoutSize(64),
                        sy + VantaPanelButton.layoutSize(13), 0xFFF2F5FB, false);
                graphics.drawString(font, trim(mod.id() + "  ·  " + mod.version(), 40),
                        sx + VantaPanelButton.layoutSize(64), sy + VantaPanelButton.layoutSize(33),
                        0xFF9BA9BC, false);
                boolean fabricLoaded = fabricLoadedIds.contains(mod.id());
                boolean sessionLoaded = sessionLoadedIds.contains(mod.id());
                String state = fabricLoaded ? "ENABLED  ·  Fabric loaded"
                        : sessionLoaded ? "SESSION-LOADED*  ·  experimental"
                        : "INSTALLED  ·  restart to load";
                int stateColor = fabricLoaded ? 0xFF78D6A0
                        : sessionLoaded ? 0xFFFFC978 : 0xFF91A0B5;
                graphics.drawString(font, state, sx + VantaPanelButton.layoutSize(64),
                        sy + VantaPanelButton.layoutSize(61), stateColor, false);
            }
            if (installedMods.isEmpty() && !busy) {
                rounded(graphics, VantaPanelButton.layoutX(252), VantaPanelButton.layoutY(132),
                        VantaPanelButton.layoutSize(636), VantaPanelButton.layoutSize(324), 14, VantaUiTheme.PANEL_RAISED);
                graphics.drawCenteredString(font, "No external Fabric mods found",
                        VantaPanelButton.layoutX(570), VantaPanelButton.layoutY(270), 0xFFE6EDF8);
                graphics.drawCenteredString(font, "Install a mod from Discover or add a JAR and restart Minecraft.",
                        VantaPanelButton.layoutX(570), VantaPanelButton.layoutY(292), 0xFF8998AD);
            }
        } else {
        for (int i = page * 6; i < Math.min(projects.size(), page * 6 + 6); i++) {
            ModrinthBrowserService.Project project = projects.get(i);
            int row = i - page * 6;
            int x = 252 + (row % 2) * 326;
            int y = 132 + (row / 2) * 112;
            int sx = VantaPanelButton.layoutX(x);
            int sy = VantaPanelButton.layoutY(y);
            int sw = VantaPanelButton.layoutSize(310);
            int sh = VantaPanelButton.layoutSize(100);
            boolean hovered = mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh;
            rounded(graphics, sx, sy + 2, sw, sh, 13, 0xFF090D15);
            rounded(graphics, sx, sy, sw, sh, 13, hovered ? VantaUiTheme.SURFACE_HOVER : VantaUiTheme.PANEL_RAISED);
            rounded(graphics, sx + VantaPanelButton.layoutSize(1), sy + VantaPanelButton.layoutSize(1),
                    sw - VantaPanelButton.layoutSize(2), sh - VantaPanelButton.layoutSize(2), 12,
                    hovered ? VantaUiTheme.SURFACE_HOVER : VantaUiTheme.PANEL_RAISED);
            rounded(graphics, sx + VantaPanelButton.layoutSize(12), sy + VantaPanelButton.layoutSize(12),
                    VantaPanelButton.layoutSize(42), VantaPanelButton.layoutSize(42), 10, VantaUiTheme.SURFACE_SELECTED);
            Identifier icon = projectIconTextures.get(project.id());
            if (icon != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon,
                        sx + VantaPanelButton.layoutSize(12), sy + VantaPanelButton.layoutSize(12),
                        0.0f, 0.0f, VantaPanelButton.layoutSize(42), VantaPanelButton.layoutSize(42),
                        VantaPanelButton.layoutSize(42), VantaPanelButton.layoutSize(42));
            } else {
                String initial = project.title().isBlank() ? "?" : project.title().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                graphics.drawString(font, initial, sx + VantaPanelButton.layoutSize(27) - font.width(initial) / 2,
                        sy + VantaPanelButton.layoutSize(28), 0xFF9BC2FF, false);
            }
            graphics.drawString(font, trim(project.title(), 34), sx + VantaPanelButton.layoutSize(64),
                    sy + VantaPanelButton.layoutSize(13), 0xFFF2F5FB, false);
            drawWrapped(graphics, trim(project.description(), 120), sx + VantaPanelButton.layoutSize(64),
                    sy + VantaPanelButton.layoutSize(31), VantaPanelButton.layoutSize(232), 0xFF9BA9BC, 2);
            graphics.drawString(font, trim(project.author(), 20) + "  ·  " + compact(project.downloads()) + " downloads",
                    sx + VantaPanelButton.layoutSize(64), sy + VantaPanelButton.layoutSize(78), 0xFF71839C, false);
            
        }
        if (projects.isEmpty() && !busy) {
            rounded(graphics, VantaPanelButton.layoutX(252), VantaPanelButton.layoutY(132),
                    VantaPanelButton.layoutSize(636), VantaPanelButton.layoutSize(324), 14, VantaUiTheme.PANEL_RAISED);
            graphics.drawCenteredString(font, status.startsWith("Search failed")
                            ? "Could not load Modrinth results" : "No matching mods found",
                    VantaPanelButton.layoutX(570), VantaPanelButton.layoutY(270), 0xFFE6EDF8);
            graphics.drawCenteredString(font, status.startsWith("Search failed")
                            ? trim(status, 90) : "Try another search, or clear the search to browse popular mods.",
                    VantaPanelButton.layoutX(570), VantaPanelButton.layoutY(292), 0xFF8998AD);
        }
        }
        int visibleCount = installedView ? installedMods.size() : projects.size();
        int pageCount = Math.max(1, (visibleCount + 5) / 6);
        graphics.drawString(font, "Page " + (page + 1) + " / " + pageCount,
                VantaPanelButton.layoutX(394), VantaPanelButton.layoutY(501), 0xFF71839C, false);
        graphics.drawString(font, busy ? "Working…" : status,
                VantaPanelButton.layoutX(252), VantaPanelButton.layoutY(522), 0xFF9EACC0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void requestVisibleIcons() {
        int start = page * 6;
        int end = Math.min(projects.size(), start + 6);
        for (int i = start; i < end; i++) {
            ModrinthBrowserService.Project project = projects.get(i);
            if (project.iconUrl() == null || project.iconUrl().isBlank()
                    || projectIconTextures.containsKey(project.id())
                    || !requestedIcons.add(project.id())) continue;
            CompletableFuture.supplyAsync(() -> {
                try { return ModrinthBrowserService.downloadIcon(project.iconUrl()); }
                catch (Exception ignored) { return new byte[0]; }
            }).thenAccept(bytes -> minecraft.execute(() -> registerProjectIcon(project.id(), bytes)));
        }
    }

    private void registerProjectIcon(String projectId, byte[] bytes) {
        if (closed || bytes == null || bytes.length < 24 || bytes.length > 512 * 1024
                || bytes[0] != (byte) 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47
                || bytes[4] != 0x0D || bytes[5] != 0x0A || bytes[6] != 0x1A || bytes[7] != 0x0A) return;
        int imageWidth = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
                | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
        int imageHeight = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16)
                | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
        if (imageWidth <= 0 || imageHeight <= 0 || imageWidth > 512 || imageHeight > 512) return;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > 512 || image.getHeight() > 512) {
                image.close();
                return;
            }
            String textureKey = java.util.UUID.nameUUIDFromBytes(
                    projectId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
            Identifier texture = Identifier.fromNamespaceAndPath("vantaclient", "modrinth/" + textureKey);
            minecraft.getTextureManager().register(texture,
                    new DynamicTexture(() -> "Vanta Modrinth icon " + projectId, image));
            projectIconTextures.put(projectId, texture);
        } catch (Exception | LinkageError ignored) {
            // Invalid optional icons fall back to the project's initial.
        }
    }

    private VantaPanelButton addPanelButton(String label, int x, int y, int w, int h,
                                            Runnable action, boolean accent) {
        return addRenderableWidget(new VantaPanelButton(x, y, w, h, label, action,
                () -> false, () -> 0xFF438BFF, accent));
    }

    private void drawWrapped(GuiGraphics graphics, String value, int x, int y,
                             int maxWidth, int color, int maxLines) {
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : value.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && !line.isEmpty()) {
                graphics.drawString(font, line.toString(), x, y + lines * 11, color, false);
                if (++lines >= maxLines) return;
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && lines < maxLines) graphics.drawString(font, line.toString(), x, y + lines * 11, color, false);
    }

    private void rounded(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            int inset = 0;
            if (row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            } else if (row >= h - r) {
                double dy = row - (h - r) + 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            }
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    @Override
    public void onClose() {
        closed = true;
        for (Identifier texture : projectIconTextures.values()) {
            minecraft.getTextureManager().release(texture);
        }
        projectIconTextures.clear();
        minecraft.setScreen(parent);
    }

    private String safeMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private String compact(int value) {
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0);
        return Integer.toString(value);
    }
}

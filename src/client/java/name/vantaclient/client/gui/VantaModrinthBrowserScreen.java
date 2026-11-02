package name.vantaclient.client.gui;

import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModrinthBrowserService;
import net.minecraft.client.gui.GuiGraphics;
import name.vantaclient.client.gui.components.VantaPanelButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
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
    private VantaPanelButton enableButton;

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
        addPanelButton("Discover", 34, 132, 184, 30, this::loadPopular, true);
        addPanelButton("Installed mods", 34, 170, 184, 30, this::onClose, false);
        addPanelButton("Back", 844, 24, 54, 26, this::onClose, false);
        addPanelButton("Previous", 252, 492, 66, 26, () -> {
            page = Math.max(0, page - 1);
            rebuildCards();
        }, false);
        addPanelButton("Next", 324, 492, 58, 26, () -> {
            page = Math.min(Math.max(0, (projects.size() - 1) / 6), page + 1);
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
        String query = searchBox.getValue().trim();
        busy = true;
        status = query.isEmpty() ? "Loading popular Fabric mods…" : "Searching Modrinth…";
        CompletableFuture.supplyAsync(() -> {
            try { return ModrinthBrowserService.search(query); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) {
                status = "Search failed: " + safeMessage(error);
                projects = List.of();
            } else {
                projects = result;
                page = 0;
                status = result.size() + " compatible results. Select Install to download a mod and its required dependencies.";
            }
            rebuildCards();
        }));
    }

    private void rebuildCards() {
        for (VantaPanelButton button : cardButtons) removeWidget(button);
        cardButtons.clear();
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
            cardButtons.add(addRenderableWidget(installButton));
        }
    }

    private void install(ModrinthBrowserService.Project project) {
        if (busy) return;
        busy = true;
        status = "Installing " + project.title() + " and resolving required dependencies…";
        CompletableFuture.supplyAsync(() -> {
            try { return ModrinthBrowserService.install(project.id()); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) {
                status = "Install failed: " + safeMessage(error);
                return;
            }
            status = result.summary();
            if (!result.installed().isEmpty()) {
                Path primary = result.installed().get(result.installed().size() - 1);
                String modId;
                try {
                    modId = ModrinthBrowserService.readFabricModId(primary);
                } catch (Exception exception) {
                    status += " Enable unavailable: " + safeMessage(exception);
                    return;
                }
                if (enableButton != null) removeWidget(enableButton);
                enableButton = addPanelButton("Enable now*", 736, 458, 162, 26, () -> {
                    ExperimentalModHotLoader.Result hotLoad = ExperimentalModHotLoader.scanAndLoad(modId);
                    status = hotLoad.summary();
                }, true);
                status += " " + modId + " is ready for an experimental session-load attempt.";
            }
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = VantaPanelButton.layoutX(0);
        int top = VantaPanelButton.layoutY(0);
        int panelWidth = VantaPanelButton.layoutSize(PANEL_WIDTH);
        int panelHeight = VantaPanelButton.layoutSize(PANEL_HEIGHT);
        graphics.fill(0, 0, width, height, 0xA8060912);
        rounded(graphics, left + 4, top + 6, panelWidth - 8, panelHeight - 10, 18, 0xB8000000);
        rounded(graphics, left, top, panelWidth, panelHeight, 18, 0xFF0C111B);
        rounded(graphics, left + 1, top + 1, panelWidth - 2, panelHeight - 2, 17, 0xFF111824);
        rounded(graphics, left + 1, top + 1, panelWidth - 2, VantaPanelButton.layoutSize(3), 2, 0xFF438BFF);
        rounded(graphics, VantaPanelButton.layoutX(20), VantaPanelButton.layoutY(112),
                VantaPanelButton.layoutSize(214), VantaPanelButton.layoutSize(404), 14, 0xFF151D2A);
        rounded(graphics, VantaPanelButton.layoutX(28), VantaPanelButton.layoutY(112),
                VantaPanelButton.layoutSize(2), VantaPanelButton.layoutSize(404), 1, 0xFF27364D);

        graphics.drawString(font, "VANTA", VantaPanelButton.layoutX(28), VantaPanelButton.layoutY(24),
                0xFF82B4FF, false);
        graphics.drawString(font, "MODRINTH LIBRARY", VantaPanelButton.layoutX(86), VantaPanelButton.layoutY(24),
                0xFFF1F5FC, false);
        graphics.drawString(font, "Discover Fabric mods that match your Minecraft version",
                VantaPanelButton.layoutX(28), VantaPanelButton.layoutY(46), 0xFF8998AD, false);
        graphics.drawString(font, "LIBRARY", VantaPanelButton.layoutX(42), VantaPanelButton.layoutY(116),
                0xFF64758D, false);
        graphics.drawString(font, "DISCOVER", VantaPanelButton.layoutX(42), VantaPanelButton.layoutY(216),
                0xFF64758D, false);
        graphics.drawString(font, "Compatible with Minecraft "
                        + net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft")
                        .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(""),
                VantaPanelButton.layoutX(42), VantaPanelButton.layoutY(238), 0xFF8292A8, false);

        rounded(graphics, VantaPanelButton.layoutX(24), VantaPanelButton.layoutY(68),
                VantaPanelButton.layoutSize(884), VantaPanelButton.layoutSize(46), 12, 0xFF171F2D);
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
            rounded(graphics, sx, sy, sw, sh, 13, hovered ? 0xFF222F43 : 0xFF192231);
            rounded(graphics, sx + VantaPanelButton.layoutSize(1), sy + VantaPanelButton.layoutSize(1),
                    sw - VantaPanelButton.layoutSize(2), sh - VantaPanelButton.layoutSize(2), 12,
                    hovered ? 0xFF202C3E : 0xFF1B2635);
            rounded(graphics, sx + VantaPanelButton.layoutSize(12), sy + VantaPanelButton.layoutSize(12),
                    VantaPanelButton.layoutSize(42), VantaPanelButton.layoutSize(42), 10, 0xFF263B5A);
            String initial = project.title().isBlank() ? "?" : project.title().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            graphics.drawString(font, initial, sx + VantaPanelButton.layoutSize(27) - font.width(initial) / 2,
                    sy + VantaPanelButton.layoutSize(28), 0xFF9BC2FF, false);
            graphics.drawString(font, trim(project.title(), 34), sx + VantaPanelButton.layoutSize(64),
                    sy + VantaPanelButton.layoutSize(13), 0xFFF2F5FB, false);
            drawWrapped(graphics, trim(project.description(), 120), sx + VantaPanelButton.layoutSize(64),
                    sy + VantaPanelButton.layoutSize(31), VantaPanelButton.layoutSize(232), 0xFF9BA9BC, 2);
            graphics.drawString(font, trim(project.author(), 20) + "  ·  " + compact(project.downloads()) + " downloads",
                    sx + VantaPanelButton.layoutSize(64), sy + VantaPanelButton.layoutSize(78), 0xFF71839C, false);
            rounded(graphics, sx + VantaPanelButton.layoutSize(10), sy + sh - VantaPanelButton.layoutSize(4),
                    sw - VantaPanelButton.layoutSize(20), VantaPanelButton.layoutSize(2), 1, 0xFF438BFF);
        }
        if (projects.isEmpty() && !busy) {
            rounded(graphics, VantaPanelButton.layoutX(252), VantaPanelButton.layoutY(132),
                    VantaPanelButton.layoutSize(636), VantaPanelButton.layoutSize(324), 14, 0xFF192231);
            graphics.drawCenteredString(font, status.startsWith("Search failed")
                            ? "Could not load Modrinth results" : "No matching mods found",
                    VantaPanelButton.layoutX(570), VantaPanelButton.layoutY(270), 0xFFE6EDF8);
            graphics.drawCenteredString(font, status.startsWith("Search failed")
                            ? trim(status, 90) : "Try another search, or clear the search to browse popular mods.",
                    VantaPanelButton.layoutX(570), VantaPanelButton.layoutY(292), 0xFF8998AD);
        }
        int pageCount = Math.max(1, (projects.size() + 5) / 6);
        graphics.drawString(font, "Page " + (page + 1) + " / " + pageCount,
                VantaPanelButton.layoutX(394), VantaPanelButton.layoutY(501), 0xFF71839C, false);
        graphics.drawString(font, busy ? "Working…" : status,
                VantaPanelButton.layoutX(252), VantaPanelButton.layoutY(522), 0xFF9EACC0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
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

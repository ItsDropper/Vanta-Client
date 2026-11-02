package name.vantaclient.client.gui;

import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModrinthBrowserService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** In-game Modrinth browser. Network and disk work never runs on the render thread. */
public final class VantaModrinthBrowserScreen extends Screen {
    private static final int PANEL_WIDTH = 760;
    private static final int PANEL_HEIGHT = 470;
    private final Screen parent;
    private EditBox searchBox;
    private List<ModrinthBrowserService.Project> projects = List.of();
    private String status = "Search Modrinth for Fabric mods compatible with this Minecraft version.";
    private boolean busy;
    private int page;
    private final List<Button> cardButtons = new java.util.ArrayList<>();
    private Button enableButton;

    public VantaModrinthBrowserScreen(Screen parent) {
        super(Component.literal("Vanta Modrinth Library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        searchBox = new EditBox(font, left + 28, top + 56, 500, 28, Component.literal("Search Modrinth"));
        searchBox.setMaxLength(100);
        searchBox.setHint(Component.literal("Search mods..."));
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), button -> search())
                .bounds(left + 540, top + 56, 100, 28).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 92, top + 12, 64, 24).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            page = Math.max(0, page - 1);
            rebuildCards();
        }).bounds(left + 28, top + PANEL_HEIGHT - 42, 34, 24).build());
        addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            page = Math.min(Math.max(0, (projects.size() - 1) / 6), page + 1);
            rebuildCards();
        }).bounds(left + 70, top + PANEL_HEIGHT - 42, 34, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh installed"), button -> {
            status = "Installed JARs are visible in the Mods section after returning.";
        }).bounds(left + PANEL_WIDTH - 190, top + PANEL_HEIGHT - 42, 162, 24).build());
        rebuildCards();
    }

    private void search() {
        if (busy) return;
        String query = searchBox.getValue().trim();
        if (query.isEmpty()) {
            status = "Enter a search term.";
            return;
        }
        busy = true;
        status = "Searching Modrinth…";
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
        for (Button button : cardButtons) removeWidget(button);
        cardButtons.clear();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int start = page * 6;
        int end = Math.min(projects.size(), start + 6);
        for (int i = start; i < end; i++) {
            ModrinthBrowserService.Project project = projects.get(i);
            int row = i - start;
            int x = left + 28 + (row % 2) * 352;
            int y = top + 104 + (row / 2) * 104;
            Button installButton = Button.builder(Component.literal("Install"), button -> install(project))
                    .bounds(x + 252, y + 57, 76, 24).build();
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
                enableButton = addRenderableWidget(Button.builder(Component.literal("Enable now*"), button -> {
                    ExperimentalModHotLoader.Result hotLoad = ExperimentalModHotLoader.scanAndLoad(modId);
                    status = hotLoad.summary();
                }).bounds((width - PANEL_WIDTH) / 2 + PANEL_WIDTH - 190,
                        (height - PANEL_HEIGHT) / 2 + PANEL_HEIGHT - 78, 162, 24).build());
                status += " " + modId + " is ready for an experimental session-load attempt.";
            }
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(0, 0, width, height, 0x99060910);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF20D111B);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF438BFF);
        graphics.drawString(font, "VANTA  /  MODRINTH LIBRARY", left + 28, top + 20, 0xFF82B4FF, false);
        graphics.drawString(font, "Discover Fabric mods without leaving your world", left + 28, top + 37, 0xFF929EAF, false);
        for (int i = page * 6; i < Math.min(projects.size(), page * 6 + 6); i++) {
            ModrinthBrowserService.Project project = projects.get(i);
            int row = i - page * 6;
            int x = left + 28 + (row % 2) * 352;
            int y = top + 104 + (row / 2) * 104;
            graphics.fill(x, y, x + 336, y + 92, 0xFF171E2B);
            graphics.fill(x, y + 88, x + 336, y + 92, 0xFF397DE0);
            graphics.drawString(font, trim(project.title(), 34), x + 12, y + 12, 0xFFF1F5FC, false);
            graphics.drawString(font, trim(project.description(), 48), x + 12, y + 31, 0xFF9AA8BA, false);
            graphics.drawString(font, "by " + trim(project.author(), 24) + "  •  " + compact(project.downloads()) + " downloads",
                    x + 12, y + 76, 0xFF74839A, false);
        }
        graphics.drawString(font, status, left + 120, top + PANEL_HEIGHT - 36, 0xFFB8C5D8, false);
        super.render(graphics, mouseX, mouseY, partialTick);
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

package name.vantaclient.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModConfigDiscovery;
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
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.concurrent.CompletableFuture;

/** Installed-mod library and details view, backed exclusively by the retained Vanta UI runtime. */
public final class VantaIntegrationsScreen extends Screen {
    private enum Selection { NONE, INSTALLED_MOD }

    private final Screen parent;
    private final List<ExperimentalModHotLoader.AvailableMod> previewMods;
    private final String previewSelectedModId;
    private VantaUiRuntime runtime;
    private Selection selection = Selection.NONE;
    private String selectedModId;
    private List<ExperimentalModHotLoader.AvailableMod> availableMods = List.of();
    private final Map<String, Identifier> modIconTextures = new HashMap<>();
    private Map<String, Object> discoveredConfigFactories = Map.of();
    private List<ModDescriptor> integrations = List.of();
    private String modSearchQuery = "";
    private boolean favoritesOnly;
    private boolean modListInitialized;
    private boolean screenActive;
    private boolean modScanInProgress;
    private VantaClientPreferences preferences;
    private String hotLoadStatus = "Experimental best-effort execution. Only try JARs you trust; restart Minecraft to recover from failed initializers.";

    public VantaIntegrationsScreen(Screen parent) {
        this(parent, null);
    }

    VantaIntegrationsScreen(Screen parent,
                            List<ExperimentalModHotLoader.AvailableMod> previewMods) {
        this(parent, previewMods, null);
    }

    VantaIntegrationsScreen(Screen parent,
                            List<ExperimentalModHotLoader.AvailableMod> previewMods,
                            String previewSelectedModId) {
        super(Component.literal("Vanta Client"));
        this.parent = parent;
        this.previewMods = previewMods == null ? null : List.copyOf(previewMods);
        this.previewSelectedModId = previewSelectedModId;
    }

    @Override
    protected void init() {
        screenActive = true;
        if (runtime != null) runtime.dispose();
        runtime = null;
        preferences = VantaClientPreferences.get();

        if (previewMods != null && !modListInitialized) {
            availableMods = previewMods;
            if (previewSelectedModId != null && availableMods.stream()
                    .anyMatch(mod -> mod.id().equals(previewSelectedModId))) {
                selectedModId = previewSelectedModId;
                selection = Selection.INSTALLED_MOD;
            }
            modListInitialized = true;
        } else if (!modListInitialized) {
            refreshModList();
            modListInitialized = true;
        } else if (modIconTextures.isEmpty() && !availableMods.isEmpty()) {
            // A child screen transition releases the old GPU textures; reload them on return.
            reloadModIcons();
        }

        rebuildRetainedUi();
    }

    private void rebuildRetainedUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRetainedRoot());
        runtime.setScale((float) preferences.uiScale);
        runtime.layout(font, width, height);
    }

    private VantaUiNode buildRetainedRoot() {
        boolean compact = width < 760;
        VantaUiNode root = VantaUiNode.column(12, 14)
                .background(VantaUiTheme.CANVAS)
                .clipToBounds();

        VantaUiNode header = VantaUiNode.row(10, 0)
                .height(52)
                .background(VantaUiTheme.PANEL)
                .padding(10)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode identity = VantaUiNode.column(2, 0);
        identity.add(VantaUiNode.eyebrow("VANTA CLIENT / MOD LIBRARY"));
        identity.add(VantaUiNode.title(selection == Selection.INSTALLED_MOD
                ? selectedModTitle() : "Installed mods"));
        header.add(identity.flex(1));
        if (!compact) {
            header.add(VantaUiNode.button("Modrinth", VantaUiNode.Icon.SEARCH,
                    () -> minecraft.setScreen(new VantaModrinthBrowserScreen(this)))
                    .size(112, 32));
            header.add(VantaUiNode.button("HUD editor", VantaUiNode.Icon.HUD,
                    () -> minecraft.setScreen(new name.vantaclient.client.hudeditor.HudEditorScreen(this)))
                    .size(104, 32));
            header.add(VantaUiNode.button("Settings", VantaUiNode.Icon.DISPLAY,
                    () -> minecraft.setScreen(new VantaSettingsScreen(this)))
                    .size(92, 32));
        } else {
            header.add(VantaUiNode.button("Modrinth", VantaUiNode.Icon.SEARCH,
                    () -> minecraft.setScreen(new VantaModrinthBrowserScreen(this)))
                    .size(90, 30));
            header.add(VantaUiNode.button("HUD", VantaUiNode.Icon.HUD,
                    () -> minecraft.setScreen(new name.vantaclient.client.hudeditor.HudEditorScreen(this)))
                    .size(54, 30));
        }
        header.add(VantaUiNode.button("Close", this::onClose).size(compact ? 48 : 62, 32));
        root.add(header);

        if (selection == Selection.INSTALLED_MOD) {
            root.add(buildRetainedModDetails());
            return root;
        }

        VantaUiNode toolbar = VantaUiNode.row(7, 0)
                .height(38)
                .align(VantaUiNode.Alignment.CENTER);
        toolbar.add(VantaUiNode.textField("Search installed mods…",
                () -> modSearchQuery, value -> modSearchQuery = value, 64)
                .flex(1).minSize(70, 32));
        toolbar.add(VantaUiNode.button("Search", VantaUiNode.Icon.SEARCH,
                this::rebuildRetainedUi).size(66, 32));
        toolbar.add(VantaUiNode.button("All", () -> {
            favoritesOnly = false;
            rebuildRetainedUi();
        }).size(46, 32).selected(!favoritesOnly));
        toolbar.add(VantaUiNode.button("★", () -> {
            favoritesOnly = true;
            rebuildRetainedUi();
        }).size(42, 32).selected(favoritesOnly));
        toolbar.add(VantaUiNode.button("Refresh", this::refreshInstalledMods)
                .size(72, 32).enabled(!modScanInProgress));
        root.add(toolbar);

        VantaUiNode content = VantaUiNode.column(8, 10)
                .flex(1)
                .background(VantaUiTheme.PANEL)
                .padding(10);
        VantaUiNode heading = VantaUiNode.row(8, 0)
                .height(24)
                .align(VantaUiNode.Alignment.CENTER);
        List<ExperimentalModHotLoader.AvailableMod> mods = filteredMods();
        heading.add(VantaUiNode.eyebrow(favoritesOnly ? "FAVORITE MODS" : "MODS IN THIS INSTANCE"));
        heading.add(VantaUiNode.spacer(1).flex(1));
        heading.add(VantaUiNode.dynamicText(
                () -> modScanInProgress ? "SCANNING…" : filteredMods().size() + " / " + availableMods.size(),
                VantaUiTheme.TEXT_MUTED));
        content.add(heading);
        if (modScanInProgress) {
            content.add(VantaUiNode.progressIndicator("Scanning local mod inventory").height(30));
        }

        VantaUiNode cards = VantaUiNode.column(8, 8)
                .flex(1)
                .background(VantaUiTheme.INPUT_SURFACE)
                .padding(8)
                .scrollable();
        if (mods.isEmpty()) {
            VantaUiNode empty = VantaUiNode.column(8, 12)
                    .height(140)
                    .background(VantaUiTheme.PANEL_RAISED)
                    .align(VantaUiNode.Alignment.CENTER);
            empty.add(VantaUiNode.icon(modScanInProgress
                    ? VantaUiNode.Icon.SEARCH : VantaUiNode.Icon.OVERVIEW)
                    .iconColor(VantaUiTheme.TEXT_MUTED));
            empty.add(VantaUiNode.title(modScanInProgress
                    ? "Reading installed mods"
                    : favoritesOnly ? "No favorite mods yet" : "Your mod library is empty"));
            empty.add(VantaUiNode.paragraph(modScanInProgress
                            ? "Checking the mods folder and reading Fabric metadata…"
                            : favoritesOnly
                            ? "Mark a mod with the star to keep it in this list."
                            : "Add Fabric mod JARs to this instance, then refresh the library.",
                    VantaUiTheme.TEXT_MUTED, 2));
            cards.add(empty);
        } else {
            addRetainedModCards(cards, mods, compact ? 1 : 3);
        }
        content.add(cards);
        content.add(VantaUiNode.dynamicText(
                () -> trim(hotLoadStatus, compact ? 66 : 108),
                VantaUiTheme.TEXT_MUTED).height(26));
        root.add(content);
        return root;
    }

    private void addRetainedModCards(VantaUiNode cards,
                                     List<ExperimentalModHotLoader.AvailableMod> mods,
                                     int columns) {
        Set<String> hotLoadedIds = new HashSet<>();
        ExperimentalModHotLoader.getHotLoadedMods().forEach(mod -> hotLoadedIds.add(mod.id()));
        for (int index = 0; index < mods.size(); index += columns) {
            VantaUiNode row = VantaUiNode.row(8, 0)
                    .height(132)
                    .align(VantaUiNode.Alignment.STRETCH);
            for (int column = 0; column < columns && index + column < mods.size(); column++) {
                row.add(buildRetainedModCard(mods.get(index + column), hotLoadedIds));
            }
            if (columns > 1 && mods.size() - index < columns) {
                for (int missing = mods.size() - index; missing < columns; missing++) {
                    row.add(VantaUiNode.spacer(1).flex(1));
                }
            }
            cards.add(row);
        }
    }

    private VantaUiNode buildRetainedModCard(ExperimentalModHotLoader.AvailableMod mod,
                                              Set<String> hotLoadedIds) {
        boolean fabricLoaded = FabricLoader.getInstance().isModLoaded(mod.id());
        boolean sessionLoaded = hotLoadedIds.contains(mod.id());
        VantaUiNode card = VantaUiNode.column(6, 9)
                .flex(1)
                .height(132)
                .background(VantaUiTheme.PANEL_RAISED)
                .padding(9);
        VantaUiNode top = VantaUiNode.row(8, 0)
                .height(38)
                .align(VantaUiNode.Alignment.CENTER);
        String initial = mod.name().isBlank() ? "?"
                : mod.name().substring(0, 1).toUpperCase(Locale.ROOT);
        top.add(VantaUiNode.dynamicImage(() -> modIconTextures.get(mod.id()), initial)
                .size(34, 34)
                .background(VantaUiTheme.SURFACE_SELECTED)
                .align(VantaUiNode.Alignment.CENTER));
        VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
        labels.add(VantaUiNode.text(trim(mod.name(), 30), VantaUiTheme.TEXT_PRIMARY));
        labels.add(VantaUiNode.text(trim(mod.id() + "  ·  " + mod.version(), 34),
                VantaUiTheme.TEXT_MUTED));
        top.add(labels);
        card.add(top);
        card.add(VantaUiNode.text(fabricLoaded ? "Enabled by Fabric"
                        : sessionLoaded ? "Session-loaded · experimental"
                        : "Installed · restart to load",
                fabricLoaded ? 0xFF78D6A0
                        : sessionLoaded ? 0xFFFFC978 : VantaUiTheme.TEXT_SECONDARY));
        VantaUiNode actions = VantaUiNode.row(6, 0)
                .height(28)
                .align(VantaUiNode.Alignment.CENTER);
        actions.add(VantaUiNode.button(preferences.favoriteMods.contains(mod.id()) ? "★" : "☆",
                () -> toggleFavorite(mod.id())).size(30, 28));
        actions.add(VantaUiNode.button("Details", () -> selectInstalledMod(mod.id()))
                .size(62, 28));
        if (!fabricLoaded && !sessionLoaded) {
            actions.add(VantaUiNode.button("Hotload", () -> hotLoadMod(mod.id()))
                    .size(68, 28).enabled(!modScanInProgress));
        } else {
            actions.add(VantaUiNode.text("ACTIVE", VantaUiTheme.TEXT_MUTED).flex(1));
        }
        card.add(actions);
        return card;
    }

    private VantaUiNode buildRetainedModDetails() {
        ExperimentalModHotLoader.AvailableMod mod = availableMods.stream()
                .filter(candidate -> candidate.id().equals(selectedModId))
                .findFirst().orElse(null);
        VantaUiNode details = VantaUiNode.column(10, 14)
                .flex(1)
                .background(VantaUiTheme.PANEL)
                .padding(14)
                .scrollable();
        if (mod == null) {
            details.add(VantaUiNode.title("Mod not found"));
            details.add(VantaUiNode.text(
                    "The mod JAR may have been removed or the folder was rescanned.",
                    VantaUiTheme.TEXT_MUTED));
        } else {
            String initial = mod.name().isBlank() ? "?"
                    : mod.name().substring(0, 1).toUpperCase(Locale.ROOT);
            VantaUiNode identity = VantaUiNode.row(12, 0)
                    .height(64)
                    .align(VantaUiNode.Alignment.CENTER);
            identity.add(VantaUiNode.dynamicImage(() -> modIconTextures.get(mod.id()), initial)
                    .size(56, 56)
                    .background(VantaUiTheme.SURFACE_SELECTED)
                    .align(VantaUiNode.Alignment.CENTER));
            VantaUiNode labels = VantaUiNode.column(4, 0).flex(1);
            labels.add(VantaUiNode.title(mod.name()));
            labels.add(VantaUiNode.text(mod.id() + "  ·  " + mod.version(),
                    VantaUiTheme.TEXT_MUTED));
            identity.add(labels);
            details.add(identity);

            ModDescriptor descriptor = integrations.stream()
                    .filter(candidate -> candidate.id().equals(mod.id()))
                    .findFirst().orElse(null);
            Object configFactory = discoveredConfigFactories.get(mod.id());
            if (descriptor != null) {
                details.add(VantaUiNode.button("Open Vanta integration settings",
                        VantaUiNode.Icon.DISPLAY,
                        () -> minecraft.setScreen(new VantaIntegrationSettingsScreen(this, descriptor)))
                        .height(34));
            } else if (configFactory != null) {
                details.add(VantaUiNode.button("Open mod settings",
                        VantaUiNode.Icon.DISPLAY, () -> {
                            Screen configScreen = ModConfigDiscovery.open(configFactory, this);
                            if (configScreen != null) minecraft.setScreen(configScreen);
                        }).height(34));
            } else if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                details.add(VantaUiNode.button("Open Mod Menu",
                        VantaUiNode.Icon.DISPLAY, this::openModMenu).height(34));
            } else {
                details.add(VantaUiNode.paragraph(
                        "This mod does not expose a settings screen that Vanta can safely open. "
                                + "Install Mod Menu to discover supported configuration screens.",
                        VantaUiTheme.TEXT_MUTED, 3));
            }
            details.add(VantaUiNode.paragraph(
                    "JAR: " + mod.path().getFileName()
                            + ". Mods loaded by Fabric cannot be safely unloaded at runtime. "
                            + "Session hotloading is experimental and restart is the recovery path.",
                    VantaUiTheme.TEXT_MUTED, 3));
        }
        details.add(VantaUiNode.button("Back to mod library", () -> {
            selection = Selection.NONE;
            selectedModId = null;
            rebuildWidgets();
        }).height(34));
        return details;
    }

    private String selectedModTitle() {
        return availableMods.stream().filter(mod -> mod.id().equals(selectedModId))
                .map(ExperimentalModHotLoader.AvailableMod::name)
                .findFirst().orElse("Mod details");
    }

    private static String trim(String value, int maximumLength) {
        if (value == null || maximumLength <= 0) return "";
        if (value.length() <= maximumLength) return value;
        if (maximumLength == 1) return "…";
        return value.substring(0, maximumLength - 1) + "…";
    }

    private void selectInstalledMod(String modId) {
        if (modId == null || modId.isBlank()) return;
        selection = Selection.INSTALLED_MOD;
        selectedModId = modId;
        rebuildRetainedUi();
    }

    private void toggleFavorite(String id) {
        if (!preferences.favoriteMods.add(id)) preferences.favoriteMods.remove(id);
        VantaClientPreferences.save();
        rebuildRetainedUi();
    }

    private void hotLoadMod(String id) {
        if (modScanInProgress) return;
        ExperimentalModHotLoader.Result result = ExperimentalModHotLoader.scanAndLoad(id);
        hotLoadStatus = result.summary();
        notifyHotLoadResult(result);
        refreshModList();
        rebuildRetainedUi();
    }

    /** Refreshes the local JAR inventory only. It never executes third-party entrypoints. */
    private void refreshInstalledMods() {
        if (modScanInProgress) return;
        hotLoadStatus = "Refreshing the local mod inventory…";
        refreshModList();
        rebuildRetainedUi();
    }

    private List<ExperimentalModHotLoader.AvailableMod> filteredMods() {
        String query = modSearchQuery == null ? "" : modSearchQuery.trim().toLowerCase(Locale.ROOT);
        return availableMods.stream().filter(mod -> {
            if (favoritesOnly && !preferences.favoriteMods.contains(mod.id())) return false;
            if (query.isEmpty()) return true;
            return mod.name().toLowerCase(Locale.ROOT).contains(query)
                    || mod.id().toLowerCase(Locale.ROOT).contains(query);
        }).toList();
    }

    private void refreshModList() {
        integrations = VantaClientApi.getRegisteredMods();
        discoveredConfigFactories = ModConfigDiscovery.discover();
        startAvailableModScan();
    }

    /**
     * Enumerates the mods folder away from the render thread. Dynamic textures are still
     * decoded/registered on the client thread after the immutable inventory result arrives.
     */
    private void startAvailableModScan() {
        if (modScanInProgress) return;
        modScanInProgress = true;
        CompletableFuture.supplyAsync(ExperimentalModHotLoader::getAvailableMods)
                .whenComplete((result, error) -> minecraft.execute(() -> {
                    modScanInProgress = false;
                    if (error != null) {
                        Throwable cause = error;
                        while (cause.getCause() != null) cause = cause.getCause();
                        hotLoadStatus = "Could not refresh installed mods: "
                                + (cause.getMessage() == null
                                ? cause.getClass().getSimpleName() : cause.getMessage());
                    } else {
                        availableMods = result == null ? List.of() : List.copyOf(result);
                        if ("Refreshing the local mod inventory…".equals(hotLoadStatus)) {
                            hotLoadStatus = "Inventory refreshed: " + availableMods.size() + " mod JAR(s).";
                        }
                    }
                    if (screenActive) {
                        reloadModIcons();
                        rebuildRetainedUi();
                    }
                }));
    }

    private void reloadModIcons() {
        for (Identifier texture : modIconTextures.values()) {
            minecraft.getTextureManager().release(texture);
        }
        modIconTextures.clear();
        for (ExperimentalModHotLoader.AvailableMod mod : availableMods) {
            loadModIcon(mod.id(), mod.path(), mod.iconPath());
        }
    }

    private void loadModIcon(String modId, Path jarPath, String iconPath) {
        if (iconPath == null || iconPath.isBlank() || iconPath.startsWith("/")
                || iconPath.contains("..") || iconPath.contains("\\")) return;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(iconPath);
            if (entry == null || entry.getSize() <= 0 || entry.getSize() > 1024 * 1024) return;
            byte[] bytes;
            try (InputStream input = jar.getInputStream(entry)) {
                bytes = input.readNBytes(1024 * 1024 + 1);
            }
            if (bytes.length < 24 || bytes.length > 1024 * 1024
                    || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                    || bytes[2] != 0x4E || bytes[3] != 0x47
                    || bytes[4] != 0x0D || bytes[5] != 0x0A
                    || bytes[6] != 0x1A || bytes[7] != 0x0A) return;
            int imageWidth = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
                    | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
            int imageHeight = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16)
                    | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
            if (imageWidth <= 0 || imageHeight <= 0 || imageWidth > 512 || imageHeight > 512) return;
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image.getWidth() > 512 || image.getHeight() > 512
                    || image.getWidth() <= 0 || image.getHeight() <= 0) {
                image.close();
                return;
            }
            Identifier texture = Identifier.fromNamespaceAndPath("vantaclient", "mod_icons/" + modId);
            minecraft.getTextureManager().register(texture,
                    new DynamicTexture(() -> "Vanta mod icon " + modId, image));
            modIconTextures.put(modId, texture);
        } catch (Exception | LinkageError ignored) {
            // Missing, malformed or oversized optional icons fall back to the mod initial.
        }
    }

    private void notifyHotLoadResult(ExperimentalModHotLoader.Result result) {
        if (result.loaded() > 0 && result.failed() == 0) {
            VantaNotificationManager.success("Session load complete",
                    "Loaded " + result.loaded() + " mod(s). Runtime loading remains experimental.");
        } else if (result.failed() > 0) {
            VantaNotificationManager.error("Session load failed",
                    "Some mod entrypoints failed. Review the scan details and restart Minecraft.");
        } else if (result.skipped() > 0) {
            VantaNotificationManager.warning("No mods loaded",
                    "These JARs are not eligible for session loading. Restart Minecraft to load normal mods.");
        }
    }

    private void openModMenu() {
        try {
            Class<?> integration = Class.forName(
                    "name.vantaclient.client.integration.VantaModMenuIntegration");
            Screen modsScreen = (Screen) integration
                    .getMethod("openModsScreen", Screen.class)
                    .invoke(null, this);
            minecraft.setScreen(modsScreen);
        } catch (ReflectiveOperationException | LinkageError exception) {
            System.err.println("[Vanta Client] Could not open Mod Menu: " + exception.getMessage());
            VantaNotificationManager.warning("Mod Menu unavailable",
                    "Could not open this mod's settings screen. Check the installed Mod Menu version.");
        }
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
        if (runtime != null && runtime.mouseClicked(
                event.x(), event.y(), event.button(), font)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = runtime != null && runtime.mouseReleased(event.button());
        return handled || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (runtime != null && runtime.mouseDragged(
                event.x(), event.y(), event.button())) return true;
        return super.mouseDragged(event, dragX, dragY);
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
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (runtime != null && runtime.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        screenActive = false;
        if (runtime != null) {
            runtime.dispose();
            runtime = null;
        }
        releaseModIcons();
        super.removed();
    }

    private void releaseModIcons() {
        for (Identifier texture : modIconTextures.values()) {
            minecraft.getTextureManager().release(texture);
        }
        modIconTextures.clear();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

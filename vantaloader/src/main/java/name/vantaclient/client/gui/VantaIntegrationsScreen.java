package name.vantaclient.client.gui;

import name.vantaclient.client.gui.compose.VantaCompose;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import com.mojang.blaze3d.platform.NativeImage;
import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModConfigDiscovery;
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
public final class VantaIntegrationsScreen extends Screen implements VantaCompose.Screen {
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

    private static final int MAX_WIDTH = 1040;
    private static final int MAX_HEIGHT = 640;

    private float zoom() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        if (!Float.isFinite(guiScale) || guiScale <= 0) guiScale = 1.0f;
        float userScale = (float) preferences.uiScale;
        if (!Float.isFinite(userScale) || userScale <= 0) userScale = 1.0f;
        float fit = Math.min(width * guiScale * 0.94f / MAX_WIDTH, height * guiScale * 0.90f / MAX_HEIGHT) / userScale;
        float stepped = Math.round(fit * 8.0f) / 8.0f;
        return Math.max(0.5f, Math.min(3.0f, stepped));
    }

    private boolean compactLayout() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        if (!Float.isFinite(guiScale) || guiScale < 1.0f) guiScale = 1.0f;
        return width * guiScale / (float) Math.max(0.5, preferences.uiScale) < 860.0f;
    }

    private void rebuildRetainedUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRetainedRoot());
        runtime.setScale((float) preferences.uiScale);
        runtime.setZoom(zoom());
        runtime.setMaxViewportSize(MAX_WIDTH, MAX_HEIGHT);
        runtime.layout(font, width, height);
    }

    private VantaUiNode buildRetainedRoot() {
        boolean compact = compactLayout();
        VantaUiNode shell = VantaUiNode.stack(0);
        shell.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.fill(g, x - 3, y + 4, w + 6, h + 4, 22, 0x1E000000);
            MenuTheme.gradient(g, x, y, w, h, 18, MenuTheme.panel(), MenuTheme.panelBottom());
            MenuTheme.outline(g, x, y, w, h, 18, 0x38FFFFFF);
        }));

        VantaUiNode layout = VantaUiNode.column(0, 0);
        boolean details = selection == Selection.INSTALLED_MOD;
        VantaUiNode bar = VantaUiNode.row(compact ? 6 : 10, compact ? 12 : 22)
                .height(compact ? 64 : 84).align(VantaUiNode.Alignment.CENTER);
        bar.add(MenuComponents.iconButton(MenuIcons.Icon.ARROW_LEFT, "Back", true, details ? () -> {
            selection = Selection.NONE;
            selectedModId = null;
            rebuildRetainedUi();
        } : this::onClose));
        String title = details ? selectedModTitle() : "Installed mods";
        bar.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.text(g, f, MenuTheme.fit(f, title, w - 12, MenuTheme.FONT_HEADING), x + 6, y + h / 2 - 8,
                    MenuTheme.TEXT, MenuTheme.FONT_HEADING);
            MenuTheme.text(g, f, details ? "Mod details" : "Everything in this instance's mods folder",
                    x + 6, y + h / 2 + 16, MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
        }).flex(1).height(48));
        if (!compact && !details) {
            bar.add(MenuComponents.button(font, "Modrinth", MenuIcons.Icon.SEARCH, false,
                    () -> minecraft.setScreen(new VantaModrinthBrowserScreen(this))).size(124, 38));
            bar.add(MenuComponents.button(font, "HUD editor", MenuIcons.Icon.HUD, false,
                    () -> minecraft.setScreen(new name.vantaclient.client.hudeditor.HudEditorScreen(this)))
                    .size(132, 38));
        }
        bar.add(MenuComponents.iconButton(MenuIcons.Icon.CLOSE, "Close", true, this::onClose));
        layout.add(bar);

        if (details) {
            layout.add(buildRetainedModDetails());
            shell.add(layout);
            return shell;
        }

        VantaUiNode content = VantaUiNode.column(12, compact ? 12 : 22).flex(1);
        VantaUiNode toolbar = VantaUiNode.row(10, 0).height(48).align(VantaUiNode.Alignment.CENTER);
        VantaUiNode field = VantaUiNode.row(0, 0).height(48).flex(1)
                .background(MenuTheme.FIELD).radius(13).border(0x33FFFFFF);
        field.add(VantaUiNode.textField("Search installed mods", () -> modSearchQuery,
                value -> modSearchQuery = value, 64).menuStyle().height(48).flex(1));
        toolbar.add(field);
        toolbar.add(MenuComponents.button(font, "Search", MenuIcons.Icon.SEARCH, true, this::rebuildRetainedUi)
                .size(compact ? 104 : 120, 44));
        content.add(toolbar);

        VantaUiNode filters = VantaUiNode.row(8, 0).height(36).align(VantaUiNode.Alignment.CENTER);
        filters.add(MenuComponents.button(font, "All mods", null, !favoritesOnly, () -> {
            favoritesOnly = false;
            rebuildRetainedUi();
        }).size(96, 34));
        filters.add(MenuComponents.button(font, "Favorites", MenuIcons.Icon.STAR, favoritesOnly, () -> {
            favoritesOnly = true;
            rebuildRetainedUi();
        }).size(118, 34));
        filters.add(VantaUiNode.spacer(1).flex(1));
        List<ExperimentalModHotLoader.AvailableMod> mods = filteredMods();
        filters.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> MenuTheme.textRight(g, f,
                modScanInProgress ? "Scanning…" : filteredMods().size() + " of " + availableMods.size(),
                x + w, y + h / 2, MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL)).width(96).height(30));
        filters.add(MenuComponents.button(font, "Refresh", null, false, this::refreshInstalledMods)
                .size(96, 34).enabled(!modScanInProgress));
        content.add(filters);
        if (modScanInProgress) content.add(VantaUiNode.progressIndicator("Scanning local mod inventory").height(26));

        VantaUiNode cards = VantaUiNode.column(12, 0).flex(1).scrollable();
        if (mods.isEmpty()) {
            String emptyTitle = modScanInProgress ? "Reading installed mods"
                    : favoritesOnly ? "No favorite mods yet" : "Your mod library is empty";
            String emptyDetail = modScanInProgress ? "Checking the mods folder and reading Fabric metadata…"
                    : favoritesOnly ? "Star a mod to keep it in this list."
                    : "Add Fabric mod JARs to this instance, then refresh.";
            cards.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
                MenuTheme.fill(g, x, y, w, h, 16, 0x5C141926);
                MenuTheme.outline(g, x, y, w, h, 16, 0x1FFFFFFF);
                MenuTheme.textCentered(g, f, emptyTitle, x + w / 2, y + h / 2 - 12, MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
                MenuTheme.textCentered(g, f, MenuTheme.fit(f, emptyDetail, w - 40, MenuTheme.FONT_SMALL),
                        x + w / 2, y + h / 2 + 14, MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
            }).height(140));
        } else {
            addRetainedModCards(cards, mods, compact ? 1 : 2, compact);
        }
        content.add(cards);
        content.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> MenuTheme.text(g, f,
                MenuTheme.fit(f, hotLoadStatus, w - 4, MenuTheme.FONT_SMALL), x + 2, y + h / 2,
                MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL)).height(22));
        layout.add(content);
        shell.add(layout);
        return shell;
    }

    private void addRetainedModCards(VantaUiNode cards,
                                     List<ExperimentalModHotLoader.AvailableMod> mods,
                                     int columns, boolean compact) {
        Set<String> hotLoadedIds = new HashSet<>();
        ExperimentalModHotLoader.getHotLoadedMods().forEach(mod -> hotLoadedIds.add(mod.id()));
        for (int index = 0; index < mods.size(); index += columns) {
            VantaUiNode row = VantaUiNode.row(12, 0).height(96).align(VantaUiNode.Alignment.STRETCH);
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
        boolean fabricLoaded = ClientPlatform.isModLoaded(mod.id());
        boolean sessionLoaded = hotLoadedIds.contains(mod.id());
        VantaUiNode card = VantaUiNode.row(12, 16).flex(1).height(96)
                .background(0x8C141926).border(0x22FFFFFF).radius(16)
                .align(VantaUiNode.Alignment.CENTER);
        String initial = mod.name().isBlank() ? "?"
                : mod.name().substring(0, 1).toUpperCase(Locale.ROOT);
        card.add(VantaUiNode.dynamicImage(() -> modIconTextures.get(mod.id()), initial)
                .size(52, 52).background(MenuTheme.alpha(MenuTheme.accent(), 40)).radius(14));
        String state = fabricLoaded ? "Enabled by Fabric"
                : sessionLoaded ? "Loaded this session" : "Restart to load";
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
        boolean favorite = preferences.favoriteMods.contains(mod.id());
        card.add(MenuComponents.iconButton(favorite ? MenuIcons.Icon.STAR_FILLED : MenuIcons.Icon.STAR,
                favorite ? "Remove from favorites" : "Add to favorites", true, () -> toggleFavorite(mod.id())));
        card.add(MenuComponents.button(font, "Details", null, false, () -> selectInstalledMod(mod.id()))
                .size(84, 36));
        if (!fabricLoaded && !sessionLoaded) {
            card.add(MenuComponents.button(font, "Hotload", null, true, () -> hotLoadMod(mod.id()))
                    .size(92, 36).enabled(!modScanInProgress));
        }
        return card;
    }

    private VantaUiNode buildRetainedModDetails() {
        ExperimentalModHotLoader.AvailableMod mod = availableMods.stream()
                .filter(candidate -> candidate.id().equals(selectedModId))
                .findFirst().orElse(null);
        VantaUiNode details = VantaUiNode.column(14, compact_pad()).flex(1).scrollable();
        if (mod == null) {
            details.add(VantaUiNode.paragraph("Mod not found. The JAR may have been removed or the folder rescanned.",
                    MenuTheme.TEXT_DIM, 3));
            return details;
        }
        String initial = mod.name().isBlank() ? "?" : mod.name().substring(0, 1).toUpperCase(Locale.ROOT);
        VantaUiNode identity = VantaUiNode.row(16, 18).height(96)
                .background(0x8C141926).border(0x22FFFFFF).radius(16).align(VantaUiNode.Alignment.CENTER);
        identity.add(VantaUiNode.dynamicImage(() -> modIconTextures.get(mod.id()), initial)
                .size(60, 60).background(MenuTheme.alpha(MenuTheme.accent(), 40)).radius(16));
        String meta = mod.id() + "  ·  " + mod.version();
        identity.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.text(g, f, MenuTheme.fit(f, mod.name(), w, MenuTheme.FONT_MEDIUM), x, y + h / 2 - 10,
                    MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            MenuTheme.text(g, f, MenuTheme.fit(f, meta, w, MenuTheme.FONT_SMALL), x, y + h / 2 + 12,
                    MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
        }).flex(1).height(60));
        details.add(identity);

        ModDescriptor descriptor = integrations.stream()
                .filter(candidate -> candidate.id().equals(mod.id()))
                .findFirst().orElse(null);
        Object configFactory = discoveredConfigFactories.get(mod.id());
        if (descriptor != null) {
            details.add(wide(MenuComponents.button(font, "Open Vanta integration settings", MenuIcons.Icon.PREFERENCES,
                    true, () -> minecraft.setScreen(new VantaIntegrationSettingsScreen(this, descriptor)))));
        } else if (configFactory != null) {
            details.add(wide(MenuComponents.button(font, "Open mod settings", MenuIcons.Icon.PREFERENCES, true, () -> {
                Screen configScreen = ModConfigDiscovery.open(configFactory, this);
                if (configScreen != null) minecraft.setScreen(configScreen);
            })));
        } else if (ClientPlatform.isModLoaded("modmenu")) {
            details.add(wide(MenuComponents.button(font, "Open Mod Menu", MenuIcons.Icon.PREFERENCES, true,
                    this::openModMenu)));
        } else {
            details.add(VantaUiNode.paragraph(
                    "This mod has no settings screen Vanta can safely open. Install Mod Menu to discover supported ones.",
                    MenuTheme.TEXT_DIM, 3));
        }
        details.add(VantaUiNode.paragraph(
                "JAR: " + mod.path().getFileName()
                        + ". Mods loaded by Fabric cannot be unloaded at runtime. Session hotloading is experimental, and a restart is the recovery path.",
                MenuTheme.TEXT_MUTED, 3));
        return details;
    }

    private int compact_pad() {
        return compactLayout() ? 12 : 22;
    }

    private static VantaUiNode wide(VantaUiNode button) {
        return VantaUiNode.row(0, 0).height(44).add(button.flex(1));
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
        if (!ClientPlatform.isFabric()) {
            hotLoadStatus = "Session hotloading is not supported by VantaLoader.";
            rebuildRetainedUi();
            return;
        }
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
        if (!ClientPlatform.isFabric()) {
            availableMods = List.of();
            hotLoadStatus = "Fabric mod inventory and hotloading are unavailable under VantaLoader.";
            return;
        }
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
        VantaCompose.renderScrim(graphics, width, height);
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

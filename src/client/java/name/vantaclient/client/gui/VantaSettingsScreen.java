package name.vantaclient.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.gui.components.settings.SettingRegistry;
import name.vantaclient.client.gui.compose.VantaCompose;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.gui.menu.KeybindsPage;
import name.vantaclient.client.gui.menu.MenuComponents;
import name.vantaclient.client.gui.menu.MenuHost;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;
import name.vantaclient.client.gui.menu.MenuTheme;
import name.vantaclient.client.gui.menu.ModCatalog;
import name.vantaclient.client.gui.menu.ModEntry;
import name.vantaclient.client.gui.menu.ModSettingsPage;
import name.vantaclient.client.gui.menu.ModsPage;
import name.vantaclient.client.gui.menu.PreferencesPage;
import name.vantaclient.client.gui.menu.ProfilesPage;
import name.vantaclient.client.gui.menu.ThemesPage;
import name.vantaclient.client.init.Keybinds;
import name.vantaclient.client.platform.ClientPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Vanta's main menu: a translucent panel with a sidebar (Mods, Profiles, Keybinds, Themes,
 * Preferences), a history-aware top bar with search, and one page body at a time.
 */
public final class VantaSettingsScreen extends Screen implements VantaCompose.Screen, MenuHost {
    /** Design size of the shell in layout units; the zoom maps it onto the window. */
    private static final int MAX_WIDTH = 1400;
    private static final int MAX_HEIGHT = 780;

    private final Screen parent;
    private VantaUiRuntime runtime;
    private ModCatalog catalog;
    private final ModsPage.State modsState = new ModsPage.State();
    private final ProfilesPage.State profilesState = new ProfilesPage.State();

    private Route route = Route.of(Page.MODS);
    private final Deque<Route> backStack = new ArrayDeque<>();
    private final Deque<Route> forwardStack = new ArrayDeque<>();
    private boolean modsGroupOpen = true;
    private boolean personalGroupOpen = true;

    private long transitionStart;

    private String searchText = "";
    private VantaUiNode searchNode;
    private boolean refocusSearch;
    private VantaUiNode focusTarget;

    private KeyMapping pendingKeyCapture;
    private int capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
    private int capturedMouseButton = -1;
    private int selectedKeyCode = GLFW.GLFW_KEY_UNKNOWN;

    public VantaSettingsScreen(Screen parent) {
        super(Component.literal("Vanta Client"));
        this.parent = parent;
    }

    // ---- lifecycle ---------------------------------------------------------------------------

    @Override
    protected void init() {
        if (catalog == null) {
            catalog = new ModCatalog(minecraft);
            catalog.refresh(this::rebuild);
        } else {
            catalog.restoreIcons();
        }
        rebuildUi();
    }

    private void rebuildUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRoot());
        runtime.setScale((float) VantaClientPreferences.get().uiScale);
        runtime.setZoom(zoom());
        runtime.setMaxViewportSize(MAX_WIDTH, MAX_HEIGHT);
        runtime.layout(font, width, height);
        if (focusTarget != null) {
            runtime.focus(focusTarget);
        } else if (refocusSearch && searchNode != null) {
            runtime.focus(searchNode);
        }
        focusTarget = null;
    }

    /**
     * Screenshot hook: 0 mods grid, 1 mods list, 2 profiles, 3 keybinds, 4 themes,
     * 5 preferences, 6 settings of the first built-in feature with options, 7 HUD filter with a search.
     */
    void setPageForScreenshot(int targetPage) {
        resetCapture();
        backStack.clear();
        forwardStack.clear();
        searchText = "";
        refocusSearch = false;
        modsState.reset();
        VantaClientPreferences.get().modsListView = targetPage == 1;
        route = switch (targetPage) {
            case 0, 1, 7 -> Route.of(Page.MODS);
            case 2 -> Route.of(Page.PROFILES);
            case 3 -> Route.of(Page.KEYBINDS);
            case 4 -> Route.of(Page.THEMES);
            case 5 -> Route.of(Page.PREFERENCES);
            case 6 -> {
                Feature feature = FeatureRegistry.FEATURES.stream()
                        .filter(candidate -> !SettingRegistry.getSettings(candidate).isEmpty())
                        .findFirst().orElse(null);
                yield feature == null ? Route.of(Page.MODS)
                        : new Route(Page.MOD, ModCatalog.builtInId(feature));
            }
            default -> throw new IllegalArgumentException("Unknown settings page: " + targetPage);
        };
        if (targetPage == 7) modsState.selectGroup(ModEntry.Group.HUD);
        rebuildUi();
    }

    // ---- layout ------------------------------------------------------------------------------

    /**
     * Grows or shrinks the whole shell so it fills roughly nine tenths of the window, in steps
     * of an eighth so lines keep landing on whole pixels at common resolutions.
     */
    private float zoom() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        if (!Float.isFinite(guiScale) || guiScale <= 0) guiScale = 1.0f;
        float userScale = (float) VantaClientPreferences.get().uiScale;
        if (!Float.isFinite(userScale) || userScale <= 0) userScale = 1.0f;
        float windowWidth = width * guiScale;
        float windowHeight = height * guiScale;
        float fit = Math.min(windowWidth * 0.94f / MAX_WIDTH, windowHeight * 0.90f / MAX_HEIGHT) / userScale;
        float stepped = Math.round(fit * 8.0f) / 8.0f;
        return Math.max(0.5f, Math.min(3.0f, stepped));
    }

    private int shellWidth() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        float effective = (float) VantaClientPreferences.get().uiScale * zoom()
                / (guiScale > 0 ? guiScale : 1.0f);
        if (!Float.isFinite(effective) || effective <= 0) effective = 1.0f;
        int layoutWidth = Math.round(width / effective);
        int margin = Math.max(1, Math.round(layoutWidth * 0.025f));
        return Math.min(Math.max(0, layoutWidth - margin * 2), MAX_WIDTH);
    }

    private boolean compactLayout() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        if (!Float.isFinite(guiScale) || guiScale < 1.0f) guiScale = 1.0f;
        float userScale = (float) VantaClientPreferences.get().uiScale;
        if (!Float.isFinite(userScale) || userScale <= 0.0f) userScale = 1.0f;
        return width * guiScale / userScale < 900.0f;
    }

    private int sidebarWidth() {
        return compactLayout() ? 210 : 252;
    }

    @Override
    public int contentWidth() {
        boolean compact = compactLayout();
        return Math.max(compact ? 120 : 240,
                shellWidth() - (compact ? 48 : sidebarWidth() + 49));
    }

    private VantaUiNode buildRoot() {
        boolean compact = compactLayout();
        int sidebar = sidebarWidth();
        VantaUiNode shell = VantaUiNode.stack(0);
        shell.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            // Soft drop shadow, glass body lit from above, a darker sidebar half and a hairline edge.
            MenuTheme.fill(g, x - 3, y + 4, w + 6, h + 4, 22, 0x1E000000);
            MenuTheme.gradient(g, x, y, w, h, 18, MenuTheme.panel(), MenuTheme.panelBottom());
            if (!compact) {
                VantaUiTheme.fillSquircleSides(g, x, y, sidebar, h, 18, MenuTheme.SIDEBAR,
                        MenuTheme.SIDEBAR, true, false);
            }
            MenuTheme.outline(g, x, y, w, h, 18, 0x38FFFFFF);
        }));
        VantaUiNode body = compact ? VantaUiNode.column(8, 8) : VantaUiNode.row(0, 0);
        if (compact) {
            body.add(buildCompactNavigation());
            body.add(buildContent());
        } else {
            body.add(buildSidebar());
            body.add(VantaUiNode.spacer(1).width(1).background(MenuTheme.LINE_SOFT).radius(0));
            body.add(buildContent());
        }
        shell.add(body);
        // Brief veil over the page area after navigating, easing the new page in.
        shell.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            int veil = veilAlpha();
            if (veil <= 0) return;
            int color = MenuTheme.alpha(0xFF0A0D14, veil);
            int veilLeft = compact ? x : x + sidebar + 1;
            int veilTop = compact ? y + 106 : y + 84;
            int veilWidth = Math.max(0, w - (veilLeft - x));
            int veilHeight = Math.max(0, h - (veilTop - y));
            if (veilWidth > 0 && veilHeight > 0) {
                VantaUiTheme.fillSquircleSides(g, veilLeft, veilTop, veilWidth, veilHeight, 18,
                        color, color, false, true);
            }
        }));
        return shell;
    }

    private int veilAlpha() {
        if (transitionStart == 0L || !VantaClientPreferences.get().animations) return 0;
        float t = (System.nanoTime() - transitionStart) / 200_000_000.0f;
        if (t >= 1.0f) {
            transitionStart = 0L;
            return 0;
        }
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        return Math.round(150.0f * (1.0f - eased));
    }

    private VantaUiNode buildCompactNavigation() {
        VantaUiNode navigation = VantaUiNode.row(5, 6).height(40)
                .padding(2).background(MenuTheme.SIDEBAR);
        navigation.add(VantaUiNode.button("Mods",
                () -> navigate(Route.of(Page.MODS))).flex(1).height(34)
                .selected(route.page() == Page.MODS || route.page() == Page.MOD));
        navigation.add(VantaUiNode.button("Profiles",
                () -> navigate(Route.of(Page.PROFILES))).flex(1).height(34)
                .selected(route.page() == Page.PROFILES));
        navigation.add(VantaUiNode.button("Keys",
                () -> navigate(Route.of(Page.KEYBINDS))).flex(1).height(34)
                .selected(route.page() == Page.KEYBINDS));
        navigation.add(VantaUiNode.button("Themes",
                () -> navigate(Route.of(Page.THEMES))).flex(1).height(34)
                .selected(route.page() == Page.THEMES));
        navigation.add(VantaUiNode.button("Prefs",
                () -> navigate(Route.of(Page.PREFERENCES))).flex(1).height(34)
                .selected(route.page() == Page.PREFERENCES));
        return navigation;
    }

    private VantaUiNode buildSidebar() {
        VantaUiNode side = VantaUiNode.column(4, 16).width(sidebarWidth());
        side.add(MenuComponents.brand());
        side.add(VantaUiNode.spacer(10));
        side.add(MenuComponents.groupHeader("MODS & OPTIONS", modsGroupOpen, () -> {
            modsGroupOpen = !modsGroupOpen;
            rebuildUi();
        }));
        if (modsGroupOpen) {
            side.add(navItem(Icon.MODS, "Mods", Page.MODS));
            side.add(navItem(Icon.PROFILES, "Profiles", Page.PROFILES));
            side.add(navItem(Icon.KEYBINDS, "Keybinds", Page.KEYBINDS));
        }
        side.add(VantaUiNode.spacer(8));
        side.add(MenuComponents.groupHeader("PERSONALIZATION", personalGroupOpen, () -> {
            personalGroupOpen = !personalGroupOpen;
            rebuildUi();
        }));
        if (personalGroupOpen) {
            side.add(navItem(Icon.THEMES, "Themes", Page.THEMES));
            side.add(navItem(Icon.PREFERENCES, "Preferences", Page.PREFERENCES));
        }
        side.add(VantaUiNode.spacer(1).flex(1));
        side.add(MenuComponents.navItem(Icon.EDIT_HUD, "Edit HUD", false, () ->
                minecraft.setScreen(new name.vantaclient.client.hudeditor.HudEditorScreen(this))));
        side.add(VantaUiNode.spacer(1).height(1).background(MenuTheme.LINE_SOFT).radius(0));
        String user = minecraft.getUser() == null ? "Player" : minecraft.getUser().getName();
        side.add(MenuComponents.footer(user, "Fabric " + ClientPlatform.minecraftVersion(), () ->
                name.vantaclient.client.gui.VantaNotificationManager.info("Vanta Client",
                        "Version " + ClientPlatform.clientVersion() + " for Minecraft "
                                + ClientPlatform.minecraftVersion())));
        return side;
    }

    private VantaUiNode navItem(Icon icon, String label, Page page) {
        return MenuComponents.navItem(icon, label, route.page() == page, () -> navigate(Route.of(page)));
    }

    private String title() {
        return switch (route.page()) {
            case MODS -> "Mods";
            case PROFILES -> "Profiles";
            case KEYBINDS -> "Keybinds";
            case THEMES -> "Themes";
            case PREFERENCES -> "Preferences";
            case MOD -> {
                ModEntry entry = catalog.find(route.modId());
                yield entry == null ? "Mod" : entry.name();
            }
        };
    }

    private VantaUiNode buildContent() {
        VantaUiNode content = VantaUiNode.column(0, 0).flex(1);
        boolean compact = compactLayout();
        VantaUiNode bar = VantaUiNode.row(compact ? 6 : 10, compact ? 8 : 22)
                .height(compact ? 58 : 84).align(VantaUiNode.Alignment.CENTER);
        bar.add(MenuComponents.iconButton(Icon.ARROW_LEFT, "Back", !backStack.isEmpty(), this::goBack));
        bar.add(MenuComponents.iconButton(Icon.ARROW_RIGHT, "Forward", !forwardStack.isEmpty(), this::goForward));
        String pageTitle = title();
        bar.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> MenuTheme.text(g, f,
                MenuTheme.fit(f, pageTitle, w, MenuTheme.FONT_HEADING), x + 6, y + h / 2,
                MenuTheme.TEXT, MenuTheme.FONT_HEADING)).flex(1).height(40));
        if (route.page() == Page.MOD) {
            ModEntry entry = catalog.find(route.modId());
            if (entry != null) {
                String info = (entry.description().isBlank() ? entry.name() : entry.description())
                        + (entry.version().isBlank() ? "" : "  ·  " + entry.version());
                bar.add(MenuComponents.iconButton(Icon.INFO, info, true, () -> { }));
                boolean favorite = VantaClientPreferences.get().favoriteMods.contains(entry.id());
                bar.add(MenuComponents.iconButton(favorite ? Icon.STAR_FILLED : Icon.STAR,
                        favorite ? "Remove from favorites" : "Add to favorites", true, () -> {
                            var favorites = VantaClientPreferences.get().favoriteMods;
                            if (!favorites.add(entry.id())) favorites.remove(entry.id());
                            VantaClientPreferences.save();
                            rebuildUi();
                        }));
            }
        }
        bar.add(buildSearch(compact));
        bar.add(MenuComponents.iconButton(Icon.CLOSE, "Close", true, this::onClose));
        content.add(bar);
        content.add(buildBody());
        return content;
    }

    private VantaUiNode buildSearch(boolean compact) {
        VantaUiNode box = VantaUiNode.row(0, 0).width(compact ? 170 : 320).height(compact ? 38 : 44)
                .background(MenuTheme.FIELD).radius(13).border(MenuTheme.alpha(MenuTheme.accent(), 230));
        box.add(VantaUiNode.canvas((g, f, x, y, w, h, st) ->
                name.vantaclient.client.gui.menu.MenuIcons.draw(g, Icon.SEARCH, x + 12, y + (h - 18) / 2, 18,
                        MenuTheme.TEXT_DIM)).width(42));
        searchNode = VantaUiNode.textField("Search mods", () -> searchText, value -> {
            searchText = value;
            refocusSearch = true;
            if (route.page() != Page.MODS) navigate(Route.of(Page.MODS));
            else rebuildUi();
        }, 40).menuStyle().height(compact ? 38 : 44).flex(1);
        box.add(searchNode);
        return box;
    }

    private VantaUiNode buildBody() {
        return switch (route.page()) {
            case MODS -> ModsPage.build(this);
            case PROFILES -> ProfilesPage.build(this);
            case KEYBINDS -> KeybindsPage.build(this);
            case THEMES -> ThemesPage.build(this);
            case PREFERENCES -> PreferencesPage.build(this);
            case MOD -> {
                ModEntry entry = catalog.find(route.modId());
                yield entry == null ? ModsPage.build(this) : ModSettingsPage.build(this, entry);
            }
        };
    }

    // ---- navigation --------------------------------------------------------------------------

    @Override
    public void navigate(Route target) {
        if (target == null) return;
        if (!target.equals(route)) {
            backStack.push(route);
            forwardStack.clear();
            route = target;
            transitionStart = System.nanoTime();
        }
        cancelKeyCaptureQuietly();
        rebuildUi();
    }

    private void goBack() {
        if (backStack.isEmpty()) return;
        forwardStack.push(route);
        route = backStack.pop();
        transitionStart = System.nanoTime();
        refocusSearch = false;
        cancelKeyCaptureQuietly();
        rebuildUi();
    }

    private void goForward() {
        if (forwardStack.isEmpty()) return;
        backStack.push(route);
        route = forwardStack.pop();
        transitionStart = System.nanoTime();
        refocusSearch = false;
        cancelKeyCaptureQuietly();
        rebuildUi();
    }

    // ---- MenuHost ----------------------------------------------------------------------------

    @Override public void focusAfterBuild(VantaUiNode node) { focusTarget = node; }
    @Override public Minecraft minecraft() { return minecraft; }
    @Override public net.minecraft.client.gui.Font font() { return font; }
    @Override public Screen screen() { return this; }
    @Override public ModCatalog catalog() { return catalog; }
    @Override public String search() { return searchText; }
    @Override public ModsPage.State modsState() { return modsState; }
    @Override public ProfilesPage.State profilesState() { return profilesState; }
    @Override public KeyMapping pendingKey() { return pendingKeyCapture; }
    @Override public int selectedKeyCode() { return selectedKeyCode; }

    @Override
    public void selectKeyCode(int glfwKeyCode) {
        selectedKeyCode = selectedKeyCode == glfwKeyCode ? GLFW.GLFW_KEY_UNKNOWN : glfwKeyCode;
        refocusSearch = false;
        rebuildUi();
    }

    @Override
    public void rebuild() {
        if (runtime != null) rebuildUi();
    }

    boolean hasBindingConflict(KeyMapping mapping) {
        return hasConflict(mapping);
    }

    @Override
    public boolean hasConflict(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return false;
        String binding = mapping.saveString();
        for (KeyMapping candidate : Minecraft.getInstance().options.keyMappings) {
            if (candidate != mapping && !candidate.isUnbound() && candidate.saveString().equals(binding)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void beginKeyCapture(KeyMapping mapping) {
        if (mapping == null || pendingKeyCapture != null) return;
        pendingKeyCapture = mapping;
        capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        capturedMouseButton = -1;
        Keybinds.setCaptureActive(true);
        refocusSearch = false;
        rebuildUi();
    }

    @Override
    public void cancelKeyCapture() {
        cancelKeyCaptureQuietly();
        rebuildUi();
    }

    private void cancelKeyCaptureQuietly() {
        pendingKeyCapture = null;
        capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        capturedMouseButton = -1;
        Keybinds.setCaptureActive(false);
    }

    private void resetCapture() {
        cancelKeyCaptureQuietly();
        selectedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
    }

    private void completeKeyCapture(InputConstants.Key key, int keyboardKey, int mouseButton) {
        KeyMapping mapping = pendingKeyCapture;
        if (mapping == null || key == null) return;
        mapping.setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        pendingKeyCapture = null;
        capturedKeyCode = keyboardKey;
        capturedMouseButton = mouseButton;
        Keybinds.setCaptureActive(keyboardKey != GLFW.GLFW_KEY_UNKNOWN || mouseButton >= 0);
        refocusSearch = false;
        rebuildUi();
    }

    @Override
    public void resetKey(KeyMapping mapping) {
        if (mapping == null || pendingKeyCapture != null) return;
        mapping.setKey(mapping.getDefaultKey());
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        rebuildUi();
    }

    // ---- Screen plumbing ---------------------------------------------------------------------

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
        if (pendingKeyCapture != null) {
            completeKeyCapture(InputConstants.Type.MOUSE.getOrCreate(event.button()),
                    GLFW.GLFW_KEY_UNKNOWN, event.button());
            return true;
        }
        if (runtime != null && runtime.mouseClicked(event.x(), event.y(), event.button(), font)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (capturedMouseButton == event.button()) {
            capturedMouseButton = -1;
            Keybinds.setCaptureActive(false);
            return true;
        }
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
        if (pendingKeyCapture != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturedKeyCode = GLFW.GLFW_KEY_ESCAPE;
                pendingKeyCapture = null;
                Keybinds.setCaptureActive(true);
                rebuildUi();
            } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                completeKeyCapture(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_UNKNOWN),
                        GLFW.GLFW_KEY_BACKSPACE, -1);
            } else {
                completeKeyCapture(InputConstants.getKey(event), event.key(), -1);
            }
            return true;
        }
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (runtime != null && runtime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (capturedKeyCode == event.key()) {
            capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
            Keybinds.setCaptureActive(false);
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (runtime != null && runtime.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        cancelKeyCaptureQuietly();
        if (runtime != null) runtime.dispose();
        if (catalog != null) catalog.release();
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

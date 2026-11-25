package name.vantaclient.client.gui.title;

import com.mojang.authlib.GameProfile;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.config.VantaClientPreferences.TitleStyle;
import name.vantaclient.client.gui.VantaNotificationManager;
import name.vantaclient.client.gui.VantaSettingsScreen;
import name.vantaclient.client.gui.VantaWorldShareScreen;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.gui.lobby.VantaLobbyScene;
import name.vantaclient.mixin.accessor.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import name.vantaclient.client.gui.VantaText;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.storage.LevelSummary;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Vanta title screen, painted over the live lobby world.
 *
 * Layout: a slim top bar (brand, tabs, actions), account / performance / link cards bottom left,
 * and a play card bottom right that launches real worlds and servers. Every interactive element
 * is a real widget, so focus, keyboard and click handling stay vanilla; the widgets draw nothing
 * themselves and this class paints them. Vanilla's Realms, Options and Quit buttons are reused.
 */
public final class VantaTitleUi {
    private static final String DISCORD_INVITE = "https://discord.gg/yG2CdjxYj";
    private static final FontDescription REGULAR = font("ui");
    private static final FontDescription MEDIUM = font("medium");
    private static final Identifier MARK = tex("mark_small");
    private static final Identifier ICON_CHAT = tex("chat");
    private static final Identifier ICON_BELL = tex("bell");
    private static final Identifier ICON_GRID = tex("grid");
    private static final Identifier ICON_CHEVRON = tex("chevron");
    private static final Identifier ICON_CHECK = tex("check");
    private static final Identifier ICON_CLOSE = tex("close");
    private static final Identifier SCRIM_LEFT = tex("scrim_left");
    private static final Identifier SCRIM_BOTTOM = tex("scrim_bottom");
    private static final Identifier SCRIM_TOP = tex("scrim_top");

    private static final Map<String, Integer> WIDTHS = new HashMap<>();
    private static final Map<String, Component> MEDIUM_COMPONENTS = new HashMap<>();
    private static final Map<String, Component> REGULAR_COMPONENTS = new HashMap<>();
    private static final VantaUiTheme THEME = new VantaUiTheme();

    private static final int TEXT_DIM = 0xFFA3AFC3;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int CARD = 0xB80A1018;
    private static final int CARD_EDGE = 0x2EFFFFFF;
    private static final int PANEL = 0xF2101621;
    private static final int PANEL_EDGE = 0x2EFFFFFF;
    private static final int GOOD = 0xFF45D6A0;
    private static final int BAD = 0xFFEF5D6B;
    private static final int WARN = 0xFFF2B84B;

    // ---- shared, screen-independent data (survives resizes and re-opening the title screen)
    private static Boolean modMenuPresent;
    private static UUID cachedProfileId;
    private static Supplier<PlayerSkin> cachedSkin;
    private static VantaTitleUi current;
    private static List<LevelSummary> worlds;
    private static long worldsLoadedAt;
    private static boolean worldsLoading;
    private static ServerStatusPinger pinger;
    private static final Map<String, ServerData> PING_CACHE = new HashMap<>();
    private static final Map<String, Long> PING_STARTED = new HashMap<>();

    private enum Tab { SINGLEPLAYER, MULTIPLAYER }

    /** One selectable thing in the play card: a world or a server. */
    private record Entry(String title, String key, LevelSummary world, ServerData server) {}

    private static final class Item {
        final String label;
        float hover;
        Item(String label) { this.label = label; }
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Screen screen;
    private final Map<AbstractButton, Item> items = new IdentityHashMap<>();
    private final List<AbstractButton> links = new ArrayList<>();
    private final AbstractButton[] styleItems = new AbstractButton[3];
    private final AbstractButton[] recent = new AbstractButton[3];
    private AbstractButton tabPlay, tabRealms, tabOptions, quit;
    private AbstractButton discord, bell, modMenu, vantaMenu, styleToggle, clear, scrim;
    private AbstractButton segSingle, segMulti, play, all;
    private boolean notificationsOpen;
    private boolean styleOpen;
    private float popupProgress;
    private long lastFrame = System.nanoTime();
    private float u;
    private int panelX, panelY, panelW, panelH;
    private int cardX, cardY, cardW, cardH;
    private int leftX, leftW, accountY, linksY, accountH, linksH;
    private Tab tab;
    private int selected;
    private float segSlide;
    private List<Entry> entries = List.of();

    public VantaTitleUi(Screen screen) {
        this.screen = screen;
    }

    // ------------------------------------------------------------------ construction

    public void init(ScreenAccessor accessor) {
        current = this;
        u = Math.max(0.12f, Math.min(2.0f, Math.min(screen.height / 420.0f, screen.width / 680.0f)));
        AbstractButton realmsButton = null, optionsButton = null, quitButton = null;
        List<AbstractButton> extra = new ArrayList<>();
        for (GuiEventListener child : new ArrayList<>(screen.children())) {
            if (!(child instanceof AbstractButton button)) continue;
            String label = button.getMessage().getString();
            String key = label.toLowerCase(Locale.ROOT);
            if (key.contains("language") || key.contains("accessibility") || key.startsWith("copyright")
                    || key.contains("singleplayer") || key.contains("single player")
                    || key.contains("multiplayer")) {
                hide(button);
            } else if (key.contains("realms")) {
                realmsButton = button;
            } else if (key.startsWith("options")) {
                optionsButton = button;
            } else if (key.startsWith("quit")) {
                quitButton = button;
            } else if (!label.isBlank()) {
                extra.add(button);
            }
        }
        // Vanilla buttons draw nothing in this style; their labels are painted by render().
        tabRealms = adopt(realmsButton, "Realms");
        tabOptions = adopt(optionsButton, "Options");
        quit = adopt(quitButton, "Quit");
        for (AbstractButton button : extra) {
            String label = trimLabel(button.getMessage().getString());
            if (label.equalsIgnoreCase("mods")) {
                hide(button);
                continue;
            }
            items.put(button, new Item(label));
            button.setMessage(Component.empty());
            links.add(button);
        }

        tabPlay = add(accessor, "Play", this::closePopups);
        discord = add(accessor, "Discord", this::openInvite);
        bell = add(accessor, "Notifications", this::toggleNotifications);
        modMenu = add(accessor, "Mods", this::openModMenu);
        modMenu.active = modMenuPresent();
        vantaMenu = add(accessor, "Vanta menu", () -> mc.setScreen(new VantaSettingsScreen(screen)));
        styleToggle = add(accessor, "Menu style", this::toggleStyle);

        links.add(0, add(accessor, "Share", () -> mc.setScreen(new VantaWorldShareScreen(screen))));
        links.add(1, add(accessor, "Language", () -> mc.setScreen(new LanguageSelectScreen(
                screen, mc.options, mc.getLanguageManager()))));
        links.add(2, add(accessor, "Accessibility", () -> mc.setScreen(
                new AccessibilityOptionsScreen(screen, mc.options))));
        links.add(3, add(accessor, "Credits", () -> mc.setScreen(new CreditsAndAttributionScreen(screen))));

        segSingle = add(accessor, "Singleplayer", () -> switchTab(Tab.SINGLEPLAYER));
        segMulti = add(accessor, "Multiplayer", () -> switchTab(Tab.MULTIPLAYER));
        play = add(accessor, "Play", this::launch);
        for (int i = 0; i < recent.length; i++) {
            int index = i;
            recent[i] = add(accessor, "Recent", () -> select(index));
        }
        all = add(accessor, "All", this::openAll);

        TitleStyle[] styles = TitleStyle.values();
        for (int i = 0; i < styles.length; i++) {
            TitleStyle style = styles[i];
            styleItems[i] = add(accessor, style.label(), () -> chooseStyle(style));
        }
        clear = add(accessor, "Clear", VantaNotificationManager::clearHistory);
        scrim = add(accessor, "", this::closePopups);

        tab = "MULTIPLAYER".equals(VantaClientPreferences.get().titleTab) ? Tab.MULTIPLAYER : Tab.SINGLEPLAYER;
        segSlide = tab == Tab.MULTIPLAYER ? 1.0f : 0.0f;
        loadWorlds();
        refreshEntries();
        layout();
    }

    private static void hide(AbstractButton button) {
        button.visible = false;
        button.active = false;
    }

    private AbstractButton adopt(AbstractButton button, String label) {
        if (button == null) return null;
        items.put(button, new Item(label));
        button.setMessage(Component.empty());
        return button;
    }

    private AbstractButton add(ScreenAccessor accessor, String label, Runnable action) {
        AbstractButton button = Button.builder(Component.empty(), b -> action.run())
                .bounds(0, 0, 10, 10).build();
        accessor.vanta$addRenderableWidget(button);
        items.put(button, new Item(label));
        return button;
    }

    private static String trimLabel(String label) {
        String value = label == null ? "" : label.strip();
        return value.endsWith("...") ? value.substring(0, value.length() - 3) : value;
    }

    // ------------------------------------------------------------------ data

    private void loadWorlds() {
        long now = System.currentTimeMillis();
        if (worldsLoading || (worlds != null && now - worldsLoadedAt < 5000L)) return;
        worldsLoading = true;
        try {
            var source = mc.getLevelSource();
            var candidates = source.findLevelCandidates();
            source.loadLevelSummaries(candidates).whenCompleteAsync((summaries, error) -> {
                worldsLoading = false;
                worldsLoadedAt = System.currentTimeMillis();
                if (error != null || summaries == null) {
                    worlds = List.of();
                } else {
                    List<LevelSummary> sorted = new ArrayList<>(summaries);
                    sorted.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
                    worlds = sorted;
                }
                if (current != null) current.onDataChanged();
            }, mc);
        } catch (Exception error) {
            worldsLoading = false;
            worlds = List.of();
        }
    }

    private void onDataChanged() {
        refreshEntries();
        layout();
    }

    private void refreshEntries() {
        List<Entry> result = new ArrayList<>();
        if (tab == Tab.SINGLEPLAYER) {
            if (worlds != null) {
                for (LevelSummary world : worlds) {
                    result.add(new Entry(world.getLevelName(), world.getLevelId(), world, null));
                }
            }
        } else {
            try {
                ServerList list = new ServerList(mc);
                list.load();
                String preferred = VantaClientPreferences.get().lastServerIp;
                List<ServerData> servers = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) servers.add(list.get(i));
                servers.sort(Comparator.comparing((ServerData d) -> !d.ip.equalsIgnoreCase(preferred)));
                if (pinger == null) pinger = new ServerStatusPinger();
                int pinged = 0;
                for (ServerData data : servers) {
                    String key = data.ip.toLowerCase(Locale.ROOT);
                    ServerData shown = PING_CACHE.get(key);
                    long started = PING_STARTED.getOrDefault(key, 0L);
                    if (shown == null || System.currentTimeMillis() - started > 60_000L) {
                        shown = data;
                        PING_CACHE.put(key, data);
                        PING_STARTED.put(key, System.currentTimeMillis());
                        if (pinged++ < 4) ping(data);
                    }
                    result.add(new Entry(shown.name, key, null, shown));
                }
            } catch (RuntimeException error) {
                // an unreadable server list simply leaves the card empty
            }
        }
        entries = result;
        if (selected >= entries.size()) selected = 0;
        if (scrim != null) layout();
    }

    private void ping(ServerData data) {
        try {
            pinger.pingServer(data, () -> {}, () -> {},
                    EventLoopGroupHolder.remote(mc.options.useNativeTransport()));
        } catch (Exception error) {
            data.setState(ServerData.State.UNREACHABLE);
        }
    }

    /** Called from the client tick while the title screen is showing. */
    public static void tick() {
        if (current != null && pinger != null && Minecraft.getInstance().screen == current.screen) {
            pinger.tick();
        }
    }

    public void dispose() {
        if (current == this) current = null;
    }

    // ------------------------------------------------------------------ layout

    private int s(float value) {
        return Math.round(value * u);
    }

    private void layout() {
        int w = screen.width;
        int h = screen.height;
        int margin = s(28);
        int bottom = h - s(42);

        // Top bar.
        int icon = s(34);
        int gap = s(8);
        int top = s(20);
        int x = w - margin - icon;
        if (quit != null) {
            bounds(quit, x, top, icon, icon);
            x -= icon + gap + s(10);
        }
        AbstractButton[] row = {styleToggle, vantaMenu, modMenu, bell, discord};
        for (AbstractButton button : row) {
            bounds(button, x, top, icon, icon);
            x -= icon + gap;
        }
        int tabsY = top;
        AbstractButton[] tabs = {tabPlay, tabRealms, tabOptions};
        int tabsWidth = 0;
        int tabGap = s(26);
        for (AbstractButton button : tabs) {
            if (button == null) continue;
            tabsWidth += textWidth(items.get(button).label, 15, true) + s(20) + tabGap;
        }
        tabsWidth -= tabGap;
        int tx = margin + s(26) + s(10) + textWidth("Vanta", 18, true) + s(34);
        for (AbstractButton button : tabs) {
            if (button == null) continue;
            int bw = textWidth(items.get(button).label, 15, true) + s(20);
            bounds(button, tx, tabsY, bw, icon);
            tx += bw + tabGap;
        }

        // Bottom-left cards.
        leftX = margin;
        leftW = s(320);
        linksH = s(42);
        accountH = s(64);
        linksY = bottom - linksH;
        accountY = linksY - s(8) - accountH;
        int labelTotal = 0;
        for (AbstractButton link : links) labelTotal += textWidth(items.get(link).label, 12, false);
        int hit = s(16);
        int free = Math.max(0, leftW - labelTotal - hit * links.size());
        int slot = free / Math.max(1, links.size());
        int lx = leftX;
        for (AbstractButton link : links) {
            int bw = textWidth(items.get(link).label, 12, false) + hit + slot;
            bounds(link, lx, linksY + s(4), bw, linksH - s(8));
            lx += bw;
        }

        // Play card, bottom right.
        cardW = s(260);
        cardH = entries.isEmpty() ? s(176) : s(224);
        cardX = w - margin - cardW;
        cardY = bottom - cardH;
        int pad = s(12);
        int innerW = cardW - pad * 2;
        bounds(segSingle, cardX + pad + s(3), cardY + pad + s(3), innerW / 2 - s(3), s(28));
        bounds(segMulti, cardX + pad + innerW / 2, cardY + pad + s(3), innerW / 2 - s(3), s(28));
        int bodyY = cardY + pad + s(34) + s(10);
        int playY = bodyY + s(52) + s(12);
        bounds(play, cardX + pad, playY, innerW, s(38));
        int recentY = playY + s(38) + s(10);
        int tile = s(28);
        for (int i = 0; i < recent.length; i++) {
            bounds(recent[i], cardX + pad + s(46) + i * (tile + s(6)), recentY, tile, tile);
            recent[i].visible = i < entries.size();
            recent[i].active = recent[i].visible;
        }
        bounds(all, cardX + cardW - pad - s(66), recentY + s(1), s(66), s(26));
        all.visible = !entries.isEmpty();
        all.active = all.visible;

        // Popups hang under the icon row, right aligned with the last icon.
        panelY = top + icon + s(10);
        if (styleOpen) {
            panelW = s(196);
            panelH = TitleStyle.values().length * s(38) + s(12);
        } else {
            panelW = s(330);
            int rows = Math.max(1, Math.min(5, VantaNotificationManager.history().size()));
            panelH = s(46) + (VantaNotificationManager.history().isEmpty() ? s(92) : rows * s(56)) + s(6);
        }
        panelX = styleToggle.getX() + styleToggle.getWidth() - panelW;
        for (int i = 0; i < styleItems.length; i++) {
            bounds(styleItems[i], panelX + s(6), panelY + s(6) + i * s(38), panelW - s(12), s(38));
            styleItems[i].visible = styleOpen;
            styleItems[i].active = styleOpen;
        }
        bounds(clear, panelX + panelW - s(78), panelY + s(6), s(66), s(34));
        clear.visible = notificationsOpen && !VantaNotificationManager.history().isEmpty();
        clear.active = clear.visible;
        bounds(scrim, 0, 0, w, h);
        scrim.visible = styleOpen || notificationsOpen;
        scrim.active = scrim.visible;
    }

    private static void bounds(AbstractButton button, int x, int y, int w, int h) {
        if (button == null) return;
        button.setX(x);
        button.setY(y);
        button.setWidth(w);
        button.setHeight(h);
    }

    // ------------------------------------------------------------------ actions

    private void switchTab(Tab next) {
        if (tab == next) return;
        tab = next;
        selected = 0;
        VantaClientPreferences.get().titleTab = next.name();
        VantaClientPreferences.save();
        refreshEntries();
        layout();
    }

    private void select(int index) {
        if (index >= 0 && index < entries.size()) selected = index;
    }

    private void launch() {
        if (entries.isEmpty()) {
            openAll();
            return;
        }
        Entry entry = entries.get(Math.min(selected, entries.size() - 1));
        if (entry.world() != null) {
            if (!entry.world().primaryActionActive()) {
                openAll();
                return;
            }
            mc.createWorldOpenFlows().openWorld(entry.world().getLevelId(),
                    () -> mc.setScreen(new TitleScreen()));
        } else if (entry.server() != null) {
            VantaClientPreferences.get().lastServerIp = entry.server().ip;
            VantaClientPreferences.save();
            ConnectScreen.startConnecting(screen, mc, ServerAddress.parseString(entry.server().ip),
                    entry.server(), false, null);
        }
    }

    private void openAll() {
        mc.setScreen(tab == Tab.SINGLEPLAYER ? new SelectWorldScreen(screen) : new JoinMultiplayerScreen(screen));
    }

    private void openInvite() {
        try {
            Util.getPlatform().openUri(URI.create(DISCORD_INVITE));
        } catch (RuntimeException error) {
            VantaNotificationManager.error("Discord", "Could not open the invite link.");
        }
    }

    private static boolean modMenuPresent() {
        if (modMenuPresent == null) {
            try {
                Class.forName("com.terraformersmc.modmenu.gui.ModsScreen", false,
                        VantaTitleUi.class.getClassLoader());
                modMenuPresent = true;
            } catch (ClassNotFoundException | LinkageError error) {
                modMenuPresent = false;
            }
        }
        return modMenuPresent;
    }

    private void openModMenu() {
        try {
            Class<?> type = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            mc.setScreen((Screen) type.getConstructor(Screen.class).newInstance(screen));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            VantaNotificationManager.error("Mod Menu", "Mod Menu could not be opened.");
        }
    }

    private void toggleNotifications() {
        boolean open = !notificationsOpen;
        closePopups();
        notificationsOpen = open;
        if (open) VantaNotificationManager.markAllRead();
        popupProgress = 0.0f;
        layout();
    }

    private void toggleStyle() {
        boolean open = !styleOpen;
        closePopups();
        styleOpen = open;
        popupProgress = 0.0f;
        layout();
    }

    private void closePopups() {
        notificationsOpen = false;
        styleOpen = false;
        layout();
    }

    private void chooseStyle(TitleStyle style) {
        VantaClientPreferences preferences = VantaClientPreferences.get();
        if (preferences.titleStyle != style) {
            preferences.titleStyle = style;
            VantaClientPreferences.save();
            mc.setScreen(new TitleScreen());
            return;
        }
        closePopups();
    }

    // ------------------------------------------------------------------ painting

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000.0f);
        lastFrame = now;
        boolean animationsEnabled = VantaClientPreferences.get().animations;
        float blend = animationsEnabled ? 1.0f - (float) Math.exp(-dt * 16.0f) : 1.0f;
        popupProgress += (1.0f - popupProgress) * blend;
        segSlide += ((tab == Tab.MULTIPLAYER ? 1.0f : 0.0f) - segSlide)
                * (animationsEnabled ? 1.0f - (float) Math.exp(-dt * 20.0f) : 1.0f);
        if (notificationsOpen) layout();

        int w = screen.width;
        int h = screen.height;
        long elapsed = VantaLobbyScene.elapsedMillis();
        float intro = animationsEnabled ? ease(clamp01(elapsed / 520.0f)) : 1.0f;
        int accent = THEME.accent();

        for (Map.Entry<AbstractButton, Item> entry : items.entrySet()) {
            AbstractButton button = entry.getKey();
            Item item = entry.getValue();
            float target = button.active && button.visible && button.isHoveredOrFocused() ? 1.0f : 0.0f;
            item.hover += (target - item.hover) * blend;
            if (Math.abs(target - item.hover) < 0.01f) item.hover = target;
        }

        // Soft edge shading keeps text legible over any lobby scene without hiding it.
        g.blit(RenderPipelines.GUI_TEXTURED, SCRIM_BOTTOM, 0, h - s(260), 0.0f, 0.0f,
                w, s(260), 4, 128, 4, 128, alpha(0xFFFFFFFF, 0.8f * intro));
        g.blit(RenderPipelines.GUI_TEXTURED, SCRIM_TOP, 0, 0, 0.0f, 0.0f,
                w, s(110), 4, 128, 4, 128, alpha(0xFFFFFFFF, intro));

        VantaAmbience.render(g, w, h, elapsed, accent, intro, u, animationsEnabled);

        renderTopBar(g, intro, accent);
        float cards = animationsEnabled ? ease(clamp01((elapsed - 120) / 520.0f)) : 1.0f;
        int lift = Math.round((1.0f - cards) * s(16));
        renderLeftCards(g, cards, lift, accent);
        renderPlayCard(g, cards, lift, accent);
        renderFooter(g, intro);
        if (styleOpen || notificationsOpen) renderPopup(g, accent);
    }

    private void renderTopBar(GuiGraphics g, float intro, int accent) {
        int x = s(30);
        int cy = s(20) + s(17);
        int mark = s(26);
        g.blit(RenderPipelines.GUI_TEXTURED, MARK, x, cy - mark / 2, 0.0f, 0.0f, mark, mark,
                96, 96, 96, 96, alpha(0xFFFFFFFF, intro));
        text(g, "Vanta", x + mark + s(10), cy, 18, alpha(TEXT_BRIGHT, intro), true);

        for (AbstractButton button : new AbstractButton[]{tabPlay, tabRealms, tabOptions}) {
            if (button == null) continue;
            Item item = items.get(button);
            boolean home = button == tabPlay;
            int color = home ? TEXT_BRIGHT : mix(0xFFC3CDDC, TEXT_BRIGHT, item.hover);
            text(g, item.label, button.getX() + s(10), button.getY() + button.getHeight() / 2, 15,
                    alpha(color, intro), true);
            if (home) {
                int tw = textWidth(item.label, 15, true);
                VantaUiTheme.fillSquircle(g, button.getX() + s(10), button.getY() + button.getHeight() - s(2),
                        tw, Math.max(2, s(2)), 1, alpha(accent, intro));
            }
            if (button.isFocused()) {
                VantaUiTheme.outlineSquircle(g, button.getX(), button.getY(), button.getWidth(),
                        button.getHeight(), s(10), alpha(accent, 0.7f));
            }
        }

        drawIconButton(g, discord, ICON_CHAT, intro, accent, 0, false);
        drawIconButton(g, bell, ICON_BELL, intro, accent, VantaNotificationManager.unreadCount(),
                notificationsOpen);
        drawIconButton(g, modMenu, ICON_GRID, intro, accent, 0, false);
        drawIconButton(g, vantaMenu, MARK, intro, accent, 0, false);
        drawIconButton(g, styleToggle, ICON_CHEVRON, intro, accent, 0, styleOpen);
        if (quit != null) {
            int sepX = quit.getX() - s(9);
            g.fill(sepX, quit.getY() + s(7), sepX + 1, quit.getY() + quit.getHeight() - s(7),
                    alpha(0xFFFFFFFF, 0.12f * intro));
            drawIconButton(g, quit, ICON_CLOSE, intro, accent, 0, false);
        }
    }

    private void drawIconButton(GuiGraphics g, AbstractButton button, Identifier icon, float intro,
                                int accent, int badge, boolean open) {
        Item item = items.get(button);
        float hover = Math.max(item.hover, open ? 1.0f : 0.0f);
        int x = button.getX(), y = button.getY(), size = button.getWidth();
        int fill = mix(0x520B111B, 0x99202B3D, hover);
        VantaUiTheme.fillSquircle(g, x, y, size, size, s(11), alpha(fill, intro));
        VantaUiTheme.outlineSquircle(g, x, y, size, size, s(11),
                alpha(open ? accent : 0xFFFFFFFF, (open ? 0.75f : 0.08f + 0.10f * hover) * intro));
        int glyph = s(icon == MARK ? 20 : 22);
        int tint;
        if (icon == MARK) {
            tint = alpha(0xFFFFFFFF, intro * (0.8f + 0.2f * hover));
        } else {
            tint = alpha(button.active ? mix(0xFFC3CCDA, TEXT_BRIGHT, hover) : 0xFF4B5563, intro);
        }
        int tex = icon == MARK ? 96 : 64;
        g.blit(RenderPipelines.GUI_TEXTURED, icon, x + (size - glyph) / 2, y + (size - glyph) / 2,
                0.0f, 0.0f, glyph, glyph, tex, tex, tex, tex, tint);
        if (badge > 0 && !open) {
            int d = s(9);
            VantaUiTheme.fillSquircle(g, x + size - d - s(5), y + s(5), d, d, d / 2, alpha(accent, intro));
        }
        if (button.isFocused()) {
            VantaUiTheme.outlineSquircle(g, x - 1, y - 1, size + 2, size + 2, s(12), alpha(accent, 0.8f), 1);
        }
    }

    private void card(GuiGraphics g, int x, int y, int w, int h, int radius, float a) {
        VantaUiTheme.fillSquircle(g, x, y, w, h, radius, alpha(CARD, a));
        VantaUiTheme.outlineSquircle(g, x, y, w, h, radius, alpha(CARD_EDGE, a));
    }

    private void renderLeftCards(GuiGraphics g, float a, int lift, int accent) {
        // Account.
        int y = accountY + lift;
        card(g, leftX, y, leftW, accountH, s(16), a);
        String name = mc.getUser() != null ? mc.getUser().getName() : "";
        int head = s(40);
        int hx = leftX + s(12), hy = y + (accountH - head) / 2;
        drawHead(g, hx, hy, head);
        if (!name.isBlank()) {
            text(g, name, hx + head + s(14), y + accountH / 2 - s(9), 15, alpha(TEXT_BRIGHT, a), true);
            text(g, "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().id(),
                    hx + head + s(14), y + accountH / 2 + s(10), 12, alpha(TEXT_DIM, a), false);
        }

        // Performance, kept inside the account card instead of a card of its own.
        int fps = Math.max(0, mc.getFps());
        int dot = s(7);
        int fpsColor = fps >= 60 ? GOOD : fps >= 30 ? WARN : BAD;
        String fpsLabel = fps + " FPS";
        int fw = textWidth(fpsLabel, 12, true);
        int fx = leftX + leftW - s(16) - fw;
        text(g, fpsLabel, fx, y + accountH / 2, 12, alpha(0xFFC4CCD8, a), true);
        VantaUiTheme.fillSquircle(g, fx - dot - s(8), y + accountH / 2 - dot / 2, dot, dot, dot / 2, alpha(fpsColor, a));

        // Links.
        y = linksY + lift;
        card(g, leftX, y, leftW, linksH, s(16), a);
        for (AbstractButton button : links) {
            Item item = items.get(button);
            int color = mix(0xFFB4BFD1, TEXT_BRIGHT, item.hover);
            String label = item.label;
            int tw = textWidth(label, 12, false);
            text(g, label, button.getX() + (button.getWidth() - tw) / 2.0f,
                    button.getY() + button.getHeight() / 2, 12, alpha(color, a), false);
            if (item.hover > 0.01f) {
                VantaUiTheme.fillSquircle(g, button.getX() + (button.getWidth() - tw) / 2, button.getY() + button.getHeight() - s(6),
                        tw, Math.max(1, s(1)), 0, alpha(accent, item.hover * a));
            }
        }
    }

    private void drawHead(GuiGraphics g, int x, int y, int size) {
        if (mc.getUser() == null) return;
        try {
            UUID id = mc.getUser().getProfileId();
            if (id == null) return;
            if (!id.equals(cachedProfileId) || cachedSkin == null) {
                cachedProfileId = id;
                GameProfile profile = mc.getGameProfile() != null
                        ? mc.getGameProfile() : new GameProfile(id, mc.getUser().getName());
                cachedSkin = mc.getSkinManager().createLookup(profile, false);
            }
            PlayerSkin skin = cachedSkin.get();
            if (skin != null) PlayerFaceRenderer.draw(g, skin, x, y, size);
        } catch (RuntimeException error) {
            cachedSkin = null;
        }
    }

    private void renderPlayCard(GuiGraphics g, float a, int lift, int accent) {
        int x = cardX, y = cardY + lift;
        VantaUiTheme.fillSquircle(g, x, y, cardW, cardH, s(20), alpha(CARD, a));
        VantaUiTheme.outlineSquircle(g, x, y, cardW, cardH, s(20), alpha(CARD_EDGE, a));
        int pad = s(14);
        int innerW = cardW - pad * 2;

        // Segmented control.
        int segY = y + pad;
        VantaUiTheme.fillSquircle(g, x + pad, segY, innerW, s(34), s(12), alpha(0xFF05080E, 0.7f * a));
        int half = innerW / 2;
        int thumbX = x + pad + s(3) + Math.round(segSlide * half);
        VantaUiTheme.fillSquircle(g, thumbX, segY + s(3), half - s(3), s(28), s(10), alpha(accent, a));
        for (AbstractButton seg : new AbstractButton[]{segSingle, segMulti}) {
            Item item = items.get(seg);
            boolean active = (seg == segSingle) == (tab == Tab.SINGLEPLAYER);
            int color = active ? TEXT_BRIGHT : mix(0xFF8D99AD, TEXT_BRIGHT, item.hover);
            String label = item.label;
            int tw = textWidth(label, 13, true);
            text(g, label, seg.getX() + (seg.getWidth() - tw) / 2.0f, seg.getY() + seg.getHeight() / 2,
                    13, alpha(color, a), true);
            if (seg.isFocused()) {
                VantaUiTheme.outlineSquircle(g, seg.getX(), seg.getY(), seg.getWidth(), seg.getHeight(),
                        s(10), alpha(TEXT_BRIGHT, 0.8f));
            }
        }

        // Selected world or server.
        int bodyY = segY + s(34) + s(10);
        int tile = s(52);
        Entry entry = entries.isEmpty() ? null : entries.get(Math.min(selected, entries.size() - 1));
        drawTile(g, x + pad, bodyY, tile, entry, a, accent, s(16), true);
        int tx = x + pad + tile + s(10);
        int maxW = cardW - pad - (tx - x);
        if (entry == null) {
            boolean waiting = tab == Tab.SINGLEPLAYER && worlds == null;
            text(g, waiting ? "Loading worlds" : tab == Tab.SINGLEPLAYER ? "No worlds yet" : "No servers yet",
                    tx, bodyY + s(16), 14, alpha(TEXT_BRIGHT, a), true);
            text(g, tab == Tab.SINGLEPLAYER ? "Create your first one" : "Add a server to play online",
                    tx, bodyY + s(34), 11, alpha(TEXT_DIM, a), false);
        } else {
            text(g, fit(entry.title(), 17, true, maxW), tx, bodyY + s(14), 15, alpha(TEXT_BRIGHT, a), true);
            renderSubtitle(g, entry, tx, bodyY + s(34), maxW, a);
        }

        // Play button.
        Item playItem = items.get(play);
        int playY = play.getY();
        int btnColor = mix(accent, 0xFFFFFFFF, 0.14f * playItem.hover);
        VantaAmbience.glow(g, play.getX() + play.getWidth() / 2, playY + play.getHeight() / 2 + s(4),
                Math.round(play.getWidth() * 0.62f), accent, (0.10f + 0.14f * playItem.hover) * a);
        VantaUiTheme.fillSquircle(g, play.getX(), playY, play.getWidth(), play.getHeight(), s(14), alpha(btnColor, a));
        if (VantaClientPreferences.get().animations) {
            VantaAmbience.sheen(g, play.getX(), playY, play.getWidth(), play.getHeight(),
                    VantaLobbyScene.elapsedMillis(), playItem.hover * a);
        }
        String label = entries.isEmpty() ? (tab == Tab.SINGLEPLAYER ? "Create world" : "Add server") : "Play";
        int lw = textWidth(label, 17, true);
        text(g, label, play.getX() + (play.getWidth() - lw) / 2.0f, playY + play.getHeight() / 2, 17,
                alpha(TEXT_BRIGHT, a), true);
        if (play.isFocused()) {
            VantaUiTheme.outlineSquircle(g, play.getX() - 1, playY - 1, play.getWidth() + 2, play.getHeight() + 2,
                    s(15), alpha(TEXT_BRIGHT, 0.85f), 1);
        }

        // Recent row, only when there is something to show.
        if (entries.isEmpty()) return;
        int rowY = recent[0].getY();
        text(g, "Recent", x + pad, rowY + s(14), 11, alpha(TEXT_DIM, a), false);
        for (int i = 0; i < recent.length; i++) {
            if (!recent[i].visible) continue;
            Item item = items.get(recent[i]);
            boolean isSelected = i == Math.min(selected, entries.size() - 1);
            drawTile(g, recent[i].getX(), rowY, recent[i].getWidth(), entries.get(i), a, accent, s(10), false);
            VantaUiTheme.outlineSquircle(g, recent[i].getX() - 2, rowY - 2, recent[i].getWidth() + 4,
                    recent[i].getHeight() + 4, s(12),
                    alpha(isSelected ? accent : 0xFFFFFFFF, (isSelected ? 0.95f : 0.20f * item.hover) * a), 1);
        }
        Item allItem = items.get(all);
        int count = tab == Tab.SINGLEPLAYER ? (worlds == null ? 0 : worlds.size()) : entries.size();
        String allLabel = "All " + count;
        VantaUiTheme.fillSquircle(g, all.getX(), all.getY(), all.getWidth(), all.getHeight(), s(10),
                alpha(mix(0x33FFFFFF, 0x55FFFFFF, allItem.hover), a));
        int aw = textWidth(allLabel, 12, true);
        text(g, allLabel, all.getX() + (all.getWidth() - aw) / 2.0f, all.getY() + all.getHeight() / 2, 12,
                alpha(TEXT_BRIGHT, a), true);
    }

    private void renderSubtitle(GuiGraphics g, Entry entry, int x, int y, int maxW, float a) {
        if (entry.world() != null) {
            text(g, "Played " + ago(System.currentTimeMillis() - entry.world().getLastPlayed()), x, y, 12,
                    alpha(TEXT_DIM, a), false);
            return;
        }
        ServerData data = entry.server();
        ServerData.State state = data.state();
        int dotColor;
        String detail;
        switch (state) {
            case SUCCESSFUL -> {
                dotColor = GOOD;
                String online = data.players != null ? String.format(Locale.ROOT, "%,d online", data.players.online()) : "Online";
                detail = online + "   " + data.ping + " ms";
            }
            case UNREACHABLE -> {
                dotColor = BAD;
                detail = "Offline";
            }
            case INCOMPATIBLE -> {
                dotColor = WARN;
                detail = "Incompatible";
            }
            default -> {
                dotColor = 0xFF6B7789;
                detail = "Checking";
            }
        }
        int dot = s(8);
        VantaUiTheme.fillSquircle(g, x, y - dot / 2, dot, dot, dot / 2, alpha(dotColor, a));
        text(g, fit(detail, 12, false, maxW - dot - s(8)), x + dot + s(8), y, 12, alpha(0xFFC4CCD8, a), false);
    }

    private void drawTile(GuiGraphics g, int x, int y, int size, Entry entry, float a, int accent, int radius,
                          boolean large) {
        int seed = entry == null ? 0 : entry.title().hashCode();
        float hue = ((seed & 0x7FFFFFFF) % 360) / 360.0f;
        int base = entry == null ? 0xFF1B2433 : java.awt.Color.HSBtoRGB(hue, 0.42f, 0.62f) | 0xFF000000;
        int shade = mix(base, 0xFF05080E, 0.45f);
        VantaUiTheme.fillSquircle(g, x, y, size, size, radius, alpha(shade, a));
        VantaUiTheme.fillSquircle(g, x + 1, y + 1, size - 2, size - 2, Math.max(1, radius - 1),
                alpha(mix(base, 0xFF0B111B, 0.35f), a));
        String letter = entry == null || entry.title().isBlank() ? "+" : entry.title().substring(0, 1).toUpperCase(Locale.ROOT);
        float fontSize = large ? 26 : 14;
        int lw = textWidth(letter, fontSize, true);
        text(g, letter, x + (size - lw) / 2.0f, y + size / 2, fontSize, alpha(TEXT_BRIGHT, a), true);
    }

    private void renderFooter(GuiGraphics g, float intro) {
        String legal = "Copyright Mojang AB. Do not distribute!";
        int lw = textWidth(legal, 10, false);
        text(g, legal, screen.width - s(28) - lw, screen.height - s(22), 10, alpha(0xFF8A96AB, intro), false);
        text(g, "Vanta Client", s(28), screen.height - s(22), 10, alpha(0xFF8A96AB, intro), false);
    }

    private void renderPopup(GuiGraphics g, int accent) {
        float p = ease(clamp01(popupProgress));
        int slide = Math.round((1.0f - p) * -s(8));
        int x = panelX;
        int y = panelY + slide;
        VantaUiTheme.fillSquircle(g, x, y, panelW, panelH, s(14), alpha(PANEL, p));
        VantaUiTheme.outlineSquircle(g, x, y, panelW, panelH, s(14), alpha(PANEL_EDGE, p));

        if (styleOpen) {
            TitleStyle current = VantaClientPreferences.get().titleStyle;
            TitleStyle[] styles = TitleStyle.values();
            for (int i = 0; i < styles.length; i++) {
                AbstractButton button = styleItems[i];
                Item item = items.get(button);
                int by = button.getY() + slide;
                if (item.hover > 0.01f) {
                    VantaUiTheme.fillSquircle(g, button.getX(), by, button.getWidth(), button.getHeight(),
                            s(9), alpha(0xFFFFFFFF, 0.07f * item.hover * p));
                }
                boolean active = styles[i] == current;
                int color = active ? TEXT_BRIGHT : mix(0xFFAAB5C6, TEXT_BRIGHT, item.hover);
                text(g, styles[i].label(), button.getX() + s(14), by + button.getHeight() / 2,
                        14, alpha(color, p), active);
                if (active) {
                    int check = s(16);
                    g.blit(RenderPipelines.GUI_TEXTURED, ICON_CHECK,
                            button.getX() + button.getWidth() - check - s(12),
                            by + (button.getHeight() - check) / 2, 0.0f, 0.0f, check, check,
                            64, 64, 64, 64, alpha(accent, p));
                }
            }
            return;
        }

        text(g, "Notifications", x + s(18), y + s(23), 15, alpha(TEXT_BRIGHT, p), true);
        if (clear.visible) {
            Item item = items.get(clear);
            int cc = mix(0xFF7D8AA0, TEXT_BRIGHT, item.hover);
            int tw = textWidth("Clear", 12, false);
            text(g, "Clear", clear.getX() + clear.getWidth() - tw - s(6), y + s(23), 12, alpha(cc, p), false);
        }
        List<VantaNotificationManager.HistoryItem> history = VantaNotificationManager.history();
        int ly = y + s(46);
        g.fill(x + s(18), ly - 1, x + panelW - s(18), ly, alpha(0xFFFFFFFF, 0.06f * p));
        if (history.isEmpty()) {
            text(g, "Nothing new", x + s(18), ly + s(30), 14, alpha(0xFFC8D0DC, p), true);
            text(g, "Downloads, profiles and sharing", x + s(18), ly + s(52), 12, alpha(0xFF6B7789, p), false);
            text(g, "updates will show up here.", x + s(18), ly + s(68), 12, alpha(0xFF6B7789, p), false);
            return;
        }
        long now = System.currentTimeMillis();
        int rows = Math.min(5, history.size());
        for (int i = 0; i < rows; i++) {
            VantaNotificationManager.HistoryItem n = history.get(i);
            int ry = ly + i * s(56);
            int dot = s(8);
            VantaUiTheme.fillSquircle(g, x + s(18), ry + s(22), dot, dot, dot / 2, alpha(typeColor(n.type()), p));
            int tx = x + s(36);
            int maxW = panelW - s(36) - s(70);
            text(g, fit(n.title(), 14, true, maxW), tx, ry + s(19), 14, alpha(TEXT_BRIGHT, p), true);
            text(g, fit(n.message(), 12, false, panelW - s(36) - s(18)), tx, ry + s(39), 12,
                    alpha(0xFF8793A6, p), false);
            String ago = ago(now - n.wallClockMillis());
            text(g, ago, x + panelW - s(18) - textWidth(ago, 11, false), ry + s(19), 11,
                    alpha(0xFF566174, p), false);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void text(GuiGraphics g, String value, float x, float centerY, float size, int color,
                      boolean medium) {
        if ((color >>> 24) == 0) return;
        VantaText.draw(g, value, x, centerY, size * u, color,
                medium ? VantaText.Weight.MEDIUM : VantaText.Weight.REGULAR);
    }

    private int textWidth(String value, float size, boolean medium) {
        return VantaText.width(value, size * u, medium ? VantaText.Weight.MEDIUM : VantaText.Weight.REGULAR);
    }

    /** Styled components are immutable and reused every frame instead of being rebuilt per draw. */
    private static Component component(String value, boolean medium) {
        Map<String, Component> cache = medium ? MEDIUM_COMPONENTS : REGULAR_COMPONENTS;
        Component component = cache.get(value);
        if (component == null) {
            component = Component.literal(value).withStyle(st -> st.withFont(medium ? MEDIUM : REGULAR));
            if (cache.size() > 512) cache.clear();
            cache.put(value, component);
        }
        return component;
    }

    private String fit(String value, float size, boolean medium, int maxWidth) {
        if (textWidth(value, size, medium) <= maxWidth) return value;
        String cut = value;
        while (cut.length() > 1 && textWidth(cut + "…", size, medium) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "…";
    }

    private static int typeColor(VantaNotificationManager.Type type) {
        return switch (type) {
            case SUCCESS -> GOOD;
            case WARNING -> WARN;
            case ERROR -> BAD;
            default -> 0xFF5B9BFF;
        };
    }

    private static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 45) return "now";
        long minutes = seconds / 60;
        if (minutes < 60) return Math.max(1, minutes) + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days == 1 ? "yesterday" : days + " days ago";
    }

    private static int alpha(int color, float factor) {
        int a = Math.round(((color >>> 24) & 0xFF) * clamp01(factor));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int mix(int from, int to, float t) {
        t = clamp01(t);
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int gr = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static float ease(float v) {
        float inv = 1.0f - v;
        return 1.0f - inv * inv * inv;
    }

    private static FontDescription font(String name) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath("vanta", name));
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("vanta", "textures/gui/title/" + name + ".png");
    }
}

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
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.net.URI;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Vanta title screen: a quiet left-aligned menu over the live lobby world.
 *
 * Every interactive element is a real widget (so focus, keyboard and click handling stay
 * vanilla); the widgets draw nothing themselves and this class paints them. Vanilla's own
 * Singleplayer, Multiplayer, Realms, Options and Quit buttons are reused, so their actions
 * and availability checks are unchanged.
 */
public final class VantaTitleUi {
    private static final String DISCORD_INVITE = "https://discord.gg/yG2CdjxYj";
    private static final FontDescription REGULAR = font("ui");
    private static final FontDescription MEDIUM = font("medium");
    private static final Identifier MARK = tex("mark");
    private static final Identifier ICON_CHAT = tex("chat");
    private static final Identifier ICON_BELL = tex("bell");
    private static final Identifier ICON_GRID = tex("grid");
    private static final Identifier ICON_CHEVRON = tex("chevron");
    private static final Identifier ICON_CHECK = tex("check");
    private static final Identifier SCRIM_LEFT = tex("scrim_left");
    private static final Identifier SCRIM_BOTTOM = tex("scrim_bottom");
    private static final Identifier SCRIM_TOP = tex("scrim_top");

    private static final int TEXT_DIM = 0xFF9AA6B8;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int PANEL = 0xF2101621;
    private static final int PANEL_EDGE = 0x2EFFFFFF;

    private static Boolean modMenuPresent;
    private static UUID cachedProfileId;
    private static Supplier<PlayerSkin> cachedSkin;

    private static final class Item {
        final String label;
        float hover;
        Item(String label) { this.label = label; }
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Screen screen;
    private final Map<AbstractButton, Item> items = new IdentityHashMap<>();
    private final List<AbstractButton> nav = new ArrayList<>();
    private final List<AbstractButton> links = new ArrayList<>();
    private final AbstractButton[] styleItems = new AbstractButton[3];
    private AbstractButton discord;
    private AbstractButton bell;
    private AbstractButton modMenu;
    private AbstractButton vantaMenu;
    private AbstractButton styleToggle;
    private AbstractButton clear;
    private AbstractButton scrim;
    private int primaryCount;
    private boolean notificationsOpen;
    private boolean styleOpen;
    private float popupProgress;
    private long lastFrame = System.nanoTime();
    private float u;
    private int panelX, panelY, panelW, panelH;

    public VantaTitleUi(Screen screen) {
        this.screen = screen;
    }

    // ------------------------------------------------------------------ construction

    public void init(ScreenAccessor accessor) {
        u = Math.max(0.8f, Math.min(2.0f, screen.height / 420.0f));
        AbstractButton singleplayer = null, multiplayer = null, realms = null, options = null, quit = null;
        List<AbstractButton> extra = new ArrayList<>();
        for (GuiEventListener child : new ArrayList<>(screen.children())) {
            if (!(child instanceof AbstractButton button)) continue;
            String label = button.getMessage().getString();
            String key = label.toLowerCase(Locale.ROOT);
            if (key.contains("language") || key.contains("accessibility")
                    || key.startsWith("copyright")) {
                button.visible = false;
                button.active = false;
            } else if (key.contains("singleplayer") || key.contains("single player")) {
                singleplayer = button;
            } else if (key.contains("multiplayer")) {
                multiplayer = button;
            } else if (key.contains("realms")) {
                realms = button;
            } else if (key.startsWith("options")) {
                options = button;
            } else if (key.startsWith("quit")) {
                quit = button;
            } else if (!label.isBlank()) {
                extra.add(button);
            }
        }
        // Vanilla buttons draw nothing in this style; their labels are painted by render().
        AbstractButton[] order = {singleplayer, multiplayer, realms, options, quit};
        primaryCount = (singleplayer != null ? 1 : 0) + (multiplayer != null ? 1 : 0) + (realms != null ? 1 : 0);
        for (AbstractButton button : order) {
            if (button == null) continue;
            String label = trimLabel(button.getMessage().getString());
            items.put(button, new Item(label));
            button.setMessage(Component.empty());
            nav.add(button);
        }
        // Mod Menu and other mods place their own buttons here; keep them reachable as quiet links.
        for (AbstractButton button : extra) {
            String label = trimLabel(button.getMessage().getString());
            if (label.equalsIgnoreCase("mods")) {
                button.visible = false;
                button.active = false;
                continue;
            }
            items.put(button, new Item(label));
            button.setMessage(Component.empty());
            links.add(button);
        }

        links.add(add(accessor, "Share", () -> mc.setScreen(new VantaWorldShareScreen(screen))));
        links.add(add(accessor, "Language", () -> mc.setScreen(new LanguageSelectScreen(
                screen, mc.options, mc.getLanguageManager()))));
        links.add(add(accessor, "Accessibility", () -> mc.setScreen(
                new AccessibilityOptionsScreen(screen, mc.options))));
        links.add(add(accessor, "Credits", () -> mc.setScreen(new CreditsAndAttributionScreen(screen))));

        discord = add(accessor, "Discord", () -> openInvite());
        bell = add(accessor, "Notifications", this::toggleNotifications);
        modMenu = add(accessor, "Mods", this::openModMenu);
        modMenu.active = modMenuPresent();
        vantaMenu = add(accessor, "Vanta menu", () -> mc.setScreen(new VantaSettingsScreen(screen)));
        styleToggle = add(accessor, "Menu style", this::toggleStyle);

        TitleStyle[] styles = TitleStyle.values();
        for (int i = 0; i < styles.length; i++) {
            TitleStyle style = styles[i];
            styleItems[i] = add(accessor, style.label(), () -> chooseStyle(style));
        }
        clear = add(accessor, "Clear", () -> {
            VantaNotificationManager.clearHistory();
        });
        scrim = add(accessor, "", this::closePopups);
        layout();
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

    // ------------------------------------------------------------------ layout

    private int s(float value) {
        return Math.round(value * u);
    }

    private void layout() {
        int w = screen.width;
        int h = screen.height;

        // Navigation, left aligned and vertically centred slightly above the middle.
        int rowH = s(40);
        int groupGap = s(14);
        int total = nav.size() * rowH + (nav.size() > primaryCount ? groupGap : 0);
        int left = Math.max(s(46), Math.round(w * 0.065f));
        int y = Math.round(h * 0.5f - total / 2.0f);
        for (int i = 0; i < nav.size(); i++) {
            if (i == primaryCount) y += groupGap;
            bounds(nav.get(i), left - s(18), y, s(300), rowH);
            y += rowH;
        }

        // Top-right icon row.
        int icon = s(34);
        int gap = s(8);
        int margin = s(30);
        int top = s(24);
        AbstractButton[] row = {discord, bell, modMenu, vantaMenu, styleToggle};
        int x = w - margin - icon;
        for (int i = row.length - 1; i >= 0; i--) {
            bounds(row[i], x, top, icon, icon);
            x -= icon + gap;
        }

        // Bottom-right quiet links, right to left.
        int linkX = w - margin;
        int linkY = h - s(30);
        for (int i = links.size() - 1; i >= 0; i--) {
            AbstractButton button = links.get(i);
            int tw = textWidth(items.get(button).label, 12, false) + s(8);
            bounds(button, linkX - tw, linkY - s(10), tw, s(24));
            linkX -= tw + s(10);
        }

        // Popups hang under the icon row, right aligned with the last icon.
        int right = w - margin;
        panelY = top + icon + s(10);
        if (styleOpen) {
            panelW = s(196);
            panelH = TitleStyle.values().length * s(38) + s(12);
        } else {
            panelW = s(330);
            int rows = Math.max(1, Math.min(5, VantaNotificationManager.history().size()));
            panelH = s(46) + (VantaNotificationManager.history().isEmpty() ? s(92) : rows * s(56)) + s(6);
        }
        panelX = right - panelW;
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
        button.setX(x);
        button.setY(y);
        button.setWidth(w);
        button.setHeight(h);
    }

    // ------------------------------------------------------------------ actions

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
        float blend = 1.0f - (float) Math.exp(-dt * 16.0f);
        popupProgress += (1.0f - popupProgress) * blend;
        if (notificationsOpen) layout(); // list length can change while open

        int w = screen.width;
        int h = screen.height;
        float intro = ease(clamp01(VantaLobbyScene.elapsedMillis() / 520.0f));
        int accent = new VantaUiTheme().accent();

        for (Map.Entry<AbstractButton, Item> entry : items.entrySet()) {
            AbstractButton button = entry.getKey();
            Item item = entry.getValue();
            float target = button.active && button.visible && (button.isHoveredOrFocused()) ? 1.0f : 0.0f;
            item.hover += (target - item.hover) * blend;
            if (Math.abs(target - item.hover) < 0.01f) item.hover = target;
        }

        // Edge scrims keep text legible over any lobby scene.
        g.blit(RenderPipelines.GUI_TEXTURED, SCRIM_LEFT, 0, 0, 0.0f, 0.0f,
                Math.round(w * 0.62f), h, 256, 4, 256, 4, alpha(0xFFFFFFFF, intro));
        g.blit(RenderPipelines.GUI_TEXTURED, SCRIM_BOTTOM, 0, h - s(150), 0.0f, 0.0f,
                w, s(150), 4, 128, 4, 128, alpha(0xFFFFFFFF, intro));
        g.blit(RenderPipelines.GUI_TEXTURED, SCRIM_TOP, 0, 0, 0.0f, 0.0f,
                w, s(120), 4, 128, 4, 128, alpha(0xFFFFFFFF, intro));

        renderBrand(g, intro);
        renderNav(g, intro, accent);
        renderIcons(g, intro, accent);
        renderAccount(g, intro);
        renderFooter(g, intro);
        if (styleOpen || notificationsOpen) renderPopup(g, accent);
    }

    private void renderBrand(GuiGraphics g, float intro) {
        int x = s(30);
        int cy = s(24) + s(17);
        int mark = s(26);
        g.blit(RenderPipelines.GUI_TEXTURED, MARK, x, cy - mark / 2, 0.0f, 0.0f, mark, mark,
                256, 256, 256, 256, alpha(0xFFFFFFFF, intro));
        int tx = x + mark + s(10);
        text(g, "Vanta", tx, cy, 18, alpha(TEXT_BRIGHT, intro), true);
        int next = tx + textWidth("Vanta", 18, true) + s(6);
        text(g, "Client", next, cy, 18, alpha(0xFF7D8AA0, intro), false);
    }

    private void renderNav(GuiGraphics g, float intro, int accent) {
        long elapsed = VantaLobbyScene.elapsedMillis();
        for (int i = 0; i < nav.size(); i++) {
            AbstractButton button = nav.get(i);
            Item item = items.get(button);
            float t = ease(clamp01((elapsed - 140 - i * 70L) / 420.0f));
            float hover = item.hover;
            int cy = button.getY() + button.getHeight() / 2;
            int baseX = button.getX() + s(18);
            float slide = (1.0f - t) * -s(18) + hover * s(10);
            int color = mix(button.active ? 0xFFB3BDCC : 0xFF5F6978, TEXT_BRIGHT, hover);
            text(g, item.label, baseX + slide, cy, 22, alpha(color, t), true);

            if (hover > 0.01f) {
                int barH = s(20);
                int barX = button.getX() + s(4) - Math.round((1.0f - hover) * s(6));
                VantaUiTheme.fillSquircle(g, barX, cy - barH / 2, Math.max(2, s(3)), barH,
                        Math.max(1, s(1)), alpha(accent, hover * t));
            }
            if (button.isFocused()) {
                VantaUiTheme.outlineSquircle(g, button.getX(), button.getY() + s(2),
                        s(260), button.getHeight() - s(4), s(10), alpha(accent, 0.7f));
            }
        }
    }

    private void renderIcons(GuiGraphics g, float intro, int accent) {
        drawIconButton(g, discord, ICON_CHAT, intro, accent, 0, false);
        drawIconButton(g, bell, ICON_BELL, intro, accent, VantaNotificationManager.unreadCount(),
                notificationsOpen);
        drawIconButton(g, modMenu, ICON_GRID, intro, accent, 0, false);
        drawIconButton(g, vantaMenu, MARK, intro, accent, 0, false);
        drawIconButton(g, styleToggle, ICON_CHEVRON, intro, accent, 0, styleOpen);
    }

    private void drawIconButton(GuiGraphics g, AbstractButton button, Identifier icon, float intro,
                                int accent, int badge, boolean open) {
        Item item = items.get(button);
        float hover = Math.max(item.hover, open ? 1.0f : 0.0f);
        int x = button.getX(), y = button.getY(), size = button.getWidth();
        int fill = mix(0x4A0B111B, 0x8C1C2636, hover);
        VantaUiTheme.fillSquircle(g, x, y, size, size, s(11), alpha(fill, intro));
        VantaUiTheme.outlineSquircle(g, x, y, size, size, s(11),
                alpha(open ? accent : 0xFFFFFFFF, (open ? 0.75f : 0.06f + 0.10f * hover) * intro));
        int glyph = s(icon == MARK ? 20 : 22);
        int tint;
        if (icon == MARK) {
            tint = alpha(0xFFFFFFFF, intro * (0.78f + 0.22f * hover));
        } else {
            tint = alpha(button.active ? mix(0xFFB9C3D2, TEXT_BRIGHT, hover) : 0xFF4B5563, intro);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, icon, x + (size - glyph) / 2,
                y + (size - glyph) / 2, 0.0f, 0.0f, glyph, glyph,
                icon == MARK ? 256 : 64, icon == MARK ? 256 : 64,
                icon == MARK ? 256 : 64, icon == MARK ? 256 : 64, tint);
        if (badge > 0 && !open) {
            int d = s(9);
            VantaUiTheme.fillSquircle(g, x + size - d - s(5), y + s(5), d, d, d / 2,
                    alpha(accent, intro));
        }
        if (button.isFocused()) {
            VantaUiTheme.outlineSquircle(g, x - 1, y - 1, size + 2, size + 2, s(12),
                    alpha(accent, 0.8f), 1);
        }
    }

    private void renderAccount(GuiGraphics g, float intro) {
        if (mc.getUser() == null) return;
        String name = mc.getUser().getName();
        if (name == null || name.isBlank()) return;
        int head = s(26);
        int pad = s(6);
        int nameW = textWidth(name, 14, true);
        int w = pad + head + s(10) + nameW + s(16);
        int h = head + pad * 2;
        int x = s(30);
        int y = screen.height - s(30) - h;
        VantaUiTheme.fillSquircle(g, x, y, w, h, s(12), alpha(0xFF0B111B, 0.62f * intro));
        VantaUiTheme.outlineSquircle(g, x, y, w, h, s(12), alpha(0xFFFFFFFF, 0.07f * intro));
        try {
            UUID id = mc.getUser().getProfileId();
            if (id != null) {
                if (!id.equals(cachedProfileId) || cachedSkin == null) {
                    cachedProfileId = id;
                    GameProfile profile = mc.getGameProfile() != null
                            ? mc.getGameProfile() : new GameProfile(id, name);
                    cachedSkin = mc.getSkinManager().createLookup(profile, false);
                }
                PlayerSkin skin = cachedSkin.get();
                if (skin != null) PlayerFaceRenderer.draw(g, skin, x + pad, y + pad, head);
            }
        } catch (RuntimeException error) {
            cachedSkin = null;
        }
        text(g, name, x + pad + head + s(10), y + h / 2, 14, alpha(TEXT_BRIGHT, intro), true);
    }

    private void renderFooter(GuiGraphics g, float intro) {
        for (AbstractButton button : links) {
            Item item = items.get(button);
            int color = mix(0xFF7D8AA0, TEXT_BRIGHT, item.hover);
            text(g, item.label, button.getX() + s(4), button.getY() + button.getHeight() / 2,
                    12, alpha(color, intro), false);
        }
        String version = "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().id();
        int vw = textWidth(version, 11, false);
        text(g, version, (screen.width - vw) / 2.0f, screen.height - s(30) - s(2), 11,
                alpha(0xFF566174, intro), false);
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
        float f = size * u / 13.0f;
        Component component = Component.literal(value).withStyle(st -> st.withFont(medium ? MEDIUM : REGULAR));
        g.pose().pushMatrix();
        g.pose().translate(x, centerY);
        g.pose().scale(f, f);
        g.drawString(mc.font, component, 0, -2, color, false);
        g.pose().popMatrix();
    }

    private int textWidth(String value, float size, boolean medium) {
        Component component = Component.literal(value).withStyle(st -> st.withFont(medium ? MEDIUM : REGULAR));
        return Math.round(mc.font.width(component) * size * u / 13.0f);
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
            case SUCCESS -> 0xFF45D6A0;
            case WARNING -> 0xFFF2B84B;
            case ERROR -> 0xFFEF5D6B;
            default -> 0xFF5B9BFF;
        };
    }

    private static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 45) return "now";
        long minutes = seconds / 60;
        if (minutes < 60) return Math.max(1, minutes) + "m";
        long hours = minutes / 60;
        return hours < 24 ? hours + "h" : hours / 24 + "d";
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

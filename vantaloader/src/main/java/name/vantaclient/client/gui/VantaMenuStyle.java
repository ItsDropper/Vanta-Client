package name.vantaclient.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.gui.compose.typography.VantaTypography;
import name.vantaclient.client.gui.lobby.VantaLobbyCamera;
import name.vantaclient.client.gui.lobby.VantaLobbyRenderer;
import name.vantaclient.client.gui.lobby.VantaLobbyScene;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Purpose-built title-screen styling; gameplay and non-title-screen widgets retain vanilla rendering. */
public final class VantaMenuStyle {
    private static final Identifier VANTA_MARK = Identifier.fromNamespaceAndPath(
            "dropperutils", "textures/gui/vanta_logo_transparent.png");
    private static final FontDescription BRAND_FONT = new FontDescription.Resource(
            VantaTypography.font(VantaTypography.Role.DISPLAY));
    private static final FontDescription UI_FONT = new FontDescription.Resource(
            VantaTypography.font(VantaTypography.Role.BODY));
    private static final FontDescription CAPTION_FONT = new FontDescription.Resource(
            VantaTypography.font(VantaTypography.Role.CAPTION));
    private static final int MARK_TEXTURE_WIDTH = 962;
    private static final int MARK_TEXTURE_HEIGHT = 702;

    private static UUID cachedProfileId;
    private static Supplier<PlayerSkin> cachedSkinLookup;
    private static PlayerModel cachedPlayerModel;
    private static PlayerModelType cachedPlayerModelType;
    private static final Map<AbstractButton, Float> BUTTON_HOVER = new WeakHashMap<>();

    private VantaMenuStyle() {}

    /** Transparent title-screen controls with restrained hover and keyboard-focus feedback. */
    public static void renderButtonSurface(GuiGraphics graphics, AbstractButton button) {
        if (!(Minecraft.getInstance().screen instanceof TitleScreen)) return;
        // The Vanta lobby style paints its own controls; only the v1 menu uses this surface.
        if (name.vantaclient.client.config.VantaClientPreferences.get().titleStyle
                != name.vantaclient.client.config.VantaClientPreferences.TitleStyle.CLASSIC) return;

        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        if (width < 4 || height < 4) return;

        float target = button.isActive() && (button.isHovered() || button.isFocused())
                ? 1.0f : 0.0f;
        float current = BUTTON_HOVER.getOrDefault(button, 0.0f);
        current += (target - current) * 0.24f;
        if (Math.abs(target - current) < 0.012f) current = target;
        BUTTON_HOVER.put(button, current);

        int accent = new VantaUiTheme().accent();
        if (current > 0.01f || button.isFocused()) {
            int surfaceAlpha = Math.round((button.isFocused() ? 0x68 : 0x42) * current);
            fillRounded(graphics, x, y, width, height, 6,
                    withAlpha(VantaUiTheme.SURFACE_HOVER, surfaceAlpha));
        }

        int borderAlpha = Math.round((button.isActive() ? 42 : 26)
                + ((button.isFocused() ? 220 : 150) - (button.isActive() ? 42 : 26)) * current);
        int border = withAlpha(button.isFocused() ? accent : 0xFF8FA5C0, borderAlpha);
        graphics.fill(x + 4, y, x + width - 4, y + 1, border);
        graphics.fill(x + 4, y + height - 1, x + width - 4, y + height, border);
        graphics.fill(x, y + 4, x + 1, y + height - 4, border);
        graphics.fill(x + width - 1, y + 4, x + width, y + height - 4, border);

        if (current > 0.01f && button.isFocused()) {
            graphics.fill(x + 2, y + 5, x + 3, y + height - 5,
                    withAlpha(accent, Math.round(230 * current)));
        }
    }

    public static FontDescription uiFont() {
        return UI_FONT;
    }

    /** Returns a compact settings shortcut position in the secondary action row. */
    public static int[] titleBrandBounds(int screenWidth, int screenHeight) {
        if (screenWidth < 220 || screenHeight < 180) return null;
        int left = menuLeft(screenWidth);
        int width = menuButtonWidth(screenWidth);
        int buttonHeight = menuButtonHeight(screenHeight);
        int gap = menuButtonGap(screenHeight);
        int top = menuTop(screenHeight);
        boolean compact = screenHeight < 420 || screenWidth < 420;
        int secondaryHeight = compact ? 20 : 27;
        int y = top + 3 * (buttonHeight + gap) + (compact ? 4 : 8) + secondaryHeight + (compact ? 3 : 6);
        return new int[]{left, Math.max(0, Math.min(screenHeight - (compact ? 22 : 48), y)),
                width, compact ? 18 : 24};
    }

    public static int menuLeft(int screenWidth) {
        int width = menuButtonWidth(screenWidth);
        return Math.max(8, screenWidth - width - Math.max(12, Math.round(screenWidth * 0.035f)));
    }

    public static int menuButtonWidth(int screenWidth) {
        if (screenWidth <= 0) return 0;
        return Math.max(1, Math.min(Math.max(1, screenWidth - 24),
                Math.max(120, Math.min(280, Math.round(screenWidth * 0.36f)))));
    }

    public static int menuButtonHeight(int screenHeight) {
        return screenHeight < 240 ? 18 : screenHeight < 420 ? 22 : 30;
    }

    public static int menuButtonGap(int screenHeight) {
        return screenHeight < 240 ? 1 : screenHeight < 420 ? 3 : 8;
    }

    public static int menuTop(int screenHeight) {
        boolean compact = screenHeight < 420;
        int height = menuButtonHeight(screenHeight);
        int gap = menuButtonGap(screenHeight);
        int settingsHeight = compact ? 18 : 24;
        int secondaryHeight = compact ? 20 : 27;
        int menuStackHeight = 3 * (height + gap) + (compact ? 4 : 8)
                + secondaryHeight + (compact ? 3 : 6) + settingsHeight;
        int utilityTop = screenHeight - ((screenHeight < 520) ? 48 : 88);
        int maximumTop = Math.max(0, utilityTop - menuStackHeight - 4);
        int preferredTop = Math.max(screenHeight / 2 - (compact ? 24 : 10), screenHeight * 48 / 100);
        return Math.max(0, Math.min(preferredTop, maximumTop));
    }

    private static int brandLeft(int screenWidth) {
        return Math.max(22, Math.round(screenWidth * 0.055f));
    }

    /** Draws Vanta's standalone V mark above the complete wordmark. */
    public static void renderTitleBranding(GuiGraphics graphics, int screenWidth,
                                           int screenHeight, long elapsedMillis) {
        if (screenHeight < 220 || screenWidth < 220) return;
        float progress = easeOut(Math.min(1.0f, Math.max(0.0f, elapsedMillis / 420.0f)));
        int alpha = Math.round(progress * 255.0f);
        boolean compact = screenHeight < 420 || screenWidth < 420;
        int markWidth = compact ? 28 : screenWidth < 700 ? 42 : 56;
        int markHeight = compact ? 21 : screenWidth < 700 ? 31 : 41;
        VantaLobbyCamera camera = VantaLobbyScene.camera();
        int x = 28 + Math.round(camera.brandOffsetX());
        int y = (compact ? 7 : 12) + Math.round(camera.brandOffsetY());
        graphics.blit(RenderPipelines.GUI_TEXTURED, VANTA_MARK,
                x, y, 0.0f, 0.0f, markWidth, markHeight,
                MARK_TEXTURE_WIDTH, MARK_TEXTURE_HEIGHT,
                MARK_TEXTURE_WIDTH, MARK_TEXTURE_HEIGHT, withAlpha(0xFFFFFFFF, alpha));

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int brandX = x + markWidth + 10;
        graphics.drawString(font, ComponentText.brand(), brandX, y + (compact ? 5 : 10),
                withAlpha(0xFFFFFFFF, alpha), false);
        if (!compact) {
            graphics.drawString(font, ComponentText.caption(), brandX, y + 34,
                    withAlpha(0xFFAAB4C3, Math.round(alpha * 0.78f)), false);
        }

    }

    /** Draws compact, resolution-independent utility glyphs over the secondary controls. */
    public static void renderUtilityIcon(GuiGraphics graphics, AbstractButton button, int kind) {
        if (button == null || !button.visible) return;
        int x = button.getX() + 9;
        int y = button.getY() + (button.getHeight() - 12) / 2;
        int color = button.isHovered() || button.isFocused() ? 0xFFEAF1FA : 0xFFB8C3D1;
        if (kind == 0) {
            graphics.fill(x + 5, y, x + 7, y + 12, color);
            graphics.fill(x, y + 5, x + 12, y + 7, color);
            graphics.fill(x + 2, y + 1, x + 10, y + 2, color);
            graphics.fill(x + 2, y + 10, x + 10, y + 11, color);
            graphics.fill(x + 1, y + 3, x + 2, y + 9, color);
            graphics.fill(x + 10, y + 3, x + 11, y + 9, color);
        } else if (kind == 1) {
            graphics.fill(x + 5, y, x + 8, y + 3, color);
            graphics.fill(x + 4, y + 4, x + 9, y + 8, color);
            graphics.fill(x + 1, y + 5, x + 4, y + 6, color);
            graphics.fill(x + 9, y + 5, x + 12, y + 6, color);
            graphics.fill(x + 5, y + 8, x + 7, y + 12, color);
            graphics.fill(x + 3, y + 10, x + 5, y + 12, color);
            graphics.fill(x + 7, y + 10, x + 9, y + 12, color);
        } else if (kind == 3) {
            graphics.fill(x + 2, y + 5, x + 5, y + 8, color);
            graphics.fill(x + 9, y + 1, x + 12, y + 4, color);
            graphics.fill(x + 9, y + 9, x + 12, y + 12, color);
            graphics.fill(x + 4, y + 6, x + 10, y + 7, color);
            graphics.fill(x + 9, y + 3, x + 10, y + 10, color);
        } else {
            graphics.fill(x + 1, y, x + 11, y + 1, color);
            graphics.fill(x, y + 1, x + 1, y + 11, color);
            graphics.fill(x + 11, y + 1, x + 12, y + 11, color);
            graphics.fill(x + 1, y + 11, x + 11, y + 12, color);
            graphics.fill(x + 5, y + 3, x + 7, y + 5, color);
            graphics.fill(x + 5, y + 6, x + 7, y + 10, color);
        }
    }

    /** Shows the active launcher profile and its resolved skin texture; never substitutes a hard-coded name. */
    public static void renderPlayerCard(GuiGraphics graphics, int screenWidth,
                                        int screenHeight, long elapsedMillis) {
        Minecraft client = Minecraft.getInstance();
        if (client.getUser() == null) return;

        GameProfile activeProfile = client.getGameProfile();
        UUID profileId = client.getUser().getProfileId();
        String playerName = client.getUser().getName();
        if (playerName == null || playerName.isBlank()) playerName = "Offline profile";

        boolean compact = screenHeight < 420 || screenWidth < 420;
        if (screenWidth < 400) return;
        int width = compact
                ? Math.min(168, Math.max(150, Math.round(screenWidth * 0.34f)))
                : Math.min(300, Math.max(240, Math.round(screenWidth * 0.45f)));
        int height = compact ? 76 : 152;
        VantaLobbyCamera camera = VantaLobbyScene.camera();
        int x = (compact
                ? Math.max(12, screenWidth - width - 12)
                : Math.max(12, screenWidth - width - Math.max(18, Math.round(screenWidth * 0.045f))))
                + Math.round(camera.cardOffsetX());
        int y = (compact ? 8 : Math.max(18, Math.round(screenHeight * 0.16f)))
                + Math.round(camera.cardOffsetY());
        int alpha = Math.round(255.0f * easeOut(Math.min(1.0f,
                Math.max(0.0f, elapsedMillis / 480.0f))));

        fillRounded(graphics, x, y, width, height, 9,
                withAlpha(0xFF101722, Math.round(alpha * 0.66f)));
        fillRounded(graphics, x + 1, y + 1, width - 2, height - 2, 8,
                withAlpha(0xFF1A2432, Math.round(alpha * 0.20f)));

        int previewLeft = x + 4;
        int previewTop = y + 5;
        int previewRight = x + Math.min(compact ? 58 : 104, width / 2);
        int previewBottom = y + height - 5;
        boolean skinDrawn = false;
        if (profileId != null) {
            try {
                if (!profileId.equals(cachedProfileId) || cachedSkinLookup == null) {
                    cachedProfileId = profileId;
                    GameProfile skinProfile = activeProfile != null
                            ? activeProfile : new GameProfile(profileId, playerName);
                    cachedSkinLookup = client.getSkinManager().createLookup(skinProfile, false);
                }

                PlayerSkin skin = cachedSkinLookup.get();
                if (skin != null && skin.body() != null) {
                    PlayerModelType modelType = skin.model();
                    if (cachedPlayerModel == null || cachedPlayerModelType != modelType) {
                        boolean slim = modelType == PlayerModelType.SLIM;
                        cachedPlayerModel = new PlayerModel(
                                client.getEntityModels().bakeLayer(
                                        slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER),
                                slim);
                        cachedPlayerModelType = modelType;
                    }

                    skinDrawn = VantaLobbyRenderer.renderPlayerPreview(
                            graphics, cachedPlayerModel, skin, elapsedMillis, compact,
                            previewLeft, previewTop, previewRight, previewBottom);
                }
            } catch (RuntimeException ignored) {
                cachedSkinLookup = null;
                cachedPlayerModel = null;
                cachedPlayerModelType = null;
            }
        }

        if (!skinDrawn) {
            fillRounded(graphics, previewLeft + 20, previewTop + 24, 32, 42, 7,
                    withAlpha(0xFF303B4C, alpha));
            graphics.drawCenteredString(client.font, "?", previewLeft + 36,
                    previewTop + 39, withAlpha(0xFFE0E6EF, alpha));
        }

        int textX = x + Math.min(compact ? 64 : 112, width / 2 + 8);
        int textWidth = width - (textX - x) - 10;
        graphics.drawString(client.font, ComponentText.account(), textX, y + (compact ? 10 : 22),
                withAlpha(0xFF9AA8BA, alpha), false);
        String safeName = fitName(client.font, playerName, textWidth);
        graphics.drawString(client.font, ComponentText.playerName(safeName), textX, y + (compact ? 24 : 44),
                withAlpha(0xFFF2F5FA, alpha), false);
        graphics.drawString(client.font, ComponentText.profileState(profileId != null),
                textX, y + (compact ? 42 : 66), withAlpha(0xFF9AA8BA, alpha), false);
        graphics.fill(textX, y + (compact ? 54 : 92), x + width - 12, y + (compact ? 55 : 93),
                withAlpha(0x465C6C82, alpha));
        graphics.drawString(client.font, ComponentText.profileHint(),
                textX, y + (compact ? 62 : 108), withAlpha(0xFF8794A6, alpha), false);
    }

    /** Replaces vanilla copyright/version text with a quiet client footer. */
    public static void renderFooter(GuiGraphics graphics, int screenWidth, int screenHeight) {
        graphics.fill(0, screenHeight - 22, screenWidth, screenHeight, 0xFF070A10);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, ComponentText.footerLeft(), 14, screenHeight - 15,
                0xFFAAB4C3, false);
        String right = "Vanta Client";
        graphics.drawString(font, ComponentText.footerRight(right),
                screenWidth - font.width(ComponentText.footerRight(right)) - 14,
                screenHeight - 15, 0xFFAAB4C3, false);
    }

    private static String fitName(Font font, String value, int maxWidth) {
        String candidate = value == null || value.isBlank() ? "Offline profile" : value;
        while (!candidate.isEmpty() && font.width(ComponentText.playerName(candidate)) > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (value != null && candidate.length() < value.length() && candidate.length() > 1) {
            candidate = candidate.substring(0, candidate.length() - 1) + "…";
        }
        return candidate;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static float easeOut(float value) {
        float inverse = 1.0f - value;
        return 1.0f - inverse * inverse * inverse;
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y,
                                    int width, int height, int radius, int color) {
        VantaUiTheme.fillSquircle(graphics, x, y, width, height, radius, color);
    }

    /** Keeps font styling centralized without creating a second UI framework. */
    private static final class ComponentText {
        private ComponentText() {}

        private static net.minecraft.network.chat.Component brand() {
            return net.minecraft.network.chat.Component.literal("Vanta Client")
                    .withStyle(style -> style.withFont(UI_FONT).withBold(true));
        }

        private static net.minecraft.network.chat.Component caption() {
            return net.minecraft.network.chat.Component.literal("Client experience")
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component account() {
            return net.minecraft.network.chat.Component.literal("Current account")
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component playerName(String name) {
            return net.minecraft.network.chat.Component.literal(name)
                    .withStyle(style -> style.withFont(UI_FONT));
        }

        private static net.minecraft.network.chat.Component profilePlaceholder() {
            return net.minecraft.network.chat.Component.literal("?")
                    .withStyle(style -> style.withFont(UI_FONT));
        }

        private static net.minecraft.network.chat.Component profileHint() {
            return net.minecraft.network.chat.Component.literal("Minecraft profile")
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component profileState(boolean available) {
            return net.minecraft.network.chat.Component.literal(
                    available ? "Active profile" : "Profile offline")
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component footerLeft() {
            return net.minecraft.network.chat.Component.literal("VANTA CLIENT  /  MINECRAFT " + SharedConstants.getCurrentVersion().id())
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component footerRight(String value) {
            return net.minecraft.network.chat.Component.literal(value)
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }
    }
}

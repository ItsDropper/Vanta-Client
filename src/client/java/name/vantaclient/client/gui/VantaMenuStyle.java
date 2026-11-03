package name.vantaclient.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final FontDescription BRAND_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("vanta", "brand"));
    private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("vanta", "ui"));
    private static final FontDescription CAPTION_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("vanta", "caption"));
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

        int idleAlpha = button.isActive() ? 0x42 : 0x24;
        int hoverAlpha = button.isFocused() ? 0x72 : 0x62;
        int surfaceAlpha = Math.round(idleAlpha + (hoverAlpha - idleAlpha) * current);
        fillRounded(graphics, x, y, width, height, 5,
                (surfaceAlpha << 24) | 0x00B7BEC8);

        int borderAlpha = Math.round((button.isActive() ? 58 : 34)
                + ((button.isFocused() ? 172 : 138) - (button.isActive() ? 58 : 34)) * current);
        int border = withAlpha(0xFFDCE3EC, borderAlpha);
        graphics.fill(x + 3, y, x + width - 3, y + 1, border);
        graphics.fill(x + 3, y + height - 1, x + width - 3, y + height, border);
        graphics.fill(x, y + 3, x + 1, y + height - 3, border);
        graphics.fill(x + width - 1, y + 3, x + width, y + height - 3, border);

        if (current > 0.01f) {
            if (button.isFocused()) {
                graphics.fill(x + 2, y + 5, x + 3, y + height - 5,
                        withAlpha(0xFFDDE8F5, Math.round(220 * current)));
            } else {
                graphics.fill(x + 9, y + height - 2, x + width - 9, y + height - 1,
                        withAlpha(0xFFE7EDF5, Math.round(150 * current)));
            }
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
        int y = top + 3 * (buttonHeight + gap) + 8 + 27 + 6;
        return new int[]{left, Math.min(screenHeight - 48, y), width, 24};
    }

    public static int menuLeft(int screenWidth) {
        return Math.max(0, (screenWidth - menuButtonWidth(screenWidth)) / 2);
    }

    public static int menuButtonWidth(int screenWidth) {
        return Math.max(176, Math.min(244, Math.round(screenWidth * 0.34f)));
    }

    public static int menuButtonHeight(int screenHeight) {
        return screenHeight < 320 ? 24 : 30;
    }

    public static int menuButtonGap(int screenHeight) {
        return screenHeight < 320 ? 4 : 8;
    }

    public static int menuTop(int screenHeight) {
        return Math.max(64, screenHeight / 2 - (screenHeight < 320 ? 28 : 24));
    }

    private static int brandLeft(int screenWidth) {
        return Math.max(22, Math.round(screenWidth * 0.055f));
    }

    /** Draws Vanta's standalone V mark above the complete wordmark. */
    public static void renderTitleBranding(GuiGraphics graphics, int screenWidth,
                                           int screenHeight, long elapsedMillis) {
        float progress = easeOut(Math.min(1.0f, Math.max(0.0f, elapsedMillis / 420.0f)));
        int alpha = Math.round(progress * 255.0f);
        int markWidth = screenWidth < 700 ? 42 : 56;
        int markHeight = screenWidth < 700 ? 31 : 41;
        int x = (screenWidth - markWidth) / 2;
        int y = Math.max(8, menuTop(screenHeight) - 104);
        graphics.blit(RenderPipelines.GUI_TEXTURED, VANTA_MARK,
                x, y, 0.0f, 0.0f, markWidth, markHeight,
                MARK_TEXTURE_WIDTH, MARK_TEXTURE_HEIGHT,
                MARK_TEXTURE_WIDTH, MARK_TEXTURE_HEIGHT, withAlpha(0xFFFFFFFF, alpha));

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        graphics.drawCenteredString(font, ComponentText.brand(), screenWidth / 2, y + 51,
                withAlpha(0xFFF5F7FC, alpha));
        graphics.drawCenteredString(font, ComponentText.caption(), screenWidth / 2, y + 78,
                withAlpha(0xFFAAB4C3, Math.round(alpha * 0.78f)));

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

        int width = Math.min(236, Math.max(190, Math.round(screenWidth * 0.43f)));
        boolean compact = screenHeight < 320;
        int height = compact ? 88 : 100;
        int x = Math.max(12, screenWidth - width - Math.max(18, Math.round(screenWidth * 0.045f)));
        int y = compact ? 8 : Math.max(18, Math.round(screenHeight * 0.16f));
        int alpha = Math.round(255.0f * easeOut(Math.min(1.0f,
                Math.max(0.0f, elapsedMillis / 480.0f))));

        fillRounded(graphics, x, y, width, height, 9,
                withAlpha(0xFF101722, Math.round(alpha * 0.66f)));
        fillRounded(graphics, x + 1, y + 1, width - 2, height - 2, 8,
                withAlpha(0xFF1A2432, Math.round(alpha * 0.20f)));

        int previewLeft = x + 4;
        int previewTop = y + 5;
        int previewRight = x + Math.min(80, width / 2);
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

                    AvatarRenderState pose = new AvatarRenderState();
                    pose.skin = skin;
                    pose.showHat = true;
                    pose.showJacket = true;
                    pose.showLeftPants = true;
                    pose.showRightPants = true;
                    pose.showLeftSleeve = true;
                    pose.showRightSleeve = true;
                    cachedPlayerModel.setupAnim(pose);
                    float idleYaw = (float) Math.sin(elapsedMillis / 1900.0) * 7.0f;
                    cachedPlayerModel.head.yRot = (float) Math.toRadians(idleYaw);
                    cachedPlayerModel.head.xRot = (float) Math.toRadians(-3.0f);
                    graphics.submitSkinRenderState(cachedPlayerModel,
                            skin.body().texturePath(), compact ? 34.0f : 40.0f, -4.0f, idleYaw,
                            0.0f, previewLeft, previewTop, previewRight, previewBottom);
                    skinDrawn = true;
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

        int textX = x + Math.min(88, width / 2 + 8);
        int textWidth = width - (textX - x) - 10;
        graphics.drawString(client.font, ComponentText.account(), textX, y + (compact ? 15 : 20),
                withAlpha(0xFF9AA8BA, alpha), false);
        String safeName = fitName(client.font, playerName, textWidth);
        graphics.drawString(client.font, ComponentText.playerName(safeName), textX, y + (compact ? 31 : 38),
                withAlpha(0xFFF2F5FA, alpha), false);
        graphics.drawString(client.font, ComponentText.profileState(profileId != null),
                textX, y + 56, withAlpha(0xFF9AA8BA, alpha), false);
        graphics.fill(textX, y + (compact ? 57 : 68), x + width - 12, y + (compact ? 58 : 69),
                withAlpha(0x465C6C82, alpha));
        graphics.drawString(client.font, ComponentText.profileHint(),
                textX, y + 79, withAlpha(0xFF8794A6, alpha), false);
    }

    /** Replaces vanilla copyright/version text with a quiet client footer. */
    public static void renderFooter(GuiGraphics graphics, int screenWidth, int screenHeight) {
        graphics.fill(0, screenHeight - 22, screenWidth, screenHeight, 0xFF070A10);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, ComponentText.footerLeft(), 14, screenHeight - 15,
                0xFFAAB4C3, false);
        String right = "VANTA CLIENT";
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
        if (width <= 0 || height <= 0) return;
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            } else if (row >= height - r) {
                double dy = row - (height - r) + 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    /** Keeps font styling centralized without creating a second UI framework. */
    private static final class ComponentText {
        private ComponentText() {}

        private static net.minecraft.network.chat.Component brand() {
            return net.minecraft.network.chat.Component.literal("Vanta Client")
                    .withStyle(style -> style.withFont(BRAND_FONT));
        }

        private static net.minecraft.network.chat.Component caption() {
            return net.minecraft.network.chat.Component.literal("CLIENT EXPERIENCE")
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component account() {
            return net.minecraft.network.chat.Component.literal("CURRENT ACCOUNT")
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
            return net.minecraft.network.chat.Component.literal("MINECRAFT PROFILE")
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component profileState(boolean available) {
            return net.minecraft.network.chat.Component.literal(
                    available ? "ACTIVE PROFILE" : "PROFILE OFFLINE")
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

package name.vantaclient.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;
import java.util.function.Supplier;

/** Purpose-built title-screen styling; gameplay and non-title-screen widgets retain vanilla rendering. */
public final class VantaMenuStyle {
    private static final Identifier VANTA_MARK = Identifier.fromNamespaceAndPath(
            "dropperutils", "textures/gui/vanta_logo_transparent.png");
    private static final Identifier BRAND_FONT = Identifier.fromNamespaceAndPath("vanta", "brand");
    private static final Identifier UI_FONT = Identifier.fromNamespaceAndPath("vanta", "ui");
    private static final Identifier CAPTION_FONT = Identifier.fromNamespaceAndPath("vanta", "caption");
    private static final int MARK_TEXTURE_WIDTH = 962;
    private static final int MARK_TEXTURE_HEIGHT = 702;

    private static UUID cachedProfileId;
    private static Supplier<PlayerSkin> cachedSkinLookup;

    private VantaMenuStyle() {}

    /** Transparent title-screen controls with restrained hover and keyboard-focus feedback. */
    public static void renderButtonSurface(GuiGraphics graphics, AbstractButton button) {
        if (!(Minecraft.getInstance().screen instanceof TitleScreen)) return;

        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        if (width < 4 || height < 4) return;

        if (!button.isActive()) {
            fillRounded(graphics, x, y, width, height, 5, 0x0EFFFFFF);
        } else if (button.isFocused()) {
            fillRounded(graphics, x, y, width, height, 5, 0x36FFFFFF);
            graphics.fill(x + 2, y + 5, x + 3, y + height - 5, 0xD8DDE8F4);
        } else if (button.isHovered()) {
            fillRounded(graphics, x, y, width, height, 5, 0x20FFFFFF);
            graphics.fill(x + 8, y + height - 2, x + width - 8, y + height - 1,
                    0x88DDE8F5);
        }
    }

    public static Identifier uiFont() {
        return UI_FONT;
    }

    /** Returns a compact settings shortcut position in the secondary action row. */
    public static int[] titleBrandBounds(int screenWidth, int screenHeight) {
        if (screenWidth < 220 || screenHeight < 180) return null;
        int left = menuLeft(screenWidth);
        int width = menuButtonWidth(screenWidth);
        int top = menuTop(screenHeight);
        int y = top + 3 * 35 + 12 + 35;
        return new int[]{left, Math.min(screenHeight - 48, y), width, 27};
    }

    public static int menuLeft(int screenWidth) {
        return Math.max(18, Math.min(72, Math.round(screenWidth * 0.075f)));
    }

    public static int menuButtonWidth(int screenWidth) {
        return Math.max(132, Math.min(220, Math.round(screenWidth * 0.29f)));
    }

    public static int menuTop(int screenHeight) {
        return Math.max(78, Math.min(screenHeight - 150, screenHeight / 2 - 48));
    }

    /** Draws Vanta's standalone V mark above the complete wordmark. */
    public static void renderTitleBranding(GuiGraphics graphics, int screenWidth,
                                           int screenHeight, long elapsedMillis) {
        float progress = easeOut(Math.min(1.0f, Math.max(0.0f, elapsedMillis / 420.0f)));
        int alpha = Math.round(progress * 255.0f);
        int x = menuLeft(screenWidth);
        int y = Math.max(24, Math.round(screenHeight * 0.13f));
        float slide = (1.0f - progress) * 8.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0, slide, 0.0);

        int markWidth = 48;
        int markHeight = 35;
        graphics.blit(RenderPipelines.GUI_TEXTURED, VANTA_MARK,
                x, y, 0.0f, 0.0f, markWidth, markHeight,
                MARK_TEXTURE_WIDTH, MARK_TEXTURE_HEIGHT,
                MARK_TEXTURE_WIDTH, MARK_TEXTURE_HEIGHT);

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        graphics.drawString(font, ComponentText.brand(), x - 1, y + 38,
                withAlpha(0xFFF5F7FC, alpha), false);
        graphics.drawString(font, ComponentText.caption(), x, y + 64,
                withAlpha(0xFFAAB4C3, Math.round(alpha * 0.78f)), false);

        graphics.pose().popPose();
    }

    /** Shows the active launcher profile and its resolved skin texture; never substitutes a hard-coded name. */
    public static void renderPlayerCard(GuiGraphics graphics, int screenWidth,
                                        int screenHeight, long elapsedMillis) {
        Minecraft client = Minecraft.getInstance();
        if (client.getUser() == null) return;

        GameProfile activeProfile = client.getGameProfile();
        UUID profileId = activeProfile == null
                ? client.getUser().getProfileId() : activeProfile.getId();
        String playerName = client.getUser().getName();
        if (playerName == null || playerName.isBlank()) playerName = "Offline profile";

        int width = Math.min(204, Math.max(164, screenWidth / 3));
        int x = screenWidth - width - Math.max(22, menuLeft(screenWidth));
        int y = Math.max(90, screenHeight / 2 - 48);
        int alpha = Math.round(255.0f * easeOut(Math.min(1.0f,
                Math.max(0.0f, elapsedMillis / 480.0f))));

        fillRounded(graphics, x, y, width, 88, 8, withAlpha(0xFF111722, Math.round(alpha * 0.56f)));
        fillRounded(graphics, x + 1, y + 1, width - 2, 86, 7,
                withAlpha(0xFF1A2230, Math.round(alpha * 0.26f)));

        int avatarX = x + 14;
        int avatarY = y + 17;
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
                    Identifier texture = skin.body().texturePath();
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                            avatarX, avatarY, 8.0f, 8.0f, 34, 34, 64, 64, 64, 64);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                            avatarX, avatarY, 40.0f, 8.0f, 34, 34, 64, 64, 64, 64);
                    skinDrawn = true;
                }
            } catch (RuntimeException ignored) {
                cachedSkinLookup = null;
            }
        }

        if (!skinDrawn) {
            fillRounded(graphics, avatarX, avatarY, 34, 34, 6,
                    withAlpha(0xFF303B4C, alpha));
            graphics.drawCenteredString(client.font, "?", avatarX + 17, avatarY + 12,
                    withAlpha(0xFFE0E6EF, alpha));
        }

        graphics.drawString(client.font, ComponentText.account(), x + 60, y + 18,
                withAlpha(0xFF9AA8BA, alpha), false);
        String safeName = playerName.length() > 16 ? playerName.substring(0, 16) : playerName;
        graphics.drawString(client.font, ComponentText.playerName(safeName), x + 60, y + 35,
                withAlpha(0xFFF2F5FA, alpha), false);
        graphics.drawString(client.font, ComponentText.profileState(profileId != null),
                x + 60, y + 54, withAlpha(0xFF9AA8BA, alpha), false);
    }

    /** Replaces vanilla copyright/version text with a quiet client footer. */
    public static void renderFooter(GuiGraphics graphics, int screenWidth, int screenHeight) {
        graphics.fill(0, screenHeight - 15, screenWidth, screenHeight, 0xB8070A10);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, ComponentText.footerLeft(), 12, screenHeight - 10,
                0xFFAAB4C3, false);
        String right = "VANTA CLIENT";
        graphics.drawString(font, ComponentText.footerRight(right),
                screenWidth - font.width(ComponentText.footerRight(right)) - 12,
                screenHeight - 10, 0xFFAAB4C3, false);
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

        private static net.minecraft.network.chat.Component profileState(boolean available) {
            return net.minecraft.network.chat.Component.literal(
                    available ? "ACTIVE PROFILE" : "PROFILE UNAVAILABLE")
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component footerLeft() {
            return net.minecraft.network.chat.Component.literal("VANTA CLIENT  /  FABRIC")
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }

        private static net.minecraft.network.chat.Component footerRight(String value) {
            return net.minecraft.network.chat.Component.literal(value)
                    .withStyle(style -> style.withFont(CAPTION_FONT));
        }
    }
}

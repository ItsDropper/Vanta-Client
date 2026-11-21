package name.vantaclient.mixin;

import name.vantaclient.client.gui.VantaMenuStyle;
import name.vantaclient.client.gui.lobby.VantaLobbyScene;
import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import name.vantaclient.client.gui.VantaSettingsScreen;
import name.vantaclient.client.gui.VantaWorldShareScreen;
import name.vantaclient.mixin.accessor.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Re-composes the vanilla title screen while retaining every original button action. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Shadow
    private SplashRenderer splash;

    @Unique
    private AbstractButton vanta$settingsShortcut;

    @Unique
    private AbstractButton vanta$shareShortcut;

    @Unique
    private AbstractButton vanta$creditsShortcut;

    @Unique
    private AbstractButton vanta$languageShortcut;

    @Unique
    private AbstractButton vanta$accessibilityShortcut;

    @Inject(method = "init", at = @At("TAIL"))
    private void vanta$composeMenu(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = (Screen) (Object) this;
        VantaLobbyScene.begin();
        splash = null;

        int[] bounds = VantaMenuStyle.titleBrandBounds(
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight());
        if (bounds != null) {
            vanta$settingsShortcut = (AbstractButton) ((ScreenAccessor) (Object) this)
                    .vanta$addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                            Component.literal("Open Vanta Client settings").withStyle(
                                    style -> style.withFont(VantaMenuStyle.uiFont())),
                            button -> client.setScreen(new VantaSettingsScreen(screen)))
                            .bounds(bounds[0], bounds[1], bounds[2], bounds[3])
                            .build());
        }

        vanta$shareShortcut = (AbstractButton) ((ScreenAccessor) (Object) this)
                .vanta$addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Vanta Share").withStyle(
                                style -> style.withFont(VantaMenuStyle.uiFont())),
                        button -> client.setScreen(new VantaWorldShareScreen(screen)))
                        .bounds(0, 0, 108, 24)
                        .build());

        vanta$creditsShortcut = (AbstractButton) ((ScreenAccessor) (Object) this)
                .vanta$addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("   Credits").withStyle(
                                style -> style.withFont(VantaMenuStyle.uiFont())),
                        button -> client.setScreen(new CreditsAndAttributionScreen(screen)))
                        .bounds(Math.max(0, screen.width - 112), Math.max(0, screen.height - 52), 80, 22)
                        .build());

        vanta$languageShortcut = (AbstractButton) ((ScreenAccessor) (Object) this)
                .vanta$addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("   Language").withStyle(
                                style -> style.withFont(VantaMenuStyle.uiFont())),
                        button -> client.setScreen(new LanguageSelectScreen(
                                screen, client.options, client.getLanguageManager())))
                        .bounds(0, 0, 104, 22)
                        .build());

        vanta$accessibilityShortcut = (AbstractButton) ((ScreenAccessor) (Object) this)
                .vanta$addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("   Accessibility").withStyle(
                                style -> style.withFont(VantaMenuStyle.uiFont())),
                        button -> client.setScreen(new AccessibilityOptionsScreen(screen, client.options)))
                        .bounds(0, 0, 104, 22)
                        .build());

        vanta$layoutButtons(screen);
    }

    @Unique
    private void vanta$layoutButtons(Screen screen) {
        int screenWidth = screen.width;
        int screenHeight = screen.height;
        int left = VantaMenuStyle.menuLeft(screenWidth);
        int width = VantaMenuStyle.menuButtonWidth(screenWidth);
        int top = VantaMenuStyle.menuTop(screenHeight);
        int buttonHeight = VantaMenuStyle.menuButtonHeight(screenHeight);
        int gap = VantaMenuStyle.menuButtonGap(screenHeight);

        AbstractButton singleplayer = null;
        AbstractButton multiplayer = null;
        AbstractButton realms = null;
        AbstractButton options = null;
        AbstractButton quit = null;
        List<AbstractButton> secondary = new ArrayList<>();

        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractButton button)) continue;
            String label = button.getMessage().getString();
            String normalized = label.toLowerCase(Locale.ROOT);

            button.setMessage(Component.literal(label).withStyle(
                    style -> style.withFont(VantaMenuStyle.uiFont())));

            if (button == vanta$settingsShortcut || button == vanta$creditsShortcut
                    || button == vanta$languageShortcut || button == vanta$accessibilityShortcut
                    || button == vanta$shareShortcut) continue;
            if (normalized.contains("language") || normalized.contains("accessibility")) {
                button.visible = false;
                button.active = false;
                continue;
            }
            if (normalized.startsWith("copyright mojang")) {
                button.visible = false;
                button.active = false;
                continue;
            }
            if (normalized.contains("singleplayer") || normalized.contains("single player")) {
                singleplayer = button;
            } else if (normalized.contains("multiplayer")) {
                multiplayer = button;
            } else if (normalized.contains("realms")) {
                realms = button;
            } else if (normalized.startsWith("options")) {
                options = button;
            } else if (normalized.startsWith("quit")) {
                quit = button;
            } else {
                secondary.add(button);
            }
        }

        AbstractButton[] primary = {singleplayer, multiplayer, realms};
        int y = top;
        for (AbstractButton button : primary) {
            if (button == null) continue;
            vanta$setBounds(button, left, y, width, buttonHeight);
            y += buttonHeight + gap;
        }

        boolean compact = screenHeight < 420 || screenWidth < 420;
        int secondaryY = top + 3 * (buttonHeight + gap) + (compact ? 4 : 8);
        int secondaryHeight = compact ? 20 : 27;
        int halfWidth = Math.max(56, (width - 6) / 2);
        if (options != null) vanta$setBounds(options, left, secondaryY, halfWidth, secondaryHeight);
        if (quit != null) vanta$setBounds(quit, left + halfWidth + 6, secondaryY,
                width - halfWidth - 6, secondaryHeight);

        if (vanta$settingsShortcut != null) {
            int[] bounds = VantaMenuStyle.titleBrandBounds(screenWidth, screenHeight);
            if (bounds != null) vanta$setBounds(vanta$settingsShortcut,
                    bounds[0], bounds[1], bounds[2], bounds[3]);
        }

        boolean compactUtilities = screenHeight < 520 || screenWidth < 420;
        int utilityY = Math.max(4, screenHeight - (compactUtilities ? 48 : 88));
        if (compactUtilities) {
            // At high GUI scales the logical viewport can be only a few hundred
            // units wide. Keep utility actions as icon-sized targets on one row.
            if (vanta$languageShortcut != null) {
                vanta$setBounds(vanta$languageShortcut, 8, utilityY, 28, 22);
                vanta$languageShortcut.setMessage(Component.literal(""));
            }
            if (vanta$accessibilityShortcut != null) {
                vanta$setBounds(vanta$accessibilityShortcut, 42, utilityY, 28, 22);
                vanta$accessibilityShortcut.setMessage(Component.literal(""));
            }
            if (vanta$shareShortcut != null) {
                vanta$setBounds(vanta$shareShortcut, 76, utilityY, 28, 22);
                vanta$shareShortcut.setMessage(Component.literal(""));
            }
            if (vanta$creditsShortcut != null) {
                vanta$setBounds(vanta$creditsShortcut, Math.max(8, screenWidth - 36), utilityY, 28, 22);
                vanta$creditsShortcut.setMessage(Component.literal(""));
            }
        } else {
            if (vanta$languageShortcut != null) vanta$setBounds(vanta$languageShortcut, 8, utilityY, 104, 24);
            if (vanta$accessibilityShortcut != null) vanta$setBounds(vanta$accessibilityShortcut, 120, utilityY, 132, 24);
            if (vanta$shareShortcut != null) vanta$setBounds(vanta$shareShortcut, 268, utilityY, 108, 24);
            if (vanta$creditsShortcut != null) vanta$setBounds(vanta$creditsShortcut,
                    Math.max(8, screenWidth - 100), Math.max(8, screenHeight - 58), 92, 22);
        }

        for (AbstractButton button : secondary) {
            if (button == options || button == quit) continue;
            vanta$setBounds(button, Math.max(8, screenWidth - left - 27), utilityY, 26, 26);
        }
    }

    @Unique
    private static void vanta$setBounds(AbstractButton button, int x, int y,
                                        int width, int height) {
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.setHeight(height);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void vanta$releaseLobbyWorld(CallbackInfo ci) {
        VantaLobbyWorld.stop();
    }

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void vanta$dimPanorama(GuiGraphics graphics, int mouseX, int mouseY,
                                   float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void vanta$removeVanillaLogo(LogoRenderer renderer, GuiGraphics graphics,
                                         int width, float alpha) {
        // Intentionally suppress the Minecraft logo; Vanta owns the title-screen identity.
    }


    @Inject(method = "render", at = @At("HEAD"))
    private void vanta$updateLobbyCamera(GuiGraphics graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        // Tick callbacks are not a reliable first-frame boundary across every
        // loader/bootstrap path. The title screen render is guaranteed to run once
        // the client has resources and its renderer are ready; start here as well.
        VantaLobbyWorld.start();
        VantaLobbyScene.update(mouseX, mouseY,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void vanta$renderVantaMenu(GuiGraphics graphics, int mouseX, int mouseY,
                                       float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        long elapsed = VantaLobbyScene.elapsedMillis();
        VantaMenuStyle.renderTitleBranding(graphics, width, height, elapsed);
        VantaMenuStyle.renderPlayerCard(graphics, width, height, elapsed);
        VantaMenuStyle.renderFooter(graphics, width, height);
        VantaMenuStyle.renderUtilityIcon(graphics, vanta$languageShortcut, 0);
        VantaMenuStyle.renderUtilityIcon(graphics, vanta$accessibilityShortcut, 1);
        VantaMenuStyle.renderUtilityIcon(graphics, vanta$shareShortcut, 3);
        VantaMenuStyle.renderUtilityIcon(graphics, vanta$creditsShortcut, 2);
    }
}

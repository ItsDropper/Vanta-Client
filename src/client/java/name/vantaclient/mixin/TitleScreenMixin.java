package name.vantaclient.mixin;

import name.vantaclient.client.gui.VantaMenuStyle;
import name.vantaclient.client.gui.VantaSettingsScreen;
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
    private AbstractButton vanta$creditsShortcut;

    @Unique
    private AbstractButton vanta$languageShortcut;

    @Unique
    private AbstractButton vanta$accessibilityShortcut;

    @Unique
    private long vanta$openedAt;

    @Inject(method = "init", at = @At("TAIL"))
    private void vanta$composeMenu(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = (Screen) (Object) this;
        vanta$openedAt = System.currentTimeMillis();
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
                    || button == vanta$languageShortcut || button == vanta$accessibilityShortcut) continue;
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

        int secondaryY = top + 3 * (buttonHeight + gap) + 8;
        int halfWidth = Math.max(72, (width - 8) / 2);
        if (options != null) vanta$setBounds(options, left, secondaryY, halfWidth, 27);
        if (quit != null) vanta$setBounds(quit, left + halfWidth + 8, secondaryY,
                width - halfWidth - 8, 27);

        if (vanta$settingsShortcut != null) {
            int[] bounds = VantaMenuStyle.titleBrandBounds(screenWidth, screenHeight);
            if (bounds != null) vanta$setBounds(vanta$settingsShortcut,
                    bounds[0], bounds[1], bounds[2], bounds[3]);
        }

        int utilityY = Math.max(8, screenHeight - 58);
        int utilityStart = Math.max(8, screenWidth - 244);
        if (vanta$languageShortcut != null) {
            vanta$setBounds(vanta$languageShortcut, utilityStart, utilityY, 104, 24);
        }
        if (vanta$accessibilityShortcut != null) {
            vanta$setBounds(vanta$accessibilityShortcut, utilityStart + 112, utilityY, 132, 24);
        }
        if (vanta$creditsShortcut != null) {
            vanta$setBounds(vanta$creditsShortcut, Math.max(8, screenWidth - 100),
                    Math.max(8, screenHeight - 52), 92, 22);
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

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void vanta$dimPanorama(GuiGraphics graphics, int mouseX, int mouseY,
                                   float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        graphics.fill(0, 0, width, height, 0x79070A11);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void vanta$removeVanillaLogo(LogoRenderer renderer, GuiGraphics graphics,
                                         int width, float alpha) {
        // Intentionally suppress the Minecraft logo; Vanta owns the title-screen identity.
    }


    @Inject(method = "render", at = @At("TAIL"))
    private void vanta$renderVantaMenu(GuiGraphics graphics, int mouseX, int mouseY,
                                       float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        long elapsed = Math.max(0L, System.currentTimeMillis() - vanta$openedAt);
        VantaMenuStyle.renderTitleBranding(graphics, width, height, elapsed);
        VantaMenuStyle.renderPlayerCard(graphics, width, height, elapsed);
        VantaMenuStyle.renderFooter(graphics, width, height);
        VantaMenuStyle.renderUtilityIcon(graphics, vanta$languageShortcut, 0);
        VantaMenuStyle.renderUtilityIcon(graphics, vanta$accessibilityShortcut, 1);
        VantaMenuStyle.renderUtilityIcon(graphics, vanta$creditsShortcut, 2);
    }
}

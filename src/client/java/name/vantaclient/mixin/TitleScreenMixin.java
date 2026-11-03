package name.vantaclient.mixin;

import name.vantaclient.client.gui.VantaMenuStyle;
import name.vantaclient.client.gui.VantaSettingsScreen;
import name.vantaclient.mixin.accessor.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a clickable Vanta settings entry without replacing vanilla title-screen actions. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Unique
    private Button vanta$settingsShortcut;

    @Inject(method = "init", at = @At("TAIL"))
    private void vanta$addSettingsShortcut(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        int[] bounds = VantaMenuStyle.titleBrandBounds(
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight());
        if (bounds == null) return;

        Button shortcut = Button.builder(
                Component.literal("Open Vanta Client settings"),
                button -> client.setScreen(new VantaSettingsScreen((Screen) (Object) this)))
                .bounds(bounds[0], bounds[1], bounds[2], bounds[3])
                .build();
        vanta$settingsShortcut = (Button) ((ScreenAccessor) (Object) this)
                .vanta$addRenderableWidget(shortcut);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void vanta$renderBranding(GuiGraphics graphics, int mouseX, int mouseY,
                                      float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        VantaMenuStyle.renderTitleBranding(graphics,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                vanta$settingsShortcut != null && vanta$settingsShortcut.isHoveredOrFocused());
    }
}

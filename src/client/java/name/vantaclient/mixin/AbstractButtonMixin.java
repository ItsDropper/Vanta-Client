package name.vantaclient.mixin;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.VantaMenuStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies transparent Vanta surfaces only to title-screen buttons. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void vanta$renderTitleSurface(GuiGraphics graphics, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof TitleScreen)) return;
        if (VantaClientPreferences.get().titleStyle == VantaClientPreferences.TitleStyle.VANILLA) return;
        VantaMenuStyle.renderButtonSurface(graphics, (AbstractButton) (Object) this);
        ci.cancel();
    }
}

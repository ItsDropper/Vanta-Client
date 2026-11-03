package name.vantaclient.mixin;

import name.vantaclient.client.gui.VantaMenuStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies Vanta's surface to every vanilla AbstractButton without replacing its behavior. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void vanta$renderGlassSurface(GuiGraphics graphics, CallbackInfo ci) {
        VantaMenuStyle.renderButtonSurface(graphics, (AbstractButton) (Object) this);
        ci.cancel();
    }
}

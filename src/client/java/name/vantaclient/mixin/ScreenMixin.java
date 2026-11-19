package name.vantaclient.mixin;

import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.gui.compose.VantaCompose;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a hosted singleplayer server ticking when its host opens the pause menu
 * or another screen. Without this, the host's integrated server pauses while
 * remote guests remain connected to the relay.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void vanta$markGlassBackdrop(GuiGraphics graphics, CallbackInfo ci) {
        if ((Object) this instanceof VantaCompose.Screen) {
            // Queue exactly one native blur boundary. Cancelling vanilla's
            // option-gated body prevents its second marker from throwing.
            graphics.blurBeforeThisStratum();
            ci.cancel();
        }
    }

    @Inject(method = "isPauseScreen", at = @At("HEAD"), cancellable = true)
    private void vanta$keepRemoteHostTicking(CallbackInfoReturnable<Boolean> cir) {
        if (VantaRemoteSessionManager.isHosting()) {
            cir.setReturnValue(false);
        }
    }
}

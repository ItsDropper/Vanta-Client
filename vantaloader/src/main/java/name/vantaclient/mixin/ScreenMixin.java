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
    private void vanta$useComposeBackdropBlur(GuiGraphics graphics, CallbackInfo ci) {
        if ((Object) this instanceof VantaCompose.Screen) {
            // Vanta queues its blur after the complete vanilla screen background.
            // Suppress vanilla's earlier option-gated marker to avoid a second blur.
            ci.cancel();
        }
    }

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void vanta$markGlassBackdrop(GuiGraphics graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof VantaCompose.Screen) {
            graphics.blurBeforeThisStratum();
        }
    }

    @Inject(method = "isPauseScreen", at = @At("HEAD"), cancellable = true)
    private void vanta$keepRemoteHostTicking(CallbackInfoReturnable<Boolean> cir) {
        if (VantaRemoteSessionManager.isHosting()) {
            cir.setReturnValue(false);
        }
    }
}

package name.vantaclient.mixin;

import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a hosted singleplayer server ticking when its host opens the pause menu
 * or another screen. Without this, the host's integrated server pauses while
 * remote guests remain connected to the relay.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "isPauseScreen", at = @At("HEAD"), cancellable = true)
    private void vanta$keepRemoteHostTicking(CallbackInfoReturnable<Boolean> cir) {
        if (VantaRemoteSessionManager.isHosting()) {
            cir.setReturnValue(false);
        }
    }
}

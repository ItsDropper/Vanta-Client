package name.vantaclient.mixin;

import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera.setup is the final owner of the view transform for each world frame.
 * Re-apply the title lobby controller here, after vanilla has positioned and
 * rotated the camera from the synthetic player.
 */
@Mixin(Camera.class)
public abstract class CameraSetupMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void vanta$applyLobbyCameraAfterSetup(CallbackInfo ci) {
        VantaLobbyWorld.applyCameraRotation();
    }
}

package name.vantaclient.mixin;

import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the title camera after vanilla has interpolated and configured it.
 * GameRenderer.renderLevel HEAD is too early: Camera.setup runs inside that
 * method and would overwrite any rotation applied there.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void vanta$applyLobbyRotationAfterSetup(CallbackInfo ci) {
        VantaLobbyWorld.applyCameraRotation();
    }
}

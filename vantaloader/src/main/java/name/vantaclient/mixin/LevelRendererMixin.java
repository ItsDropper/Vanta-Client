package name.vantaclient.mixin;

import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs after GameRenderer has configured the main camera and immediately
 * before LevelRenderer consumes it for the world render.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vanta$applyLobbyCameraBeforeTerrain(CallbackInfo ci) {
        VantaLobbyWorld.applyCameraRotation();
    }
}

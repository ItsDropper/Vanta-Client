package name.vantaclient.mixin;

import name.vantaclient.client.gui.lobby.VantaLobbyScene;\nimport name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;\nimport org.spongepowered.asm.mixin.injection.Inject;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Applies the lobby camera's subtle cursor parallax to the actual 3D cubemap
 * transform used by Minecraft's title panorama.
 */
@Mixin(PanoramaRenderer.class)
public abstract class PanoramaRendererMixin {\n    @Inject(method = "render", at = @At("HEAD"), cancellable = true)\n    private void vanta$hidePanoramaWhenWorldIsReady(net.minecraft.client.Minecraft client, float alpha, float tickDelta, CallbackInfo ci) {\n        if (client.screen instanceof TitleScreen && VantaLobbyWorld.isActive()) ci.cancel();\n    }
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CubeMap;render(Lnet/minecraft/client/Minecraft;FF)V"
            ),
            index = 1
    )
    private float vanta$parallaxPitch(float pitch) {
        return Minecraft.getInstance().screen instanceof TitleScreen
                ? pitch + VantaLobbyScene.camera().scenePitch()
                : pitch;
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CubeMap;render(Lnet/minecraft/client/Minecraft;FF)V"
            ),
            index = 2
    )
    private float vanta$parallaxYaw(float yaw) {
        return Minecraft.getInstance().screen instanceof TitleScreen
                ? yaw + VantaLobbyScene.camera().sceneYaw()
                : yaw;
    }
}

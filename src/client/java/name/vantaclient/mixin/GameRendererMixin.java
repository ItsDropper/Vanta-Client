package name.vantaclient.mixin;

import name.vantaclient.client.feature.ZoomFeature;
import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void vanta$beginTitleWorld(CallbackInfo ci) {
        VantaLobbyWorld.beginFrame();
    }


    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void vanta$finishTitleWorldAfterTerrain(CallbackInfo ci) {
        VantaLobbyWorld.endFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void vanta$endTitleWorldFallback(CallbackInfo ci) {
        VantaLobbyWorld.endFrame();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void dropperutils$beginWorldRender(CallbackInfo ci) {
        ClientPerformanceMetrics.beginWorldRender();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void dropperutils$endWorldRender(CallbackInfo ci) {
        ClientPerformanceMetrics.endWorldRender();
        VantaLobbyWorld.markWorldRendered();
    }

    @ModifyVariable(
            method = "tickFov",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private float modifyZoomFov(float fov) {
        if (ZoomFeature.isZooming()) {
            return fov * ZoomFeature.getZoomMultiplier();
        }

        return fov;
    }

    @ModifyConstant(
            method = "tickFov",
            constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = 0.5F)
    )
    private float modifyZoomSpeed(float value) {
        return ZoomFeature.getZoomSpeed();
    }
}

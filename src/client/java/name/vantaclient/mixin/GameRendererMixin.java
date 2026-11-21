package name.vantaclient.mixin;

import name.vantaclient.client.feature.ZoomFeature;\nimport name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {\n\n    @Inject(method = "render", at = @At("HEAD"))\n    private void vanta$beginTitleWorld(CallbackInfo ci) {\n        VantaLobbyWorld.beginFrame();\n    }\n\n    @Inject(method = "render", at = @At("RETURN"))\n    private void vanta$endTitleWorld(CallbackInfo ci) {\n        VantaLobbyWorld.endFrame();\n    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void dropperutils$beginWorldRender(CallbackInfo ci) {
        ClientPerformanceMetrics.beginWorldRender();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void dropperutils$endWorldRender(CallbackInfo ci) {
        ClientPerformanceMetrics.endWorldRender();
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

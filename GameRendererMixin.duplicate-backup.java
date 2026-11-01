package name.vantaclient.mixin;

import name.vantaclient.client.feature.ZoomFeature;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

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
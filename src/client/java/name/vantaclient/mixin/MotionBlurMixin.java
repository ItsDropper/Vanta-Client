package name.vantaclient.mixin;

import name.vantaclient.client.feature.MotionBlurFeature;
import name.vantaclient.client.util.MotionBlurRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MotionBlurMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void dropperutils$renderMotionBlur(CallbackInfo ci) {
        if (MotionBlurFeature.INSTANCE.isEnabled()) MotionBlurRenderer.render();
        else MotionBlurRenderer.reset();
    }
}

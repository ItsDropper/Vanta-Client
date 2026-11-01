package name.vantaclient.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import name.vantaclient.client.feature.MotionBlurFeature;
import name.vantaclient.client.util.MotionBlurRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MotionBlurMixin {
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "render", at = @At("TAIL"))
    private void dropperutils$renderMotionBlur(DeltaTracker tracker, boolean tick, CallbackInfo ci) {
        if (MotionBlurFeature.INSTANCE.isEnabled()) {
            MotionBlurRenderer.render(this.resourcePool);
        } else {
            MotionBlurRenderer.reset();
        }
    }
}

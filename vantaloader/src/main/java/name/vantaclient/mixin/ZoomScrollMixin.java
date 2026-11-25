package name.vantaclient.mixin;

import name.vantaclient.client.feature.ZoomFeature;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class ZoomScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void dropperutils$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ZoomFeature.isZooming()) {
            ZoomFeature.scrollZoom(vertical);
            ci.cancel();
        }
    }
}
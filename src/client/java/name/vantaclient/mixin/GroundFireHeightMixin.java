package name.vantaclient.mixin;

import name.vantaclient.client.feature.GroundFireHeightFeature;
import net.minecraft.client.particle.FlameParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks vanilla flame particle quads for vertical scaling during particle mesh
 * construction. A negative size is used as an internal marker and is decoded
 * by QuadParticleRenderStateMixin before vertices are emitted.
 */
@Mixin(FlameParticle.class)
public abstract class GroundFireHeightMixin {
    @Inject(method = "getQuadSize(F)F", at = @At("RETURN"), cancellable = true)
    private void vanta$markGroundFireQuad(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (!GroundFireHeightFeature.INSTANCE.isEnabled()) return;

        float size = cir.getReturnValue();
        if (Float.isFinite(size) && size > 0.0f) {
            cir.setReturnValue(-size);
        }
    }
}

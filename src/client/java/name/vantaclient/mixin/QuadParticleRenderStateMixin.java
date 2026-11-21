package name.vantaclient.mixin;

import name.vantaclient.client.feature.GroundFireHeightFeature;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Decodes the marker placed on FlameParticle quads and scales only their
 * local vertical axis before the billboard vertices are rotated and emitted.
 */
@Mixin(QuadParticleRenderState.class)
public abstract class QuadParticleRenderStateMixin {
    @ModifyArgs(
            method = "renderRotatedQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/state/QuadParticleRenderState;renderVertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Quaternionf;FFFFFFFFII)V"
            )
    )
    private void vanta$scaleGroundFireHeight(Args args) {
        float encodedSize = args.get(7);
        if (!(encodedSize < 0.0f)) return;

        args.set(6, (float) args.get(6) * GroundFireHeightFeature.getHeightScale());
        args.set(7, -encodedSize);
    }
}

package name.vantaclient.mixin;

import name.vantaclient.client.feature.GroundFireHeightFeature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Scales only the baked quads belonging to placed fire blocks.
 *
 * ModelBlockRenderer builds chunk geometry on worker threads. For a fire block the
 * pose stack is pushed and scaled vertically on entry to tesselateBlock and popped on
 * every return, so no other block sees the transform. This only touches the public
 * entry point; the private quad-submission methods changed in 1.21.9+ and are not hooked.
 *
 * Flame particles and the first-person fire overlay use separate render paths
 * and are deliberately untouched.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class GroundFireHeightMixin {
    @Unique
    private static final ThreadLocal<Boolean> vanta$scaled = new ThreadLocal<>();

    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void vanta$beginGroundFireScale(
            BlockAndTintGetter level,
            List<?> parts,
            BlockState state,
            BlockPos pos,
            PoseStack poseStack,
            VertexConsumer builder,
            boolean cull,
            int overlayCoords,
            CallbackInfo ci
    ) {
        vanta$scaled.remove();
        if (GroundFireHeightFeature.isActive() && !parts.isEmpty() && vanta$isFire(state)) {
            float scale = GroundFireHeightFeature.getHeightScale();
            if (Float.isFinite(scale) && Math.abs(scale - 1.0f) > 0.0001f) {
                // Scoped to this one block: pushed here, popped at RETURN. The scale is applied
                // around the block's local origin so the flames stay anchored to the ground.
                poseStack.pushPose();
                poseStack.scale(1.0f, scale, 1.0f);
                vanta$scaled.set(Boolean.TRUE);
            }
        }
    }

    @Inject(method = "tesselateBlock", at = @At("RETURN"))
    private void vanta$endGroundFireScale(
            BlockAndTintGetter level,
            List<?> parts,
            BlockState state,
            BlockPos pos,
            PoseStack poseStack,
            VertexConsumer builder,
            boolean cull,
            int overlayCoords,
            CallbackInfo ci
    ) {
        if (vanta$scaled.get() != null) {
            vanta$scaled.remove();
            poseStack.popPose();
        }
    }

    @Unique
    private static boolean vanta$isFire(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }
}

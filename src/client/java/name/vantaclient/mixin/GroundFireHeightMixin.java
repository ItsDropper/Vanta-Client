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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Scales only the baked quads belonging to placed fire blocks.
 *
 * ModelBlockRenderer builds chunk geometry on worker threads. Applying a scale
 * to its shared PoseStack at method entry is easy to accidentally couple to
 * unrelated block transforms. Instead, capture the current fire scale while a
 * fire block is tessellated, then replace the pose passed to VertexConsumer's
 * array-based putBulkData overload. ModelBlockRenderer funnels both ambient-
 * occlusion and flat block quads through this exact submission call.
 *
 * Flame particles and the first-person fire overlay use separate render paths
 * and are deliberately untouched.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class GroundFireHeightMixin {
    @Unique
    private static final ThreadLocal<Float> vanta$activeFireScale = new ThreadLocal<>();

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
        vanta$activeFireScale.remove();
        if (GroundFireHeightFeature.isActive() && !parts.isEmpty() && vanta$isFire(state)) {
            float scale = GroundFireHeightFeature.getHeightScale();
            if (Float.isFinite(scale) && Math.abs(scale - 1.0f) > 0.0001f) {
                vanta$activeFireScale.set(scale);
            }
        }
    }

    @ModifyArg(
            method = "putQuadData",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[II)V"
            ),
            index = 0,
            require = 1
    )
    private PoseStack.Pose vanta$scaleGroundFireQuad(PoseStack.Pose originalPose) {
        Float scale = vanta$activeFireScale.get();
        if (scale == null) return originalPose;
        return GroundFireHeightFeature.scaleQuadPose(originalPose, scale);
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
        vanta$activeFireScale.remove();
    }

    @Unique
    private static boolean vanta$isFire(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }
}

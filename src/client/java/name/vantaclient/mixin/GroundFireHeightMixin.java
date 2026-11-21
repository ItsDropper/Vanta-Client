package name.vantaclient.mixin;

import name.vantaclient.client.feature.GroundFireHeightFeature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Scales the baked world geometry for fire blocks around their base.
 * The first-person fire overlay is rendered by ScreenEffectRenderer and is not touched here.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class GroundFireHeightMixin {
    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void vanta$pushGroundFireScale(
            BlockAndTintGetter level,
            List<?> parts,
            BlockState state,
            BlockPos pos,
            PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer builder,
            boolean cull,
            int overlayCoords,
            CallbackInfo ci
    ) {
        if (!GroundFireHeightFeature.INSTANCE.isEnabled() || parts.isEmpty() || !vanta$isFire(state)) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(1.0F, GroundFireHeightFeature.getHeightScale(), 1.0F);
    }

    @Inject(method = "tesselateBlock", at = @At("RETURN"))
    private void vanta$popGroundFireScale(
            BlockAndTintGetter level,
            List<?> parts,
            BlockState state,
            BlockPos pos,
            PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer builder,
            boolean cull,
            int overlayCoords,
            CallbackInfo ci
    ) {
        if (GroundFireHeightFeature.INSTANCE.isEnabled() && !parts.isEmpty() && vanta$isFire(state)) {
            poseStack.popPose();
        }
    }

    private static boolean vanta$isFire(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }
}

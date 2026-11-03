package name.vantaclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import name.vantaclient.client.feature.NametagUtilsFeature;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies nameplate controls around vanilla's queued text rendering. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "submitNameTag", at = @At("HEAD"))
    private void vanta$pushNametagTransform(
            EntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!NametagUtilsFeature.INSTANCE.isEnabled()) return;
        poseStack.pushPose();
        float scale = NametagUtilsFeature.getScale();
        poseStack.scale(scale, scale, scale);
    }

    @Inject(method = "submitNameTag", at = @At("TAIL"))
    private void vanta$popNametagTransform(
            EntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!NametagUtilsFeature.INSTANCE.isEnabled()) return;
        poseStack.popPose();
    }

    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void vanta$hidePlayerNametags(
            Entity entity,
            double squaredDistanceToCamera,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (NametagUtilsFeature.INSTANCE.isEnabled()
                && NametagUtilsFeature.isHidePlayers()
                && entity instanceof Player) {
            cir.setReturnValue(false);
        }
    }
}

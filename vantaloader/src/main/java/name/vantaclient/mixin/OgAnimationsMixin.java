package name.vantaclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import name.vantaclient.client.feature.OgAnimationsFeature;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only the first-person swing transform; equip and use-item timing remain vanilla. */
@Mixin(ItemInHandRenderer.class)
public abstract class OgAnimationsMixin {
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void vanta$applyLegacySwing(PoseStack poseStack, HumanoidArm arm,
                                        float swingProgress, CallbackInfo ci) {
        if (!OgAnimationsFeature.INSTANCE.isEnabled()) return;

        float progress = Mth.clamp(swingProgress, 0.0f, 1.0f);
        float rootSwing = Mth.sin(Mth.sqrt(progress) * (float) Math.PI);
        float swing = Mth.sin(progress * (float) Math.PI);
        float verticalBob = Mth.sin(Mth.sqrt(progress) * (float) Math.PI * 2.0f) * 0.2f;
        float scale = OgAnimationsFeature.getPreset()
                == OgAnimationsFeature.Preset.SOFT_LEGACY ? 0.72f : 1.0f;
        float handedness = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;

        poseStack.translate(
                handedness * rootSwing * -0.4f * scale,
                verticalBob * scale,
                swing * -0.2f * scale
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(handedness * swing * -20.0f * scale));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rootSwing * -20.0f * scale));
        poseStack.mulPose(Axis.XP.rotationDegrees(swing * -80.0f * scale));
        ci.cancel();
    }
}

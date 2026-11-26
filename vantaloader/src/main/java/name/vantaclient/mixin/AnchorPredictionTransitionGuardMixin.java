package name.vantaclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Anchor Optimizer from asking Sodium to rebuild chunks after the
 * client world has been detached during a disconnect/server transfer.
 *
 * This is deliberately scoped to the renderer update: prediction cleanup can
 * continue, while an update with no active ClientLevel is discarded.
 */
@Pseudo
@Mixin(targets = "me.cutebow.client_side_anchors.client.AnchorPredictionManager", remap = false)
public abstract class AnchorPredictionTransitionGuardMixin {
    @Inject(
            method = "scheduleRerender",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void vanta$skipRerenderWithoutClientWorld(CallbackInfo ci) {
        if (Minecraft.getInstance().level == null) {
            ci.cancel();
        }
    }
}

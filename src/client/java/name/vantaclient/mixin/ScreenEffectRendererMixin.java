package name.vantaclient.mixin;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.LowFireFeature;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(
            method = "renderFire",
            at = @At("HEAD")
    )
    private static void lowerFire(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            TextureAtlasSprite sprite,
            CallbackInfo ci
    ) {

        if (!LowFireFeature.INSTANCE.isEnabled()) {
            return;
        }

        float slider = DropperUtilsConfig.get().lowFireHeight;

        float height = slider * 0.5f;

        poseStack.translate(
                0.0f,
                -height,
                0.0f
        );

    }
}
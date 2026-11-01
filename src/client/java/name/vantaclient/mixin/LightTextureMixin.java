package name.vantaclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import name.vantaclient.client.feature.FullbrightFeature;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    @WrapOperation(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Double;floatValue()F",
                    ordinal = 1
            )
    )
    private float dropperUtils$fullbright(
            Double instance,
            Operation<Float> original
    ) {

        if (FullbrightFeature.INSTANCE.isEnabled()) {

            float brightness =
                    FullbrightFeature.getBrightness();

            float vanilla = original.call(instance);
            float target = brightness / 10.0F;
            float blended = vanilla + (target - vanilla) * (brightness / 100.0F);
            float floor = FullbrightFeature.getMinimumBrightness() / 10.0F;
            return Math.max(floor, blended);
        }

        return original.call(instance);
    }
}
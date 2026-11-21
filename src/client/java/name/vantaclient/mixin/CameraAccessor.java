package name.vantaclient.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("eyeHeight")
    float vanta$getEyeHeight();

    @Accessor("eyeHeight")
    void vanta$setEyeHeight(float value);

    @Accessor("eyeHeightOld")
    float vanta$getEyeHeightOld();

    @Accessor("eyeHeightOld")
    void vanta$setEyeHeightOld(float value);
}

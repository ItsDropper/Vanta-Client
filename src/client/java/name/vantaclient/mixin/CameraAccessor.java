package name.vantaclient.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

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

    @Invoker("setRotation")
    void vanta$setRotation(float yaw, float pitch);
}

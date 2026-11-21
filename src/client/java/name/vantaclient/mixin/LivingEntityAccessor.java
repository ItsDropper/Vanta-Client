package name.vantaclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keeps the previous and current render rotations aligned for the unticked lobby avatar. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("yHeadRotO")
    void vanta$setPreviousHeadYaw(float yaw);

    @Accessor("yBodyRotO")
    void vanta$setPreviousBodyYaw(float yaw);
}

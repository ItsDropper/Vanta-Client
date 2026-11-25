package name.vantaclient.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class AnchorExplosionPrediction {

    public static void predict(BlockPos pos) {

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }


        Vec3 center = Vec3.atCenterOf(pos);
        float damage =
                AnchorDamageCalculator.calculate(center);


        boolean lethal =
                AnchorDamageCalculator.isLethal(damage);


        mc.level.addParticle(
                net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                center.x,
                center.y,
                center.z,
                0,
                0,
                0
        );

    }
}
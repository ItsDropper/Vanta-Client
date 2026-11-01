package name.vantaclient.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class AnchorDamageCalculator {

    public static float calculate(Vec3 explosionPos) {

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return 0;
        }


        Vec3 playerPos = mc.player.position();


        double distance =
                playerPos.distanceTo(explosionPos);


        if (distance > 12) {
            return 0;
        }


        double exposure =
                1.0 - (distance / 12.0);


        float baseDamage = 85.0F;


        return (float)
                (baseDamage * exposure * exposure);

    }


    public static boolean isLethal(float damage) {

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return false;
        }


        return damage >= mc.player.getHealth();

    }
}
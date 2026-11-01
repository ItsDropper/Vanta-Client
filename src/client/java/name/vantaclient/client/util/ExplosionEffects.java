package name.vantaclient.client.util;

import net.minecraft.client.Minecraft;

import name.vantaclient.client.feature.NoExplosionEffectsFeature;

public class ExplosionEffects {

    public static void tick() {

        if (!NoExplosionEffectsFeature.INSTANCE.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }

    }
}
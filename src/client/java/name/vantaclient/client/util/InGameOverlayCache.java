package name.vantaclient.client.util;

import name.vantaclient.client.feature.PotionEffectsFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Tick-rate snapshots for custom overlays that are otherwise formatted every frame.
 * All access happens on the Minecraft client thread.
 */
public final class InGameOverlayCache {
    private static List<String> potionEffectLines = List.of();

    private InGameOverlayCache() {}

    public static void tick(Minecraft client) {
        if (!PotionEffectsFeature.INSTANCE.isEnabled() || client.player == null) {
            potionEffectLines = List.of();
            return;
        }

        List<String> updated = new ArrayList<>();
        for (MobEffectInstance effect : client.player.getActiveEffects()) {
            String name = effect.getEffect().value().getDisplayName().getString();
            String duration = MobEffectUtil.formatDuration(effect, 1.0F, 20.0F).getString();
            updated.add(name + " " + duration);
        }

        potionEffectLines = updated.isEmpty() ? List.of() : List.copyOf(updated);
    }

    public static List<String> getPotionEffectLines() {
        return potionEffectLines;
    }
}

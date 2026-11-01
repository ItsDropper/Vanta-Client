package name.vantaclient.mixin;

import name.vantaclient.client.feature.NoExplosionEffectsFeature;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(
            method = "createParticle",
            at = @At("HEAD"),
            cancellable = true
    )
    private void removeExplosionParticles(
            ParticleOptions particle,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfoReturnable<?> cir
    ) {

        if (!NoExplosionEffectsFeature.INSTANCE.isEnabled()) {
            return;
        }

        if (particle == ParticleTypes.EXPLOSION
                || particle == ParticleTypes.EXPLOSION_EMITTER
                || particle == ParticleTypes.LARGE_SMOKE
                || particle == ParticleTypes.SMOKE
                || particle == ParticleTypes.POOF
                || particle == ParticleTypes.CAMPFIRE_COSY_SMOKE
                || particle == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE) {

            cir.setReturnValue(null);
        }
    }
}
package name.vantaclient.mixin;

import name.vantaclient.client.feature.CrystalOptimizerFeature;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the point after vanilla has sent an attack so the crystal optimizer can predict the break. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CrystalOptimizerMixin {
    @Inject(method = "attack", at = @At("TAIL"))
    private void vanta$predictCrystalBreak(Player player, Entity target, CallbackInfo ci) {
        CrystalOptimizerFeature.onAttack(player, target);
    }
}

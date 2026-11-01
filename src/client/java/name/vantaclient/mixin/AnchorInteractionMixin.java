package name.vantaclient.mixin;

import name.vantaclient.client.util.AnchorExplosionPrediction;
import name.vantaclient.client.util.AnchorPrediction;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(MultiPlayerGameMode.class)
public class AnchorInteractionMixin {


    @Inject(
            method = "useItemOn",
            at = @At("RETURN")
    )
    private void onUseAnchor(
            net.minecraft.client.player.LocalPlayer player,
            InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hitResult,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir
    ) {

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos();

        if (!mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
            return;
        }


        if (player.isShiftKeyDown()) {
            return;
        }


        if (player.getItemInHand(hand).is(net.minecraft.world.item.Items.GLOWSTONE)) {
            return;
        }


        int charges =
                mc.level.getBlockState(pos)
                        .getValue(
                                net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE
                        );


        if (charges <= 0) {
            return;
        }


        if (!cir.getReturnValue().consumesAction()) {
            return;
        }

        AnchorPrediction.predict(pos);
        AnchorExplosionPrediction.predict(pos);
    }
}
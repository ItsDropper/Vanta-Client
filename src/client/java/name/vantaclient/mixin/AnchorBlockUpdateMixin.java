package name.vantaclient.mixin;

import name.vantaclient.client.util.AnchorPrediction;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.Blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ClientPacketListener.class)
public class AnchorBlockUpdateMixin {


    @Inject(
            method = "handleBlockUpdate",
            at = @At("TAIL")
    )
    private void onBlockUpdate(
            ClientboundBlockUpdatePacket packet,
            CallbackInfo ci
    ) {

        var pos = packet.getPos();


        if (!AnchorPrediction.isPredicted(pos)) {
            return;
        }


        if (packet.getBlockState().isAir()) {

            // server accepted explosion
            AnchorPrediction.confirm(pos);

        } else if (packet.getBlockState().is(Blocks.RESPAWN_ANCHOR)) {

            // server rejected prediction
            AnchorPrediction.rollback(pos);

        }

    }
}
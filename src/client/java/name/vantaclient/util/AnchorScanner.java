package name.vantaclient.client.util;

import name.vantaclient.client.feature.AnchorOptimizerFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class AnchorScanner {

    public static void scanNearbyAnchors() {

        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null) {
            return;
        }

        long start = System.nanoTime();

        BlockPos playerPos = client.player.blockPosition();
        Vec3 look = client.player.getLookAngle();

        int range = AnchorOptimizerFeature.getScanRange();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {

                    BlockPos pos = playerPos.offset(x, y, z);

                    Vec3 direction = new Vec3(x, y, z);

                    if (direction.lengthSqr() > 0) {
                        direction = direction.normalize();
                    }

                    if (look.dot(direction) < -0.25) {
                        continue;
                    }

                    if (client.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
                        AnchorTracker.track(pos);
                    }
                }
            }
        }

        AnchorStats.finishScan(start);
    }
}
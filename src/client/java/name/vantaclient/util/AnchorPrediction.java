package name.vantaclient.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AnchorPrediction {

    private static final Map<BlockPos, PredictedAnchor> PREDICTED_ANCHORS = new HashMap<>();


    public static void predict(BlockPos pos) {

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }


        if (PREDICTED_ANCHORS.containsKey(pos)) {
            return;
        }


        BlockState state = mc.level.getBlockState(pos);


        if (!state.is(Blocks.RESPAWN_ANCHOR)) {
            return;
        }


        PREDICTED_ANCHORS.put(
                pos,
                new PredictedAnchor(pos, state)
        );


        mc.level.setBlock(
                pos,
                Blocks.AIR.defaultBlockState(),
                3
        );
    }


    public static boolean isPredicted(BlockPos pos) {

        return PREDICTED_ANCHORS.containsKey(pos);

    }


    public static void confirm(BlockPos pos) {

        PREDICTED_ANCHORS.remove(pos);

    }


    public static void rollback(BlockPos pos) {

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }


        PredictedAnchor predicted = PREDICTED_ANCHORS.remove(pos);


        if (predicted != null) {

            mc.level.setBlock(
                    pos,
                    predicted.getOldState(),
                    3
            );

        }
    }


    public static void clear() {

        PREDICTED_ANCHORS.clear();

    }

    public static void tick() {

        Iterator<PredictedAnchor> iterator =
                PREDICTED_ANCHORS.values().iterator();


        while (iterator.hasNext()) {

            PredictedAnchor anchor = iterator.next();

            anchor.tick();


            // 2 seconds timeout
            if (anchor.getTicksAlive() > 40) {

                Minecraft mc = Minecraft.getInstance();

                if (mc.level != null) {
                    mc.level.setBlock(
                            anchor.getPos(),
                            anchor.getOldState(),
                            3
                    );
                }

                iterator.remove();
            }
        }
    }
}
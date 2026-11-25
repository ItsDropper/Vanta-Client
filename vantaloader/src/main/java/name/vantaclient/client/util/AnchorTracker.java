package name.vantaclient.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class AnchorTracker {

    private static final List<TrackedAnchor> TRACKED_ANCHORS =
            new ArrayList<>();

    private static final Set<BlockPos> TRACKED_POSITIONS =
            new HashSet<>();

    public static void track(BlockPos pos) {

        if (TRACKED_POSITIONS.contains(pos)) {
            return;
        }

        TRACKED_POSITIONS.add(pos);
        TRACKED_ANCHORS.add(new TrackedAnchor(pos));
    }

    public static void tick() {

        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null) {
            return;
        }

        BlockPos playerPos = client.player.blockPosition();

        Iterator<TrackedAnchor> iterator =
                TRACKED_ANCHORS.iterator();

        while (iterator.hasNext()) {

            TrackedAnchor anchor = iterator.next();

            double distance =
                    anchor.getPos().distSqr(playerPos);

            if (distance > 64 * 64) {

                TRACKED_POSITIONS.remove(anchor.getPos());
                iterator.remove();

                continue;
            }

            if (anchor.getTicks() % 10 != 0) {
                anchor.tick();
                continue;
            }

            boolean exists =
                    client.level
                            .getBlockState(anchor.getPos())
                            .is(Blocks.RESPAWN_ANCHOR);

            if (!exists) {

                TRACKED_POSITIONS.remove(anchor.getPos());
                iterator.remove();

                continue;
            }

            anchor.setLoaded(true);
            anchor.updateDistance(Math.sqrt(distance));
            anchor.setTargetDistance(Math.sqrt(distance));
            anchor.smoothTick();
            anchor.tick();
        }
    }

    public static List<TrackedAnchor> getTrackedAnchors() {
        return TRACKED_ANCHORS;
    }

    public static void clear() {
        TRACKED_ANCHORS.clear();
        TRACKED_POSITIONS.clear();
    }
}
package name.vantaclient.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class PredictedAnchor {

    private final BlockPos pos;
    private final BlockState oldState;

    private int ticksAlive;


    public PredictedAnchor(BlockPos pos, BlockState oldState) {
        this.pos = pos;
        this.oldState = oldState;
    }


    public BlockPos getPos() {
        return pos;
    }


    public BlockState getOldState() {
        return oldState;
    }


    public int getTicksAlive() {
        return ticksAlive;
    }


    public void tick() {
        ticksAlive++;
    }
}
package name.vantaclient.client.util;

import net.minecraft.core.BlockPos;

public class TrackedAnchor {

    private final BlockPos pos;
    private int ticks;
    private boolean lastState;
    private double displayedDistance = 0;
    private double targetDistance = 0;
    private int stableTicks = 0;

    public TrackedAnchor(BlockPos pos) {
        this.pos = pos;
        this.ticks = 0;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getTicks() {
        return ticks;
    }

    public void tick() {
        ticks++;
    }

    private boolean loaded = true;

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    private double distance;

    public double getDistance() {
        return distance;
    }

    public void updateDistance(double distance) {
        this.distance = distance;
    }

    public boolean getLastState() {
        return lastState;
    }

    public void setLastState(boolean state) {
        this.lastState = state;
    }

    public double getDisplayedDistance() {
        return displayedDistance;
    }

    public void setTargetDistance(double distance) {
        this.targetDistance = distance;
    }

    public void smoothTick() {
        displayedDistance += (targetDistance - displayedDistance) * 0.15;
    }

    public int getStableTicks() {
        return stableTicks;
    }

    public void resetStableTicks() {
        stableTicks = 0;
    }

    public void incrementStableTicks() {
        stableTicks++;
    }
}
package name.vantaclient.client.util;

import name.vantaclient.client.feature.AnchorOptimizerFeature;
import name.vantaclient.client.feature.DebugHudFeature;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class AnchorOptimizer {


    private static int activeTicks = 0;
    private static int scanTimer = 0;

    private static BlockPos lastPlayerPos;
    private static float lastYaw;
    private static float lastPitch;

    public static void tick() {

        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null) {
            return;
        }

        // Debug only
        if (!AnchorOptimizerFeature.INSTANCE.isEnabled()) {

            if (DebugHudFeature.INSTANCE.isEnabled()) {
                scanTimer++;

                if (scanTimer >= 20) {
                    AnchorScanner.scanNearbyAnchors();
                    scanTimer = 0;
                }
            }

            return;
        }

        if (activeTicks > 0) {
            activeTicks--;
        }

        if (activeTicks <= 0) {
            return;
        }

        BlockPos playerPos = client.player.blockPosition();

        float yaw = client.player.getYRot();
        float pitch = client.player.getXRot();

        boolean moved =
                lastPlayerPos == null ||
                        !playerPos.equals(lastPlayerPos);

        boolean turned =
                Math.abs(yaw - lastYaw) > 15 ||
                        Math.abs(pitch - lastPitch) > 15;

        if (moved || turned) {

            lastPlayerPos = playerPos;
            lastYaw = yaw;
            lastPitch = pitch;

            AnchorScanner.scanNearbyAnchors();
        }

        scanTimer++;

        if (scanTimer >= AnchorOptimizerFeature.getScanInterval()) {
            AnchorScanner.scanNearbyAnchors();
            scanTimer = 0;
        }

        AnchorTracker.tick();
    }


    public static void wakeUp() {
        activeTicks = 40;
    }
}
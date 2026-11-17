package name.vantaclient.client.util;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.Arrays;

/** Regression coverage for directional distant-terrain self-shadowing. */
@SuppressWarnings("UnstableApiUsage")
public final class FarTerrainShadingGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        verifyFlatTerrainRemainsUnshadowed();
        verifyRidgeCastsBoundedShadow();
        verifyOutOfBoundsSamplesAreIgnored();
        verifyInvalidInputsAreRejected();
    }

    private static void verifyFlatTerrainRemainsUnshadowed() {
        int[] heights = new int[25];
        Arrays.fill(heights, 80);
        double factor = FarTerrainLodMath.terrainShadowFactor(heights, 4, 3, 3, 16.0);
        if (factor != 1.0) {
            throw new AssertionError("Flat terrain must not receive artificial self-shadowing.");
        }
    }

    private static void verifyRidgeCastsBoundedShadow() {
        int[] heights = new int[25];
        Arrays.fill(heights, 80);
        heights[2 * 5 + 2] = 140;
        double factor = FarTerrainLodMath.terrainShadowFactor(heights, 4, 3, 3, 16.0);
        if (!(factor < 1.0) || factor < 0.76 || factor > 1.0) {
            throw new AssertionError("A ridge toward the sun must cast a bounded distant-terrain shadow.");
        }
    }

    private static void verifyOutOfBoundsSamplesAreIgnored() {
        int[] heights = new int[25];
        Arrays.fill(heights, 80);
        heights[4 * 5 + 4] = 200;
        double factor = FarTerrainLodMath.terrainShadowFactor(heights, 4, 0, 0, 16.0);
        if (factor != 1.0) {
            throw new AssertionError("Terrain shadow sampling must not wrap at tile boundaries.");
        }
    }

    private static void verifyInvalidInputsAreRejected() {
        try {
            FarTerrainLodMath.terrainShadowFactor(new int[4], 4, 1, 1, 16.0);
            throw new AssertionError("Invalid height-grid dimensions must be rejected.");
        } catch (IllegalArgumentException expected) {
            // Expected validation path.
        }
    }
}

package name.vantaclient.client.util;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Regression coverage for weighted biome colour blending. */
@SuppressWarnings("UnstableApiUsage")
public final class FarTerrainBiomeBlenderGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        verifyWeightedBlend();
        verifyBlendIsOrderIndependent();
        verifyInvalidWeightsAreIgnored();
    }

    private static void verifyWeightedBlend() {
        int blended = FarTerrainBiomeBlender.blendColors(
                new int[]{0xFFFF0000, 0xFF0000FF},
                new double[]{3.0, 1.0});
        if (blended != 0xFFBF0040) {
            throw new AssertionError("Biome blend did not preserve the expected weighted RGB average: "
                    + Integer.toHexString(blended));
        }
    }

    private static void verifyBlendIsOrderIndependent() {
        int forward = FarTerrainBiomeBlender.blendColors(
                new int[]{0xFF70B050, 0xFF3F76E4, 0xFFD8C58A},
                new double[]{5.0, 1.0, 0.5});
        int reverse = FarTerrainBiomeBlender.blendColors(
                new int[]{0xFFD8C58A, 0xFF3F76E4, 0xFF70B050},
                new double[]{0.5, 1.0, 5.0});
        if (forward != reverse || (forward >>> 24) != 0xFF) {
            throw new AssertionError("Biome blending must be deterministic and independent of sample order.");
        }
    }

    private static void verifyInvalidWeightsAreIgnored() {
        int blended = FarTerrainBiomeBlender.blendColors(
                new int[]{0xFF123456, 0xFFFFFFFF, 0xFF000000},
                new double[]{Double.NaN, -1.0, 2.0});
        if (blended != 0xFF000000) {
            throw new AssertionError("Invalid biome weights contaminated the blended colour.");
        }
    }
}

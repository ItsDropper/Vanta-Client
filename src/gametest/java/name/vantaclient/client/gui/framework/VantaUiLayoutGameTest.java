package name.vantaclient.client.gui.framework;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Regression coverage for overflow-safe retained flow layout. */
@SuppressWarnings("UnstableApiUsage")
public final class VantaUiLayoutGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        int[] compressed = VantaUiLayout.fitPrimarySizes(new int[]{180, 180, 180}, 320);
        assertTotalAtMost(compressed, 320, "three fixed controls in a compact row");
        if (compressed[0] != 107 || compressed[1] != 107 || compressed[2] != 106) {
            throw new AssertionError("Expected proportional allocation, got "
                    + compressed[0] + ", " + compressed[1] + ", " + compressed[2]);
        }

        assertTotalAtMost(VantaUiLayout.fitPrimarySizes(new int[]{180, 180, 180}, 0),
                0, "zero-width viewport");
        int[] unchanged = VantaUiLayout.fitPrimarySizes(new int[]{40, 60, 20}, 140);
        if (unchanged[0] != 40 || unchanged[1] != 60 || unchanged[2] != 20) {
            throw new AssertionError("A flow that already fits must retain its requested dimensions.");
        }

        int[] onePixel = VantaUiLayout.fitPrimarySizes(new int[]{80, 80, 80}, 1);
        assertTotalAtMost(onePixel, 1, "one-pixel viewport");
        for (int size : onePixel) {
            if (size < 0) throw new AssertionError("Layout produced a negative child size.");
        }
    }

    private static void assertTotalAtMost(int[] values, int available, String scenario) {
        int total = 0;
        for (int value : values) {
            if (value < 0) throw new AssertionError(scenario + " produced a negative size.");
            total += value;
        }
        if (total > available) {
            throw new AssertionError(scenario + " overflows: " + total + " > " + available);
        }
    }
}

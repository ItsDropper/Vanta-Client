package name.vantaclient.client.gui.framework;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

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

        context.runOnClient(client -> {
            VantaUiNode row = VantaUiNode.row(8, 0);
            row.add(VantaUiNode.button("First", () -> {}).width(180).height(32));
            row.add(VantaUiNode.button("Second", () -> {}).width(180).height(32));
            row.add(VantaUiNode.button("Third", () -> {}).width(180).height(32));
            VantaUiRuntime rowRuntime = new VantaUiRuntime(row);
            rowRuntime.setMaxViewportSize(320, 80);
            rowRuntime.layout(Minecraft.getInstance().font, 320, 80);
            assertChildrenFitWithoutOverlap(row, true);
            rowRuntime.dispose();

            VantaUiNode column = VantaUiNode.column(8, 0);
            column.add(VantaUiNode.button("First", () -> {}).width(120).height(60));
            column.add(VantaUiNode.button("Second", () -> {}).width(120).height(60));
            column.add(VantaUiNode.button("Third", () -> {}).width(120).height(60));
            VantaUiRuntime columnRuntime = new VantaUiRuntime(column);
            columnRuntime.setMaxViewportSize(200, 100);
            columnRuntime.layout(Minecraft.getInstance().font, 200, 100);
            assertChildrenFitWithoutOverlap(column, false);
            columnRuntime.dispose();
        });
    }


    private static void assertChildrenFitWithoutOverlap(VantaUiNode parent, boolean horizontal) {
        var parentBounds = parent.bounds();
        var children = parent.children();
        int previousEnd = horizontal ? parentBounds.x() : parentBounds.y();
        for (var child : children) {
            var bounds = child.bounds();
            int start = horizontal ? bounds.x() : bounds.y();
            int end = horizontal ? bounds.x() + bounds.width() : bounds.y() + bounds.height();
            int parentStart = horizontal ? parentBounds.x() : parentBounds.y();
            int parentEnd = horizontal
                    ? parentBounds.x() + parentBounds.width()
                    : parentBounds.y() + parentBounds.height();
            if (start < parentStart || end > parentEnd || start < previousEnd) {
                throw new AssertionError("Retained flow children overlap or escape their parent: "
                        + bounds + " within " + parentBounds);
            }
            previousEnd = end;
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

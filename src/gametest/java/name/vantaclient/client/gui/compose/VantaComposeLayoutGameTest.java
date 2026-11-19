package name.vantaclient.client.gui.compose;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import name.vantaclient.client.gui.compose.layout.VantaLayoutEngine;

import java.util.List;

/** Pure layout-contract regression tests; deliberately independent of legacy VantaUiNode. */
@SuppressWarnings("UnstableApiUsage")
public final class VantaComposeLayoutGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        verifyRowMeasurementAndGap();
        verifyWeightedFlexAllocation();
        verifyCrossAxisAlignment();
        verifyConstraintClamping();
        verifyOverlayPlacement();
    }

    private static void verifyRowMeasurementAndGap() {
        VantaLayoutEngine.Element root = VantaLayoutEngine.Element.container(
                "root", VantaLayoutEngine.Axis.ROW,
                List.of(VantaLayoutEngine.Element.leaf("a", 30, 12),
                        VantaLayoutEngine.Element.leaf("b", 20, 18)),
                4, 6, VantaLayoutEngine.Alignment.START);
        VantaLayoutEngine.Result result = new VantaLayoutEngine().layout(
                root, new VantaLayoutEngine.Rect(10, 20, 200, 80));
        require(result.measuredWidth() == 64, "Row intrinsic width must include padding and gap.");
        require(result.measuredHeight() == 26, "Row intrinsic height must include vertical padding.");
        require(result.bounds("a").equals(new VantaLayoutEngine.Rect(14, 24, 30, 12)),
                "First row child must begin inside parent padding.");
        require(result.bounds("b").x() == 50, "Second row child must follow first child and gap.");
    }

    private static void verifyWeightedFlexAllocation() {
        VantaLayoutEngine.Element first = VantaLayoutEngine.Element.leaf("first", 10, 20).withFlex(1);
        VantaLayoutEngine.Element second = VantaLayoutEngine.Element.leaf("second", 10, 20).withFlex(2);
        VantaLayoutEngine.Element fixed = VantaLayoutEngine.Element.leaf("fixed", 40, 20);
        VantaLayoutEngine.Element root = VantaLayoutEngine.Element.container(
                "root", VantaLayoutEngine.Axis.ROW, List.of(first, second, fixed),
                0, 0, VantaLayoutEngine.Alignment.STRETCH);
        VantaLayoutEngine.Result result = new VantaLayoutEngine().layout(
                root, new VantaLayoutEngine.Rect(0, 0, 300, 40));
        require(result.bounds("first").width() == 87, "First flex child must receive one third of remaining width.");
        require(result.bounds("second").width() == 173, "Second flex child must receive remaining weighted width.");
        require(result.bounds("fixed").width() == 40, "Non-flex child must retain intrinsic width.");
    }

    private static void verifyCrossAxisAlignment() {
        VantaLayoutEngine.Element centered = new VantaLayoutEngine.Element(
                "centered", VantaLayoutEngine.Axis.OVERLAY,
                List.of(), 20, 10, 0, 0, 0,
                VantaLayoutEngine.Alignment.CENTER, VantaLayoutEngine.Constraints.UNBOUNDED);
        VantaLayoutEngine.Element root = VantaLayoutEngine.Element.container(
                "root", VantaLayoutEngine.Axis.ROW, List.of(centered),
                0, 0, VantaLayoutEngine.Alignment.CENTER);
        VantaLayoutEngine.Result result = new VantaLayoutEngine().layout(
                root, new VantaLayoutEngine.Rect(0, 0, 100, 50));
        require(result.bounds("centered").y() == 20, "Cross-axis center alignment must center child bounds.");
    }

    private static void verifyConstraintClamping() {
        VantaLayoutEngine.Element constrained = VantaLayoutEngine.Element.leaf("bounded", 200, 80)
                .withConstraints(new VantaLayoutEngine.Constraints(40, 20, 90, 50));
        VantaLayoutEngine.Result result = new VantaLayoutEngine().layout(
                constrained, new VantaLayoutEngine.Rect(0, 0, 300, 100));
        require(result.measuredWidth() == 90 && result.measuredHeight() == 50,
                "Intrinsic measurement must respect explicit maximum constraints.");
    }

    private static void verifyOverlayPlacement() {
        VantaLayoutEngine.Element overlay = VantaLayoutEngine.Element.container(
                "overlay", VantaLayoutEngine.Axis.OVERLAY,
                List.of(VantaLayoutEngine.Element.leaf("back", 20, 20),
                        VantaLayoutEngine.Element.leaf("front", 30, 10)),
                5, 0, VantaLayoutEngine.Alignment.START);
        VantaLayoutEngine.Result result = new VantaLayoutEngine().layout(
                overlay, new VantaLayoutEngine.Rect(7, 9, 100, 60));
        require(result.bounds("back").equals(new VantaLayoutEngine.Rect(12, 14, 20, 20)),
                "Overlay children must share the same origin when not stretched.");
        require(result.bounds("front").equals(new VantaLayoutEngine.Rect(12, 14, 30, 10)),
                "Overlay children must stack at the same origin.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

package name.vantaclient.client.gui.compose;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import name.vantaclient.client.gui.framework.VantaState;
import name.vantaclient.client.gui.framework.VantaUiComponent;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;

/** Regression coverage for the retained component contract and observable state lifecycle. */
@SuppressWarnings("UnstableApiUsage")
public final class VantaUiFoundationGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        verifyComponentSemantics();
        verifyStateInvalidationAndDisposal();
    }

    private static void verifyComponentSemantics() {
        VantaState<Boolean> enabled = new VantaState<>(true);
        VantaUiNode toggle = VantaComponents.toggle("Enable motion blur", enabled)
                .tooltip("Controls the motion blur effect");
        VantaUiComponent.Semantics semantics = toggle.semantics();
        require(semantics.role() == VantaUiComponent.Role.SWITCH,
                "Toggle components must expose switch semantics.");
        require("Enable motion blur".equals(semantics.label()),
                "Component semantics must preserve the visible label.");
        require("true".equals(semantics.value()),
                "Toggle semantics must expose the current bound value.");
        require("Controls the motion blur effect".equals(semantics.description()),
                "Component semantics must expose its description.");
        require(toggle.bounds().width() == 0 && toggle.bounds().height() == 0,
                "Unarranged components must expose zero-sized bounds.");
    }

    private static void verifyStateInvalidationAndDisposal() {
        VantaState<Boolean> enabled = new VantaState<>(true);
        VantaUiNode root = VantaUiNode.column(0, 0)
                .add(VantaComponents.toggle("Enabled", enabled));
        VantaUiRuntime runtime = new VantaUiRuntime(root);
        require(enabled.subscriberCount() == 1,
                "A runtime must subscribe to state observed by its component tree.");
        long before = runtime.invalidationRevision();
        enabled.set(false);
        require(runtime.invalidationRevision() > before,
                "Bound state changes must invalidate the owning UI runtime.");
        require(runtime.isRenderDirty(),
                "A state change must mark the retained tree dirty until it is rendered.");
        runtime.dispose();
        require(enabled.subscriberCount() == 0,
                "Disposing a runtime must release all state subscriptions.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

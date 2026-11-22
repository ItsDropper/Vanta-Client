package name.vantaclient.client.gui.compose;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import name.vantaclient.client.gui.framework.VantaState;
import name.vantaclient.client.gui.framework.VantaUiComponent;
import name.vantaclient.client.gui.framework.VantaUiNode;

/** Regression coverage for reusable retained UI component composition and bindings. */
@SuppressWarnings("UnstableApiUsage")
public final class VantaUiComponentsGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        verifySurfaceAndBadgeComponents();
        verifySettingAndEmptyStateComposition();
        verifyStateBoundDropdown();
    }

    private static void verifySurfaceAndBadgeComponents() {
        VantaUiNode panel = VantaComponents.panel(VantaComponents.Surface.ELEVATED, 8, 12);
        require(panel.kind() == VantaUiNode.Kind.CONTAINER,
                "Panels must compose a retained container.");
        require(panel.children().isEmpty(), "A new panel must not invent child content.");

        VantaUiNode badge = VantaComponents.badge("Connected", VantaComponents.Tone.SUCCESS);
        require(badge.semantics().role() == VantaUiComponent.Role.GROUP,
                "A badge must remain a semantic group.");
        require(badge.children().size() == 1,
                "A badge must expose its visible label as a child component.");
        VantaUiComponent.Semantics label = badge.children().getFirst().semantics();
        require(label.role() == VantaUiComponent.Role.LABEL
                        && "Connected".equals(label.label()),
                "Badge text must be exposed as a label.");
    }

    private static void verifySettingAndEmptyStateComposition() {
        VantaUiNode control = VantaUiNode.toggle("Enabled", () -> true, ignored -> {});
        VantaUiNode row = VantaComponents.settingRow("Feature", "Feature description", control, 88);
        require(row.children().size() == 2,
                "A setting row must expose label content and its control separately.");
        require("Feature".equals(row.children().getFirst().children().getFirst()
                        .semantics().label()),
                "A setting row must preserve its visible title.");

        VantaUiNode empty = VantaComponents.emptyState(VantaUiNode.Icon.SEARCH,
                "No results", "Try another query.", "Clear", () -> {});
        require(empty.children().size() == 4,
                "An actionable empty state must include icon, title, description and action.");
        require(empty.children().get(1).semantics().role() == VantaUiComponent.Role.LABEL,
                "Empty-state title must remain semantic text.");
    }

    private static void verifyStateBoundDropdown() {
        VantaState<String> selected = new VantaState<>("Balanced");
        VantaUiNode dropdown = VantaComponents.dropdown("Mode",
                java.util.List.of("Balanced", "Performance", "Quiet"), selected);
        require("Balanced".equals(dropdown.semantics().value()),
                "Dropdown semantics must expose the current selected option.");
        selected.set("Performance");
        require("Performance".equals(dropdown.semantics().value()),
                "Dropdown semantics must reflect state changes without rebuilding the node.");
        require(selected.subscriberCount() == 0,
                "State-bound components subscribe only when attached to a runtime.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

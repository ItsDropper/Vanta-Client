package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.FarTerrainRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Regression coverage for FarView's fail-closed activation and immediate shutdown. */
@SuppressWarnings("UnstableApiUsage")
public final class FarViewActivationGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        boolean previousFeatureState = FarViewFeature.INSTANCE.isEnabled();
        boolean previousConfigState = config.farViewEnabled;

        try {
            FarViewFeature.INSTANCE.setEnabled(false);
            config.farViewEnabled = true;
            if (FarViewFeature.isRenderingEnabled()) {
                throw new AssertionError("FarView must not render when its live feature state is disabled.");
            }

            FarViewFeature.INSTANCE.setEnabled(true);
            config.farViewEnabled = false;
            if (FarViewFeature.isRenderingEnabled()) {
                throw new AssertionError("FarView must fail closed when its persisted preference is disabled.");
            }

            config.farViewEnabled = true;
            if (!FarViewFeature.isRenderingEnabled()) {
                throw new AssertionError("FarView should render only when both activation states are enabled.");
            }

            config.farViewEnabled = false;
            FarViewFeature.INSTANCE.setEnabled(false);
            if (!"Far terrain renderer is disabled".equals(FarTerrainRenderer.getStatusLine())) {
                throw new AssertionError("FarView renderer status must report disabled immediately after shutdown.");
            }
        } finally {
            FarViewFeature.INSTANCE.setEnabled(previousFeatureState);
            config.farViewEnabled = previousConfigState;
            DropperUtilsConfig.save();
        }
    }
}

package name.vantaclient.client.gui;

import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.hudeditor.HudElement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphics;

/** Regression coverage for custom HUD sizing across Minecraft GUI scales. */
@SuppressWarnings("UnstableApiUsage")
public final class HudScaleCompensationGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            assertScale(1, 1.0f);
            assertScale(2, 0.5f);
            assertScale(3, 1.0f / 3.0f);
            assertScale(4, 0.25f);
            assertScale(6, 1.0f / 6.0f);
            assertScale(12, 1.0f / 12.0f);
            assertScale(0, 1.0f);

            int[][] framebufferSizes = {
                    {1920, 1080}, {1280, 720}, {960, 540},
                    {640, 360}, {480, 270}, {320, 180}
            };
            float[] preferredScales = {0.8f, 1.0f, 1.2f};
            float[] zoomLevels = {0.5f, 1.0f, 3.0f};
            for (int guiScale = 1; guiScale <= 8; guiScale++) {
                for (int[] framebuffer : framebufferSizes) {
                    int guiWidth = Math.max(1, Math.round(framebuffer[0] / (float) guiScale));
                    int guiHeight = Math.max(1, Math.round(framebuffer[1] / (float) guiScale));
                    for (float preferred : preferredScales) {
                        for (float zoom : zoomLevels) {
                            VantaUiRuntime.ViewportLayout viewport =
                                    VantaUiRuntime.calculateViewportLayout(guiWidth, guiHeight,
                                            guiScale, preferred, zoom, 1400, 780);
                            if (!viewport.fits(guiWidth, guiHeight)) {
                                throw new AssertionError("Retained UI viewport escaped the screen at GUI scale "
                                        + guiScale + ", framebuffer " + framebuffer[0] + "x" + framebuffer[1]
                                        + ", preferred scale " + preferred + ", zoom " + zoom
                                        + ": " + viewport);
                            }
                        }
                    }
                }
            }
            VantaUiRuntime.ViewportLayout invalidScale =
                    VantaUiRuntime.calculateViewportLayout(640, 360, Float.NaN,
                            Float.NaN, Float.POSITIVE_INFINITY, 1400, 780);
            if (!invalidScale.fits(640, 360)) {
                throw new AssertionError("Invalid scale preferences must fall back to a safe viewport.");
            }

            var window = client.getWindow();
            int guiScale = Math.max(1, window.getGuiScale());
            float compensation = HudElement.guiScaleCompensation(guiScale);
            ProbeHudElement element = new ProbeHudElement();
            int expectedWidth = Math.max(1, Math.round(100.0f * compensation));
            int expectedHeight = Math.max(1, Math.round(20.0f * compensation));
            if (element.getScaledWidth() != expectedWidth
                    || element.getHitboxWidth() != expectedWidth
                    || element.getScaledHeight() != expectedHeight
                    || element.getHitboxHeight() != expectedHeight) {
                throw new AssertionError("HUD rendering and hitbox dimensions did not share GUI-scale compensation: "
                        + element.getScaledWidth() + "x" + element.getScaledHeight() + "/"
                        + element.getHitboxWidth() + "x" + element.getHitboxHeight()
                        + " != " + expectedWidth + "x" + expectedHeight);
            }

            VanillaHudElement vanilla = new VanillaHudElement(VanillaHudElement.Part.HOTBAR);
            int expectedVanillaWidth = Math.max(1,
                    Math.round(vanilla.getWidth() * vanilla.getScale()));
            if (vanilla.getScaledWidth() != expectedVanillaWidth) {
                throw new AssertionError("Vanilla HUD editor proxies were incorrectly compensated.");
            }

            float physicalWidth = element.getScaledWidth() * guiScale;
            float physicalHeight = element.getScaledHeight() * guiScale;
            float roundingTolerance = Math.max(2.0f, guiScale * 0.5f + 0.5f);
            if (Math.abs(physicalWidth - 100.0f) > roundingTolerance
                    || Math.abs(physicalHeight - 20.0f) > roundingTolerance) {
                throw new AssertionError("HUD physical size changed with GUI scale: "
                        + physicalWidth + "x" + physicalHeight);
            }
        });
    }

    private static void assertScale(int guiScale, float expected) {
        float actual = HudElement.guiScaleCompensation(guiScale);
        if (Math.abs(actual - expected) > 0.001f) {
            throw new AssertionError("Unexpected GUI-scale compensation for "
                    + guiScale + ": " + actual + " != " + expected);
        }
    }

    private static final class ProbeHudElement extends HudElement {
        private ProbeHudElement() {
            super(0.0f, 0.0f);
        }

        @Override
        protected void renderElement(GuiGraphics graphics) {
        }

        @Override
        public int getWidth() {
            return 100;
        }

        @Override
        public int getHeight() {
            return 20;
        }
    }
}

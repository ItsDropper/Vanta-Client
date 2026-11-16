package name.vantaclient.client.gui;

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
            assertScale(1920, 1920, 1.0f);
            assertScale(1920, 960, 0.5f);
            assertScale(1920, 640, 1.0f / 3.0f);
            assertScale(1920, 480, 0.25f);
            assertScale(0, 0, 1.0f);

            var window = client.getWindow();
            int guiWidth = Math.max(1, window.getGuiScaledWidth());
            int framebufferWidth = Math.max(1, window.getWidth());
            float compensation = HudElement.guiScaleCompensation(framebufferWidth, guiWidth);
            ProbeHudElement element = new ProbeHudElement();
            int expectedWidth = Math.max(1, Math.round(100.0f * compensation));
            if (element.getScaledWidth() != expectedWidth) {
                throw new AssertionError("HUD hitbox width did not use the active GUI-scale compensation: "
                        + element.getScaledWidth() + " != " + expectedWidth);
            }

            float physicalWidth = element.getScaledWidth() * framebufferWidth / (float) guiWidth;
            if (Math.abs(physicalWidth - 100.0f) > 2.0f) {
                throw new AssertionError("HUD physical size changed with GUI scale: " + physicalWidth);
            }
        });
    }

    private static void assertScale(int framebufferWidth, int guiWidth, float expected) {
        float actual = HudElement.guiScaleCompensation(framebufferWidth, guiWidth);
        if (Math.abs(actual - expected) > 0.001f) {
            throw new AssertionError("Unexpected GUI-scale compensation for "
                    + framebufferWidth + "/" + guiWidth + ": " + actual + " != " + expected);
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

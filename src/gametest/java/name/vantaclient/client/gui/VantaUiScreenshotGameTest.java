package name.vantaclient.client.gui;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import name.vantaclient.client.hudeditor.HudEditorScreen;
import name.vantaclient.client.hudeditor.HudManager;
import name.vantaclient.client.hudeditor.HudSettingsPanel;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Launches the actual Minecraft client and captures the live Vanta settings workspace.
 *
 * These tests do not create a world, connect to a server, or require an
 * authenticated Minecraft account. They only exercise the client Screen.
 */
@SuppressWarnings("UnstableApiUsage")
public final class VantaUiScreenshotGameTest implements FabricClientGameTest {
    private static final int PAGE_OVERVIEW = 0;
    private static final int PAGE_HUD = 1;
    private static final int PAGE_APPEARANCE = 2;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.restoreDefaultGameOptions();

        VantaSettingsScreen screen = context.computeOnClient(
                client -> new VantaSettingsScreen(null));

        configureViewport(context, 1280, 720, 1);
        context.setScreen(() -> screen);
        context.waitForScreen(VantaSettingsScreen.class);
        capturePages(context, screen, "1280x720-gui1");
        captureHudEditor(context, screen, "1280x720-gui1");

        configureViewport(context, 1280, 720, 2);
        capturePages(context, screen, "1280x720-gui2");
        captureHudEditor(context, screen, "1280x720-gui2");

        configureViewport(context, 960, 540, 1);
        capturePages(context, screen, "960x540-gui1");
        captureHudEditor(context, screen, "960x540-gui1");

        configureViewport(context, 960, 540, 2);
        capturePages(context, screen, "960x540-gui2");
        captureHudEditor(context, screen, "960x540-gui2");

        context.setScreen(() -> null);
        context.waitTicks(1);
    }

    private static void captureHudEditor(ClientGameTestContext context,
                                         VantaSettingsScreen settingsScreen,
                                         String viewportLabel) {
        HudEditorScreen editor = context.computeOnClient(client -> new HudEditorScreen(null));
        context.setScreen(() -> editor);
        context.waitForScreen(HudEditorScreen.class);
        context.runOnClient(client -> {
            HudManager.init();
            if (!HudManager.getElements().isEmpty()) {
                HudSettingsPanel.setSelected(HudManager.getElements().get(0));
            }
        });
        context.waitTicks(2);

        Path screenshot = context.takeScreenshot("vanta-hud-editor-" + viewportLabel);
        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Minecraft did not produce HUD editor screenshot: " + screenshot);
        }
        try {
            if (Files.size(screenshot) < 1_024L) {
                throw new AssertionError("HUD editor screenshot is unexpectedly small: " + screenshot);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not verify HUD editor screenshot: " + screenshot, exception);
        }

        context.setScreen(() -> settingsScreen);
        context.waitForScreen(VantaSettingsScreen.class);
    }

    private static void configureViewport(ClientGameTestContext context,
                                          int width, int height, int guiScale) {
        context.runOnClient(client -> {
            client.getWindow().setWindowed(width, height);
            client.options.guiScale().set(guiScale);
        });
        context.waitTicks(3);
    }

    private static void capturePages(ClientGameTestContext context,
                                     VantaSettingsScreen screen,
                                     String viewportLabel) {
        capturePage(context, screen, PAGE_OVERVIEW,
                "vanta-ui-" + viewportLabel + "-overview");
        capturePage(context, screen, PAGE_HUD,
                "vanta-ui-" + viewportLabel + "-hud");
        capturePage(context, screen, PAGE_APPEARANCE,
                "vanta-ui-" + viewportLabel + "-appearance");
    }

    private static void capturePage(ClientGameTestContext context,
                                    VantaSettingsScreen screen,
                                    int page, String screenshotName) {
        context.runOnClient(client -> screen.setPageForScreenshot(page));
        context.waitTicks(2);

        Path screenshot = context.takeScreenshot(screenshotName);
        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Minecraft did not produce screenshot: " + screenshot);
        }

        try {
            if (Files.size(screenshot) < 1_024L) {
                throw new AssertionError("Screenshot is unexpectedly small: " + screenshot);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not verify screenshot: " + screenshot, exception);
        }

        System.out.println("[Vanta UI] Captured " + screenshotName + " -> " + screenshot);
    }
}

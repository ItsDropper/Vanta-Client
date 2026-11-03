package name.vantaclient.client.gui;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import name.vantaclient.client.hudeditor.HudEditorScreen;
import name.vantaclient.client.hudeditor.HudManager;
import name.vantaclient.client.hudeditor.HudSettingsPanel;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;

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
    private static final int PAGE_VISUAL = 2;
    private static final int PAGE_COMBAT = 3;
    private static final int PAGE_MISC = 4;
    private static final int PAGE_APPEARANCE = 5;
    private static final int PAGE_FEATURE_SETTINGS = 6;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.restoreDefaultGameOptions();
        verifyScrollbarThumbDragging(context);

        VantaSettingsScreen screen = context.computeOnClient(
                client -> new VantaSettingsScreen(null));

        configureViewport(context, 1280, 720, 1);
        context.setScreen(() -> screen);
        context.waitForScreen(VantaSettingsScreen.class);
        captureTitleScreen(context, screen, "1280x720-gui1");
        capturePages(context, screen, "1280x720-gui1");
        captureHudEditor(context, screen, "1280x720-gui1");

        configureViewport(context, 1280, 720, 2);
        captureTitleScreen(context, screen, "1280x720-gui2");
        capturePages(context, screen, "1280x720-gui2");
        captureHudEditor(context, screen, "1280x720-gui2");

        configureViewport(context, 960, 540, 1);
        captureTitleScreen(context, screen, "960x540-gui1");
        capturePages(context, screen, "960x540-gui1");
        captureHudEditor(context, screen, "960x540-gui1");

        configureViewport(context, 960, 540, 2);
        captureTitleScreen(context, screen, "960x540-gui2");
        capturePages(context, screen, "960x540-gui2");
        captureHudEditor(context, screen, "960x540-gui2");

        context.setScreen(() -> null);
        context.waitTicks(1);
    }


    private static void verifyScrollbarThumbDragging(ClientGameTestContext context) {
        context.runOnClient(client -> {
            VantaUiNode scroll = VantaUiNode.column(2, 4)
                    .width(220).height(120).scrollable();
            VantaUiNode firstRow = VantaUiNode.text("First scroll row");
            scroll.add(firstRow);
            for (int index = 1; index < 16; index++) {
                scroll.add(VantaUiNode.text("Scrollable row " + index));
            }

            VantaUiRuntime runtime = new VantaUiRuntime(scroll);
            runtime.layout(client.font, 320, 220);
            int initialY = firstRow.y();
            int viewportHeight = scroll.height() - 8;
            int contentHeight = 16 * 14 + 15 * 2;
            int thumbHeight = Math.min(viewportHeight,
                    Math.max(12, viewportHeight * viewportHeight / contentHeight));
            int trackX = scroll.x() + scroll.width() - 2;
            int trackTop = scroll.y() + 4;
            double thumbCenter = trackTop + thumbHeight / 2.0;
            double bottomThumbCenter = trackTop + viewportHeight - thumbHeight / 2.0;

            if (!runtime.mouseClicked(trackX, thumbCenter, 0, client.font)) {
                throw new AssertionError("Scrollable UI did not capture the scrollbar thumb.");
            }
            if (!runtime.mouseDragged(trackX, bottomThumbCenter, 0)) {
                throw new AssertionError("Scrollbar thumb did not retain pointer capture.");
            }
            runtime.mouseReleased(0);
            runtime.layout(client.font, 320, 220);
            if (firstRow.y() >= initialY) {
                throw new AssertionError("Dragging the scrollbar thumb did not scroll the content.");
            }
            runtime.dispose();
        });
    }

    private static void captureTitleScreen(ClientGameTestContext context,
                                            VantaSettingsScreen settingsScreen,
                                            String viewportLabel) {
        TitleScreen titleScreen = context.computeOnClient(client -> new TitleScreen());
        context.setScreen(() -> titleScreen);
        context.waitForScreen(TitleScreen.class);
        context.waitTicks(4);
        context.runOnClient(client -> {
            boolean hasSettingsShortcut = titleScreen.children().stream()
                    .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                    .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                    .anyMatch(widget -> widget.getMessage().getString()
                            .equals("Open Vanta Client settings"));
            if (!hasSettingsShortcut) {
                throw new AssertionError("Title screen is missing the Vanta settings shortcut.");
            }
        });

        Path screenshot = context.takeScreenshot("vanta-menu-" + viewportLabel);
        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Minecraft did not produce title menu screenshot: " + screenshot);
        }
        try {
            if (Files.size(screenshot) < 1_024L) {
                throw new AssertionError("Title menu screenshot is unexpectedly small: " + screenshot);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not verify title menu screenshot: " + screenshot, exception);
        }

        context.setScreen(() -> settingsScreen);
        context.waitForScreen(VantaSettingsScreen.class);
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
        capturePage(context, screen, PAGE_VISUAL,
                "vanta-ui-" + viewportLabel + "-visual");
        capturePage(context, screen, PAGE_COMBAT,
                "vanta-ui-" + viewportLabel + "-combat");
        capturePage(context, screen, PAGE_MISC,
                "vanta-ui-" + viewportLabel + "-misc");
        capturePage(context, screen, PAGE_APPEARANCE,
                "vanta-ui-" + viewportLabel + "-appearance");
        capturePage(context, screen, PAGE_FEATURE_SETTINGS,
                "vanta-ui-" + viewportLabel + "-feature-settings");
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

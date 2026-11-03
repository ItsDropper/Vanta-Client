package name.vantaclient.client.gui;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import name.vantaclient.client.hudeditor.HudEditorScreen;
import name.vantaclient.client.hud.HudBackgroundRenderer;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.hudeditor.HudManager;
import name.vantaclient.client.hudeditor.HudSettingsPanel;
import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModrinthBrowserService;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiMotion;
import name.vantaclient.client.integration.VantaLanHostingService;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.init.Keybinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
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
    private static final int PAGE_CONTROLS = 7;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.restoreDefaultGameOptions();
        verifyScrollbarThumbDragging(context);
        verifyDropdownPopupOverlay(context);
        verifyConnectedHudBackgrounds(context);
        verifyHudHitboxGeometry(context);
        verifyKeybindCaptureLifecycle(context);
        verifyUiMotion(context);
        verifyProgressComponent(context);
        verifyWorldShareStateWithoutWorld(context);

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

        configureViewport(context, 1280, 720, 1);
        captureModrinthBrowser(context, screen, "1280x720-gui1");
        configureViewport(context, 960, 540, 2);
        captureModrinthBrowser(context, screen, "960x540-gui2");

        configureViewport(context, 1280, 720, 1);
        captureIntegrationsLibrary(context, screen, "1280x720-gui1");
        configureViewport(context, 960, 540, 2);
        captureIntegrationsLibrary(context, screen, "960x540-gui2");

        configureViewport(context, 1280, 720, 1);
        captureWorldShareScreen(context, screen, "1280x720-gui1");
        configureViewport(context, 960, 540, 2);
        captureWorldShareScreen(context, screen, "960x540-gui2");

        context.setScreen(() -> null);
        context.waitTicks(1);
    }


    private static void verifyWorldShareStateWithoutWorld(ClientGameTestContext context) {
        context.runOnClient(client -> {
            VantaLanHostingService.Status status = VantaLanHostingService.status();
            if (status.state() != VantaLanHostingService.State.NO_WORLD || status.port() != -1) {
                throw new AssertionError("World sharing must report NO_WORLD when no integrated server exists: "
                        + status);
            }
            VantaLanHostingService.Status start = VantaLanHostingService.start(
                    net.minecraft.world.level.GameType.SURVIVAL, false);
            if (start.state() != VantaLanHostingService.State.NO_WORLD || start.port() != -1) {
                throw new AssertionError("World sharing must not try to publish a missing world: " + start);
            }
        });
    }

    private static void verifyProgressComponent(ClientGameTestContext context) {
        context.runOnClient(client -> {
            VantaUiNode progress = VantaUiNode.progressIndicator("Checking local files");
            if (progress.kind() != VantaUiNode.Kind.PROGRESS || progress.isInteractive()) {
                throw new AssertionError("Progress indicators must be presentation nodes, not controls.");
            }

            VantaUiRuntime runtime = new VantaUiRuntime(
                    VantaUiNode.column(4, 8).width(320).height(90).add(progress));
            runtime.layout(client.font, 360, 160);
            if (progress.width() <= 0 || progress.height() != 30) {
                throw new AssertionError("Progress indicator layout produced invalid bounds: "
                        + progress.width() + "x" + progress.height());
            }
            runtime.dispose();

            float atSixtyFps = 0.0f;
            for (int frame = 0; frame < 60; frame++) {
                atSixtyFps = VantaUiMotion.advancePingPongPhase(
                        atSixtyFps, 1.0f / 60.0f, 0.9f, true);
            }
            float atThirtyFps = 0.0f;
            for (int frame = 0; frame < 30; frame++) {
                atThirtyFps = VantaUiMotion.advancePingPongPhase(
                        atThirtyFps, 1.0f / 30.0f, 0.9f, true);
            }
            if (Math.abs(atSixtyFps - atThirtyFps) > 0.0002f) {
                throw new AssertionError("Progress motion must be frame-rate independent: 60 FPS="
                        + atSixtyFps + ", 30 FPS=" + atThirtyFps);
            }
            if (VantaUiMotion.advancePingPongPhase(0.25f, 1.0f / 60.0f, 0.9f, false) != 1.0f) {
                throw new AssertionError("Progress motion must remain static when animations are disabled.");
            }
            if (VantaUiMotion.advancePingPongPhase(Float.NaN, 1.0f / 60.0f, 0.9f, true) != 0.0f) {
                throw new AssertionError("Progress motion must recover from a non-finite phase.");
            }
        });
    }

    private static void verifyUiMotion(ClientGameTestContext context) {
        context.runOnClient(client -> {
            float atSixtyFps = 0.0f;
            for (int frame = 0; frame < 60; frame++) {
                atSixtyFps = VantaUiMotion.approach(
                        atSixtyFps, 1.0f, 12.0f, 1.0f / 60.0f, true);
            }

            float atThirtyFps = 0.0f;
            for (int frame = 0; frame < 30; frame++) {
                atThirtyFps = VantaUiMotion.approach(
                        atThirtyFps, 1.0f, 12.0f, 1.0f / 30.0f, true);
            }

            if (Math.abs(atSixtyFps - atThirtyFps) > 0.0002f) {
                throw new AssertionError("UI easing must be frame-rate independent: 60 FPS="
                        + atSixtyFps + ", 30 FPS=" + atThirtyFps);
            }
            if (VantaUiMotion.frameDeltaSeconds(0L, 500_000_000L)
                    != VantaUiMotion.DEFAULT_FRAME_DELTA_SECONDS) {
                throw new AssertionError("The first UI frame must use the nominal 60 FPS delta.");
            }
            if (VantaUiMotion.frameDeltaSeconds(1L, 100_000_001L)
                    != VantaUiMotion.MAX_FRAME_DELTA_SECONDS) {
                throw new AssertionError("Long UI frames must be capped to prevent animation jumps.");
            }
            if (VantaUiMotion.frameDeltaSeconds(100L, 90L) != 0.0f) {
                throw new AssertionError("A non-monotonic frame timestamp must not produce negative delta.");
            }
            if (VantaUiMotion.approach(0.25f, 1.0f, 12.0f, 1.0f / 60.0f, false) != 1.0f) {
                throw new AssertionError("Disabling interface animations must snap controls to their target.");
            }
            if (VantaUiMotion.approach(Float.NaN, 1.0f, 12.0f, 1.0f / 60.0f, true) != 1.0f) {
                throw new AssertionError("UI motion must recover cleanly from a non-finite current value.");
            }
        });
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

    private static void verifyDropdownPopupOverlay(ClientGameTestContext context) {
        context.runOnClient(client -> {
            String[] selected = {"Option 0"};
            List<String> options = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(index -> "Option " + index)
                    .toList();

            VantaUiNode scroll = VantaUiNode.column(2, 6)
                    .width(240).height(110).scrollable();
            for (int index = 0; index < 6; index++) {
                scroll.add(VantaUiNode.text("Setting row " + index).height(22));
            }
            VantaUiNode dropdown = VantaUiNode.dropdown("Display mode", options,
                    () -> selected[0], value -> selected[0] = value).height(32);
            scroll.add(dropdown);

            VantaUiNode root = VantaUiNode.column(0, 0).add(scroll);
            VantaUiRuntime runtime = new VantaUiRuntime(root);
            runtime.layout(client.font, 320, 220);
            int originalHeight = dropdown.height();
            int scrollX = scroll.x() + 12;
            int scrollY = scroll.y() + scroll.height() - 10;
            runtime.mouseScrolled(scrollX, scrollY, -30.0);
            runtime.layout(client.font, 320, 220);

            double controlX = dropdown.x() + dropdown.width() - 12;
            double controlY = dropdown.y() + 12;
            if (!runtime.mouseClicked(controlX, controlY, 0, client.font)) {
                throw new AssertionError("Dropdown control did not open.");
            }
            runtime.layout(client.font, 320, 220);
            VantaUiNode.DropdownPopupBounds popup = runtime.dropdownPopupBounds();
            if (popup == null || popup.visibleOptions() <= 0 || popup.width() <= 0
                    || popup.y() < 0 || popup.y() + popup.height() > 220) {
                throw new AssertionError("Dropdown popup was not positioned inside the viewport: " + popup);
            }
            if (dropdown.height() != originalHeight) {
                throw new AssertionError("Opening a dropdown must not expand or reflow its parent layout.");
            }

            int clipTop = scroll.y() + 6;
            int clipBottom = scroll.y() + scroll.height() - 6;
            if (popup.y() >= clipTop && popup.y() + popup.height() <= clipBottom) {
                throw new AssertionError("Dropdown popup did not escape its scrollable parent's scissor.");
            }

            if (!runtime.mouseScrolled(popup.x() + popup.width() - 10,
                    popup.y() + 10, -2.0)) {
                throw new AssertionError("Dropdown popup did not capture its own mouse-wheel input.");
            }
            VantaUiNode.DropdownPopupBounds scrolledPopup = runtime.dropdownPopupBounds();
            if (scrolledPopup == null || scrolledPopup.firstOption() != 2) {
                throw new AssertionError("Scrolling the dropdown did not advance its visible option window: "
                        + scrolledPopup);
            }

            if (!runtime.mouseClicked(scrolledPopup.x() + 12, scrolledPopup.y() + 11,
                    0, client.font)) {
                throw new AssertionError("Scrolled dropdown option did not receive the click.");
            }
            if (!"Option 2".equals(selected[0]) || runtime.dropdownPopupBounds() != null) {
                throw new AssertionError("Scrolled dropdown option selection failed: " + selected[0]);
            }

            runtime.layout(client.font, 320, 220);
            if (!runtime.mouseClicked(controlX, controlY, 0, client.font)) {
                throw new AssertionError("Dropdown did not reopen for keyboard interaction.");
            }
            runtime.layout(client.font, 320, 220);
            if (!runtime.keyPressed(264, false) || !runtime.keyPressed(257, false)) {
                throw new AssertionError("Dropdown did not accept keyboard navigation and confirmation.");
            }
            if (!"Option 3".equals(selected[0]) || runtime.dropdownPopupBounds() != null) {
                throw new AssertionError("Keyboard dropdown selection failed: " + selected[0]);
            }

            runtime.layout(client.font, 320, 220);
            if (!runtime.mouseClicked(controlX, controlY, 0, client.font)) {
                throw new AssertionError("Dropdown did not reopen for parent-scroll dismissal.");
            }
            runtime.layout(client.font, 320, 220);
            if (!runtime.mouseScrolled(scroll.x() + 12, scroll.y() + 12, -1.0)
                    || runtime.dropdownPopupBounds() != null) {
                throw new AssertionError("Scrolling the parent viewport did not dismiss its dropdown.");
            }
            runtime.dispose();
        });
    }

    private static void verifyConnectedHudBackgrounds(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var rectangles = java.util.List.of(
                    new HudBackgroundRenderer.Bounds(10, 10, 40, 20),
                    new HudBackgroundRenderer.Bounds(43, 10, 70, 20),
                    new HudBackgroundRenderer.Bounds(73, 10, 90, 20),
                    new HudBackgroundRenderer.Bounds(10, 25, 40, 35)
            );
            var merged = HudBackgroundRenderer.mergeConnectedBounds(rectangles, 4);
            if (merged.size() != 2) {
                throw new AssertionError("HUD backgrounds did not merge the transitive group: " + merged);
            }
            boolean joined = merged.stream().anyMatch(bounds ->
                    bounds.left() == 10 && bounds.top() == 10
                            && bounds.right() == 90 && bounds.bottom() == 20);
            if (!joined) {
                throw new AssertionError("Connected HUD background bounds were not unioned: " + merged);
            }
            var strict = HudBackgroundRenderer.mergeConnectedBounds(rectangles, 2);
            if (strict.size() != 4) {
                throw new AssertionError("The configured connection distance was not respected: " + strict);
            }
            var overlapping = HudBackgroundRenderer.mergeConnectedBounds(java.util.List.of(
                    new HudBackgroundRenderer.Bounds(100, 100, 130, 120),
                    new HudBackgroundRenderer.Bounds(120, 110, 150, 130)
            ), 0);
            if (overlapping.size() != 1
                    || overlapping.get(0).left() != 100
                    || overlapping.get(0).top() != 100
                    || overlapping.get(0).right() != 150
                    || overlapping.get(0).bottom() != 130) {
                throw new AssertionError("Overlapping backgrounds must be unioned even with gap-bridging off: "
                        + overlapping);
            }
            var noPhantomConnection = HudBackgroundRenderer.mergeConnectedBounds(java.util.List.of(
                    new HudBackgroundRenderer.Bounds(0, 0, 10, 10),
                    new HudBackgroundRenderer.Bounds(14, 0, 24, 10),
                    new HudBackgroundRenderer.Bounds(10, 14, 14, 24)
            ), 4);
            if (noPhantomConnection.size() != 2) {
                throw new AssertionError("A union's empty bounding-box area must not connect unrelated HUDs: "
                        + noPhantomConnection);
            }
        });
    }

    private static void verifyKeybindCaptureLifecycle(ClientGameTestContext context) {
        context.runOnClient(client -> {
            KeyMapping mapping = Keybinds.guiKey;
            if (mapping == null) {
                throw new AssertionError("Vanta key mappings were not registered before the Controls test.");
            }
            String originalKey = mapping.saveString();
            String conflictingKey = Keybinds.fullbrightKey.saveString();
            VantaSettingsScreen screen = new VantaSettingsScreen(null);
            try {
                mapping.setKey(InputConstants.getKey(conflictingKey));
                KeyMapping.resetMapping();
                if (!screen.hasBindingConflict(mapping)) {
                    throw new AssertionError("The Controls page did not detect a duplicate Minecraft/Vanta binding.");
                }
                mapping.setKey(InputConstants.getKey(originalKey));
                KeyMapping.resetMapping();
                client.setScreen(screen);

                screen.beginKeyCapture(mapping);
                KeyEvent xKey = new KeyEvent(GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_X, 0);
                if (!Keybinds.isCaptureActive() || !screen.keyPressed(xKey)) {
                    throw new AssertionError("Keyboard key capture did not enter its active state.");
                }
                if (!mapping.saveString().equals(InputConstants.getKey(xKey).getName())) {
                    throw new AssertionError("The selected keyboard binding was not applied.");
                }
                if (!Keybinds.isCaptureActive()) {
                    throw new AssertionError("Gameplay hotkeys must remain suppressed until key release.");
                }
                if (!screen.keyReleased(xKey) || Keybinds.isCaptureActive()) {
                    throw new AssertionError("Keyboard capture suppression did not end on key release.");
                }

                screen.beginKeyCapture(mapping);
                KeyEvent escape = new KeyEvent(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ESCAPE, 0);
                if (!screen.keyPressed(escape) || !Keybinds.isCaptureActive()) {
                    throw new AssertionError("Escape did not cancel capture while retaining release suppression.");
                }
                if (!mapping.saveString().equals(InputConstants.getKey(xKey).getName())) {
                    throw new AssertionError("Cancelling a capture changed the current binding.");
                }
                if (!screen.keyReleased(escape) || Keybinds.isCaptureActive()) {
                    throw new AssertionError("Escape release did not clear the capture guard.");
                }

                screen.beginKeyCapture(mapping);
                KeyEvent backspace = new KeyEvent(
                        GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_BACKSPACE, 0);
                if (!screen.keyPressed(backspace)) {
                    throw new AssertionError("Backspace was not handled as the unbind command.");
                }
                if (!mapping.isUnbound()) {
                    throw new AssertionError("Backspace did not unbind the selected Vanta shortcut.");
                }
                if (!screen.keyReleased(backspace) || Keybinds.isCaptureActive()) {
                    throw new AssertionError("Unbind capture guard did not clear on key release.");
                }
            } finally {
                mapping.setKey(InputConstants.getKey(originalKey));
                KeyMapping.resetMapping();
                client.options.save();
                Keybinds.setCaptureActive(false);
                client.setScreen(null);
            }
        });
    }

    private static void verifyHudHitboxGeometry(ClientGameTestContext context) {
        context.runOnClient(client -> {
            HudManager.init();
            name.vantaclient.client.hud.ArmorHudElement armor =
                    HudManager.getElements().stream()
                            .filter(name.vantaclient.client.hud.ArmorHudElement.class::isInstance)
                            .map(name.vantaclient.client.hud.ArmorHudElement.class::cast)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Armor HUD is missing from the registry."));

            boolean originalOrientation = armor.isHorizontal();
            try {
                armor.setHorizontal(true);
                int expectedTopExpansion = Math.round(10 * armor.getScale());
                if (armor.getHeight() != 20
                        || armor.getHitboxY() != armor.getY() - expectedTopExpansion
                        || armor.getHitboxHeight() != armor.getScaledHeight() + expectedTopExpansion
                        || armor.getWidth() < 82) {
                    throw new AssertionError("Horizontal armor HUD hitbox must fit its actual label and bar area.");
                }
                if (!armor.isHovered(armor.getX() + 4, armor.getY() - Math.max(1, expectedTopExpansion / 2))) {
                    throw new AssertionError("The horizontal armor label area must be part of its draggable hitbox.");
                }

                armor.setHorizontal(false);
                if (armor.getHeight() != 86
                        || armor.getHitboxHeight() != armor.getScaledHeight()
                        || armor.getWidth() < 16) {
                    throw new AssertionError("Vertical armor HUD hitbox must fit its four rendered slots and labels.");
                }
            } finally {
                armor.setHorizontal(originalOrientation);
            }
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
        HudElement[] previewElements = new HudElement[2];
        int[] originalPositions = new int[4];
        boolean[] originalBackgrounds = new boolean[2];
        boolean[] originalConnect = new boolean[1];
        int[] originalConnectionGap = new int[1];
        boolean[] originalSnapToGrid = new boolean[1];
        context.runOnClient(client -> {
            HudManager.init();
            HudElement keystrokes = HudManager.getElements().stream()
                    .filter(element -> element.getBackgroundId().equals("KeystrokesHudElement"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Keystrokes HUD is missing from the editor registry."));
            HudElement fps = HudManager.getElements().stream()
                    .filter(element -> element.getBackgroundId().equals("FpsHudElement"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("FPS HUD is missing from the editor registry."));
            if (!keystrokes.shouldRender() || !fps.shouldRender()) {
                throw new AssertionError("Disabled HUD elements must remain previewable in the HUD editor.");
            }

            previewElements[0] = keystrokes;
            previewElements[1] = fps;
            originalPositions[0] = keystrokes.getX();
            originalPositions[1] = keystrokes.getY();
            originalPositions[2] = fps.getX();
            originalPositions[3] = fps.getY();

            VantaClientPreferences preferences = VantaClientPreferences.get();
            originalBackgrounds[0] = preferences.isHudBackgroundEnabled(keystrokes.getBackgroundId());
            originalBackgrounds[1] = preferences.isHudBackgroundEnabled(fps.getBackgroundId());
            originalConnect[0] = preferences.connectHudBackgrounds;
            originalConnectionGap[0] = preferences.hudBackgroundConnectionGap;
            originalSnapToGrid[0] = preferences.hudSnapToGrid;

            preferences.hudSnapToGrid = false;
            preferences.connectHudBackgrounds = true;
            preferences.hudBackgroundConnectionGap = 4;
            keystrokes.move(90, 110);
            fps.move(keystrokes.getX() + keystrokes.getHitboxWidth() + 2, keystrokes.getY());
            preferences.setHudBackgroundEnabled(keystrokes.getBackgroundId(), true);
            preferences.setHudBackgroundEnabled(fps.getBackgroundId(), true);

            var connected = HudBackgroundRenderer.mergeConnectedBounds(List.of(
                    new HudBackgroundRenderer.Bounds(
                            keystrokes.getHitboxX(), keystrokes.getHitboxY(),
                            keystrokes.getHitboxX() + keystrokes.getHitboxWidth(),
                            keystrokes.getHitboxY() + keystrokes.getHitboxHeight()),
                    new HudBackgroundRenderer.Bounds(
                            fps.getHitboxX(), fps.getHitboxY(),
                            fps.getHitboxX() + fps.getHitboxWidth(),
                            fps.getHitboxY() + fps.getHitboxHeight())
            ), preferences.hudBackgroundConnectionGap);
            if (connected.size() != 1) {
                throw new AssertionError("Adjacent HUD elements should share one connected background: "
                        + connected);
            }
            HudSettingsPanel.setSelected(keystrokes);
        });
        context.waitTicks(2);

        try {
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
        } finally {
            context.runOnClient(client -> {
                VantaClientPreferences preferences = VantaClientPreferences.get();
                for (int i = 0; i < previewElements.length; i++) {
                    HudElement element = previewElements[i];
                    if (element == null) continue;
                    element.move(originalPositions[i * 2], originalPositions[i * 2 + 1]);
                    preferences.setHudBackgroundEnabled(
                            element.getBackgroundId(), originalBackgrounds[i]);
                }
                preferences.connectHudBackgrounds = originalConnect[0];
                preferences.hudBackgroundConnectionGap = originalConnectionGap[0];
                preferences.hudSnapToGrid = originalSnapToGrid[0];
                VantaClientPreferences.save();
            });
        }

        context.setScreen(() -> settingsScreen);
        context.waitForScreen(VantaSettingsScreen.class);
    }

    private static void captureModrinthBrowser(ClientGameTestContext context,
                                                VantaSettingsScreen settingsScreen,
                                                String viewportLabel) {
        List<ModrinthBrowserService.Project> previewProjects = java.util.List.of(
                new ModrinthBrowserService.Project("fabric-api", "Fabric API",
                        "Core hooks and compatibility layer used by Fabric mods.",
                        "Fabric", "", 22_000_000),
                new ModrinthBrowserService.Project("sodium", "Sodium",
                        "Rendering optimisation for smoother frame pacing and higher performance.",
                        "CaffeineMC", "", 18_000_000),
                new ModrinthBrowserService.Project("lithium", "Lithium",
                        "General-purpose game logic optimisation.",
                        "CaffeineMC", "", 8_000_000),
                new ModrinthBrowserService.Project("modmenu", "Mod Menu",
                        "Browse installed mods and open the configuration screens they provide.",
                        "Prospector", "", 35_000_000),
                new ModrinthBrowserService.Project("indium", "Indium",
                        "Rendering API compatibility for mods using Fabric rendering hooks.",
                        "comp500", "", 4_000_000),
                new ModrinthBrowserService.Project("entityculling", "Entity Culling",
                        "Skip rendering entities and block entities that are not visible.",
                        "tr7zw", "", 6_000_000)
        );
        VantaModrinthBrowserScreen browser = context.computeOnClient(
                client -> new VantaModrinthBrowserScreen(settingsScreen, previewProjects));
        context.setScreen(() -> browser);
        context.waitForScreen(VantaModrinthBrowserScreen.class);
        context.waitTicks(3);

        Path screenshot = context.takeScreenshot("vanta-modrinth-" + viewportLabel);
        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Minecraft did not produce Modrinth browser screenshot: " + screenshot);
        }
        try {
            if (Files.size(screenshot) < 1_024L) {
                throw new AssertionError("Modrinth screenshot is unexpectedly small: " + screenshot);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not verify Modrinth screenshot: " + screenshot, exception);
        }

        context.setScreen(() -> settingsScreen);
        context.waitForScreen(VantaSettingsScreen.class);
    }

    private static void captureIntegrationsLibrary(ClientGameTestContext context,
                                                     VantaSettingsScreen settingsScreen,
                                                     String viewportLabel) {
        List<ExperimentalModHotLoader.AvailableMod> previewMods = List.of(
                new ExperimentalModHotLoader.AvailableMod("fabric-api", "Fabric API",
                        "0.141.4+1.21.11", java.nio.file.Path.of("fabric-api.jar"), ""),
                new ExperimentalModHotLoader.AvailableMod("sodium", "Sodium",
                        "0.8.4", java.nio.file.Path.of("sodium.jar"), ""),
                new ExperimentalModHotLoader.AvailableMod("lithium", "Lithium",
                        "0.20.0", java.nio.file.Path.of("lithium.jar"), ""),
                new ExperimentalModHotLoader.AvailableMod("modmenu", "Mod Menu",
                        "16.0.0", java.nio.file.Path.of("modmenu.jar"), ""),
                new ExperimentalModHotLoader.AvailableMod("indium", "Indium",
                        "1.0.36", java.nio.file.Path.of("indium.jar"), ""),
                new ExperimentalModHotLoader.AvailableMod("entityculling", "Entity Culling",
                        "1.8.0", java.nio.file.Path.of("entityculling.jar"), "")
        );
        VantaIntegrationsScreen library = context.computeOnClient(
                client -> new VantaIntegrationsScreen(settingsScreen, previewMods));
        context.setScreen(() -> library);
        context.waitForScreen(VantaIntegrationsScreen.class);
        context.waitTicks(3);

        Path screenshot = context.takeScreenshot("vanta-integrations-" + viewportLabel);
        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Minecraft did not produce integrations screenshot: " + screenshot);
        }
        try {
            if (Files.size(screenshot) < 1_024L) {
                throw new AssertionError("Integrations screenshot is unexpectedly small: " + screenshot);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not verify integrations screenshot: " + screenshot, exception);
        }

        VantaIntegrationsScreen details = context.computeOnClient(
                client -> new VantaIntegrationsScreen(settingsScreen, previewMods, "sodium"));
        context.setScreen(() -> details);
        context.waitForScreen(VantaIntegrationsScreen.class);
        context.waitTicks(3);

        Path detailsScreenshot = context.takeScreenshot("vanta-integration-details-" + viewportLabel);
        if (!Files.isRegularFile(detailsScreenshot)) {
            throw new AssertionError("Minecraft did not produce integration details screenshot: "
                    + detailsScreenshot);
        }
        try {
            if (Files.size(detailsScreenshot) < 1_024L) {
                throw new AssertionError("Integration details screenshot is unexpectedly small: "
                        + detailsScreenshot);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not verify integration details screenshot: "
                    + detailsScreenshot, exception);
        }

        context.setScreen(() -> settingsScreen);
        context.waitForScreen(VantaSettingsScreen.class);
    }

    private static void captureWorldShareScreen(ClientGameTestContext context,
                                                 VantaSettingsScreen settingsScreen,
                                                 String viewportLabel) {
        VantaWorldShareScreen shareScreen = context.computeOnClient(
                client -> new VantaWorldShareScreen(settingsScreen));
        context.setScreen(() -> shareScreen);
        context.waitForScreen(VantaWorldShareScreen.class);
        context.waitTicks(3);

        Path screenshot = context.takeScreenshot("vanta-world-share-" + viewportLabel);
        if (!Files.isRegularFile(screenshot)) {
            throw new AssertionError("Minecraft did not produce world-sharing screenshot: " + screenshot);
        }
        try {
            if (Files.size(screenshot) < 1_024L) {
                throw new AssertionError("World-sharing screenshot is unexpectedly small: " + screenshot);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not verify world-sharing screenshot: " + screenshot, exception);
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
        capturePage(context, screen, PAGE_CONTROLS,
                "vanta-ui-" + viewportLabel + "-controls");
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

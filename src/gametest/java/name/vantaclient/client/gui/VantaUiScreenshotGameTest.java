package name.vantaclient.client.gui;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import name.vantaclient.client.hudeditor.HudEditorScreen;
import name.vantaclient.client.hud.HudBackgroundRenderer;
import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.hudeditor.HudManager;
import name.vantaclient.client.hudeditor.HudSettingsPanel;
import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.integration.ModrinthBrowserService;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiMotion;
import name.vantaclient.client.integration.VantaLanHostingService;
import name.vantaclient.client.integration.net.LoopbackTcpBridge;
import name.vantaclient.client.integration.net.VantaRelayClient;
import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.init.Keybinds;
import name.vantaclient.client.feature.ChatTimestampsFeature;
import name.vantaclient.client.init.FeatureKeybinds;
import name.vantaclient.client.util.AdaptiveEntityDistanceController;
import name.vantaclient.client.util.FarTerrainLodMath;
import name.vantaclient.client.feature.FeatureRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

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
        verifyRelayEndpointValidation(context);
        verifyScrollbarThumbDragging(context);
        verifyDropdownPopupOverlay(context);
        verifyConnectedHudBackgrounds(context);
        verifyHudHitboxGeometry(context);
        verifyHudVisibilitySnapshot(context);
        verifyKeybindCaptureLifecycle(context);
        verifyChatTimestampDecoration(context);
        verifyAdaptiveEntityDistancePolicy(context);
        verifyFarTerrainPlanningMath(context);
        verifyUiMotion(context);
        verifyProgressComponent(context);
        verifyWorldShareStateWithoutWorld(context);
        verifyLoopbackTcpBridge(context);

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


    private static void verifyRelayEndpointValidation(ClientGameTestContext context) {
        context.runOnClient(client -> {
            String secure = VantaRelayClient.normalizeBaseUrl("https://relay.example.net");
            if (!"https://relay.example.net/".equals(secure)) {
                throw new AssertionError("HTTPS relay origin was not normalized: " + secure);
            }
            String local = VantaRelayClient.normalizeBaseUrl("http://localhost:8080");
            if (!"http://localhost:8080/".equals(local)) {
                throw new AssertionError("Loopback HTTP development origin was not normalized: " + local);
            }
            assertInvalidRelayEndpoint("http://relay.example.net");
            assertInvalidRelayEndpoint("wss://relay.example.net");
            assertInvalidRelayEndpoint("https://relay.example.net/path");
            assertInvalidRelayEndpoint("https://user:password@relay.example.net");
            assertInvalidRelayEndpoint("https://relay.example.net/?token=secret");

            if (VantaRemoteSessionManager.shouldCancelPendingGuestJoin()
                    || VantaRemoteSessionManager.isHosting()) {
                throw new AssertionError("Remote session manager must start without active sessions.");
            }
        });
    }

    private static void assertInvalidRelayEndpoint(String value) {
        try {
            VantaRelayClient.normalizeBaseUrl(value);
            throw new AssertionError("Relay endpoint must be rejected: " + value);
        } catch (IllegalArgumentException expected) {
            // Invalid endpoints are rejected before network activity begins.
        }
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

    private static void verifyLoopbackTcpBridge(ClientGameTestContext context) {
        context.runOnClient(client -> {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            try (ServerSocket echoServer = new ServerSocket(0, 2, loopback)) {
                Thread echoWorker = new Thread(() -> {
                    try (Socket socket = echoServer.accept()) {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = socket.getInputStream().read(buffer)) != -1) {
                            socket.getOutputStream().write(buffer, 0, read);
                            socket.getOutputStream().flush();
                        }
                        socket.shutdownOutput();
                    } catch (IOException exception) {
                        if (!echoServer.isClosed()) {
                            throw new AssertionError("Loopback echo server failed.", exception);
                        }
                    }
                }, "Vanta bridge echo test");
                echoWorker.setDaemon(true);
                echoWorker.start();

                try (LoopbackTcpBridge bridge = LoopbackTcpBridge.listen(() -> {
                    Socket upstream = new Socket();
                    upstream.connect(new InetSocketAddress(loopback, echoServer.getLocalPort()), 2_000);
                    upstream.setSoTimeout(2_000);
                    return LoopbackTcpBridge.fromSocket(upstream);
                }, 2)) {
                    if (!bridge.address().getAddress().isLoopbackAddress()) {
                        throw new AssertionError("A Vanta tunnel listener must bind to loopback only: "
                                + bridge.address());
                    }

                    byte[] payload = new byte[] {
                            0x00, 0x01, 0x7F, (byte) 0x80, (byte) 0xFF, 0x21, 0x42, 0x00
                    };
                    try (Socket clientSocket = new Socket()) {
                        clientSocket.connect(bridge.address(), 2_000);
                        clientSocket.setSoTimeout(2_000);
                        clientSocket.getOutputStream().write(payload);
                        clientSocket.getOutputStream().flush();
                        clientSocket.shutdownOutput();

                        byte[] echoed = clientSocket.getInputStream().readNBytes(payload.length);
                        if (!Arrays.equals(payload, echoed)) {
                            throw new AssertionError("Loopback tunnel corrupted binary payload: "
                                    + Arrays.toString(echoed));
                        }
                        if (clientSocket.getInputStream().read() != -1) {
                            throw new AssertionError("Loopback tunnel did not propagate the remote half-close.");
                        }
                    }
                }
            } catch (IOException exception) {
                throw new AssertionError("Loopback TCP bridge failed its local forwarding test.", exception);
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
            if (VantaUiMotion.advancePingPongPhase(0.25f, 1.0f / 60.0f, 0.9f, false) != 0.5f) {
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
            List<FeatureKeybinds.Binding> featureBindings = FeatureKeybinds.bindings();
            if (featureBindings.size() != FeatureRegistry.FEATURES.size()
                    || featureBindings.stream().map(FeatureKeybinds.Binding::feature).distinct().count()
                    != featureBindings.size()) {
                throw new AssertionError("Every registered feature must have exactly one feature shortcut.");
            }
            for (FeatureKeybinds.Binding binding : featureBindings) {
                if (FeatureKeybinds.mappingFor(binding.feature()) != binding.mapping()) {
                    throw new AssertionError("Feature keybinding lookup returned a different mapping.");
                }
                boolean foundInOptions = false;
                for (KeyMapping candidate : client.options.keyMappings) {
                    if (candidate == binding.mapping()) {
                        foundInOptions = true;
                        break;
                    }
                }
                if (!foundInOptions) {
                    throw new AssertionError("Feature shortcut was not registered with Minecraft options: "
                            + binding.feature().getName());
                }
            }
            FeatureKeybinds.Binding featureBinding = featureBindings.getFirst();
            String originalFeatureKey = featureBinding.mapping().saveString();
            VantaSettingsScreen screen = new VantaSettingsScreen(null);
            try {
                featureBinding.mapping().setKey(InputConstants.getKey(conflictingKey));
                KeyMapping.resetMapping();
                if (!screen.hasBindingConflict(featureBinding.mapping())) {
                    throw new AssertionError("Feature shortcuts must participate in Minecraft/Vanta conflict detection.");
                }
                featureBinding.mapping().setKey(InputConstants.getKey(originalFeatureKey));
                KeyMapping.resetMapping();
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
                featureBinding.mapping().setKey(InputConstants.getKey(originalFeatureKey));
                KeyMapping.resetMapping();
                client.options.save();
                Keybinds.setCaptureActive(false);
                client.setScreen(null);
            }
        });
    }

    private static void verifyAdaptiveEntityDistancePolicy(ClientGameTestContext context) {
        context.runOnClient(client -> {
            double reduced = AdaptiveEntityDistanceController.calculateNextScale(
                    1.0, 1.0, 0.65, 28.0, 42);
            if (Math.abs(reduced - 0.75) > 0.0001) {
                throw new AssertionError("Over-budget rendering did not reduce entity distance gradually: "
                        + reduced);
            }

            double floor = AdaptiveEntityDistanceController.calculateNextScale(
                    0.75, 1.0, 0.65, 30.0, 40);
            if (Math.abs(floor - 0.65) > 0.0001) {
                throw new AssertionError("Adaptive entity distance did not respect its configured floor: "
                        + floor);
            }

            double recovered = AdaptiveEntityDistanceController.calculateNextScale(
                    0.65, 1.0, 0.65, 8.0, 120);
            if (Math.abs(recovered - 0.775) > 0.0001) {
                throw new AssertionError("Entity distance did not recover gradually with frame headroom: "
                        + recovered);
            }

            double customMaximum = AdaptiveEntityDistanceController.calculateNextScale(
                    1.0, 2.0, 0.5, 8.0, 120);
            if (Math.abs(customMaximum - 1.125) > 0.0001) {
                throw new AssertionError("Adaptive recovery did not preserve the user's custom ceiling: "
                        + customMaximum);
            }

            double unchanged = AdaptiveEntityDistanceController.calculateNextScale(
                    0.8, 1.0, 0.65, 17.0, 60);
            if (Math.abs(unchanged - 0.8) > 0.0001) {
                throw new AssertionError("The adaptive policy changed distance without overload or headroom: "
                        + unchanged);
            }

            double noMeasurements = AdaptiveEntityDistanceController.calculateNextScale(
                    0.8, 1.0, 0.65, 0.0, 0);
            if (Math.abs(noMeasurements - 0.8) > 0.0001) {
                throw new AssertionError("The adaptive policy changed distance without fresh measurements: "
                        + noMeasurements);
            }

            double minimumBound = AdaptiveEntityDistanceController.calculateNextScale(
                    0.5, 1.0, 0.5, 30.0, 30);
            if (Math.abs(minimumBound - 0.5) > 0.0001) {
                throw new AssertionError("Adaptive entity distance went below Minecraft's supported minimum: "
                        + minimumBound);
            }
        });
    }

    private static void verifyFarTerrainPlanningMath(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (FarTerrainLodMath.shouldRebuildPlan(true,
                    100, -100, 103, -98, 32, 32, 16, 16, 512, 512)) {
                throw new AssertionError("FarView rebuilt an unchanged plan after sub-threshold movement.");
            }
            if (!FarTerrainLodMath.shouldRebuildPlan(true,
                    100, -100, 104, -100, 32, 32, 16, 16, 512, 512)
                    || !FarTerrainLodMath.shouldRebuildPlan(true,
                    100, -100, 100, -104, 32, 32, 16, 16, 512, 512)) {
                throw new AssertionError("FarView did not invalidate a plan after four chunks of movement.");
            }
            if (!FarTerrainLodMath.shouldRebuildPlan(false,
                    100, -100, 100, -100, 32, 32, 16, 16, 512, 512)) {
                throw new AssertionError("FarView did not create its initial tile plan.");
            }
            if (!FarTerrainLodMath.shouldRebuildPlan(true,
                    100, -100, 100, -100, 32, 16, 16, 16, 512, 512)
                    || !FarTerrainLodMath.shouldRebuildPlan(true,
                    100, -100, 100, -100, 32, 32, 16, 24, 512, 512)
                    || !FarTerrainLodMath.shouldRebuildPlan(true,
                    100, -100, 100, -100, 32, 32, 16, 16, 512, 256)) {
                throw new AssertionError("FarView did not invalidate a plan after topology settings changed.");
            }

            double nearest = FarTerrainLodMath.distanceToRectangleSquared(
                    0.0, 0.0, 3.0, 4.0, 13.0, 14.0);
            if (nearest != 25.0) {
                throw new AssertionError("Squared nearest-rectangle distance is incorrect: " + nearest);
            }
            double inside = FarTerrainLodMath.distanceToRectangleSquared(
                    8.0, 9.0, 3.0, 4.0, 13.0, 14.0);
            if (inside != 0.0) {
                throw new AssertionError("A point inside a tile must have zero rectangle distance: " + inside);
            }
            double farthest = FarTerrainLodMath.farthestCornerDistanceSquared(
                    0.0, 0.0, 3.0, 4.0, 13.0, 14.0);
            if (farthest != 365.0) {
                throw new AssertionError("Squared farthest-corner distance is incorrect: " + farthest);
            }

            if (!FarTerrainLodMath.isEntireTileInsideInnerRadius(99.0, 100.0)
                    || FarTerrainLodMath.isEntireTileInsideInnerRadius(101.0, 100.0)) {
                throw new AssertionError("Inner-radius tile rejection did not respect its conservative bound.");
            }
            if (!FarTerrainLodMath.isEntireTileInsideRenderBand(100.0, 400.0, 100.0, 400.0)
                    || FarTerrainLodMath.isEntireTileInsideRenderBand(99.0, 400.0, 100.0, 400.0)
                    || FarTerrainLodMath.isEntireTileInsideRenderBand(100.0, 401.0, 100.0, 400.0)) {
                throw new AssertionError("Visible-band tile fast path crossed a render-distance boundary.");
            }

            if (!FarTerrainLodMath.isWorkObsolete(3L, 4L, true)
                    || !FarTerrainLodMath.isWorkObsolete(4L, 4L, false)
                    || FarTerrainLodMath.isWorkObsolete(4L, 4L, true)) {
                throw new AssertionError("FarView queued-job validity did not respect world revision and desired tiles.");
            }

            if (FarTerrainLodMath.workerBackoffMillis(0.0, 0.0, 120) != 0
                    || FarTerrainLodMath.workerBackoffMillis(10.0, 10.0, 120) != 0
                    || FarTerrainLodMath.workerBackoffMillis(17.0, 10.0, 75) != 8
                    || FarTerrainLodMath.workerBackoffMillis(24.0, 10.0, 75) != 20
                    || FarTerrainLodMath.workerBackoffMillis(10.0, 42.0, 120) != 40
                    || FarTerrainLodMath.workerBackoffMillis(10.0, 10.0, 40) != 20
                    || FarTerrainLodMath.workerBackoffMillis(10.0, 10.0, 25) != 40) {
                throw new AssertionError("FarView worker backoff did not follow the measured client load tiers.");
            }

            if (FarTerrainLodMath.isOutsideHorizontalViewCone(10.0, 0.0, 1.0, 0.0, 0.0)
                    || !FarTerrainLodMath.isOutsideHorizontalViewCone(-10.0, 0.0, 1.0, 0.0, 0.0)
                    || !FarTerrainLodMath.isOutsideHorizontalViewCone(0.0, 10.0, 1.0, 0.0, 0.0)
                    || FarTerrainLodMath.isOutsideHorizontalViewCone(0.0, 10.0, 1.0, 0.0, 100.0)
                    || FarTerrainLodMath.isOutsideHorizontalViewCone(0.5, 0.5, 1.0, 0.0, 0.0)) {
                throw new AssertionError("FarView conservative horizontal view-cone culling failed boundary cases.");
            }

            double[] viewConeRadii = {0.0, 16.0, 128.0, 512.0, 4096.0};
            double baseViewAngle = Math.toRadians(68.0);
            for (int direction = 0; direction < 16; direction++) {
                double angle = direction * (Math.PI / 8.0);
                double forwardX = Math.cos(angle);
                double forwardZ = Math.sin(angle);
                for (int dx = -32; dx <= 32; dx += 4) {
                    for (int dz = -32; dz <= 32; dz += 4) {
                        double distanceSquared = (double) dx * dx + (double) dz * dz;
                        if (distanceSquared <= 1.0) continue;
                        double distance = Math.sqrt(distanceSquared);
                        double projection = (dx * forwardX + dz * forwardZ) / distance;
                        for (double radius : viewConeRadii) {
                            boolean referenceCull = projection
                                    < Math.cos(baseViewAngle + Math.atan2(radius, distance));
                            boolean actualCull = FarTerrainLodMath.isOutsideHorizontalViewCone(
                                    dx, dz, forwardX, forwardZ, radius);
                            if (actualCull && !referenceCull) {
                                throw new AssertionError("FarView view-cone helper culled a tile retained by "
                                        + "the original angular test: dx=" + dx + ", dz=" + dz
                                        + ", radius=" + radius + ", direction=" + direction);
                            }
                        }
                    }
                }
            }

            int[][] darkeningCases = {
                    {0xFFFFFFFF, 0xFF7F7F7F},
                    {0xFF010203, 0xFF000101},
                    {0xFF00FE80, 0xFF007F40},
                    {0xFF808080, 0xFF404040},
                    {0xFFABCDEF, 0xFF556677}
            };
            for (int[] colorCase : darkeningCases) {
                int actual = FarTerrainLodMath.darkenOpaqueArgb(colorCase[0]);
                if (actual != colorCase[1]) {
                    throw new AssertionError("FarView packed skirt darkening mismatch: expected 0x"
                            + Integer.toHexString(colorCase[1]) + ", got 0x" + Integer.toHexString(actual));
                }
            }

            Matrix4f vertexTransform = new Matrix4f()
                    .translation(13.0f, -7.0f, 21.0f)
                    .rotateY(0.37f)
                    .scale(1.25f);
            Vector3f reusablePosition = new Vector3f();
            float[][] vertexPositions = {
                    {0.0f, 0.0f, 0.0f},
                    {128.0f, 64.0f, -256.0f},
                    {-30_000_000.0f, 320.0f, 30_000_000.0f},
                    {-0.5f, -64.0f, 0.25f}
            };
            for (float[] position : vertexPositions) {
                Vector3f expectedPosition = new Vector3f(position[0], position[1], position[2]);
                vertexTransform.transformPosition(expectedPosition);
                reusablePosition.set(position[0], position[1], position[2]);
                vertexTransform.transformPosition(reusablePosition);
                if (Math.abs(expectedPosition.x - reusablePosition.x) > 0.001f
                        || Math.abs(expectedPosition.y - reusablePosition.y) > 0.001f
                        || Math.abs(expectedPosition.z - reusablePosition.z) > 0.001f) {
                    throw new AssertionError("Reusing FarView's vertex-transform scratch changed transformed coordinates.");
                }
            }

            int[] waterHeights = {
                    0, 0, 100,
                    0, 0, 100,
                    100, 100, 100
            };
            long[] waterMask = FarTerrainLodMath.createWaterCellMask(
                    waterHeights, 2, 50, true);
            if (!FarTerrainLodMath.isWaterCell(waterMask, 0)
                    || FarTerrainLodMath.isWaterCell(waterMask, 1)
                    || FarTerrainLodMath.isWaterCell(waterMask, 2)
                    || FarTerrainLodMath.isWaterCell(waterMask, 3)) {
                throw new AssertionError("FarView water-cell mask disagreed with the four-corner sea-level rule.");
            }
            long[] disabledWaterMask = FarTerrainLodMath.createWaterCellMask(
                    waterHeights, 2, 50, false);
            if (FarTerrainLodMath.isWaterCell(disabledWaterMask, 0)) {
                throw new AssertionError("FarView water mask emitted water in a dimension with water disabled.");
            }

            int[] largeWaterHeights = new int[100];
            largeWaterHeights[99] = 10;
            long[] largeWaterMask = FarTerrainLodMath.createWaterCellMask(
                    largeWaterHeights, 9, 10, true);
            if (!FarTerrainLodMath.isWaterCell(largeWaterMask, 63)
                    || !FarTerrainLodMath.isWaterCell(largeWaterMask, 64)
                    || FarTerrainLodMath.isWaterCell(largeWaterMask, 80)
                    || largeWaterMask.length != 2) {
                throw new AssertionError("FarView water mask failed across a packed 64-cell word boundary.");
            }
            if (FarTerrainLodMath.isWaterCell(largeWaterMask, -1)
                    || FarTerrainLodMath.isWaterCell(largeWaterMask, 81)) {
                throw new AssertionError("FarView water mask accepted an out-of-range cell index.");
            }

            int[] expectedSampleCoordinates = {100, 103, 105, 108, 110};
            for (int index = 0; index < expectedSampleCoordinates.length; index++) {
                int actual = FarTerrainLodMath.sampleWorldCoordinate(100, 10, index, 4);
                if (actual != expectedSampleCoordinates[index]) {
                    throw new AssertionError("FarView sample coordinate mismatch at index "
                            + index + ": expected " + expectedSampleCoordinates[index] + ", got " + actual);
                }
            }
            if (FarTerrainLodMath.sampleWorldCoordinate(-100, 10, 4, 4) != -90) {
                throw new AssertionError("FarView sample coordinates did not preserve negative world origins.");
            }

            if (FarTerrainLodMath.adjustDrawBudget(36_000, 1.9, 12_000, 96_000) != 38_000
                    || FarTerrainLodMath.adjustDrawBudget(36_000, 2.0, 12_000, 96_000) != 36_000
                    || FarTerrainLodMath.adjustDrawBudget(36_000, 4.0, 12_000, 96_000) != 36_000
                    || FarTerrainLodMath.adjustDrawBudget(36_000, 4.1, 12_000, 96_000) != 30_600
                    || FarTerrainLodMath.adjustDrawBudget(12_000, 8.0, 12_000, 96_000) != 12_000
                    || FarTerrainLodMath.adjustDrawBudget(96_000, 1.0, 12_000, 96_000) != 96_000
                    || FarTerrainLodMath.adjustDrawBudget(40_000, Double.NaN, 12_000, 96_000) != 40_000) {
                throw new AssertionError("FarView adaptive draw budget exceeded its bounded response policy.");
            }

            int[] expectedSizes = {8, 16, 32, 64, 128, 256, 512};
            double[] distances = {63.0, 64.0, 128.0, 256.0, 512.0, 1024.0, 1536.0};
            for (int index = 0; index < distances.length; index++) {
                double distanceSquared = distances[index] * distances[index];
                int actual = FarTerrainLodMath.desiredTileSizeForDistanceSquared(distanceSquared);
                if (actual != expectedSizes[index]) {
                    throw new AssertionError("FarView LOD threshold mismatch at " + distances[index]
                            + " chunks: expected " + expectedSizes[index] + ", got " + actual);
                }
            }
        });
    }

    private static void verifyChatTimestampDecoration(ClientGameTestContext context) {
        context.runOnClient(client -> {
            String originalFormat = ChatTimestampsFeature.getFormatLabel();
            Component original = Component.literal("Vanta timestamp probe");
            try {
                ChatTimestampsFeature.setFormat("24-hour");
                String twentyFourHour = ChatTimestampsFeature.decorateMessage(original).getString();
                if (!twentyFourHour.matches("\\[\\d{2}:\\d{2}\\] Vanta timestamp probe")) {
                    throw new AssertionError("24-hour timestamps were not attached to the message: "
                            + twentyFourHour);
                }

                ChatTimestampsFeature.setFormat("24-hour with seconds");
                String withSeconds = ChatTimestampsFeature.decorateMessage(original).getString();
                if (!withSeconds.matches("\\[\\d{2}:\\d{2}:\\d{2}\\] Vanta timestamp probe")) {
                    throw new AssertionError("Second-resolution timestamps were not formatted correctly: "
                            + withSeconds);
                }

                ChatTimestampsFeature.setFormat("12-hour");
                String twelveHour = ChatTimestampsFeature.decorateMessage(original).getString();
                if (!twelveHour.matches("\\[\\d{1,2}:\\d{2} (AM|PM)\\] Vanta timestamp probe")) {
                    throw new AssertionError("12-hour timestamps were not formatted correctly: " + twelveHour);
                }

                if (!"Vanta timestamp probe".equals(original.getString())) {
                    throw new AssertionError("Timestamp decoration mutated the original server message.");
                }
            } finally {
                ChatTimestampsFeature.setFormat(originalFormat);
            }
        });
    }

    private static void verifyHudVisibilitySnapshot(ClientGameTestContext context) {
        context.runOnClient(client -> {
            HudManager.init();
            boolean originalEditorEnabled = HudEditorFeature.INSTANCE.isEnabled();
            try {
                HudEditorFeature.INSTANCE.setEnabled(true);
                HudManager.refreshVisibleElements();
                List<HudElement> vanillaElements = HudManager.getVanillaElements();
                if (vanillaElements.size() != VanillaHudElement.Part.values().length) {
                    throw new AssertionError("The vanilla HUD registry must contain each vanilla element exactly once.");
                }
                List<HudElement> activeInEditor = HudManager.getActiveElements();
                for (HudElement element : HudManager.getElements()) {
                    if (element instanceof VanillaHudElement && !activeInEditor.contains(element)) {
                        throw new AssertionError("Vanilla HUD editor previews must be included in the cached visible snapshot.");
                    }
                }
                try {
                    activeInEditor.clear();
                    throw new AssertionError("The cached visible HUD list must not be externally mutable.");
                } catch (UnsupportedOperationException expected) {
                    // The renderer receives a read-only view over the tick-refreshed active list.
                }

                HudEditorFeature.INSTANCE.setEnabled(false);
                HudManager.refreshVisibleElements();
                if (HudManager.getActiveElements().stream().anyMatch(VanillaHudElement.class::isInstance)) {
                    throw new AssertionError("Vanilla HUD editor previews must be absent when the editor is closed.");
                }
            } finally {
                HudEditorFeature.INSTANCE.setEnabled(originalEditorEnabled);
                HudManager.refreshVisibleElements();
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

        VantaModrinthBrowserScreen progressBrowser = context.computeOnClient(
                client -> new VantaModrinthBrowserScreen(settingsScreen, previewProjects, true));
        context.setScreen(() -> progressBrowser);
        context.waitForScreen(VantaModrinthBrowserScreen.class);
        context.waitTicks(3);
        Path progressScreenshot = context.takeScreenshot("vanta-modrinth-progress-" + viewportLabel);
        if (!Files.isRegularFile(progressScreenshot)) {
            throw new AssertionError("Minecraft did not produce Modrinth progress screenshot: "
                    + progressScreenshot);
        }
        try {
            if (Files.size(progressScreenshot) < 1_024L) {
                throw new AssertionError("Modrinth progress screenshot is unexpectedly small: "
                        + progressScreenshot);
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not verify Modrinth progress screenshot: "
                    + progressScreenshot, exception);
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

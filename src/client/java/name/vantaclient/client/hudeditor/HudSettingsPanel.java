package name.vantaclient.client.hudeditor;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.hud.ArmorHudElement;
import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.feature.VanillaHudFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** HUD selection inspector, rendered and controlled by the shared Vanta UI runtime. */
public final class HudSettingsPanel {
    private static HudElement selected;
    private static VantaUiRuntime runtime;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private HudSettingsPanel() {}

    public static HudElement getSelected() {
        return selected;
    }

    public static boolean isOpen() {
        return selected != null && runtime != null;
    }

    public static void setSelected(HudElement element) {
        if (element == null) {
            clear();
            return;
        }
        selected = element;
        rebuild();
    }

    public static void close() {
        clear();
    }

    public static void clear() {
        selected = null;
        if (runtime != null) runtime.dispose();
        runtime = null;
        lastWidth = -1;
        lastHeight = -1;
    }

    private static void rebuild() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRoot());
        runtime.setScale((float) VantaClientPreferences.get().uiScale);
        lastWidth = -1;
        lastHeight = -1;
        layout();
    }

    private static VantaUiNode buildRoot() {
        Minecraft mc = Minecraft.getInstance();
        int viewportWidth = mc.getWindow().getGuiScaledWidth();
        int viewportHeight = mc.getWindow().getGuiScaledHeight();
        float preferredScale = (float) Math.max(0.8, Math.min(1.2,
                VantaClientPreferences.get().uiScale));
        float guiScale = (float) Math.max(1.0, mc.getWindow().getGuiScale());
        float effectiveScale = preferredScale / guiScale;
        // Match VantaUiRuntime's physical-pixel-equivalent coordinate space.
        int layoutWidth = Math.max(1, Math.round(viewportWidth / effectiveScale));
        int layoutHeight = Math.max(1, Math.round(viewportHeight / effectiveScale));
        int marginY = Math.max(1, Math.round(layoutHeight * 0.035f));
        int contentHeight = Math.max(0, layoutHeight - marginY * 2);
        int panelWidth = Math.max(160, Math.round(layoutWidth * 0.42f));
        int panelHeight = Math.max(112, Math.round(layoutHeight * 0.72f));
        int panelTop = Math.round(layoutHeight * 0.167f);

        VantaUiNode root = VantaUiNode.row(0, 0)
                .height(contentHeight)
                .align(VantaUiNode.Alignment.START);
        root.add(VantaUiNode.spacer(1).flex(1));

        VantaUiNode panel = VantaComponents.panel(VantaComponents.Surface.POPOVER, 10, 18)
                .width(panelWidth).height(panelHeight).scrollable()
                // Opaque enough that the placeholder HUD elements behind never show through.
                .background(0xFF0B111B).border(0x30FFFFFF).radius(18);

        VantaUiNode header = VantaUiNode.row(8, 0)
                .height(38)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode labels = VantaUiNode.column(2, 0).flex(1);
        labels.add(VantaUiNode.text("Element inspector", VantaUiTheme.TEXT_MUTED));
        labels.add(VantaUiNode.dynamicText(HudSettingsPanel::selectedName,
                VantaUiTheme.TEXT_PRIMARY));
        header.add(labels);
        header.add(name.vantaclient.client.gui.menu.MenuComponents.iconButton(
                name.vantaclient.client.gui.menu.MenuIcons.Icon.CLOSE, "Close inspector", true,
                HudSettingsPanel::close));
        panel.add(header);
        panel.add(VantaUiNode.paragraph("Changes apply immediately to the selected HUD element.",
                VantaUiTheme.TEXT_SECONDARY, 2));
        panel.add(VantaComponents.divider());

        panel.add(VantaUiNode.text("Scale and position", VantaUiTheme.TEXT_MUTED));
        panel.add(VantaUiNode.slider("Element scale", 0.25, 3.0, 0.05,
                () -> selected == null ? 1.0 : selected.getScale(),
                value -> { if (selected != null) selected.setScale((float) value); }));
        panel.add(VantaUiNode.row(0, 0).height(40).add(
                name.vantaclient.client.gui.menu.MenuComponents.button(mc.font, "Center on screen", null, false, () -> {
                    if (selected != null) selected.centerOnScreen();
                }).flex(1).tooltip("Move the selected HUD element to the center of the screen")));

        if (selected instanceof ArmorHudElement armor) {
            panel.add(VantaUiNode.dropdown("Orientation",
                    java.util.List.of("Vertical", "Horizontal"),
                    () -> armor.isHorizontal() ? "Horizontal" : "Vertical",
                    value -> armor.setHorizontal(value.equals("Horizontal"))));
        }

        if (selected instanceof VanillaHudElement) {
            panel.add(toggleRow("Apply vanilla HUD positions",
                    VanillaHudFeature.INSTANCE::isEnabled,
                    enabled -> {
                        VanillaHudFeature.INSTANCE.setEnabled(enabled);
                        VanillaHudFeature.INSTANCE.saveConfig();
                    }));
            panel.add(VantaUiNode.text(
                    "Native Minecraft rendering is retained; this changes only the component position.",
                    VantaUiTheme.TEXT_MUTED));
        }

        panel.add(VantaComponents.divider());
        panel.add(VantaUiNode.text("Background", VantaUiTheme.TEXT_MUTED));
        panel.add(toggleRow("Show dark background",
                () -> selected != null && VantaClientPreferences.get()
                        .isHudBackgroundEnabled(selected.getBackgroundId()),
                enabled -> {
                    if (selected != null) {
                        VantaClientPreferences.get().setHudBackgroundEnabled(
                                selected.getBackgroundId(), enabled);
                    }
                }));
        panel.add(toggleRow("Connect nearby backgrounds",
                () -> VantaClientPreferences.get().connectHudBackgrounds,
                enabled -> {
                    VantaClientPreferences.get().connectHudBackgrounds = enabled;
                    VantaClientPreferences.save();
                }));
        panel.add(VantaUiNode.slider("Connection distance", 0, 24, 1,
                () -> VantaClientPreferences.get().hudBackgroundConnectionGap,
                value -> {
                    VantaClientPreferences.get().hudBackgroundConnectionGap =
                            Math.max(0, Math.min(24, (int) Math.round(value)));
                    VantaClientPreferences.save();
                }));
        panel.add(VantaUiNode.paragraph(
                "Connected hitboxes merge into one continuous panel. Background opacity is 80%.",
                VantaUiTheme.TEXT_MUTED, 2));

        panel.add(VantaComponents.divider());
        panel.add(VantaUiNode.text("Grid and snapping", VantaUiTheme.TEXT_MUTED));
        panel.add(toggleRow("Snap to grid",
                () -> VantaClientPreferences.get().hudSnapToGrid,
                value -> {
                    VantaClientPreferences.get().hudSnapToGrid = value;
                    VantaClientPreferences.save();
                }));
        panel.add(toggleRow("Show grid",
                () -> VantaClientPreferences.get().hudShowGrid,
                value -> {
                    VantaClientPreferences.get().hudShowGrid = value;
                    VantaClientPreferences.save();
                }));
        panel.add(VantaUiNode.dropdown("Grid spacing",
                java.util.List.of("8 px", "16 px", "24 px", "32 px"),
                () -> VantaClientPreferences.get().hudGridSize + " px",
                value -> {
                    VantaClientPreferences.get().hudGridSize =
                            Integer.parseInt(value.substring(0, value.indexOf(' ')));
                    VantaClientPreferences.save();
                }));
        panel.add(VantaUiNode.row(0, 0).height(42).add(
                name.vantaclient.client.gui.menu.MenuComponents.button(mc.font, "Done", null, true,
                        HudSettingsPanel::close).flex(1)));

        VantaUiNode inspectorSlot = VantaUiNode.column(0, 0)
                .width(panelWidth)
                .height(contentHeight)
                .align(VantaUiNode.Alignment.START);
        inspectorSlot.add(VantaUiNode.spacer(1).height(panelTop));
        inspectorSlot.add(panel);
        root.add(inspectorSlot);
        return root;
    }

    /** Label on the left, Vanta switch on the right, matching the main menu's setting rows. */
    private static VantaUiNode toggleRow(String label, java.util.function.BooleanSupplier value,
                                         java.util.function.Consumer<Boolean> setter) {
        VantaUiNode row = VantaUiNode.row(8, 0).height(38).align(VantaUiNode.Alignment.CENTER);
        row.add(VantaUiNode.text(label, VantaUiTheme.TEXT_PRIMARY).flex(1));
        row.add(name.vantaclient.client.gui.menu.ModSettingsPage.toggleControl(value, setter).width(70));
        return row;
    }

    private static String selectedName() {
        if (selected == null) return "No element selected";
        if (selected instanceof VanillaHudElement vanilla) return vanilla.getDisplayName();
        return switch (selected.getClass().getSimpleName()) {
            case "ArmorHudElement" -> "Armor HUD";
            case "DebugHudElement" -> "Debug HUD";
            case "TotemCounterHudElement" -> "Totem Counter";
            case "PotionEffectsHudElement" -> "Potion Effects";
            case "FpsHudElement" -> "FPS";
            case "PingHudElement" -> "Ping";
            case "KeystrokesHudElement" -> "Keystrokes";
            case "CpsHudElement" -> "CPS";
            case "PerformanceHudElement" -> "Performance";
            default -> selected.getClass().getSimpleName();
        };
    }

    private static void layout() {
        if (runtime == null) return;
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        runtime.setScale((float) VantaClientPreferences.get().uiScale);
        if (width != lastWidth || height != lastHeight) {
            runtime.layout(mc.font, width, height);
            lastWidth = width;
            lastHeight = height;
        }
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (runtime == null || selected == null) return;
        layout();
        Minecraft mc = Minecraft.getInstance();
        runtime.render(graphics, mc.font, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), mouseX, mouseY);
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (runtime == null) return false;
        layout();
        return runtime.mouseClicked(mouseX, mouseY, button, Minecraft.getInstance().font);
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        return runtime != null && runtime.mouseDragged(mouseX, mouseY, 0);
    }

    public static void mouseReleased() {
        if (runtime != null) runtime.mouseReleased(0);
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return runtime != null && runtime.mouseScrolled(mouseX, mouseY, amount);
    }

    public static boolean keyPressed(int keyCode, boolean shiftDown) {
        return runtime != null && runtime.keyPressed(keyCode, shiftDown);
    }
}

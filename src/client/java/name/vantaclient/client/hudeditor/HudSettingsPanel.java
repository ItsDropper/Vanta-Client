package name.vantaclient.client.hudeditor;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
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
        int viewportHeight = mc.getWindow().getGuiScaledHeight();
        int panelHeight = Math.max(140, Math.min(390, viewportHeight - 104));

        VantaUiNode root = VantaUiNode.row(0, 0)
                .height(Math.max(0, viewportHeight - 44))
                .align(VantaUiNode.Alignment.START);
        root.add(VantaUiNode.spacer(1).flex(1));

        VantaUiNode panel = VantaUiNode.column(9, 14)
                .width(252)
                .height(panelHeight)
                .background(VantaUiTheme.PANEL)
                .scrollable();

        VantaUiNode header = VantaUiNode.row(8, 0)
                .height(34)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode labels = VantaUiNode.column(2, 0).flex(1);
        labels.add(VantaUiNode.eyebrow("HUD / INSPECTOR"));
        labels.add(VantaUiNode.dynamicText(HudSettingsPanel::selectedName,
                VantaUiTheme.TEXT_PRIMARY));
        header.add(labels);
        header.add(VantaUiNode.button("×", HudSettingsPanel::close)
                .size(28, 28).tooltip("Close inspector"));
        panel.add(header);
        panel.add(VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE));

        if (selected instanceof ArmorHudElement armor) {
            panel.add(VantaUiNode.dropdown("Orientation",
                    java.util.List.of("Vertical", "Horizontal"),
                    () -> armor.isHorizontal() ? "Horizontal" : "Vertical",
                    value -> armor.setHorizontal(value.equals("Horizontal"))));
        }

        if (selected instanceof VanillaHudElement) {
            panel.add(VantaUiNode.toggle("Apply vanilla HUD positions",
                    VanillaHudFeature.INSTANCE::isEnabled,
                    enabled -> {
                        VanillaHudFeature.INSTANCE.setEnabled(enabled);
                        VanillaHudFeature.INSTANCE.saveConfig();
                    }));
            panel.add(VantaUiNode.text(
                    "The selected vanilla component keeps its native rendering and uses the position set here.",
                    VantaUiTheme.TEXT_MUTED));
        }

        panel.add(VantaUiNode.slider("Element scale", 0.25, 3.0, 0.05,
                () -> selected == null ? 1.0 : selected.getScale(),
                value -> { if (selected != null) selected.setScale((float) value); }));

        panel.add(VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE));
        panel.add(VantaUiNode.eyebrow("ALIGNMENT"));
        panel.add(VantaUiNode.toggle("Snap to grid",
                () -> VantaClientPreferences.get().hudSnapToGrid,
                value -> {
                    VantaClientPreferences.get().hudSnapToGrid = value;
                    VantaClientPreferences.save();
                }));
        panel.add(VantaUiNode.toggle("Show grid",
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
        panel.add(VantaUiNode.button("Done", HudSettingsPanel::close)
                .height(30));

        VantaUiNode inspectorSlot = VantaUiNode.column(0, 0)
                .width(252)
                .height(Math.max(0, viewportHeight - 44))
                .align(VantaUiNode.Alignment.START);
        inspectorSlot.add(VantaUiNode.spacer(1).height(60));
        inspectorSlot.add(panel);
        root.add(inspectorSlot);
        return root;
    }

    private static String selectedName() {
        if (selected == null) return "No element selected";
        if (selected instanceof VanillaHudElement vanilla) return vanilla.getDisplayName();
        return switch (selected.getClass().getSimpleName()) {
            case "ArmorHudElement" -> "Armor HUD";
            case "DebugHudElement" -> "Debug HUD";
            case "TotemCounterHudElement" -> "Totem Counter";
            case "PotionEffectsHudElement" -> "Potion Effects";
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

package name.vantaclient.client.hudeditor;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.hud.ArmorHudElement;
import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.feature.VanillaHudFeature;
import name.vantaclient.client.gui.menu.MenuComponents;
import name.vantaclient.client.gui.menu.MenuIcons;
import name.vantaclient.client.gui.menu.MenuTheme;
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
        int panelHeight = Math.max(112, Math.round(layoutHeight * 0.80f));
        int panelTop = Math.round(layoutHeight * 0.125f);

        VantaUiNode root = VantaUiNode.row(0, 0)
                .height(contentHeight)
                .align(VantaUiNode.Alignment.START);
        root.add(VantaUiNode.spacer(1).flex(1));

        VantaUiNode shellPanel = VantaUiNode.column(0, 0)
                .width(panelWidth).height(panelHeight)
                // Opaque enough that the placeholder HUD elements behind never show through.
                .background(0xFF0B111B).border(0x30FFFFFF).radius(18);
        net.minecraft.client.gui.Font font = mc.font;
        int labelWidthWide = Math.max(60, panelWidth - 36 - 32 - 230);
        int labelWidthNarrow = Math.max(60, panelWidth - 36 - 32 - 150);
        int labelWidth = labelWidthNarrow;
        VantaUiNode panel = VantaUiNode.column(10, 16).flex(1).scrollable();

        VantaUiNode header = VantaUiNode.row(8, 0).height(52)
                .align(VantaUiNode.Alignment.CENTER);
        header.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.text(g, f, "Element inspector", x, y + 12, MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
            MenuTheme.text(g, f, MenuTheme.fit(f, selectedName(), w, MenuTheme.FONT_MEDIUM), x, y + 34,
                    MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
        }).flex(1).height(48));
        header.add(MenuComponents.iconButton(MenuIcons.Icon.CLOSE, "Close inspector", true,
                HudSettingsPanel::close));
        shellPanel.add(VantaUiNode.column(0, 16).height(68).add(header));
        shellPanel.add(panel);

        panel.add(MenuComponents.sectionTitle("Position and size"));
        panel.add(MenuComponents.settingRow(font, "Element scale", null,
                VantaUiNode.slider("", 0.25, 3.0, 0.05,
                        () -> selected == null ? 1.0 : selected.getScale(),
                        value -> { if (selected != null) selected.setScale((float) value); })
                        .menuStyle().height(30), 230, labelWidthWide));
        panel.add(VantaUiNode.row(0, 0).height(44).add(
                MenuComponents.button(font, "Center on screen", MenuIcons.Icon.HUD, false, () -> {
                    if (selected != null) selected.centerOnScreen();
                }).flex(1)));

        if (selected instanceof ArmorHudElement armor) {
            panel.add(MenuComponents.settingRow(font, "Orientation", null,
                    VantaUiNode.dropdown("", java.util.List.of("Vertical", "Horizontal"),
                            () -> armor.isHorizontal() ? "Horizontal" : "Vertical",
                            value -> armor.setHorizontal(value.equals("Horizontal"))).menuStyle().height(34),
                    150, labelWidth));
        }
        if (selected instanceof VanillaHudElement) {
            panel.add(MenuComponents.settingRow(font, "Apply vanilla HUD positions",
                    "Changes only where Minecraft draws it.",
                    toggleControl(VanillaHudFeature.INSTANCE::isEnabled, enabled -> {
                        VanillaHudFeature.INSTANCE.setEnabled(enabled);
                        VanillaHudFeature.INSTANCE.saveConfig();
                    }), 70, labelWidth));
        }

        panel.add(MenuComponents.sectionTitle("Background"));
        panel.add(MenuComponents.settingRow(font, "Show dark background", null,
                toggleControl(() -> selected != null && VantaClientPreferences.get()
                        .isHudBackgroundEnabled(selected.getBackgroundId()),
                        enabled -> {
                            if (selected != null) {
                                VantaClientPreferences.get().setHudBackgroundEnabled(
                                        selected.getBackgroundId(), enabled);
                            }
                        }), 70, labelWidth));
        panel.add(MenuComponents.settingRow(font, "Connect nearby backgrounds",
                "Merges close hitboxes into one panel.",
                toggleControl(() -> VantaClientPreferences.get().connectHudBackgrounds,
                        enabled -> {
                            VantaClientPreferences.get().connectHudBackgrounds = enabled;
                            VantaClientPreferences.save();
                        }), 70, labelWidth));
        panel.add(MenuComponents.settingRow(font, "Connection distance", null,
                VantaUiNode.slider("", 0, 24, 1,
                        () -> VantaClientPreferences.get().hudBackgroundConnectionGap,
                        value -> {
                            VantaClientPreferences.get().hudBackgroundConnectionGap =
                                    Math.max(0, Math.min(24, (int) Math.round(value)));
                            VantaClientPreferences.save();
                        }).menuStyle().height(30), 230, labelWidthWide));

        panel.add(MenuComponents.sectionTitle("Grid"));
        panel.add(MenuComponents.settingRow(font, "Snap to grid", null,
                toggleControl(() -> VantaClientPreferences.get().hudSnapToGrid,
                        value -> {
                            VantaClientPreferences.get().hudSnapToGrid = value;
                            VantaClientPreferences.save();
                        }), 70, labelWidth));
        panel.add(MenuComponents.settingRow(font, "Show grid", null,
                toggleControl(() -> VantaClientPreferences.get().hudShowGrid,
                        value -> {
                            VantaClientPreferences.get().hudShowGrid = value;
                            VantaClientPreferences.save();
                        }), 70, labelWidth));
        panel.add(MenuComponents.settingRow(font, "Grid spacing", null,
                VantaUiNode.dropdown("", java.util.List.of("8 px", "16 px", "24 px", "32 px"),
                        () -> VantaClientPreferences.get().hudGridSize + " px",
                        value -> {
                            VantaClientPreferences.get().hudGridSize =
                                    Integer.parseInt(value.substring(0, value.indexOf(' ')));
                            VantaClientPreferences.save();
                        }).menuStyle().height(34), 150, labelWidth));
        shellPanel.add(VantaUiNode.row(0, 16).height(70).align(VantaUiNode.Alignment.CENTER).add(
                MenuComponents.button(font, "Done", null, true, HudSettingsPanel::close).flex(1).height(44)));

        VantaUiNode inspectorSlot = VantaUiNode.column(0, 0)
                .width(panelWidth)
                .height(contentHeight)
                .align(VantaUiNode.Alignment.START);
        inspectorSlot.add(VantaUiNode.spacer(1).height(panelTop));
        inspectorSlot.add(shellPanel);
        root.add(inspectorSlot);
        return root;
    }

    private static VantaUiNode toggleControl(java.util.function.BooleanSupplier value,
                                              java.util.function.Consumer<Boolean> setter) {
        return name.vantaclient.client.gui.menu.ModSettingsPage.toggleControl(value, setter);
    }

    private static String selectedName() {
        return selected == null ? "No element selected" : selected.getDisplayName();
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

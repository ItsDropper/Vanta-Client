package name.vantaclient.client.hudeditor;

import name.vantaclient.client.gui.VantaText;
import name.vantaclient.client.gui.compose.VantaCompose;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.hud.HudBackgroundRenderer;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiMotion;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

public class HudEditorScreen extends Screen implements VantaCompose.Screen {
    private float entrance = 0.0f;
    private float selectionPulse = 0.0f;
    private long lastAnimationFrameNanos;
    private boolean panelPointer;
    private boolean toolbarPointer;
    private VantaUiRuntime toolbarRuntime;
    private final net.minecraft.client.gui.screens.Screen parent;

    public HudEditorScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HudEditorFeature.INSTANCE.setEnabled(true);
        HudManager.refreshVisibleElements();
        buildToolbar();
    }

    private void buildToolbar() {
        if (toolbarRuntime != null) toolbarRuntime.dispose();
        // GUI scale 3 commonly yields a 640×360 viewport and scale 4
        // roughly 480×270; compact the toolbar by either dimension.
        boolean compact = VantaUiRuntime.designWidth(width, height) < 700.0f
                || VantaUiRuntime.designHeight(width, height) < 400.0f;
        boolean showGridControl = !compact;
        int toolbarHeight = compact ? 36 : 56;

        VantaUiNode root = VantaUiNode.row(0, 0).height(toolbarHeight)
                .align(VantaUiNode.Alignment.START);
        VantaUiNode toolbar = VantaComponents.panel(VantaComponents.Surface.POPOVER,
                VantaUiNode.Direction.ROW, compact ? 8 : 12, compact ? 8 : 14)
                .height(toolbarHeight).flex(1)
                .align(VantaUiNode.Alignment.CENTER);
        toolbar.add(VantaUiNode.image(Identifier.fromNamespaceAndPath(
                "vanta", "textures/gui/title/mark_small.png"))
                .size(compact ? 20 : 26, compact ? 20 : 26));
        VantaUiNode labels = VantaUiNode.column(2, 0);
        labels.add(VantaUiNode.title("HUD editor"));
        if (!compact) {
            labels.add(VantaUiNode.text("Arrange your on-screen elements",
                    VantaUiTheme.TEXT_MUTED));
        }
        toolbar.add(labels);
        toolbar.add(VantaUiNode.spacer(1).flex(1));
        if (showGridControl) {
            toolbar.add(VantaUiNode.toggle("Grid",
                    () -> VantaClientPreferences.get().hudShowGrid,
                    value -> {
                        VantaClientPreferences.get().hudShowGrid = value;
                        VantaClientPreferences.save();
                    }).width(compact ? 76 : 92).tooltip("Show or hide the alignment grid")
                    .align(VantaUiNode.Alignment.CENTER));
        }
        toolbar.add(VantaUiNode.toggle("Snap",
                () -> VantaClientPreferences.get().hudSnapToGrid,
                value -> {
                    VantaClientPreferences.get().hudSnapToGrid = value;
                    VantaClientPreferences.save();
                }).width(compact ? 68 : 84)
                .tooltip("Snap moved elements to the alignment grid")
                .align(VantaUiNode.Alignment.CENTER));
        toolbar.add(VantaComponents.actionButton("Done", null,
                VantaComponents.ButtonVariant.PRIMARY, this::onClose)
                .size(compact ? 56 : 82, compact ? 26 : 32)
                .tooltip("Finish editing the HUD"));
        root.add(toolbar);
        toolbarRuntime = new VantaUiRuntime(root);
        toolbarRuntime.setScale((float) VantaClientPreferences.get().uiScale);
        toolbarRuntime.layout(font, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        VantaClientPreferences preferences = VantaClientPreferences.get();
        boolean animations = preferences.animations;
        long nowNanos = System.nanoTime();
        float animationDeltaSeconds = VantaUiMotion.frameDeltaSeconds(
                lastAnimationFrameNanos, nowNanos);
        lastAnimationFrameNanos = nowNanos;
        entrance = VantaUiMotion.approach(
                entrance, 1.0f, 12.0f, animationDeltaSeconds, animations);

        VantaCompose.renderScrim(graphics, width, height);
        if (preferences.hudShowGrid) drawGrid(graphics, preferences.hudGridSize, entrance);

        HudMouseHandler.updateHover(mouseX, mouseY);
        HudBackgroundRenderer.render(graphics);
        HudManager.render(graphics);
        drawElementHitboxes(graphics, preferences.accent.color());

        HudElement focused = HudSettingsPanel.getSelected();
        if (focused == null) focused = HudMouseHandler.getHovered();
        selectionPulse = VantaUiMotion.approach(selectionPulse,
                focused == null ? 0.0f : 1.0f, 15.0f, animationDeltaSeconds, animations);
        if (focused != null) {
            drawSelection(graphics, focused, preferences.accent.color(), selectionPulse);
            drawElementTag(graphics, mc, focused, preferences.accent.color());
        }

        if (toolbarRuntime != null) {
            toolbarRuntime.setScale((float) preferences.uiScale);
            toolbarRuntime.render(graphics, font, width, height, mouseX, mouseY);
        }
        HudSettingsPanel.render(graphics, mouseX, mouseY);
        drawInteractionHint(graphics, mc);
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawInteractionHint(GuiGraphics graphics, Minecraft mc) {
        String[] hints = HudSettingsPanel.isOpen()
                ? new String[]{"Adjust settings", "Drag to reposition", "Esc to exit"}
                : new String[]{"Drag to move", "Click to edit", "Esc to exit"};
        float size = 11.5f;
        int gap = 18;
        // Drop the leading hints until the pill fits the screen so it never runs off the edges.
        int total = hintsWidth(hints, size, gap);
        while (hints.length > 1 && total + 36 > this.width - 24) {
            hints = java.util.Arrays.copyOfRange(hints, 1, hints.length);
            total = hintsWidth(hints, size, gap);
        }
        int w = total + 36;
        int h = 26;
        int x = (this.width - w) / 2;
        int y = this.height - h - 14;
        fillRounded(graphics, x, y, w, h, 13, 0xD60A1018);
        drawRoundedOutline(graphics, x, y, w, h, 13, 0x2EFFFFFF, 1);
        int tx = x + 18;
        for (int i = 0; i < hints.length; i++) {
            VantaText.draw(graphics, hints[i], tx, y + h / 2.0f, size, 0xFFC3CDDC, VantaText.Weight.MEDIUM);
            tx += VantaText.width(hints[i], size, VantaText.Weight.MEDIUM);
            if (i < hints.length - 1) {
                graphics.fill(tx + gap / 2, y + 8, tx + gap / 2 + 1, y + h - 8, 0x33FFFFFF);
                tx += gap;
            }
        }
    }

    private static int hintsWidth(String[] hints, float size, int gap) {
        int total = 0;
        for (String hint : hints) total += VantaText.width(hint, size, VantaText.Weight.MEDIUM);
        return total + gap * (hints.length - 1);
    }

    private void drawGrid(GuiGraphics graphics, int spacing, float opacity) {
        int color = VantaUiTheme.withAlpha(VantaClientPreferences.get().accent.color(),
                Math.round(34.0f * opacity));
        for (int y = 12; y < height; y += spacing) {
            for (int x = 12; x < width; x += spacing) {
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    private static String elementName(HudElement element) {
        return element instanceof VanillaHudElement vanilla
                ? vanilla.getDisplayName()
                : switch (element.getClass().getSimpleName()) {
                    case "ArmorHudElement" -> "Armor";
                    case "DebugHudElement" -> "Debug";
                    case "TotemCounterHudElement" -> "Totem counter";
                    case "PotionEffectsHudElement" -> "Potion effects";
                    default -> element.getClass().getSimpleName()
                            .replace("HudElement", "").replaceAll("([a-z])([A-Z])", "$1 $2");
                };
    }

    private void drawElementHitboxes(GuiGraphics graphics, int accent) {
        HudElement hovered = HudMouseHandler.getHovered();
        for (HudElement element : HudManager.getElements()) {
            // Draw the guide on the exact hit-test/background rectangle. Selection
            // emphasis is a separate outer outline and must not redefine the hitbox.
            int x = element.getHitboxX();
            int y = element.getHitboxY();
            int w = Math.max(1, element.getHitboxWidth());
            int h = Math.max(1, element.getHitboxHeight());
            boolean selected = element == HudSettingsPanel.getSelected();
            boolean hot = selected || element == hovered;
            fillRounded(graphics, x, y, w, h, 6,
                    hot ? VantaUiTheme.withAlpha(accent, 0x2E) : 0x12FFFFFF);
            drawRoundedOutline(graphics, x, y, w, h, 6,
                    hot ? accent : 0x3DFFFFFF, 1);
            if (hot) continue;
            // A quiet in-box name; boxes too small for it stay unlabeled until hovered.
            String name = elementName(element);
            int available = w - 12;
            if (h >= 18 && VantaText.width(name, 10, VantaText.Weight.MEDIUM) <= available) {
                VantaText.draw(graphics, name, x + 6, y + 9, 10, 0xB8DCE6F5, VantaText.Weight.MEDIUM);
            }
        }
    }

    private void drawSelection(GuiGraphics graphics, HudElement element,
                               int accent, float pulse) {
        int x = element.getHitboxX() - 3;
        int y = element.getHitboxY() - 3;
        int w = Math.max(12, element.getHitboxWidth() + 6);
        int h = Math.max(12, element.getHitboxHeight() + 6);
        int color = (Math.round(120.0f + 100.0f * pulse) << 24)
                | (accent & 0x00FFFFFF);
        drawRoundedOutline(graphics, x, y, w, h, 8, color, 1);
    }

    private void drawElementTag(GuiGraphics graphics, Minecraft mc,
                                HudElement element, int accent) {
        String label = elementName(element);
        int tagWidth = VantaText.width(label, 11, VantaText.Weight.BOLD) + 18;
        int x = Math.max(8, Math.min(element.getHitboxX() - 3, width - tagWidth - 8));
        int toolbarBottom = 68;
        int y = element.getHitboxY() - 28;
        if (y < toolbarBottom) y = Math.min(height - 30, element.getHitboxY() + element.getHitboxHeight() + 10);
        fillRounded(graphics, x, y, tagWidth, 20, 10, accent);
        VantaText.drawCentered(graphics, label, x + tagWidth / 2.0f, y + 10, 11,
                0xFFFFFFFF, VantaText.Weight.BOLD);
    }

    private static void drawRoundedOutline(GuiGraphics graphics, int x, int y,
                                            int width, int height, int radius,
                                            int color, int thickness) {
        VantaUiTheme.outlineSquircle(graphics, x, y, width, height, radius, color, thickness);
    }

    private static void fillRounded(GuiGraphics graphics, int x, int y,
                                     int width, int height, int radius, int color) {
        VantaUiTheme.fillSquircle(graphics, x, y, width, height, radius, color);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (HudSettingsPanel.mouseClicked(mouseX, mouseY, event.button())) {
            panelPointer = true;
            return true;
        }
        if (toolbarRuntime != null
                && toolbarRuntime.mouseClicked(mouseX, mouseY, event.button(), font)) {
            toolbarPointer = true;
            return true;
        }
        if (event.button() == 0) {
            HudMouseHandler.updateHover(mouseX, mouseY);
            HudElement hovered = HudMouseHandler.getHovered();
            if (hovered != null) {
                HudMouseHandler.startDragging(hovered, mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0) {
            boolean moved = HudMouseHandler.hasMoved();
            boolean consumedByPanel = panelPointer || toolbarPointer;
            HudMouseHandler.release();
            HudSettingsPanel.mouseReleased();
            panelPointer = false;
            toolbarPointer = false;
            if (!moved && !consumedByPanel) {
                HudMouseHandler.updateHover(event.x(), event.y());
                HudElement hovered = HudMouseHandler.getHovered();
                if (hovered != null) HudSettingsPanel.setSelected(hovered);
            }
        }
        if (toolbarRuntime != null) toolbarRuntime.mouseReleased(event.button());
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
                                double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (panelPointer) {
            HudSettingsPanel.mouseDragged(mouseX, mouseY);
            return true;
        }
        if (toolbarPointer) return true;
        if (event.button() == 0) {
            HudMouseHandler.drag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (HudSettingsPanel.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (HudSettingsPanel.keyPressed(event.key(), shiftDown)) return true;
        if (toolbarRuntime != null && toolbarRuntime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        HudMouseHandler.release();
        panelPointer = false;
        toolbarPointer = false;
        if (toolbarRuntime != null) toolbarRuntime.dispose();
        HudSettingsPanel.clear();
        HudEditorFeature.INSTANCE.setEnabled(false);
        HudManager.refreshVisibleElements();
        super.removed();
    }

    @Override
    public void onClose() {
        HudMouseHandler.release();
        panelPointer = false;
        HudSettingsPanel.clear();
        if (parent != null) Minecraft.getInstance().setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

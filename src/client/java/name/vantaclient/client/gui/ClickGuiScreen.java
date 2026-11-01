package name.vantaclient.client.gui;

import name.vantaclient.client.feature.Category;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.gui.components.FeatureButton;
import name.vantaclient.client.gui.components.FeaturePanel;
import name.vantaclient.client.gui.components.GuiInitializer;
import name.vantaclient.client.gui.components.settings.SettingsPanel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClickGuiScreen extends Screen {

    private FeaturePanel featurePanel;

    private Category selectedCategory = Category.HUD;

    private SettingsPanel settingsPanel;

    private Feature selectedFeature;

    private Feature hoveredFeature;

    public ClickGuiScreen() {
        super(Component.literal("DropperUtils"));
    }

    @Override
    protected void init() {
        settingsPanel = new SettingsPanel(this);

        rebuildWidgets();
    }

    public void openIntegrations() {
        Minecraft.getInstance().setScreen(new VantaIntegrationsScreen(this));
    }

    public void addButton(AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    public void addSlider(AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    @Override
    protected void rebuildWidgets() {

        clearWidgets();

        hoveredFeature = null;

        GuiInitializer.setup(this);
    }

    public void openSettings(
            Feature feature,
            int buttonX,
            int buttonY
    ) {
        selectedFeature = feature;

        settingsPanel.open(
                feature,
                buttonX,
                buttonY
        );
    }

    public void closeSettings() {

        selectedFeature = null;

        settingsPanel.close();

        rebuildWidgets();
    }

    public boolean isSettingsOpen() {
        return selectedFeature != null;
    }

    @Override
    public void tick() {
        super.tick();

        if (settingsPanel != null) {
            settingsPanel.tick();
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {

        /*
         * Background
         */
        graphics.fill(
                0,
                0,
                width,
                height,
                0x99000000
        );

        /*
         * Title
         */
        graphics.drawCenteredString(
                font,
                "DropperUtils",
                width / 2,
                40,
                0xFFFFFFFF
        );

        /*
         * Render the settings panel FIRST.
         *
         * This draws the panel background underneath
         * the actual slider/button widgets.
         */
        if (settingsPanel != null) {
            settingsPanel.render(
                    graphics,
                    mouseX,
                    mouseY
            );
        }

        /*
         * Render all widgets.
         *
         * This now puts sliders and the CloseButton
         * ABOVE the settings panel background.
         */
        super.render(
                graphics,
                mouseX,
                mouseY,
                delta
        );

        /*
         * Find the feature currently under the mouse.
         */
        hoveredFeature = null;

        for (GuiEventListener widget : children()) {

            if (widget instanceof FeatureButton featureButton
                    && featureButton.isMouseOver(mouseX, mouseY)) {

                hoveredFeature = featureButton.getFeature();
                break;
            }
        }

        /*
         * Feature description.
         */
        if (hoveredFeature != null) {

            int padding = 10;
            int minWidth = 160;
            int maxWidth = 360;

            String title = hoveredFeature.getName();
            String description = hoveredFeature.getDescription();

            int titleWidth = font.width(title);
            int descriptionWidth = font.width(description);

            int panelWidth = Math.max(
                    minWidth,
                    Math.min(
                            maxWidth,
                            Math.max(titleWidth, descriptionWidth)
                                    + padding * 2
                    )
            );

            int panelHeight = 55;

            int panelX = width / 2 - 75;
            int panelY = 210;

            /*
             * Background
             */
            graphics.fill(
                    panelX,
                    panelY,
                    panelX + panelWidth,
                    panelY + panelHeight,
                    0xEE18181C
            );

            /*
             * Accent
             */
            graphics.fill(
                    panelX,
                    panelY,
                    panelX + 2,
                    panelY + panelHeight,
                    0xFF6677FF
            );

            /*
             * Title
             */
            graphics.drawString(
                    font,
                    title,
                    panelX + padding,
                    panelY + 8,
                    0xFFFFFFFF
            );

            /*
             * Description
             */
            graphics.drawString(
                    font,
                    description,
                    panelX + padding,
                    panelY + 25,
                    0xFFAAAAAA
            );
        }
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (settingsPanel != null && settingsPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public int getScreenWidth() {
        return width;
    }

    public void setFeaturePanel(
            FeaturePanel panel
    ) {
        this.featurePanel = panel;
    }

    public void rebuild() {
        rebuildWidgets();
    }

    public Category getSelectedCategory() {
        return selectedCategory;
    }

    public void setSelectedCategory(
            Category category
    ) {
        this.selectedCategory = category;
    }

    public SettingsPanel getSettingsPanel() {
        return settingsPanel;
    }

    public void refreshWidgets() {
        rebuildWidgets();
    }

    public void removeWidgetPublic(
            GuiEventListener widget
    ) {
        removeWidget(widget);
    }

    public void selectCategory(
            Category category
    ) {

        selectedCategory = category;

        /*
         * Close settings immediately when
         * changing category.
         */
        if (settingsPanel != null
                && settingsPanel.isOpen()) {

            settingsPanel.closeImmediately();
        }

        selectedFeature = null;

        rebuildWidgets();
    }

    public Feature getHoveredFeature() {
        return hoveredFeature;
    }
}
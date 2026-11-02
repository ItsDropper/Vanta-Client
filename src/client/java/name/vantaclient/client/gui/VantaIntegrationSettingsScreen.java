package name.vantaclient.client.gui;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.setting.SettingDefinition;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/** Retained-mode settings surface for settings contributed through the Vanta API. */
public final class VantaIntegrationSettingsScreen extends Screen {
    private final Screen parent;
    private final ModDescriptor descriptor;
    private VantaUiRuntime runtime;

    public VantaIntegrationSettingsScreen(Screen parent, ModDescriptor descriptor) {
        super(Component.literal(descriptor.displayName()));
        this.parent = parent;
        this.descriptor = descriptor;
    }

    @Override
    protected void init() {
        rebuildUi();
    }

    private void rebuildUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRoot());
        runtime.setScale((float) name.vantaclient.client.config.VantaClientPreferences.get().uiScale);
        runtime.layout(font, width, height);
    }

    private VantaUiNode buildRoot() {
        boolean compact = width < 700;
        VantaUiNode root = VantaUiNode.column(10, 12).background(VantaUiTheme.CANVAS);
        VantaUiNode header = VantaUiNode.row(10, 12).height(46)
                .background(VantaUiTheme.PANEL).align(VantaUiNode.Alignment.CENTER);
        header.add(VantaUiNode.icon(VantaUiNode.Icon.OVERVIEW));
        VantaUiNode heading = VantaUiNode.column(2, 0).flex(1);
        heading.add(VantaUiNode.eyebrow("VANTA INTEGRATION"));
        heading.add(VantaUiNode.title(descriptor.displayName()));
        header.add(heading);
        header.add(VantaUiNode.button("BACK", this::onClose).height(30));
        root.add(header);

        VantaUiNode content = VantaUiNode.column(12, compact ? 12 : 18)
                .flex(1).background(VantaUiTheme.PANEL).scrollable();
        content.add(VantaUiNode.eyebrow(descriptor.id()
                + (descriptor.version().isBlank() ? "" : " / " + descriptor.version())));
        if (!descriptor.description().isBlank()) {
            content.add(VantaUiNode.paragraph(descriptor.description(),
                    VantaUiTheme.TEXT_SECONDARY, 3));
        }
        if (descriptor.settings().isEmpty()) {
            content.add(VantaUiNode.text("This integration does not expose configurable settings.",
                    VantaUiTheme.TEXT_MUTED));
        } else {
            VantaUiNode settings = VantaUiNode.column(9, 12)
                    .background(VantaUiTheme.PANEL_RAISED);
            for (SettingDefinition<?> definition : descriptor.settings()) {
                addSetting(settings, definition);
            }
            content.add(settings);
        }
        root.add(content);
        return root;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addSetting(VantaUiNode panel, SettingDefinition setting) {
        String description = setting.description();
        switch (setting.type()) {
            case BOOLEAN -> {
                panel.add(VantaUiNode.toggle(setting.name(),
                        () -> Boolean.TRUE.equals(readValue(setting)),
                        value -> writeValue(setting, value)));
            }
            case INTEGER, DECIMAL -> {
                double minimum = setting.minimum() == null ? 0.0 : setting.minimum();
                double maximum = setting.maximum() == null ? minimum : setting.maximum();
                double step = setting.type() == SettingDefinition.Type.INTEGER
                        ? 1.0 : Math.max(0.01, (maximum - minimum) / 100.0);
                panel.add(VantaUiNode.slider(setting.name(), minimum, maximum, step,
                        () -> {
                            Object value = readValue(setting);
                            return value instanceof Number number ? number.doubleValue() : minimum;
                        }, value -> {
                            if (setting.type() == SettingDefinition.Type.INTEGER) {
                                writeValue(setting, Integer.valueOf((int) Math.round(value)));
                            } else {
                                writeValue(setting, Double.valueOf(value));
                            }
                        }));
            }
            case ENUM -> {
                List<String> options = setting.options();
                if (options.isEmpty()) {
                    panel.add(VantaUiNode.text(setting.name() + " — no available options",
                            VantaUiTheme.TEXT_MUTED));
                } else {
                    panel.add(VantaUiNode.dropdown(setting.name(), options,
                            () -> {
                                Object value = readValue(setting);
                                return value instanceof Enum<?> enumeration
                                        ? enumeration.name() : options.get(0);
                            }, value -> {
                                Object[] constants = setting.valueClass().getEnumConstants();
                                if (constants == null) return;
                                Arrays.stream(constants)
                                        .filter(option -> ((Enum<?>) option).name().equals(value))
                                        .findFirst().ifPresent(option -> writeValue(setting, option));
                            }));
                }
            }
            case STRING -> {
                int maximumLength = setting.maximum() == null ? 128
                        : Math.max(1, (int) Math.floor(setting.maximum()));
                panel.add(VantaUiNode.textField(setting.name(), 
                        () -> {
                            Object value = readValue(setting);
                            return value instanceof String string ? string : "";
                        }, value -> writeValue(setting, value), maximumLength));
            }
        }
        if (description != null && !description.isBlank()) {
            panel.add(VantaUiNode.paragraph(description, VantaUiTheme.TEXT_MUTED, 2));
        }
        panel.add(VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE));
    }

    private Object readValue(SettingDefinition<?> setting) {
        try {
            return setting.value();
        } catch (RuntimeException exception) {
            return setting.defaultValue();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void writeValue(SettingDefinition setting, Object value) {
        try {
            setting.set(value);
        } catch (RuntimeException exception) {
            System.err.println("[Vanta Client] Could not update integration setting "
                    + setting.id() + ": " + exception.getMessage());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (runtime != null) runtime.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE6090C10);
        if (runtime != null) {
            runtime.setScale((float) name.vantaclient.client.config.VantaClientPreferences.get().uiScale);
            runtime.render(graphics, font, width, height, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (runtime != null && runtime.mouseClicked(event.x(), event.y(), event.button(), font)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = runtime != null && runtime.mouseReleased(event.button());
        return handled || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (runtime != null && runtime.mouseDragged(event.x(), event.y(), event.button())) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (runtime != null && runtime.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (runtime != null && runtime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (runtime != null && runtime.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        if (runtime != null) runtime.dispose();
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

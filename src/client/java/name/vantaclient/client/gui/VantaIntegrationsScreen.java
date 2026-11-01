package name.vantaclient.client.gui;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.api.setting.SettingDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** In-game browser and editor for mods registered through the public Vanta Client API. */
public final class VantaIntegrationsScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 145;
    private static final int ROW_HEIGHT = 34;

    private final Screen parent;
    private List<ModDescriptor> integrations = List.of();
    private ModDescriptor selected;
    private int firstSetting;

    public VantaIntegrationsScreen(Screen parent) {
        super(Component.literal("Vanta Client Integrations"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        integrations = VantaClientApi.getRegisteredMods();
        if (selected != null) {
            selected = integrations.stream()
                    .filter(mod -> mod.id().equals(selected.id()))
                    .findFirst().orElse(null);
        }
        if (selected == null && !integrations.isEmpty()) {
            selected = integrations.getFirst();
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(12, 12, 70, 20).build());

        int sidebarY = 48;
        for (ModDescriptor mod : integrations) {
            ModDescriptor entry = mod;
            addRenderableWidget(Button.builder(
                    Component.literal(mod.displayName()),
                    button -> {
                        selected = entry;
                        firstSetting = 0;
                        rebuildWidgets();
                    }).bounds(12, sidebarY, SIDEBAR_WIDTH, 22).build());
            sidebarY += 27;
        }

        addSettingWidgets();
    }

    private void addSettingWidgets() {
        if (selected == null) return;

        List<SettingDefinition<?>> settings = selected.settings();
        int visibleRows = Math.max(1, (height - 112) / ROW_HEIGHT);
        int maxFirst = Math.max(0, settings.size() - visibleRows);
        firstSetting = Math.max(0, Math.min(firstSetting, maxFirst));

        int end = Math.min(settings.size(), firstSetting + visibleRows);
        int y = 88;
        for (int index = firstSetting; index < end; index++) {
            SettingDefinition<?> setting = settings.get(index);
            addSettingControl(setting, y);
            y += ROW_HEIGHT;
        }

        if (firstSetting > 0) {
            addRenderableWidget(Button.builder(Component.literal("↑"), button -> {
                firstSetting = Math.max(0, firstSetting - 1);
                rebuildWidgets();
            }).bounds(width - 44, 55, 30, 20).build());
        }
        if (end < settings.size()) {
            addRenderableWidget(Button.builder(Component.literal("↓"), button -> {
                firstSetting = Math.min(maxFirst, firstSetting + 1);
                rebuildWidgets();
            }).bounds(width - 44, height - 34, 30, 20).build());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addSettingControl(SettingDefinition setting, int y) {
        int controlX = Math.max(250, width - 190);
        int controlWidth = Math.max(110, Math.min(180, width - controlX - 24));

        Object value;
        try {
            value = setting.value();
        } catch (RuntimeException exception) {
            addRenderableWidget(Button.builder(Component.literal("Unavailable"), button -> {})
                    .bounds(controlX, y, controlWidth, 20).build());
            return;
        }

        switch (setting.type()) {
            case BOOLEAN -> addRenderableWidget(Button.builder(
                    Component.literal(Boolean.TRUE.equals(value) ? "Enabled" : "Disabled"),
                    button -> {
                        try {
                            setting.set(!Boolean.TRUE.equals(setting.value()));
                            button.setMessage(Component.literal(Boolean.TRUE.equals(setting.value())
                                    ? "Enabled" : "Disabled"));
                        } catch (RuntimeException ignored) {
                            button.setMessage(Component.literal("Error"));
                        }
                    }).bounds(controlX, y, controlWidth, 20).build());

            case INTEGER, DECIMAL -> {
                double minimum = setting.minimum() == null ? 0.0 : setting.minimum();
                double maximum = setting.maximum() == null ? minimum : setting.maximum();
                double current = value instanceof Number number ? number.doubleValue() : minimum;
                double step = setting.type() == SettingDefinition.Type.INTEGER
                        ? 1.0 : Math.max(0.01, (maximum - minimum) / 100.0);

                Button label = Button.builder(Component.literal(formatNumber(current)), button -> {})
                        .bounds(controlX + 24, y, Math.max(55, controlWidth - 48), 20).build();

                Button decrement = Button.builder(Component.literal("−"), button -> {
                    double next = clamp(currentValue(setting), minimum, maximum, -step);
                    try {
                        setNumericValue(setting, next);
                    } catch (RuntimeException ignored) {
                        // Keep the last valid value visible.
                    }
                    refreshNumericLabel(setting, label);
                }).bounds(controlX, y, 22, 20).build();

                Button increment = Button.builder(Component.literal("+"), button -> {
                    double next = clamp(currentValue(setting), minimum, maximum, step);
                    try {
                        setNumericValue(setting, next);
                    } catch (RuntimeException ignored) {
                        // Keep the last valid value visible.
                    }
                    refreshNumericLabel(setting, label);
                }).bounds(controlX + controlWidth - 22, y, 22, 20).build();

                addRenderableWidget(decrement);
                addRenderableWidget(label);
                addRenderableWidget(increment);
            }

            case ENUM -> {
                Object[] constants = setting.valueClass().getEnumConstants();
                addRenderableWidget(Button.builder(Component.literal(String.valueOf(value)), button -> {
                    if (constants == null || constants.length == 0) return;
                    try {
                        Object current = setting.value();
                        int currentIndex = 0;
                        for (int i = 0; i < constants.length; i++) {
                            if (constants[i].equals(current)) {
                                currentIndex = i;
                                break;
                            }
                        }
                        setting.set(constants[(currentIndex + 1) % constants.length]);
                        button.setMessage(Component.literal(String.valueOf(setting.value())));
                    } catch (RuntimeException ignored) {
                        button.setMessage(Component.literal("Error"));
                    }
                }).bounds(controlX, y, controlWidth, 20).build());
            }

            case STRING -> {
                EditBox input = new EditBox(font, controlX, y, controlWidth, 20,
                        Component.literal(setting.name()));
                input.setMaxLength(setting.maximum() == null
                        ? 256 : Math.max(0, Math.min(32767, setting.maximum().intValue())));
                input.setValue(value instanceof String string ? string : "");
                input.setResponder(text -> {
                    try {
                        setting.set(text);
                    } catch (RuntimeException ignored) {
                        // Invalid intermediate input remains local until it becomes valid.
                    }
                });
                addRenderableWidget(input);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setNumericValue(SettingDefinition setting, double value) {
        if (setting.type() == SettingDefinition.Type.INTEGER) {
            setting.set((int) Math.round(value));
        } else {
            setting.set(value);
        }
    }

    private static double currentValue(SettingDefinition<?> setting) {
        Object value = setting.value();
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static double clamp(double current, double minimum, double maximum, double delta) {
        return Math.max(minimum, Math.min(maximum, current + delta));
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value)
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static void refreshNumericLabel(SettingDefinition<?> setting, Button label) {
        try {
            label.setMessage(Component.literal(formatNumber(currentValue(setting))));
        } catch (RuntimeException ignored) {
            label.setMessage(Component.literal("Error"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101014);
        graphics.fill(0, 0, SIDEBAR_WIDTH + 24, height, 0xFF18181D);
        graphics.fill(SIDEBAR_WIDTH + 23, 0, SIDEBAR_WIDTH + 24, height, 0xFF34343D);

        graphics.drawString(font, "VANTA CLIENT", 16, 34, 0xFF8C9BFF);
        if (selected == null) {
            graphics.drawString(font, "No integrations registered", SIDEBAR_WIDTH + 44, 62, 0xFFFFFFFF);
        } else {
            int contentX = SIDEBAR_WIDTH + 44;
            graphics.drawString(font, selected.displayName(), contentX, 34, 0xFFFFFFFF);
            if (!selected.description().isBlank()) {
                graphics.drawString(font, selected.description(), contentX, 52, 0xFFAAAAAA);
            }
            graphics.drawString(font, "Settings", contentX, 72, 0xFF8C9BFF);

            List<SettingDefinition<?>> settings = selected.settings();
            int visibleRows = Math.max(1, (height - 112) / ROW_HEIGHT);
            int end = Math.min(settings.size(), firstSetting + visibleRows);
            int y = 94;
            for (int i = firstSetting; i < end; i++) {
                SettingDefinition<?> setting = settings.get(i);
                graphics.drawString(font, setting.name(), contentX, y, 0xFFEAEAF0);
                if (!setting.description().isBlank()) {
                    graphics.drawString(font, setting.description(), contentX, y + 12, 0xFF85858F);
                }
                y += ROW_HEIGHT;
            }
            graphics.drawString(font, "v" + (selected.version().isBlank() ? "—" : selected.version())
                    + "  •  " + selected.id(), contentX, height - 18, 0xFF777780);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selected != null && mouseX >= SIDEBAR_WIDTH + 24) {
            int visibleRows = Math.max(1, (height - 112) / ROW_HEIGHT);
            int maxFirst = Math.max(0, selected.settings().size() - visibleRows);
            int next = Math.max(0, Math.min(maxFirst,
                    firstSetting - (int) Math.signum(scrollY)));
            if (next != firstSetting) {
                firstSetting = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

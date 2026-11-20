package name.vantaclient.client.integration.builtin;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientIntegration;
import name.vantaclient.api.setting.SettingDefinition;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Native Vanta settings for uku's Armor HUD (1.21.11).
 *
 * The mod and its ukulib config dependency remain optional. This adapter binds
 * to the public ArmorHudMod.getManager()/ConfigManager API without linking
 * against either mod at compile time.
 */
public final class UkusArmorHudIntegration implements VantaClientIntegration {
    private static final String MOD_ID = "ukus-armor-hud";
    private static final String IMPLEMENTATION = "ru.berdinskiybear.armorhud.ArmorHudMod";

    private final Object manager;
    private final Object config;

    public UkusArmorHudIntegration() {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> modClass = Class.forName(IMPLEMENTATION, false, loader);
            manager = modClass.getMethod("getManager").invoke(null);
            config = manager.getClass().getMethod("getConfig").invoke(manager);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not bind uku's Armor HUD configuration", exception);
        }
    }

    @Override
    public ModDescriptor createDescriptor() {
        var metadata = FabricLoader.getInstance().getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException("uku's Armor HUD is not loaded"))
                .getMetadata();

        ModDescriptor.Builder descriptor = ModDescriptor.builder(MOD_ID, metadata.getName())
                .description("Configure uku's Armor HUD layout, appearance, and durability warnings")
                .version(metadata.getVersion().getFriendlyString());

        addBoolean(descriptor, "enabled", "Enabled", "Show the armor HUD");
        addEnum(descriptor, "anchor", "Anchor", "Screen position used by the armor HUD");
        addEnum(descriptor, "side", "Side", "Side of the screen used by the HUD");
        addInteger(descriptor, "offsetX", "Horizontal offset", "Horizontal HUD offset", -1000, 1000);
        addInteger(descriptor, "offsetY", "Vertical offset", "Vertical HUD offset", -1000, 1000);
        addEnum(descriptor, "style", "Slot style", "Visual style used for armor slots");
        addEnum(descriptor, "orientation", "Orientation", "Arrange armor slots horizontally or vertically");
        addEnum(descriptor, "widgetShown", "Slot visibility", "Choose when armor slots are displayed");
        addEnum(descriptor, "offhandSlotBehavior", "Offhand spacing", "How the HUD accounts for offhand and attack indicator space");
        addEnum(descriptor, "durabilityDisplay", "Durability display", "How remaining durability is displayed");
        addBoolean(descriptor, "pushBossbars", "Avoid boss bars", "Move boss bars when the HUD is centered at the top");
        addBoolean(descriptor, "pushStatusEffectIcons", "Avoid effect icons", "Move status effect icons when the HUD occupies their area");
        addBoolean(descriptor, "pushSubtitles", "Avoid subtitles", "Move subtitles when the HUD occupies their area");
        addBoolean(descriptor, "reversed", "Reverse slot order", "Reverse the order of armor slots");
        addBoolean(descriptor, "iconsShown", "Show empty-slot icons", "Show special icons in empty armor slots");
        addBoolean(descriptor, "warningShown", "Show durability warnings", "Show a warning for armor with low durability");
        addBoolean(descriptor, "playBreakSound", "Play break warning sound", "Play a sound when displayed equipment reaches the warning threshold");
        addInteger(descriptor, "minDurabilityValue", "Minimum durability", "Warn when remaining durability is at or below this value", 0, 100000);
        addDecimal(descriptor, "minDurabilityPercentage", "Minimum durability percentage", "Warn when remaining durability is at or below this fraction", 0.0, 1.0);
        addInteger(descriptor, "warningBobIntensity", "Warning animation intensity", "Intensity of the durability warning animation", 0, 20);

        return descriptor.build();
    }

    private void addBoolean(ModDescriptor.Builder descriptor, String property, String label, String description) {
        boolean defaultValue = (Boolean) read(property);
        descriptor.setting(SettingDefinition.bool(property, label, description, defaultValue,
                () -> (Boolean) read(property),
                value -> update(property, value)));
    }

    private void addInteger(ModDescriptor.Builder descriptor, String property, String label,
            String description, int minimum, int maximum) {
        int defaultValue = ((Number) read(property)).intValue();
        descriptor.setting(SettingDefinition.integer(property, label, description, defaultValue,
                minimum, maximum,
                () -> ((Number) read(property)).intValue(),
                value -> update(property, value)));
    }

    private void addDecimal(ModDescriptor.Builder descriptor, String property, String label,
            String description, double minimum, double maximum) {
        double defaultValue = ((Number) read(property)).doubleValue();
        descriptor.setting(SettingDefinition.decimal(property, label, description, defaultValue,
                minimum, maximum,
                () -> ((Number) read(property)).doubleValue(),
                value -> update(property, value)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addEnum(ModDescriptor.Builder descriptor, String property, String label, String description) {
        Object current = read(property);
        if (!(current instanceof Enum<?> enumValue)) {
            throw new IllegalStateException("Armor HUD setting is not an enum: " + property);
        }
        Class<? extends Enum> enumClass = (Class<? extends Enum>) enumValue.getDeclaringClass();
        descriptor.setting(SettingDefinition.enumeration(property, label, description,
                (Class) enumClass, (Enum) enumValue,
                () -> (Enum) read(property),
                value -> update(property, value)));
    }

    private Object read(String property) {
        try {
            String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            Method getter;
            try {
                getter = config.getClass().getMethod("get" + suffix);
            } catch (NoSuchMethodException ignored) {
                getter = config.getClass().getMethod("is" + suffix);
            }
            return getter.invoke(config);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read Armor HUD setting " + property, exception);
        }
    }

    private void update(String property, Object value) {
        try {
            String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            Method setter = java.util.Arrays.stream(config.getClass().getMethods())
                    .filter(method -> method.getName().equals("set" + suffix) && method.getParameterCount() == 1)
                    .filter(method -> boxed(method.getParameterTypes()[0]).isInstance(value))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("set" + suffix));
            setter.invoke(config, value);
            manager.getClass().getMethod("saveConfig").invoke(manager);
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getCause() : exception;
            throw new IllegalStateException("Could not update or persist Armor HUD setting " + property, cause);
        }
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == double.class) return Double.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        return type;
    }
}

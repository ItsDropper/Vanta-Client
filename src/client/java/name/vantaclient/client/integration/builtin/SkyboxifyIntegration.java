package name.vantaclient.client.integration.builtin;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientIntegration;
import name.vantaclient.api.setting.SettingDefinition;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Native Vanta settings for Skyboxify.
 *
 * Skyboxify is optional, so this adapter deliberately links to its public config
 * API through reflection instead of adding it as a compile-time dependency.
 */
public final class SkyboxifyIntegration implements VantaClientIntegration {
    private static final String MOD_ID = "skyboxify";
    private static final String IMPLEMENTATION = "btw.lowercase.skyboxify.api.SkyboxifyImpl";

    private final Object config;
    private final Object configHandler;
    private final ClassLoader modClassLoader;

    public SkyboxifyIntegration() {
        try {
            var mod = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .orElseThrow(() -> new IllegalStateException("Skyboxify is not loaded"));
            modClassLoader = mod.getClass().getClassLoader();
            Class<?> implementationClass = Class.forName(IMPLEMENTATION, true, modClassLoader);
            Object instance = implementationClass.getMethod("getInstance").invoke(null);
            config = implementationClass.getMethod("getConfig").invoke(instance);
            configHandler = implementationClass.getMethod("getConfigHandler").invoke(instance);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not bind Skyboxify configuration", exception);
        }
    }

    @Override
    public ModDescriptor createDescriptor() {
        var metadata = FabricLoader.getInstance().getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Skyboxify is not loaded"))
                .getMetadata();

        ModDescriptor.Builder descriptor = ModDescriptor.builder(
                MOD_ID, metadata.getName())
                .description("Configure Skyboxify rendering and sky behavior")
                .version(metadata.getVersion().getFriendlyString());

        addBoolean(descriptor, "enabled", "Enabled",
                "Enable or disable Skyboxify sky rendering");
        addBoolean(descriptor, "renderSky", "Render sky",
                "Render the custom skybox");
        addBoolean(descriptor, "renderSunMoon", "Render sun and moon",
                "Keep the vanilla sun and moon visible");
        addBoolean(descriptor, "renderStars", "Render stars",
                "Render vanilla stars while a skybox is active");
        addBoolean(descriptor, "showOverworldForUnknownDimension",
                "Overworld sky in unknown dimensions",
                "Use the overworld sky when a dimension has no matching sky definition");
        addEnum(descriptor, "filteringMode", "Texture filtering",
                "Texture sampling mode used for skybox textures");
        addBoolean(descriptor, "debug", "Debug logging",
                "Enable Skyboxify diagnostic logging");
        addBoolean(descriptor, "preloadTextures", "Preload textures",
                "Preload skybox textures");

        return descriptor.build();
    }

    private void addBoolean(ModDescriptor.Builder descriptor, String fieldName,
            String label, String description) {
        Field field = field(fieldName, boolean.class);
        boolean defaultValue = readBoolean(field);
        descriptor.setting(SettingDefinition.bool(
                fieldName, label, description, defaultValue,
                () -> readBoolean(field),
                value -> {
                    writeBoolean(field, value);
                    save();
                }));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addEnum(ModDescriptor.Builder descriptor, String fieldName,
            String label, String description) {
        Field field = field(fieldName, null);
        Class<? extends Enum> enumClass = (Class<? extends Enum>) field.getType();
        Object defaultObject = read(field);
        if (!(defaultObject instanceof Enum<?> defaultValue)) {
            throw new IllegalStateException("Skyboxify field " + fieldName
                    + " is not initialized to an enum value");
        }
        descriptor.setting(SettingDefinition.enumeration(
                fieldName, label, description, (Class) enumClass, (Enum) defaultValue,
                () -> (Enum) read(field),
                value -> {
                    write(field, value);
                    save();
                }));
    }

    private Field field(String name, Class<?> expectedType) {
        try {
            Field field = config.getClass().getField(name);
            if (expectedType != null && field.getType() != expectedType) {
                throw new IllegalStateException("Unexpected type for Skyboxify setting " + name);
            }
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Skyboxify setting is unavailable: " + name, exception);
        }
    }

    private boolean readBoolean(Field field) {
        Object value = read(field);
        if (!(value instanceof Boolean result)) {
            throw new IllegalStateException("Skyboxify setting is not boolean: " + field.getName());
        }
        return result;
    }

    private Object read(Field field) {
        try {
            return field.get(config);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not read Skyboxify setting " + field.getName(), exception);
        }
    }

    private void writeBoolean(Field field, boolean value) {
        try {
            field.setBoolean(config, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not update Skyboxify setting " + field.getName(), exception);
        }
    }

    private void write(Field field, Object value) {
        try {
            field.set(config, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not update Skyboxify setting " + field.getName(), exception);
        }
    }

    private void save() {
        try {
            Method save = configHandler.getClass().getMethod("save");
            save.invoke(configHandler);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not persist Skyboxify settings", exception);
        }
    }
}

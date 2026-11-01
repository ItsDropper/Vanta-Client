package name.vantaclient.api.setting;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Public, Minecraft-independent description and binding for one mod setting. */
public final class SettingDefinition<T> {
    public enum Type { BOOLEAN, INTEGER, DECIMAL, STRING, ENUM }

    private final String id, name, description;
    private final Type type;
    private final Class<T> valueClass;
    private final T defaultValue;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final Predicate<T> validator;
    private final Double minimum, maximum;
    private final List<String> options;

    private SettingDefinition(String id, String name, String description, Type type,
            Class<T> valueClass, T defaultValue, Supplier<T> getter, Consumer<T> setter,
            Predicate<T> validator, Double minimum, Double maximum, List<String> options) {
        this.id = requireId(id);
        this.name = requireText(name, "name");
        this.description = description == null ? "" : description;
        this.type = Objects.requireNonNull(type, "type");
        this.valueClass = Objects.requireNonNull(valueClass, "valueClass");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.getter = Objects.requireNonNull(getter, "getter");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.minimum = minimum;
        this.maximum = maximum;
        this.options = List.copyOf(options);
        if (!valueClass.isInstance(defaultValue) || !validator.test(defaultValue))
            throw new IllegalArgumentException("Invalid default value for setting: " + id);
    }

    public static SettingDefinition<Boolean> bool(String id, String name, String description,
            boolean defaultValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new SettingDefinition<>(id, name, description, Type.BOOLEAN, Boolean.class,
                defaultValue, getter, setter, Objects::nonNull, null, null, List.of());
    }

    public static SettingDefinition<Integer> integer(String id, String name, String description,
            int defaultValue, int minimum, int maximum,
            Supplier<Integer> getter, Consumer<Integer> setter) {
        if (minimum > maximum) throw new IllegalArgumentException("minimum exceeds maximum");
        return new SettingDefinition<>(id, name, description, Type.INTEGER, Integer.class,
                defaultValue, getter, setter,
                v -> v != null && v >= minimum && v <= maximum,
                (double) minimum, (double) maximum, List.of());
    }

    public static SettingDefinition<Double> decimal(String id, String name, String description,
            double defaultValue, double minimum, double maximum,
            Supplier<Double> getter, Consumer<Double> setter) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum)
            throw new IllegalArgumentException("Invalid numeric range");
        return new SettingDefinition<>(id, name, description, Type.DECIMAL, Double.class,
                defaultValue, getter, setter,
                v -> v != null && Double.isFinite(v) && v >= minimum && v <= maximum,
                minimum, maximum, List.of());
    }

    public static SettingDefinition<String> string(String id, String name, String description,
            String defaultValue, int maximumLength,
            Supplier<String> getter, Consumer<String> setter) {
        if (maximumLength < 0) throw new IllegalArgumentException("Negative maximumLength");
        return new SettingDefinition<>(id, name, description, Type.STRING, String.class,
                defaultValue, getter, setter,
                v -> v != null && v.length() <= maximumLength,
                null, (double) maximumLength, List.of());
    }

    public static <E extends Enum<E>> SettingDefinition<E> enumeration(String id, String name,
            String description, Class<E> enumClass, E defaultValue,
            Supplier<E> getter, Consumer<E> setter) {
        Objects.requireNonNull(enumClass, "enumClass");
        List<String> options = java.util.Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name).toList();
        return new SettingDefinition<>(id, name, description, Type.ENUM, enumClass,
                defaultValue, getter, setter,
                v -> v != null && enumClass.isInstance(v), null, null, options);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public Type type() { return type; }
    public Class<T> valueClass() { return valueClass; }
    public T defaultValue() { return defaultValue; }
    public Double minimum() { return minimum; }
    public Double maximum() { return maximum; }
    public List<String> options() { return options; }

    public T value() {
        T current = getter.get();
        return current != null && valueClass.isInstance(current) && validator.test(current)
                ? current : defaultValue;
    }

    public void set(T value) {
        if (value == null || !valueClass.isInstance(value) || !validator.test(value))
            throw new IllegalArgumentException("Invalid value for setting: " + id);
        setter.accept(value);
    }

    private static String requireId(String value) {
        String id = requireText(value, "id");
        if (!id.matches("[a-z0-9][a-z0-9_.-]{0,63}"))
            throw new IllegalArgumentException("Invalid setting id: " + id);
        return id;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

package name.vantaclient.api;

import name.vantaclient.api.setting.SettingDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable metadata and setting definitions contributed by an integrating mod. */
public final class ModDescriptor {
    private final String id, displayName, description, version;
    private final List<SettingDefinition<?>> settings;

    private ModDescriptor(Builder builder) {
        id = requireId(builder.id);
        displayName = requireText(builder.displayName, "displayName");
        description = builder.description == null ? "" : builder.description;
        version = builder.version == null ? "" : builder.version;
        settings = List.copyOf(builder.settings);
        Set<String> ids = new HashSet<>();
        for (SettingDefinition<?> setting : settings) {
            Objects.requireNonNull(setting, "setting");
            if (!ids.add(setting.id()))
                throw new IllegalArgumentException("Duplicate setting id '" + setting.id()
                        + "' in mod " + id);
        }
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String version() { return version; }
    public List<SettingDefinition<?>> settings() { return settings; }

    public static final class Builder {
        private final String id, displayName;
        private String description = "", version = "";
        private final List<SettingDefinition<?>> settings = new ArrayList<>();
        private Builder(String id, String displayName) {
            this.id = id; this.displayName = displayName;
        }
        public Builder description(String value) { description = value; return this; }
        public Builder version(String value) { version = value; return this; }
        public Builder setting(SettingDefinition<?> value) {
            settings.add(Objects.requireNonNull(value, "setting")); return this;
        }
        public ModDescriptor build() { return new ModDescriptor(this); }
    }

    private static String requireId(String value) {
        String id = requireText(value, "id");
        if (!id.matches("[a-z0-9][a-z0-9_.-]{0,63}"))
            throw new IllegalArgumentException("Invalid mod id: " + id);
        return id;
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

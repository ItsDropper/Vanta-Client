package name.vantaclient.client.feature;

public abstract class Feature {

    private final String name;
    private final String description;
    private boolean enabled;
    private final Category category;

    public Feature(
            String name,
            String description,
            Category category
    ) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public Category getCategory() {
        return category;
    }

    public void saveConfig() {}

    public void loadConfig() {}

    public void setEnabled(boolean enabled) {

        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;

        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void onEnable() {}

    public void onDisable() {}
}
package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Disables only the forward double-tap sprint window. Sprint key behavior and
 * Vanta's independent sprint toggle remain unchanged.
 */
public final class DisableDoubleTapSprintFeature extends Feature {
    public static final DisableDoubleTapSprintFeature INSTANCE = new DisableDoubleTapSprintFeature();

    private DisableDoubleTapSprintFeature() {
        super("Disable Double-Tap Sprint",
                "Prevents sprinting from triggering by double-tapping the forward key.",
                Category.MISC);
    }

    public static void tick(Minecraft client) {
        if (client == null || client.options == null) return;
        DropperUtilsConfig config = DropperUtilsConfig.get();
        Options options = client.options;

        if (!INSTANCE.isEnabled()) {
            restore(options, config);
            return;
        }

        if (!config.sprintWindowCaptured) {
            config.sprintWindowBeforeDisable = Math.max(0,
                    Math.min(20, options.sprintWindow().get()));
            config.sprintWindowCaptured = true;
            DropperUtilsConfig.save();
        }

        if (options.sprintWindow().get() != 0) {
            options.sprintWindow().set(0);
        }
    }

    private static void restore(Options options, DropperUtilsConfig config) {
        if (!config.sprintWindowCaptured) return;
        if (options.sprintWindow().get() == 0) {
            options.sprintWindow().set(Math.max(0,
                    Math.min(20, config.sprintWindowBeforeDisable)));
        }
        config.sprintWindowCaptured = false;
        DropperUtilsConfig.save();
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().disableDoubleTapSprint = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        config.disableDoubleTapSprint = false;
        DropperUtilsConfig.save();
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            restore(client.options, config);
        }
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().disableDoubleTapSprint = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().disableDoubleTapSprint);
    }
}

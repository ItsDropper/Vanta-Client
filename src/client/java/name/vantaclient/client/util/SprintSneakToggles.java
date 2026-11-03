package name.vantaclient.client.util;

import name.vantaclient.client.config.DropperUtilsConfig;
import net.minecraft.client.Minecraft;

public final class SprintSneakToggles {
    private static boolean sprintToggled;
    private static boolean sneakToggled;

    private SprintSneakToggles() {}

    public static void load() {
        sprintToggled = DropperUtilsConfig.get().sprintToggleEnabled;
        sneakToggled = DropperUtilsConfig.get().sneakToggleEnabled;
    }

    public static void toggleSprint() {
        sprintToggled = !sprintToggled;
        DropperUtilsConfig.get().sprintToggleEnabled = sprintToggled;
        DropperUtilsConfig.save();
    }

    public static void toggleSneak() {
        sneakToggled = !sneakToggled;
        DropperUtilsConfig.get().sneakToggleEnabled = sneakToggled;
        DropperUtilsConfig.save();
    }

    public static boolean isSprintToggled() {
        return sprintToggled;
    }

    public static boolean isSneakToggled() {
        return sneakToggled;
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.options == null) return;

        if (client.player.getAbilities().flying && sneakToggled) {
            sneakToggled = false;
            DropperUtilsConfig.get().sneakToggleEnabled = false;
            DropperUtilsConfig.save();
        }

        boolean sprintRequested = sprintToggled || client.options.keySprint.isDown();
        boolean sneakRequested = sneakToggled || client.options.keyShift.isDown();

        client.player.setSprinting(sprintRequested
                && client.player.getAbilities().mayBuild()
                && client.player.isAlive());
        client.player.setShiftKeyDown(sneakRequested);
    }
}

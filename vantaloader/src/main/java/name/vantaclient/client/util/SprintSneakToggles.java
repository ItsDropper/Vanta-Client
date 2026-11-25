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

        // Only override vanilla input while a toggle is active. Previously this
        // wrote both states every tick, even with both toggles off, which could
        // fight Minecraft's own keyboard-input and sprint-state handling.
        if (sneakToggled) {
            client.player.setShiftKeyDown(true);
        }

        if (sprintToggled) {
            boolean physicalSneak = client.options.keyShift.isDown();
            boolean canSprint = client.player.isAlive()
                    && client.player.zza > 0.0f
                    && !sneakToggled
                    && !physicalSneak
                    && !client.player.isUsingItem()
                    && !client.player.isPassenger();
            client.player.setSprinting(canSprint);
        }
    }
}

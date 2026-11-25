package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * A client-only flight-speed adjustment, deliberately restricted to a local
 * single-player session. It never modifies a multiplayer player's abilities.
 */
public final class FlyBoostFeature extends Feature {
    public static final FlyBoostFeature INSTANCE = new FlyBoostFeature();

    private static final float MIN_MULTIPLIER = 1.0f;
    private static final float MAX_MULTIPLIER = 3.0f;

    private static float multiplier = 1.5f;
    private static Player boostedPlayer;
    private static float originalFlyingSpeed;

    private FlyBoostFeature() {
        super("Fly Boost",
                "Adjusts creative flight speed only in a local single-player world.",
                Category.MISC);
    }

    public static float getMultiplier() {
        return multiplier;
    }

    public static void setMultiplier(float value) {
        multiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER,
                Float.isFinite(value) ? value : 1.5f));
        DropperUtilsConfig.get().flyBoostMultiplier = multiplier;
        DropperUtilsConfig.save();
    }

    public static void tick(Minecraft client) {
        Player player = client.player;
        boolean canApply = INSTANCE.isEnabled()
                && client.hasSingleplayerServer()
                && player != null
                && player.getAbilities().mayfly
                && player.getAbilities().flying;

        if (!canApply) {
            restoreOriginalSpeed();
            return;
        }

        if (boostedPlayer != null && boostedPlayer != player) {
            restoreOriginalSpeed();
        }

        if (boostedPlayer == null) {
            boostedPlayer = player;
            originalFlyingSpeed = player.getAbilities().getFlyingSpeed();
        }

        player.getAbilities().setFlyingSpeed(
                Math.max(0.01f, Math.min(0.5f, originalFlyingSpeed * multiplier)));
    }

    private static void restoreOriginalSpeed() {
        if (boostedPlayer != null) {
            boostedPlayer.getAbilities().setFlyingSpeed(originalFlyingSpeed);
            boostedPlayer = null;
        }
    }

    @Override
    public void onDisable() {
        restoreOriginalSpeed();
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().flyBoost = isEnabled();
        DropperUtilsConfig.get().flyBoostMultiplier = multiplier;
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        multiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER,
                Float.isFinite(config.flyBoostMultiplier) ? config.flyBoostMultiplier : 1.5f));
        setEnabled(config.flyBoost);
    }
}

package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/**
 * Crystal Optimizer: removes an end crystal on the client the moment you hit it, instead of waiting
 * for the server's reply, so back-to-back crystal placing and breaking looks and feels instant.
 *
 * This is the same client-side prediction the widely allowed "crystal optimizer" mods use. It does not
 * change, add or reorder any packet: the normal attack packet is sent first and the server stays
 * authoritative over whether the crystal really broke. Nothing is automated; it only reacts to a hit
 * you made yourself.
 */
public final class CrystalOptimizerFeature extends Feature {
    public static final CrystalOptimizerFeature INSTANCE = new CrystalOptimizerFeature();

    private CrystalOptimizerFeature() {
        super("Crystal Optimizer",
                "Removes an end crystal on your screen the instant you hit it, without waiting for the server. "
                        + "Visual prediction only; sends nothing extra.",
                Category.COMBAT);
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().crystalOptimizer = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().crystalOptimizer = false;
        DropperUtilsConfig.save();
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().crystalOptimizer = isEnabled();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().crystalOptimizer);
    }

    /**
     * Called after vanilla has sent the attack packet. Predicts the break only when the hit can really
     * break the crystal: it must be a live, vulnerable crystal and the attacker must be able to deal
     * damage (for example, Weakness can reduce a bare-handed hit to zero, which breaks nothing).
     */
    public static void onAttack(Player attacker, Entity target) {
        if (!INSTANCE.isEnabled() || !(target instanceof EndCrystal crystal)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player != attacker || minecraft.gameMode == null) return;
        if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        if (crystal.isRemoved() || crystal.isInvulnerable()) return;
        if (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0.0) return;
        minecraft.level.removeEntity(crystal.getId(), Entity.RemovalReason.KILLED);
    }
}

package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Adjusts the vertical geometry of vanilla fire blocks in the world. */
public final class GroundFireHeightFeature extends Feature {
    public static final GroundFireHeightFeature INSTANCE = new GroundFireHeightFeature();

    private GroundFireHeightFeature() {
        super("Ground Fire Height", "Adjusts the height of fire and soul fire blocks rendered in the world.", Category.VISUAL);
    }

    public static float getHeightScale() {
        float value = DropperUtilsConfig.get().groundFireHeight;
        return Float.isFinite(value) ? Math.max(0.25f, Math.min(2.0f, value)) : 1.0f;
    }

    public static void setHeightScale(float value) {
        float normalized = Float.isFinite(value)
                ? Math.max(0.25f, Math.min(2.0f, value))
                : 1.0f;
        DropperUtilsConfig config = DropperUtilsConfig.get();
        if (Float.compare(config.groundFireHeight, normalized) == 0) return;

        config.groundFireHeight = normalized;
        DropperUtilsConfig.save();
        if (INSTANCE.isEnabled()) {
            refreshWorldGeometry();
        }
    }

    private static void refreshWorldGeometry() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;

        BlockPos playerPos = minecraft.player.blockPosition();
        int renderDistance = Math.max(2, minecraft.options.renderDistance().get() + 1);
        int centerX = Math.floorDiv(playerPos.getX(), 16);
        int centerZ = Math.floorDiv(playerPos.getZ(), 16);
        int minY = Math.floorDiv(level.getMinY(), 16);
        int maxY = Math.floorDiv(level.getMaxY() - 1, 16);

        // Mark visible chunk sections for rebuilding so an adjusted baked model appears immediately.
        minecraft.levelRenderer.setSectionRangeDirty(
                centerX - renderDistance, minY, centerZ - renderDistance,
                centerX + renderDistance, maxY, centerZ + renderDistance
        );
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().groundFireHeightEnabled = true;
        DropperUtilsConfig.save();
        refreshWorldGeometry();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().groundFireHeightEnabled = false;
        DropperUtilsConfig.save();
        refreshWorldGeometry();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().groundFireHeightEnabled);
    }
}

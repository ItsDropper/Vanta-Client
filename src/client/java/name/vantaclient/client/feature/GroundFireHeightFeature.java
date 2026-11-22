package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Adjusts the baked world geometry of placed fire and soul-fire blocks. */
public final class GroundFireHeightFeature extends Feature {
    public static final GroundFireHeightFeature INSTANCE = new GroundFireHeightFeature();

    // Chunk tessellation runs on worker threads; publish both values safely.
    private static volatile boolean active;
    private static volatile float heightScale = 1.0f;

    /** Number of fire blocks whose geometry was scaled; used by the in-world game test. */
    public static final java.util.concurrent.atomic.AtomicInteger SCALED_BLOCKS = new java.util.concurrent.atomic.AtomicInteger();

    private GroundFireHeightFeature() {
        super("Ground Fire Height", "Adjusts the height of placed fire and soul fire blocks in the world.", Category.VISUAL);
    }

    public static boolean isActive() {
        return active;
    }

    public static float getHeightScale() {
        return heightScale;
    }

    /**
     * Creates a per-quad pose with its vertical axis scaled around the block's
     * local origin. The caller's pose is never mutated.
     */
    public static PoseStack.Pose scaleQuadPose(PoseStack.Pose original, float scale) {
        PoseStack.Pose scaled = original.copy();
        scaled.scale(1.0f, scale, 1.0f);
        return scaled;
    }

    private static float normalizeHeightScale(float value) {
        return Float.isFinite(value) ? Math.max(0.25f, Math.min(2.0f, value)) : 1.0f;
    }

    public static void setHeightScale(float value) {
        float normalized = normalizeHeightScale(value);
        DropperUtilsConfig config = DropperUtilsConfig.get();
        if (Float.compare(config.groundFireHeight, normalized) == 0) {
            heightScale = normalized;
            return;
        }

        config.groundFireHeight = normalized;
        heightScale = normalized;
        DropperUtilsConfig.save();
        if (active) {
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

        // These arguments are section coordinates, not block coordinates.
        minecraft.levelRenderer.setSectionRangeDirty(
                centerX - renderDistance, minY, centerZ - renderDistance,
                centerX + renderDistance, maxY, centerZ + renderDistance
        );
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        heightScale = normalizeHeightScale(config.groundFireHeight);
        config.groundFireHeightEnabled = true;
        active = true;
        DropperUtilsConfig.save();
        refreshWorldGeometry();
    }

    @Override
    public void onDisable() {
        active = false;
        DropperUtilsConfig.get().groundFireHeightEnabled = false;
        DropperUtilsConfig.save();
        refreshWorldGeometry();
    }

    @Override
    public void loadConfig() {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        heightScale = normalizeHeightScale(config.groundFireHeight);
        setEnabled(config.groundFireHeightEnabled);
    }
}

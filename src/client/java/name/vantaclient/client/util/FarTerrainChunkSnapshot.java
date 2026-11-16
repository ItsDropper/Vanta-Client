package name.vantaclient.client.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Immutable, compact surface data captured from a real client chunk.
 *
 * Chunk and world objects are only read on the client thread during ingestion.
 * FarView workers consume these primitive arrays and never touch live chunks.
 */
final class FarTerrainChunkSnapshot {
    private final int[] heights;
    private final int[] surfaceColors;

    private FarTerrainChunkSnapshot(int[] heights, int[] surfaceColors) {
        this.heights = heights;
        this.surfaceColors = surfaceColors;
    }

    static FarTerrainChunkSnapshot capture(ClientLevel level, LevelChunk chunk) {
        int[] heights = new int[16 * 16];
        int[] colors = new int[16 * 16];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int index = z * 16 + x;
                heights[index] = height;
                if (height <= level.getMinY()) {
                    colors[index] = 0xFF536477;
                    continue;
                }

                position.set(originX + x, height - 1, originZ + z);
                BlockState surface = chunk.getBlockState(position);
                int mapColor = surface.getMapColor(level, position).col;
                colors[index] = 0xFF000000 | (mapColor & 0x00FFFFFF);
            }
        }
        return new FarTerrainChunkSnapshot(heights, colors);
    }

    int height(int localX, int localZ) {
        return heights[(localZ & 15) * 16 + (localX & 15)];
    }

    int surfaceColor(int localX, int localZ) {
        return surfaceColors[(localZ & 15) * 16 + (localX & 15)];
    }
}

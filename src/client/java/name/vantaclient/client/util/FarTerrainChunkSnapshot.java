package name.vantaclient.client.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable, compact block-derived surface data captured from a real client chunk.
 *
 * The chunk and world are only read on the client thread during ingestion. LOD
 * workers and the persistence layer consume primitive arrays, never live chunks.
 */
final class FarTerrainChunkSnapshot {
    static final int COLUMN_COUNT = 16 * 16;

    private final int chunkX;
    private final int chunkZ;
    private final int[] heights;
    private final int[] surfaceColors;
    private final FarTerrainVoxelSection[] voxelSections;

    private FarTerrainChunkSnapshot(int chunkX, int chunkZ, int[] heights, int[] surfaceColors,
                                    FarTerrainVoxelSection[] voxelSections) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.heights = heights;
        this.surfaceColors = surfaceColors;
        this.voxelSections = voxelSections;
    }

    static FarTerrainChunkSnapshot capture(ClientLevel level, LevelChunk chunk) {
        int[] heights = new int[COLUMN_COUNT];
        int[] colors = new int[COLUMN_COUNT];
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
        LevelChunkSection[] sourceSections = chunk.getSections();
        List<FarTerrainVoxelSection> voxelSections = new ArrayList<>(sourceSections.length);
        int minSection = Math.floorDiv(level.getMinY(), FarTerrainVoxelSection.EDGE);
        for (int i = 0; i < sourceSections.length; i++) {
            LevelChunkSection section = sourceSections[i];
            if (section.hasOnlyAir()) continue;
            FarTerrainVoxelSection captured = FarTerrainVoxelSection.capture(
                    section, minSection + i, level, originX, originZ);
            if (captured != null) voxelSections.add(captured);
        }

        return new FarTerrainChunkSnapshot(chunk.getPos().x, chunk.getPos().z, heights, colors,
                voxelSections.toArray(FarTerrainVoxelSection[]::new));
    }

    static FarTerrainChunkSnapshot read(DataInput input, int version) throws IOException {
        int chunkX = input.readInt();
        int chunkZ = input.readInt();
        int[] heights = new int[COLUMN_COUNT];
        int[] colors = new int[COLUMN_COUNT];
        for (int i = 0; i < COLUMN_COUNT; i++) heights[i] = input.readInt();
        for (int i = 0; i < COLUMN_COUNT; i++) colors[i] = input.readInt();
        FarTerrainVoxelSection[] sections = new FarTerrainVoxelSection[0];
        if (version >= 2) {
            int sectionCount = input.readUnsignedShort();
            if (sectionCount > 64) throw new IOException("Invalid FarView voxel section count");
            sections = new FarTerrainVoxelSection[sectionCount];
            for (int i = 0; i < sectionCount; i++) sections[i] = FarTerrainVoxelSection.read(input, version);
        }
        return new FarTerrainChunkSnapshot(chunkX, chunkZ, heights, colors, sections);
    }

    void write(DataOutput output) throws IOException {
        output.writeInt(chunkX);
        output.writeInt(chunkZ);
        for (int height : heights) output.writeInt(height);
        for (int color : surfaceColors) output.writeInt(color);
        output.writeShort(voxelSections.length);
        for (FarTerrainVoxelSection section : voxelSections) section.write(output);
    }

    int chunkX() {
        return chunkX;
    }

    int chunkZ() {
        return chunkZ;
    }

    int height(int localX, int localZ) {
        return heights[(localZ & 15) * 16 + (localX & 15)];
    }

    int surfaceColor(int localX, int localZ) {
        return surfaceColors[(localZ & 15) * 16 + (localX & 15)];
    }

    FarTerrainVoxelSection[] voxelSections() {
        return voxelSections;
    }
}

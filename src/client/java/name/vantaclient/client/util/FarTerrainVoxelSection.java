package name.vantaclient.client.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable palette-compressed 16^3 block-state volume with packed light/material data. */
final class FarTerrainVoxelSection {
    static final int EDGE = 16;
    static final int VOXEL_COUNT = EDGE * EDGE * EDGE;

    private final int sectionY;
    private final int[] palette;
    private final short[] paletteIndices;
    private final byte[] packedLight;
    private final int[] paletteColors;
    private final boolean[] solidPalette;

    private FarTerrainVoxelSection(int sectionY, int[] palette, short[] paletteIndices,
                                   byte[] packedLight, int[] paletteColors, boolean[] solidPalette) {
        this.sectionY = sectionY;
        this.palette = palette;
        this.paletteIndices = paletteIndices;
        this.packedLight = packedLight;
        this.paletteColors = paletteColors;
        this.solidPalette = solidPalette;
    }

    static FarTerrainVoxelSection capture(LevelChunkSection section, int sectionY,
                                          ClientLevel level, int chunkMinX, int chunkMinZ) {
        Map<Integer, Integer> paletteLookup = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Boolean> solids = new ArrayList<>();
        short[] indices = new short[VOXEL_COUNT];
        byte[] light = new byte[VOXEL_COUNT];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int baseY = sectionY * EDGE;
        boolean hasSolidBlock = false;

        for (int y = 0; y < EDGE; y++) {
            for (int z = 0; z < EDGE; z++) {
                for (int x = 0; x < EDGE; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    int stateId = Block.getId(state);
                    int index = index(x, y, z);
                    position.set(chunkMinX + x, baseY + y, chunkMinZ + z);
                    Integer paletteIndex = paletteLookup.get(stateId);
                    if (paletteIndex == null) {
                        paletteIndex = palette.size();
                        paletteLookup.put(stateId, paletteIndex);
                        palette.add(stateId);
                        int mapColor = state.getMapColor(level, position).col;
                        colors.add(0xFF000000 | (mapColor & 0x00FFFFFF));
                        solids.add(!state.isAir() && state.getFluidState().isEmpty());
                    }
                    indices[index] = (short) (int) paletteIndex;
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        hasSolidBlock = true;
                        int sky = level.getBrightness(LightLayer.SKY, position) & 15;
                        int block = level.getBrightness(LightLayer.BLOCK, position) & 15;
                        light[index] = (byte) ((sky << 4) | block);
                    }
                }
            }
        }

        if (!hasSolidBlock) return null;
        int[] paletteArray = new int[palette.size()];
        int[] colorArray = new int[palette.size()];
        boolean[] solidArray = new boolean[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            paletteArray[i] = palette.get(i);
            colorArray[i] = colors.get(i);
            solidArray[i] = solids.get(i);
        }
        return new FarTerrainVoxelSection(sectionY, paletteArray, indices, light, colorArray, solidArray);
    }

    static FarTerrainVoxelSection read(DataInput input, int version) throws IOException {
        int sectionY = input.readInt();
        int paletteSize = input.readUnsignedShort();
        if (paletteSize < 1 || paletteSize > VOXEL_COUNT) {
            throw new IOException("Invalid FarView voxel palette size: " + paletteSize);
        }
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) palette[i] = input.readInt();
        int[] colors = new int[paletteSize];
        boolean[] solids = new boolean[paletteSize];
        if (version >= 3) {
            for (int i = 0; i < paletteSize; i++) colors[i] = input.readInt();
            for (int i = 0; i < paletteSize; i++) solids[i] = input.readBoolean();
        } else {
            for (int i = 0; i < paletteSize; i++) {
                colors[i] = 0xFF858585;
                solids[i] = palette[i] != 0;
            }
        }
        short[] indices = new short[VOXEL_COUNT];
        for (int i = 0; i < VOXEL_COUNT; i++) {
            int paletteIndex = input.readUnsignedShort();
            if (paletteIndex >= paletteSize) throw new IOException("Invalid FarView voxel palette index");
            indices[i] = (short) paletteIndex;
        }
        byte[] light = new byte[VOXEL_COUNT];
        input.readFully(light);
        return new FarTerrainVoxelSection(sectionY, palette, indices, light, colors, solids);
    }

    void write(DataOutput output) throws IOException {
        output.writeInt(sectionY);
        output.writeShort(palette.length);
        for (int stateId : palette) output.writeInt(stateId);
        for (int color : paletteColors) output.writeInt(color);
        for (boolean solid : solidPalette) output.writeBoolean(solid);
        for (short paletteIndex : paletteIndices) output.writeShort(Short.toUnsignedInt(paletteIndex));
        output.write(packedLight);
    }

    int sectionY() { return sectionY; }

    int blockStateId(int x, int y, int z) {
        return palette[Short.toUnsignedInt(paletteIndices[index(x, y, z)])];
    }

    int materialColor(int x, int y, int z) {
        return paletteColors[Short.toUnsignedInt(paletteIndices[index(x, y, z)])];
    }

    boolean isSolid(int x, int y, int z) {
        return solidPalette[Short.toUnsignedInt(paletteIndices[index(x, y, z)])];
    }

    int skyLight(int x, int y, int z) {
        return (packedLight[index(x, y, z)] >>> 4) & 15;
    }

    int blockLight(int x, int y, int z) {
        return packedLight[index(x, y, z)] & 15;
    }

    Octree buildOctree() {
        List<Integer> materials = new ArrayList<>();
        List<Byte> lights = new ArrayList<>();
        List<Integer> children = new ArrayList<>();
        buildNode(0, 0, 0, EDGE, materials, lights, children);
        int[] materialArray = new int[materials.size()];
        byte[] lightArray = new byte[lights.size()];
        int[] childArray = new int[children.size()];
        for (int i = 0; i < materialArray.length; i++) {
            materialArray[i] = materials.get(i);
            lightArray[i] = lights.get(i);
        }
        for (int i = 0; i < childArray.length; i++) childArray[i] = children.get(i);
        return new Octree(materialArray, lightArray, childArray);
    }

    private int buildNode(int x, int y, int z, int size,
                          List<Integer> materials, List<Byte> lights, List<Integer> children) {
        int nodeIndex = materials.size();
        materials.add(0);
        lights.add((byte) 0);
        for (int i = 0; i < 8; i++) children.add(-1);
        if (size == 1) {
            materials.set(nodeIndex, blockStateId(x, y, z));
            lights.set(nodeIndex, packedLight[index(x, y, z)]);
            return nodeIndex;
        }

        int half = size / 2;
        int[] childMaterials = new int[8];
        int skySum = 0;
        int blockSum = 0;
        for (int child = 0; child < 8; child++) {
            int childX = x + ((child & 1) == 0 ? 0 : half);
            int childY = y + ((child & 2) == 0 ? 0 : half);
            int childZ = z + ((child & 4) == 0 ? 0 : half);
            int childIndex = buildNode(childX, childY, childZ, half, materials, lights, children);
            children.set(nodeIndex * 8 + child, childIndex);
            childMaterials[child] = materials.get(childIndex);
            int childLight = Byte.toUnsignedInt(lights.get(childIndex));
            skySum += (childLight >>> 4) & 15;
            blockSum += childLight & 15;
        }
        materials.set(nodeIndex, dominantSolidMaterial(childMaterials));
        lights.set(nodeIndex, (byte) (((skySum / 8) << 4) | (blockSum / 8)));
        return nodeIndex;
    }

    private static int dominantSolidMaterial(int[] values) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = 0;
        int bestCount = 0;
        for (int value : values) {
            if (value == 0) continue;
            int count = counts.merge(value, 1, Integer::sum);
            if (count > bestCount || (count == bestCount && value < best)) {
                best = value;
                bestCount = count;
            }
        }
        if (bestCount > 0) return best;
        int fallback = values[0];
        for (int value : values) fallback = Math.min(fallback, value);
        return fallback;
    }

    private static int index(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    record Octree(int[] materialIds, byte[] packedLight, int[] childIndices) {
        int nodeCount() { return materialIds.length; }
        boolean isLeaf(int nodeIndex) {
            int offset = nodeIndex * 8;
            for (int i = 0; i < 8; i++) if (childIndices[offset + i] >= 0) return false;
            return true;
        }
        int child(int nodeIndex, int octant) { return childIndices[nodeIndex * 8 + octant]; }
        int skyLight(int nodeIndex) { return (packedLight[nodeIndex] >>> 4) & 15; }
        int blockLight(int nodeIndex) { return packedLight[nodeIndex] & 15; }
    }
}

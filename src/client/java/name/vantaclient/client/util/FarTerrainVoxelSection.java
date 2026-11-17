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

    FarTerrainVoxelSection(int sectionY, int[] palette, short[] paletteIndices,
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
        OctreeBuilder builder = new OctreeBuilder();
        int root = builder.build(0, 0, 0, EDGE);
        return builder.finish(root);
    }

    /** Sparse octree builder: omit empty branches and collapse uniform solid regions. */
    private final class OctreeBuilder {
        private int[] materials = new int[256];
        private byte[] lights = new byte[256];
        private byte[] sizes = new byte[256];
        private int[] solidCounts = new int[256];
        private int[] children = new int[256 * 8];
        private int count;

        int build(int x, int y, int z, int size) {
            int firstMaterial = -1;
            int firstLight = -1;
            int solids = 0;
            boolean uniform = true;
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    for (int dx = 0; dx < size; dx++) {
                        int vx = x + dx;
                        int vy = y + dy;
                        int vz = z + dz;
                        if (!isSolid(vx, vy, vz)) {
                            uniform = false;
                            continue;
                        }
                        solids++;
                        int material = blockStateId(vx, vy, vz);
                        int light = Byte.toUnsignedInt(packedLight[index(vx, vy, vz)]);
                        if (firstMaterial < 0) {
                            firstMaterial = material;
                            firstLight = light;
                        } else if (material != firstMaterial || light != firstLight) {
                            uniform = false;
                        }
                    }
                }
            }
            if (solids == 0) return -1;
            if (uniform) return addNode(firstMaterial, firstLight, size, solids, null);

            int half = size / 2;
            int[] childNodes = new int[8];
            java.util.Arrays.fill(childNodes, -1);
            Map<Integer, Integer> materialWeights = new HashMap<>();
            long skyTotal = 0L;
            long blockTotal = 0L;
            int childSolidCount = 0;
            for (int octant = 0; octant < 8; octant++) {
                int childX = x + ((octant & 1) == 0 ? 0 : half);
                int childY = y + ((octant & 2) == 0 ? 0 : half);
                int childZ = z + ((octant & 4) == 0 ? 0 : half);
                int child = build(childX, childY, childZ, half);
                childNodes[octant] = child;
                if (child < 0) continue;
                int weight = solidCounts[child];
                materialWeights.merge(materials[child], weight, Integer::sum);
                int light = Byte.toUnsignedInt(lights[child]);
                skyTotal += (long) ((light >>> 4) & 15) * weight;
                blockTotal += (long) (light & 15) * weight;
                childSolidCount += weight;
            }

            int dominantMaterial = 0;
            int dominantWeight = 0;
            for (Map.Entry<Integer, Integer> entry : materialWeights.entrySet()) {
                if (entry.getValue() > dominantWeight
                        || (entry.getValue() == dominantWeight && entry.getKey() < dominantMaterial)) {
                    dominantMaterial = entry.getKey();
                    dominantWeight = entry.getValue();
                }
            }
            int packedAverageLight = childSolidCount == 0 ? 0
                    : (int) (((skyTotal / childSolidCount) << 4)
                    | (blockTotal / childSolidCount));
            return addNode(dominantMaterial, packedAverageLight, size, solids, childNodes);
        }

        private int addNode(int material, int light, int size, int solids, int[] childNodes) {
            ensureCapacity(count + 1);
            int node = count++;
            materials[node] = material;
            lights[node] = (byte) light;
            sizes[node] = (byte) size;
            solidCounts[node] = solids;
            int childOffset = node * 8;
            java.util.Arrays.fill(children, childOffset, childOffset + 8, -1);
            if (childNodes != null) System.arraycopy(childNodes, 0, children, childOffset, 8);
            return node;
        }

        private void ensureCapacity(int required) {
            if (required <= materials.length) return;
            int capacity = Math.max(required, materials.length * 2);
            materials = java.util.Arrays.copyOf(materials, capacity);
            lights = java.util.Arrays.copyOf(lights, capacity);
            sizes = java.util.Arrays.copyOf(sizes, capacity);
            solidCounts = java.util.Arrays.copyOf(solidCounts, capacity);
            children = java.util.Arrays.copyOf(children, capacity * 8);
        }

        Octree finish(int root) {
            return new Octree(root,
                    java.util.Arrays.copyOf(materials, count),
                    java.util.Arrays.copyOf(lights, count),
                    java.util.Arrays.copyOf(children, count * 8),
                    java.util.Arrays.copyOf(sizes, count),
                    java.util.Arrays.copyOf(solidCounts, count));
        }
    }

    private static int index(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    record Octree(int rootIndex, int[] materialIds, byte[] packedLight,
                  int[] childIndices, byte[] nodeSizes, int[] solidCounts) {
        int nodeCount() { return materialIds.length; }
        boolean isEmpty() { return rootIndex < 0; }
        boolean isLeaf(int nodeIndex) {
            int offset = nodeIndex * 8;
            for (int i = 0; i < 8; i++) if (childIndices[offset + i] >= 0) return false;
            return true;
        }
        int child(int nodeIndex, int octant) { return childIndices[nodeIndex * 8 + octant]; }
        int size(int nodeIndex) { return Byte.toUnsignedInt(nodeSizes[nodeIndex]); }
        int solidCount(int nodeIndex) { return solidCounts[nodeIndex]; }
        int skyLight(int nodeIndex) { return (packedLight[nodeIndex] >>> 4) & 15; }
        int blockLight(int nodeIndex) { return packedLight[nodeIndex] & 15; }
    }
}

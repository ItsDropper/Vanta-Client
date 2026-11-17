package name.vantaclient.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/** Immutable palette-compressed 16^3 block-state volume with packed light/material data. */
final class FarTerrainVoxelSection {
    static final int EDGE = 16;
    static final int VOXEL_COUNT = EDGE * EDGE * EDGE;
    private static final Map<Integer, int[]> BLOCK_FACE_TEXTURE_COLORS = new ConcurrentHashMap<>();
    private static final Map<Identifier, Integer> SPRITE_TEXTURE_COLORS = new ConcurrentHashMap<>();

    private final int sectionY;
    private final int[] palette;
    private final short[] paletteIndices;
    private final byte[] packedLight;
    private final int[] voxelColors;
    private final int[] paletteColors;
    private final int[] paletteFaceColors;
    private final boolean[] solidPalette;
    private final boolean[] translucentPalette;
    /** Persisted derived hierarchy; lazily rebuilt for legacy cache entries. */
    private volatile Octree cachedOctree;

    FarTerrainVoxelSection(int sectionY, int[] palette, short[] paletteIndices,
                                   byte[] packedLight, int[] voxelColors, int[] paletteColors,
                                   boolean[] solidPalette, boolean[] translucentPalette) {
        this(sectionY, palette, paletteIndices, packedLight, voxelColors, paletteColors,
                defaultFaceColors(paletteColors), solidPalette, translucentPalette);
    }

    FarTerrainVoxelSection(int sectionY, int[] palette, short[] paletteIndices,
                           byte[] packedLight, int[] voxelColors, int[] paletteColors,
                           int[] paletteFaceColors, boolean[] solidPalette,
                           boolean[] translucentPalette) {
        this.sectionY = sectionY;
        this.palette = palette;
        this.paletteIndices = paletteIndices;
        this.packedLight = packedLight;
        this.voxelColors = voxelColors;
        this.paletteColors = paletteColors;
        this.paletteFaceColors = paletteFaceColors;
        this.solidPalette = solidPalette;
        this.translucentPalette = translucentPalette;
    }

    private static int[] defaultFaceColors(int[] paletteColors) {
        int[] result = new int[paletteColors.length * Direction.values().length];
        for (int paletteIndex = 0; paletteIndex < paletteColors.length; paletteIndex++) {
            java.util.Arrays.fill(result, paletteIndex * Direction.values().length,
                    (paletteIndex + 1) * Direction.values().length, paletteColors[paletteIndex]);
        }
        return result;
    }

    static FarTerrainVoxelSection capture(LevelChunkSection section, int sectionY,
                                          ClientLevel level, int chunkMinX, int chunkMinZ) {
        Map<Integer, Integer> paletteLookup = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<int[]> faceColors = new ArrayList<>();
        List<Boolean> solids = new ArrayList<>();
        List<Boolean> translucent = new ArrayList<>();
        short[] indices = new short[VOXEL_COUNT];
        byte[] light = new byte[VOXEL_COUNT];
        int[] voxelColors = new int[VOXEL_COUNT];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int baseY = sectionY * EDGE;
        boolean hasRenderableBlock = false;

        for (int y = 0; y < EDGE; y++) {
            for (int z = 0; z < EDGE; z++) {
                for (int x = 0; x < EDGE; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    int stateId = Block.getId(state);
                    int index = index(x, y, z);
                    position.set(chunkMinX + x, baseY + y, chunkMinZ + z);
                    int mapColor = state.getMapColor(level, position).col;
                    Integer paletteIndex = paletteLookup.get(stateId);
                    if (paletteIndex == null) {
                        paletteIndex = palette.size();
                        paletteLookup.put(stateId, paletteIndex);
                        palette.add(stateId);
                        int[] sampledFaces = BLOCK_FACE_TEXTURE_COLORS.computeIfAbsent(stateId,
                                ignored -> blockFaceTextureColors(state, stateId, mapColor));
                        faceColors.add(sampledFaces);
                        colors.add(0xFF000000 | (averageRgb(sampledFaces) & 0x00FFFFFF));
                        boolean translucentState = isTranslucentState(state);
                        solids.add(!state.isAir() && !translucentState);
                        translucent.add(!state.isAir() && translucentState);
                    }
                    indices[index] = (short) (int) paletteIndex;
                    int baseColor = colors.get(paletteIndex) & 0x00FFFFFF;
                    int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, position, 0);
                    voxelColors[index] = 0xFF000000 | (tint == -1
                            ? baseColor : multiplyRgb(baseColor, tint));
                    if (!state.isAir()) {
                        hasRenderableBlock = true;
                        int sky = level.getBrightness(LightLayer.SKY, position) & 15;
                        int block = level.getBrightness(LightLayer.BLOCK, position) & 15;
                        light[index] = (byte) ((sky << 4) | block);
                    }
                }
            }
        }

        if (!hasRenderableBlock) return null;
        int[] paletteArray = new int[palette.size()];
        int[] colorArray = new int[palette.size()];
        int[] faceColorArray = new int[palette.size() * Direction.values().length];
        boolean[] solidArray = new boolean[palette.size()];
        boolean[] translucentArray = new boolean[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            paletteArray[i] = palette.get(i);
            colorArray[i] = colors.get(i);
            System.arraycopy(faceColors.get(i), 0, faceColorArray,
                    i * Direction.values().length, Direction.values().length);
            solidArray[i] = solids.get(i);
            translucentArray[i] = translucent.get(i);
        }
        return new FarTerrainVoxelSection(sectionY, paletteArray, indices, light, voxelColors, colorArray, faceColorArray, solidArray, translucentArray);
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
        int[] faceColors = new int[paletteSize * Direction.values().length];
        boolean[] solids = new boolean[paletteSize];
        boolean[] translucent = new boolean[paletteSize];
        if (version >= 3) {
            for (int i = 0; i < paletteSize; i++) colors[i] = input.readInt();
            for (int i = 0; i < paletteSize; i++) solids[i] = input.readBoolean();
            if (version >= 4) {
                for (int i = 0; i < paletteSize; i++) translucent[i] = input.readBoolean();
            }
            if (version >= 6) {
                for (int i = 0; i < faceColors.length; i++) faceColors[i] = input.readInt();
            } else {
                faceColors = defaultFaceColors(colors);
            }
        } else {
            for (int i = 0; i < paletteSize; i++) {
                colors[i] = 0xFF858585;
                solids[i] = palette[i] != 0;
            }
            faceColors = defaultFaceColors(colors);
        }
        short[] indices = new short[VOXEL_COUNT];
        for (int i = 0; i < VOXEL_COUNT; i++) {
            int paletteIndex = input.readUnsignedShort();
            if (paletteIndex >= paletteSize) throw new IOException("Invalid FarView voxel palette index");
            indices[i] = (short) paletteIndex;
        }
        byte[] light = new byte[VOXEL_COUNT];
        input.readFully(light);
        int[] voxelColors = new int[VOXEL_COUNT];
        if (version >= 5) {
            for (int i = 0; i < VOXEL_COUNT; i++) voxelColors[i] = input.readInt();
        } else {
            for (int i = 0; i < VOXEL_COUNT; i++) {
                voxelColors[i] = colors[Short.toUnsignedInt(indices[i])];
            }
        }
        FarTerrainVoxelSection result = new FarTerrainVoxelSection(sectionY, palette, indices, light,
                voxelColors, colors, faceColors, solids, translucent);
        if (version >= 7) result.cachedOctree = readOctree(input);
        return result;
    }

    void write(DataOutput output) throws IOException {
        output.writeInt(sectionY);
        output.writeShort(palette.length);
        for (int stateId : palette) output.writeInt(stateId);
        for (int color : paletteColors) output.writeInt(color);
        for (boolean solid : solidPalette) output.writeBoolean(solid);
        for (boolean translucent : translucentPalette) output.writeBoolean(translucent);
        for (int color : paletteFaceColors) output.writeInt(color);
        for (short paletteIndex : paletteIndices) output.writeShort(Short.toUnsignedInt(paletteIndex));
        output.write(packedLight);
        for (int color : voxelColors) output.writeInt(color);
        writeOctree(output);
    }

    int sectionY() { return sectionY; }

    long estimatedBytes() {
        return (long) palette.length * Integer.BYTES
                + (long) paletteIndices.length * Short.BYTES
                + packedLight.length
                + (long) voxelColors.length * Integer.BYTES
                + (long) paletteColors.length * Integer.BYTES
                + (long) paletteFaceColors.length * Integer.BYTES
                + solidPalette.length + translucentPalette.length
                + (cachedOctree == null ? 0L : 8L + (long) cachedOctree.nodeCount() * 42L);
    }

    int blockStateId(int x, int y, int z) {
        return palette[Short.toUnsignedInt(paletteIndices[index(x, y, z)])];
    }

    int materialColor(int x, int y, int z) {
        return voxelColors[index(x, y, z)];
    }

    int materialColor(int x, int y, int z, int axis, int direction) {
        int voxelIndex = index(x, y, z);
        int paletteIndex = Short.toUnsignedInt(paletteIndices[voxelIndex]);
        int face = switch (axis) {
            case 0 -> direction > 0 ? Direction.EAST.ordinal() : Direction.WEST.ordinal();
            case 1 -> direction > 0 ? Direction.UP.ordinal() : Direction.DOWN.ordinal();
            default -> direction > 0 ? Direction.SOUTH.ordinal() : Direction.NORTH.ordinal();
        };
        int baseAverage = paletteColors[paletteIndex];
        int tintedAverage = voxelColors[voxelIndex];
        int faceColor = paletteFaceColors[paletteIndex * Direction.values().length + face];
        return 0xFF000000 | tintFaceColor(faceColor, baseAverage, tintedAverage);
    }

    private static int tintFaceColor(int faceColor, int baseAverage, int tintedAverage) {
        int red = tintChannel((faceColor >>> 16) & 255,
                (baseAverage >>> 16) & 255, (tintedAverage >>> 16) & 255);
        int green = tintChannel((faceColor >>> 8) & 255,
                (baseAverage >>> 8) & 255, (tintedAverage >>> 8) & 255);
        int blue = tintChannel(faceColor & 255, baseAverage & 255, tintedAverage & 255);
        return (red << 16) | (green << 8) | blue;
    }

    private static int tintChannel(int face, int base, int tinted) {
        if (base == 0) return 0;
        int tint = Math.min(255, tinted * 255 / base);
        return face * tint / 255;
    }

    boolean isSolid(int x, int y, int z) {
        return solidPalette[Short.toUnsignedInt(paletteIndices[index(x, y, z)])];
    }

    boolean isTranslucent(int x, int y, int z) {
        return translucentPalette[Short.toUnsignedInt(paletteIndices[index(x, y, z)])];
    }

    int skyLight(int x, int y, int z) {
        return (packedLight[index(x, y, z)] >>> 4) & 15;
    }

    int blockLight(int x, int y, int z) {
        return packedLight[index(x, y, z)] & 15;
    }

    Octree buildOctree() {
        Octree result = cachedOctree;
        if (result != null) return result;
        synchronized (this) {
            result = cachedOctree;
            if (result == null) {
                OctreeBuilder builder = new OctreeBuilder();
                int root = builder.build(0, 0, 0, EDGE);
                result = builder.finish(root);
                cachedOctree = result;
            }
        }
        return result;
    }

    private static Octree readOctree(DataInput input) throws IOException {
        int root = input.readInt();
        int count = input.readInt();
        if (count < 0 || count > 65_536 || (count == 0 ? root != -1 : root < 0 || root >= count)) {
            throw new IOException("Invalid FarView octree header");
        }
        int[] materials = new int[count];
        byte[] lights = new byte[count];
        int[] children = new int[count * 8];
        byte[] sizes = new byte[count];
        int[] solidCounts = new int[count];
        for (int node = 0; node < count; node++) {
            materials[node] = input.readInt();
            lights[node] = input.readByte();
            int size = input.readUnsignedByte();
            int solidCount = input.readInt();
            if (size != 1 && size != 2 && size != 4 && size != 8 && size != 16) {
                throw new IOException("Invalid FarView octree node size");
            }
            if (solidCount < 1 || solidCount > size * size * size) {
                throw new IOException("Invalid FarView octree solid count");
            }
            sizes[node] = (byte) size;
            solidCounts[node] = solidCount;
            for (int child = 0; child < 8; child++) {
                int childIndex = input.readInt();
                if (childIndex < -1 || childIndex >= node) {
                    throw new IOException("Invalid FarView octree child index");
                }
                children[node * 8 + child] = childIndex;
            }
        }
        return new Octree(root, materials, lights, children, sizes, solidCounts);
    }

    private void writeOctree(DataOutput output) throws IOException {
        Octree tree = buildOctree();
        output.writeInt(tree.rootIndex());
        output.writeInt(tree.nodeCount());
        for (int node = 0; node < tree.nodeCount(); node++) {
            output.writeInt(tree.materialIds()[node]);
            output.writeByte(tree.packedLight()[node]);
            output.writeByte(tree.nodeSizes()[node]);
            output.writeInt(tree.solidCounts()[node]);
            for (int child = 0; child < 8; child++) {
                output.writeInt(tree.childIndices()[node * 8 + child]);
            }
        }
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

    static boolean isTranslucentState(BlockState state) {
        return !state.getFluidState().isEmpty() || state.getBlock() instanceof HalfTransparentBlock;
    }

    private static int[] blockFaceTextureColors(BlockState state, int stateId, int fallbackColor) {
        int[] faceColors = new int[Direction.values().length];
        java.util.Arrays.fill(faceColors, fallbackColor & 0x00FFFFFF);
        try {
            List<BlockModelPart> parts = new ArrayList<>();
            Minecraft.getInstance().getBlockRenderer().getBlockModel(state)
                    .collectParts(RandomSource.create(stateId), parts);
            for (Direction direction : Direction.values()) {
                long red = 0L;
                long green = 0L;
                long blue = 0L;
                int samples = 0;
                for (BlockModelPart part : parts) {
                    for (BakedQuad quad : part.getQuads(direction)) {
                        int color = spriteAverage(quad.sprite().contents().name());
                        if (color < 0) continue;
                        red += (color >>> 16) & 255;
                        green += (color >>> 8) & 255;
                        blue += color & 255;
                        samples++;
                    }
                }
                if (samples > 0) {
                    faceColors[direction.ordinal()] = ((int) (red / samples) << 16)
                            | ((int) (green / samples) << 8)
                            | (int) (blue / samples);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Missing or custom model implementations retain the map-color fallback.
        }
        return faceColors;
    }

    private static int averageRgb(int[] colors) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        for (int color : colors) {
            red += (color >>> 16) & 255;
            green += (color >>> 8) & 255;
            blue += color & 255;
        }
        return colors.length == 0 ? 0x858585
                : ((int) (red / colors.length) << 16) | ((int) (green / colors.length) << 8)
                | (int) (blue / colors.length);
    }

    private static int spriteAverage(Identifier spriteId) {
        return SPRITE_TEXTURE_COLORS.computeIfAbsent(spriteId, id -> {
            Identifier textureId = Identifier.fromNamespaceAndPath(
                    id.getNamespace(), "textures/" + id.getPath() + ".png");
            try {
                Resource resource = Minecraft.getInstance().getResourceManager()
                        .getResource(textureId).orElse(null);
                if (resource == null) return -1;
                try (InputStream input = resource.open()) {
                    BufferedImage image = ImageIO.read(input);
                    if (image == null) return -1;
                    int stepX = Math.max(1, image.getWidth() / 16);
                    int stepY = Math.max(1, image.getHeight() / 16);
                    long red = 0L;
                    long green = 0L;
                    long blue = 0L;
                    int samples = 0;
                    for (int y = 0; y < image.getHeight(); y += stepY) {
                        for (int x = 0; x < image.getWidth(); x += stepX) {
                            int pixel = image.getRGB(x, y);
                            if ((pixel >>> 24) < 32) continue;
                            red += (pixel >>> 16) & 255;
                            green += (pixel >>> 8) & 255;
                            blue += pixel & 255;
                            samples++;
                        }
                    }
                    if (samples == 0) return -1;
                    return ((int) (red / samples) << 16)
                            | ((int) (green / samples) << 8)
                            | (int) (blue / samples);
                }
            } catch (IOException | RuntimeException ignored) {
                return -1;
            }
        });
    }

    private static int multiplyRgb(int left, int right) {
        int red = ((left >>> 16) & 255) * ((right >>> 16) & 255) / 255;
        int green = ((left >>> 8) & 255) * ((right >>> 8) & 255) / 255;
        int blue = (left & 255) * (right & 255) / 255;
        return (red << 16) | (green << 8) | blue;
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
        int materialId(int nodeIndex) { return materialIds[nodeIndex]; }
        int skyLight(int nodeIndex) { return (packedLight[nodeIndex] >>> 4) & 15; }
        int blockLight(int nodeIndex) { return packedLight[nodeIndex] & 15; }
    }
}

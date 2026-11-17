package name.vantaclient.client.util;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** CPU-only regression coverage for sparse octrees and 3D greedy meshing. */
@SuppressWarnings("UnstableApiUsage")
public final class FarTerrainVoxelMeshGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        verifyUniformSectionCollapsesToOneOctreeLeaf();
        verifyOctreeUsesWeightedDominantMaterial();
        verifySingleBlockProducesSixFaces();
        verifyAdjacentBlocksGreedyMerge();
        verifyUpperSectionUsesWorldHeight();
        verifyFacesAreHiddenAcrossVerticalSections();
        verifyVoxelPayloadRoundTrip();
        verifyFluidVoxelUsesTranslucentMesh();
        verifyVoxelPayloadRoundTrip();
        verifyRegionStorageHandlesNegativeChunkCoordinates();
        verifySnapshotMemoryEstimateIncludesVoxelPayload();
        verifyPackedGpuVertexCoordinatesRoundTrip();
    }

    private static void verifyUniformSectionCollapsesToOneOctreeLeaf() {
        FarTerrainVoxelSection section = createFullSection();
        FarTerrainVoxelSection.Octree octree = section.buildOctree();
        if (octree.nodeCount() != 1 || octree.isEmpty()) {
            throw new AssertionError("A uniform 16^3 section should collapse to one sparse-octree node.");
        }
        int root = octree.rootIndex();
        if (!octree.isLeaf(root) || octree.size(root) != 16 || octree.solidCount(root) != 4096) {
            throw new AssertionError("Uniform octree leaf did not preserve its 16^3 extent and voxel count.");
        }
        FarTerrainVoxelMesh mesh = FarTerrainVoxelMesh.build(snapshot(section));
        if (mesh == null || mesh.quadCount() != 6) {
            throw new AssertionError("A uniform 16^3 cube should greedily reduce to six faces.");
        }
    }

    private static void verifyOctreeUsesWeightedDominantMaterial() {
        short[] indices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 2; x++) {
                    indices[index(x, y, z)] = (short) (x == 1 && y == 1 && z == 1 ? 2 : 1);
                }
            }
        }
        int[] voxelColors = colorsForIndices(indices);
        FarTerrainVoxelSection section = new FarTerrainVoxelSection(0,
                new int[]{0, 1, 2}, indices, new byte[FarTerrainVoxelSection.VOXEL_COUNT],
                voxelColors, new int[]{0xFF000000, 0xFF777777, 0xFF996633},
                new boolean[]{false, true, true}, new boolean[]{false, false, false});
        FarTerrainVoxelSection.Octree octree = section.buildOctree();
        if (octree.isEmpty() || octree.materialId(octree.rootIndex()) != 1
                || octree.solidCount(octree.rootIndex()) != 8) {
            throw new AssertionError("Octree parent material must use solid-voxel frequency, not child count.");
        }
    }

    private static void verifySingleBlockProducesSixFaces() {
        short[] indices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        indices[index(7, 5, 9)] = 1;
        FarTerrainVoxelSection section = createSection(indices,
                new byte[FarTerrainVoxelSection.VOXEL_COUNT]);
        FarTerrainVoxelMesh mesh = FarTerrainVoxelMesh.build(snapshot(section));
        if (mesh == null || mesh.quadCount() != 6) {
            throw new AssertionError("One isolated voxel must produce exactly six exposed greedy faces.");
        }
        FarTerrainVoxelSection.Octree octree = section.buildOctree();
        if (octree.solidCount(octree.rootIndex()) != 1 || octree.isLeaf(octree.rootIndex())) {
            throw new AssertionError("Sparse octree failed to retain one isolated voxel.");
        }
    }

    private static void verifyAdjacentBlocksGreedyMerge() {
        short[] indices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        indices[index(3, 4, 5)] = 1;
        indices[index(4, 4, 5)] = 1;
        FarTerrainVoxelSection section = createSection(indices,
                new byte[FarTerrainVoxelSection.VOXEL_COUNT]);
        FarTerrainVoxelMesh mesh = FarTerrainVoxelMesh.build(snapshot(section));
        if (mesh == null || mesh.quadCount() != 6) {
            throw new AssertionError("Two adjacent equal-material voxels should merge into six cuboid faces.");
        }
    }

    private static void verifyUpperSectionUsesWorldHeight() {
        FarTerrainVoxelSection upper = createFullSectionAtY(5);
        FarTerrainVoxelMesh mesh = FarTerrainVoxelMesh.build(snapshot(upper));
        if (mesh == null) throw new AssertionError("Upper section did not produce voxel geometry.");
        float[] positions = mesh.positions();
        for (int i = 1; i < positions.length; i += 3) {
            if (positions[i] >= 80.0f) return;
        }
        throw new AssertionError("Upper-section geometry was incorrectly collapsed into Y=0..16.");
    }

    private static void verifyFacesAreHiddenAcrossVerticalSections() {
        short[] lowerIndices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        short[] upperIndices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        lowerIndices[index(8, 15, 8)] = 1;
        upperIndices[index(8, 0, 8)] = 1;
        FarTerrainVoxelSection lower = createSectionAtY(0, lowerIndices);
        FarTerrainVoxelSection upper = createSectionAtY(1, upperIndices);
        FarTerrainVoxelMesh mesh = FarTerrainVoxelMesh.build(new FarTerrainChunkSnapshot(
                0, 0, new int[256], new int[256], new FarTerrainVoxelSection[]{lower, upper}));
        if (mesh == null || mesh.quadCount() != 10) {
            throw new AssertionError("Adjacent voxels across vertical sections must not emit their shared face.");
        }
    }

    private static FarTerrainVoxelSection createFullSectionAtY(int sectionY) {
        short[] indices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        java.util.Arrays.fill(indices, (short) 1);
        return createSectionAtY(sectionY, indices);
    }

    private static FarTerrainVoxelSection createSectionAtY(int sectionY, short[] indices) {
        return new FarTerrainVoxelSection(sectionY,
                new int[]{0, 1}, indices, new byte[FarTerrainVoxelSection.VOXEL_COUNT],
                colorsForIndices(indices), new int[]{0xFF000000, 0xFF777777},
                new boolean[]{false, true}, new boolean[]{false, false});
    }

    private static void verifyFluidVoxelUsesTranslucentMesh() {
        short[] indices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        indices[index(4, 7, 9)] = 1;
        FarTerrainVoxelSection section = new FarTerrainVoxelSection(0,
                new int[]{0, 1}, indices, new byte[FarTerrainVoxelSection.VOXEL_COUNT],
                colorsForIndices(indices), new int[]{0xFF000000, 0xFF5577AA},
                new boolean[]{false, false}, new boolean[]{false, true});
        FarTerrainVoxelMesh mesh = FarTerrainVoxelMesh.build(snapshot(section));
        if (mesh == null || mesh.quadCount() != 0 || mesh.translucentQuadCount() != 6) {
            throw new AssertionError("Fluid-only sections must create a separate six-face translucent mesh.");
        }
        for (int color : mesh.translucentColors()) {
            if ((color >>> 24) != 0xA0) {
                throw new AssertionError("Translucent voxel material lost its alpha channel.");
            }
        }
    }

    private static void verifyRegionStorageHandlesNegativeChunkCoordinates() {
        java.nio.file.Path root = java.nio.file.Path.of("world", "vanta", "farview", "overworld");
        if (!FarTerrainSnapshotStore.regionDirectory(root, -1, -1).endsWith("r_-1_-1")
                || !FarTerrainSnapshotStore.regionDirectory(root, -32, -32).endsWith("r_-1_-1")
                || !FarTerrainSnapshotStore.regionDirectory(root, -33, -33).endsWith("r_-2_-2")
                || !FarTerrainSnapshotStore.regionDirectory(root, 31, 31).endsWith("r_0_0")
                || !FarTerrainSnapshotStore.regionDirectory(root, 32, 32).endsWith("r_1_1")) {
            throw new AssertionError("Spatial FarView cache sharding mishandled negative chunk coordinates.");
        }
    }

    private static void verifySnapshotMemoryEstimateIncludesVoxelPayload() {
        FarTerrainVoxelSection section = createFullSection();
        FarTerrainChunkSnapshot snapshot = snapshot(section);
        if (section.estimatedBytes() < 24_000L
                || snapshot.estimatedBytes() < section.estimatedBytes()) {
            throw new AssertionError("Snapshot byte accounting omitted its dense voxel and tint payload.");
        }
    }

    private static void verifyPackedGpuVertexCoordinatesRoundTrip() {
        int packed = FarTerrainVoxelGpuRenderer.packPosition(16.0f, -64.0f, 15.0f);
        if (FarTerrainVoxelGpuRenderer.unpackX(packed) != 16
                || FarTerrainVoxelGpuRenderer.unpackY(packed) != -64
                || FarTerrainVoxelGpuRenderer.unpackZ(packed) != 15) {
            throw new AssertionError("Packed 64-bit GPU vertex format corrupted block coordinates.");
        }
    }

    private static void verifyVoxelPayloadRoundTrip() {
        short[] indices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        byte[] light = new byte[FarTerrainVoxelSection.VOXEL_COUNT];
        indices[index(2, 6, 11)] = 1;
        light[index(2, 6, 11)] = (byte) 0xC5;
        int[] voxelColors = colorsForIndices(indices);
        voxelColors[index(2, 6, 11)] = 0xFF12AB34;
        FarTerrainVoxelSection source = new FarTerrainVoxelSection(0,
                new int[]{0, 1}, indices, light, voxelColors,
                new int[]{0xFF000000, 0xFF777777},
                new boolean[]{false, true}, new boolean[]{false, false});
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.DataOutputStream output = new java.io.DataOutputStream(bytes)) {
                source.write(output);
            }
            FarTerrainVoxelSection restored;
            try (java.io.DataInputStream input = new java.io.DataInputStream(
                    new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                restored = FarTerrainVoxelSection.read(input, 5);
            }
            if (restored.blockStateId(2, 6, 11) != 1
                    || !restored.isSolid(2, 6, 11)
                    || restored.materialColor(2, 6, 11) != 0xFF12AB34
                    || restored.skyLight(2, 6, 11) != 12
                    || restored.blockLight(2, 6, 11) != 5) {
                throw new AssertionError("Version 5 voxel payload lost palette, per-voxel material, or packed light data.");
            }
        } catch (java.io.IOException error) {
            throw new AssertionError("Voxel payload round-trip failed.", error);
        }
    }

    private static int[] colorsForIndices(short[] indices) {
        int[] colors = new int[FarTerrainVoxelSection.VOXEL_COUNT];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = Short.toUnsignedInt(indices[i]) == 0 ? 0xFF000000 : 0xFF777777;
        }
        return colors;
    }

    private static FarTerrainVoxelSection createFullSection() {
        short[] indices = new short[FarTerrainVoxelSection.VOXEL_COUNT];
        java.util.Arrays.fill(indices, (short) 1);
        byte[] light = new byte[FarTerrainVoxelSection.VOXEL_COUNT];
        java.util.Arrays.fill(light, (byte) 0xF0);
        return createSection(indices, light);
    }

    private static FarTerrainVoxelSection createSection(short[] indices, byte[] light) {
        return new FarTerrainVoxelSection(0,
                new int[]{0, 1}, indices, light,
                colorsForIndices(indices), new int[]{0xFF000000, 0xFF777777},
                new boolean[]{false, true}, new boolean[]{false, false});
    }

    private static FarTerrainChunkSnapshot snapshot(FarTerrainVoxelSection section) {
        return new FarTerrainChunkSnapshot(0, 0, new int[256], new int[256],
                new FarTerrainVoxelSection[]{section});
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}

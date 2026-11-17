package name.vantaclient.client.util;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** CPU-only regression coverage for sparse octrees and 3D greedy meshing. */
@SuppressWarnings("UnstableApiUsage")
public final class FarTerrainVoxelMeshGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        verifyUniformSectionCollapsesToOneOctreeLeaf();
        verifySingleBlockProducesSixFaces();
        verifyAdjacentBlocksGreedyMerge();
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
                new int[]{0xFF000000, 0xFF777777},
                new boolean[]{false, true});
    }

    private static FarTerrainChunkSnapshot snapshot(FarTerrainVoxelSection section) {
        return new FarTerrainChunkSnapshot(0, 0, new int[256], new int[256],
                new FarTerrainVoxelSection[]{section});
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}

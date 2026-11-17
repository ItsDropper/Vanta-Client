package name.vantaclient.client.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Greedy-meshed opaque voxel geometry for one captured chunk. */
final class FarTerrainVoxelMesh {
    private static final int EDGE = 16;
    private static final int MAX_QUADS = 16_384;

    private final int chunkX;
    private final int chunkZ;
    private final float[] positions;
    private final int[] colors;
    private final FarTerrainVoxelSection.Octree[] octrees;

    private FarTerrainVoxelMesh(int chunkX, int chunkZ, float[] positions,
                                int[] colors, FarTerrainVoxelSection.Octree[] octrees) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.positions = positions;
        this.colors = colors;
        this.octrees = octrees;
    }

    static FarTerrainVoxelMesh build(FarTerrainChunkSnapshot snapshot) {
        FarTerrainVoxelSection[] sections = snapshot.voxelSections();
        if (sections.length == 0) return null;

        Map<Integer, FarTerrainVoxelSection> bySectionY = new HashMap<>(sections.length * 2);
        FarTerrainVoxelSection.Octree[] octrees = new FarTerrainVoxelSection.Octree[sections.length];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < sections.length; i++) {
            FarTerrainVoxelSection section = sections[i];
            bySectionY.put(section.sectionY(), section);
            octrees[i] = section.buildOctree();
            minY = Math.min(minY, section.sectionY() * EDGE);
            maxY = Math.max(maxY, section.sectionY() * EDGE + EDGE - 1);
        }

        GeometryBuilder geometry = new GeometryBuilder();
        int[] mask = new int[EDGE * EDGE];
        for (int axis = 0; axis < 3 && geometry.quadCount() < MAX_QUADS; axis++) {
            int uAxis = (axis + 1) % 3;
            int vAxis = (axis + 2) % 3;
            int firstSlice = axis == 1 ? minY : 0;
            int lastSlice = axis == 1 ? maxY : EDGE - 1;
            for (int direction : new int[]{-1, 1}) {
                for (int slice = firstSlice; slice <= lastSlice && geometry.quadCount() < MAX_QUADS; slice++) {
                    java.util.Arrays.fill(mask, 0);
                    for (int v = 0; v < EDGE; v++) {
                        for (int u = 0; u < EDGE; u++) {
                            int x = axis == 0 ? slice : (uAxis == 0 ? u : v);
                            int y = axis == 1 ? slice : (uAxis == 1 ? u : v);
                            int z = axis == 2 ? slice : (uAxis == 2 ? u : v);
                            FarTerrainVoxelSection section = bySectionY.get(Math.floorDiv(y, EDGE));
                            if (section == null || !section.isSolid(x, y, z)) continue;

                            int nx = x + (axis == 0 ? direction : 0);
                            int ny = y + (axis == 1 ? direction : 0);
                            int nz = z + (axis == 2 ? direction : 0);
                            if (isSolid(bySectionY, nx, ny, nz)) continue;
                            mask[v * EDGE + u] = litColor(section, x, y, z);
                        }
                    }

                    for (int v = 0; v < EDGE && geometry.quadCount() < MAX_QUADS; v++) {
                        for (int u = 0; u < EDGE && geometry.quadCount() < MAX_QUADS;) {
                            int color = mask[v * EDGE + u];
                            if (color == 0) {
                                u++;
                                continue;
                            }
                            int width = 1;
                            while (u + width < EDGE && mask[v * EDGE + u + width] == color) width++;
                            int height = 1;
                            boolean extend = true;
                            while (v + height < EDGE && extend) {
                                for (int x = 0; x < width; x++) {
                                    if (mask[(v + height) * EDGE + u + x] != color) {
                                        extend = false;
                                        break;
                                    }
                                }
                                if (extend) height++;
                            }

                            geometry.addFace(axis, direction, slice, uAxis, u, u + width,
                                    vAxis, v, v + height, color);
                            for (int row = 0; row < height; row++) {
                                java.util.Arrays.fill(mask, (v + row) * EDGE + u,
                                        (v + row) * EDGE + u + width, 0);
                            }
                            u += width;
                        }
                    }
                }
            }
        }

        if (geometry.quadCount() == 0) return null;
        return new FarTerrainVoxelMesh(snapshot.chunkX(), snapshot.chunkZ(),
                geometry.positions(), geometry.colors(), octrees);
    }

    private static boolean isSolid(Map<Integer, FarTerrainVoxelSection> sections,
                                   int x, int y, int z) {
        if (x < 0 || x >= EDGE || z < 0 || z >= EDGE) return false;
        FarTerrainVoxelSection section = sections.get(Math.floorDiv(y, EDGE));
        return section != null && section.isSolid(x, y, z);
    }

    private static int litColor(FarTerrainVoxelSection section, int x, int y, int z) {
        int color = section.materialColor(x, y, z);
        float sky = section.skyLight(x, y, z) / 15.0f;
        float block = section.blockLight(x, y, z) / 15.0f;
        float brightness = Math.max(0.12f, Math.max(sky * 0.92f, block));
        int red = channel(color, 16, brightness + block * 0.08f);
        int green = channel(color, 8, brightness + block * 0.025f);
        int blue = channel(color, 0, brightness - block * 0.06f);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int color, int shift, float multiplier) {
        return Math.max(0, Math.min(255, Math.round(((color >>> shift) & 0xFF) * multiplier)));
    }

    int chunkX() { return chunkX; }
    int chunkZ() { return chunkZ; }
    int quadCount() { return colors.length; }
    float[] positions() { return positions; }
    int[] colors() { return colors; }
    int octreeSectionCount() { return octrees.length; }

    private static final class GeometryBuilder {
        private final List<Float> positions = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();

        int quadCount() { return colors.size(); }

        void addFace(int axis, int direction, int slice,
                     int uAxis, int u0, int u1, int vAxis, int v0, int v1, int color) {
            float plane = slice + (direction > 0 ? 1.0f : 0.0f);
            float[][] corners = new float[4][3];
            for (float[] corner : corners) corner[axis] = plane;
            corners[0][uAxis] = u0; corners[0][vAxis] = v0;
            corners[1][uAxis] = u1; corners[1][vAxis] = v0;
            corners[2][uAxis] = u1; corners[2][vAxis] = v1;
            corners[3][uAxis] = u0; corners[3][vAxis] = v1;
            for (float[] corner : corners) {
                positions.add(corner[0]);
                positions.add(corner[1]);
                positions.add(corner[2]);
            }
            colors.add(color);
        }

        float[] positions() {
            float[] result = new float[positions.size()];
            for (int i = 0; i < result.length; i++) result[i] = positions.get(i);
            return result;
        }

        int[] colors() {
            int[] result = new int[colors.size()];
            for (int i = 0; i < result.length; i++) result[i] = colors.get(i);
            return result;
        }
    }
}

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
    private final float[] translucentPositions;
    private final int[] translucentColors;
    private final FarTerrainVoxelSection.Octree[] octrees;

    private FarTerrainVoxelMesh(int chunkX, int chunkZ, float[] positions, int[] colors,
                                float[] translucentPositions, int[] translucentColors,
                                FarTerrainVoxelSection.Octree[] octrees) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.positions = positions;
        this.colors = colors;
        this.translucentPositions = translucentPositions;
        this.translucentColors = translucentColors;
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

        GeometryBuilder opaque = buildGeometry(sections, bySectionY, false);
        GeometryBuilder translucent = buildGeometry(sections, bySectionY, true);
        if (opaque.quadCount() == 0 && translucent.quadCount() == 0) return null;
        return new FarTerrainVoxelMesh(snapshot.chunkX(), snapshot.chunkZ(),
                opaque.positions(), opaque.colors(),
                translucent.positions(), translucent.colors(), octrees);
    }

    private static GeometryBuilder buildGeometry(FarTerrainVoxelSection[] sections,
                                                   Map<Integer, FarTerrainVoxelSection> bySectionY,
                                                   boolean translucentPass) {
        GeometryBuilder geometry = new GeometryBuilder();
        int[] mask = new int[EDGE * EDGE];
        for (FarTerrainVoxelSection section : sections) {
            int sectionBaseY = section.sectionY() * EDGE;
            for (int axis = 0; axis < 3 && geometry.quadCount() < MAX_QUADS; axis++) {
                int uAxis = (axis + 1) % 3;
                int vAxis = (axis + 2) % 3;
                for (int direction : new int[]{-1, 1}) {
                    for (int slice = 0; slice < EDGE && geometry.quadCount() < MAX_QUADS; slice++) {
                        java.util.Arrays.fill(mask, 0);
                        for (int v = 0; v < EDGE; v++) {
                            for (int u = 0; u < EDGE; u++) {
                                int x;
                                int y;
                                int z;
                                if (axis == 0) {
                                    x = slice;
                                    y = sectionBaseY + u;
                                    z = v;
                                } else if (axis == 1) {
                                    x = v;
                                    y = sectionBaseY + slice;
                                    z = u;
                                } else {
                                    x = u;
                                    y = sectionBaseY + v;
                                    z = slice;
                                }
                                int localY = y - sectionBaseY;
                                boolean occupied = translucentPass
                                        ? section.isTranslucent(x, localY, z)
                                        : section.isSolid(x, localY, z);
                                if (!occupied) continue;

                                int nx = x + (axis == 0 ? direction : 0);
                                int ny = y + (axis == 1 ? direction : 0);
                                int nz = z + (axis == 2 ? direction : 0);
                                boolean neighborOpaque = isSolid(bySectionY, nx, ny, nz);
                                boolean neighborTranslucent = isTranslucent(bySectionY, nx, ny, nz);
                                if (translucentPass
                                        ? neighborOpaque || neighborTranslucent
                                        : neighborOpaque) continue;
                                mask[v * EDGE + u] = litColor(section, x, localY, z, translucentPass, axis, direction);
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

                                int u0 = u;
                                int u1 = u + width;
                                int v0 = v;
                                int v1 = v + height;
                                if (uAxis == 1) {
                                    u0 += sectionBaseY;
                                    u1 += sectionBaseY;
                                }
                                if (vAxis == 1) {
                                    v0 += sectionBaseY;
                                    v1 += sectionBaseY;
                                }
                                geometry.addFace(axis, direction,
                                        axis == 1 ? sectionBaseY + slice : slice,
                                        uAxis, u0, u1, vAxis, v0, v1, color);
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
        }
        return geometry;
    }

    private static boolean isTranslucent(Map<Integer, FarTerrainVoxelSection> sections,
                                          int x, int y, int z) {
        if (x < 0 || x >= EDGE || z < 0 || z >= EDGE) return false;
        FarTerrainVoxelSection section = sections.get(Math.floorDiv(y, EDGE));
        return section != null && section.isTranslucent(x, y, z);
    }

    private static boolean isSolid(Map<Integer, FarTerrainVoxelSection> sections,
                                   int x, int y, int z) {
        if (x < 0 || x >= EDGE || z < 0 || z >= EDGE) return false;
        FarTerrainVoxelSection section = sections.get(Math.floorDiv(y, EDGE));
        return section != null && section.isSolid(x, y, z);
    }

    private static int litColor(FarTerrainVoxelSection section, int x, int y, int z, boolean translucent, int axis, int direction) {
        int color = section.materialColor(x, y, z);
        float sky = section.skyLight(x, y, z) / 15.0f;
        float block = section.blockLight(x, y, z) / 15.0f;
        float directional = axis == 1 ? (direction > 0 ? 1.0f : 0.52f)
                : axis == 0 ? 0.78f : 0.88f;
        float brightness = Math.max(0.12f, Math.max(sky * 0.92f, block)) * directional;
        int red = channel(color, 16, brightness + block * 0.08f);
        int green = channel(color, 8, brightness + block * 0.025f);
        int blue = channel(color, 0, brightness - block * 0.06f);
        return (translucent ? 0xA0000000 : 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int color, int shift, float multiplier) {
        return Math.max(0, Math.min(255, Math.round(((color >>> shift) & 0xFF) * multiplier)));
    }

    int chunkX() { return chunkX; }
    int chunkZ() { return chunkZ; }
    int quadCount() { return colors.length; }
    float[] positions() { return positions; }
    int[] colors() { return colors; }
    int translucentQuadCount() { return translucentColors.length; }
    float[] translucentPositions() { return translucentPositions; }
    int[] translucentColors() { return translucentColors; }
    int octreeSectionCount() { return octrees.length; }

    long estimatedBytes() {
        long bytes = (long) (positions.length + translucentPositions.length) * Float.BYTES
                + (long) (colors.length + translucentColors.length) * Integer.BYTES;
        for (FarTerrainVoxelSection.Octree octree : octrees) {
            bytes += (long) octree.materialIds().length * Integer.BYTES;
            bytes += octree.packedLight().length;
            bytes += (long) octree.childIndices().length * Integer.BYTES;
            bytes += octree.nodeSizes().length;
            bytes += (long) octree.solidCounts().length * Integer.BYTES;
        }
        return bytes;
    }

    private static final class GeometryBuilder {
        private float[] positions = new float[4096];
        private int[] colors = new int[256];
        private int positionSize;
        private int colorSize;

        int quadCount() { return colorSize; }

        void addFace(int axis, int direction, int slice,
                     int uAxis, int u0, int u1, int vAxis, int v0, int v1, int color) {
            float plane = slice + (direction > 0 ? 1.0f : 0.0f);
            addVertex(axis, plane, uAxis, u0, vAxis, v0);
            addVertex(axis, plane, uAxis, u1, vAxis, v0);
            addVertex(axis, plane, uAxis, u1, vAxis, v1);
            addVertex(axis, plane, uAxis, u0, vAxis, v1);
            if (colorSize == colors.length) colors = java.util.Arrays.copyOf(colors, colors.length * 2);
            colors[colorSize++] = color;
        }

        private void addVertex(int axis, float plane, int uAxis, float u, int vAxis, float v) {
            if (positionSize + 3 > positions.length) {
                positions = java.util.Arrays.copyOf(positions, positions.length * 2);
            }
            positions[positionSize++] = axis == 0 ? plane : (uAxis == 0 ? u : v);
            positions[positionSize++] = axis == 1 ? plane : (uAxis == 1 ? u : v);
            positions[positionSize++] = axis == 2 ? plane : (uAxis == 2 ? u : v);
        }

        float[] positions() { return java.util.Arrays.copyOf(positions, positionSize); }
        int[] colors() { return java.util.Arrays.copyOf(colors, colorSize); }
    }
}

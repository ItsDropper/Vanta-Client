package name.vantaclient.client.gui.lobby;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Deterministic generator for the title lobby's landscape.
 *
 * The scene is a sunlit valley: a flat stone plaza under the avatar, a winding path, a lake framed
 * by cherry trees, a timber pavilion on the right, forested hills and snow-capped mountains on the
 * horizon, with lanterns and a campfire for warm light. Every block is a pure function of its world
 * coordinates, so chunks can be generated in any order and trees overlap chunk borders cleanly.
 */
final class VantaLobbyTerrain {
    static final int WATER_LEVEL = 61;
    static final int BASE = 63;
    private static final int MARGIN = 8;
    private static final int CELL = 7;

    // Landmarks, in world coordinates.
    private static final double PLAZA_X = 0.0, PLAZA_Z = -4.0, PLAZA_RADIUS = 7.5;
    private static final double LAKE_X = -26.0, LAKE_Z = -34.0, LAKE_RADIUS = 17.0;
    private static final int PAVILION_X = 24, PAVILION_Z = -21;
    private static final double[][] MAIN_PATH = {{2, -8}, {3, -22}, {-2, -36}, {-5, -54}, {0, -76}, {4, -100}};
    private static final double[][] SPUR_PATH = {{6, -7}, {12, -13}, {17, -19}};

    private VantaLobbyTerrain() {}

    // ------------------------------------------------------------------ noise

    private static float hash(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0f;
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double noise(double x, double z, int seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = smooth(x - xi), fz = smooth(z - zi);
        double a = hash(xi, zi, seed), b = hash(xi + 1, zi, seed);
        double c = hash(xi, zi + 1, seed), d = hash(xi + 1, zi + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private static double fbm(double x, double z, int seed, int octaves) {
        double sum = 0.0, amplitude = 0.5, total = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x, z, seed + i * 31) * amplitude;
            total += amplitude;
            x *= 2.03;
            z *= 2.03;
            amplitude *= 0.5;
        }
        return sum / total;
    }

    // ------------------------------------------------------------------ shape

    static double heightAt(int x, int z) {
        double hills = (fbm(x * 0.011, z * 0.011, 11, 4) - 0.5) * 2.0;
        double detail = (fbm(x * 0.07, z * 0.07, 23, 2) - 0.5) * 2.0;
        double h = BASE + hills * 8.5 + detail * 1.4;

        // Mountains rise on the horizon, ridges on both flanks frame the view.
        double ridge = smooth((-z - 46) / 70.0);
        h += ridge * ridge * (30.0 + fbm(x * 0.02, z * 0.02, 37, 4) * 34.0);
        double flank = smooth((Math.abs(x) - 42) / 54.0);
        h += flank * (10.0 + fbm(x * 0.03, z * 0.03, 41, 3) * 16.0);
        // Keep the area behind the camera gentle so the foreground stays calm.
        if (z > 0) h = BASE + (h - BASE) * (1.0 - smooth(z / 14.0) * 0.7);

        // Lake basin.
        double lake = Math.hypot(x - LAKE_X, (z - LAKE_Z) * 1.15) / LAKE_RADIUS;
        if (lake < 1.45) {
            double bowl = smooth((1.45 - lake) / 0.95);
            h = h + (WATER_LEVEL - 4.0 - h) * bowl;
        }

        // Flat pads for the plaza and the pavilion.
        double plaza = 1.0 - smooth((Math.hypot(x - PLAZA_X, z - PLAZA_Z) - 13.0) / 17.0);
        h += (BASE - h) * plaza;
        double pavilion = 1.0 - smooth((Math.hypot(x - PAVILION_X, z - PAVILION_Z) - 12.0) / 9.0);
        h += (BASE - h) * pavilion;
        return h;
    }

    private static double distanceToPath(double[][] path, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < path.length; i++) {
            double ax = path[i][0], az = path[i][1], bx = path[i + 1][0], bz = path[i + 1][1];
            double dx = bx - ax, dz = bz - az;
            double t = Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz)));
            best = Math.min(best, Math.hypot(x - (ax + dx * t), z - (az + dz * t)));
        }
        return best;
    }

    private static double pathDistance(int x, int z) {
        return Math.min(distanceToPath(MAIN_PATH, x, z), distanceToPath(SPUR_PATH, x, z));
    }

    private static boolean inPavilionZone(int x, int z) {
        return Math.abs(x - PAVILION_X) <= 10 && Math.abs(z - PAVILION_Z) <= 10;
    }

    private static boolean inPlazaZone(int x, int z) {
        return Math.hypot(x - PLAZA_X, z - PLAZA_Z) <= PLAZA_RADIUS + 1.5;
    }

    // ------------------------------------------------------------------ chunk builder

    private static final class Cell {
        final LevelChunkSection[] sections;
        final int minSection, chunkX, chunkZ;
        final int[] heights;
        final int span = 16 + MARGIN * 2;

        Cell(LevelChunkSection[] sections, int minSection, int chunkX, int chunkZ) {
            this.sections = sections;
            this.minSection = minSection;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.heights = new int[span * span];
            for (int dz = 0; dz < span; dz++) {
                for (int dx = 0; dx < span; dx++) {
                    heights[dz * span + dx] = (int) Math.round(heightAt(chunkX * 16 - MARGIN + dx, chunkZ * 16 - MARGIN + dz));
                }
            }
        }

        int height(int wx, int wz) {
            int dx = wx - (chunkX * 16 - MARGIN), dz = wz - (chunkZ * 16 - MARGIN);
            if (dx < 0 || dz < 0 || dx >= span || dz >= span) return (int) Math.round(heightAt(wx, wz));
            return heights[dz * span + dx];
        }

        boolean inChunk(int wx, int wz) {
            return Math.floorDiv(wx, 16) == chunkX && Math.floorDiv(wz, 16) == chunkZ;
        }

        void set(int wx, int y, int wz, BlockState state) {
            if (!inChunk(wx, wz)) return;
            int index = Math.floorDiv(y, 16) - minSection;
            if (index < 0 || index >= sections.length) return;
            sections[index].setBlockState(Math.floorMod(wx, 16), Math.floorMod(y, 16), Math.floorMod(wz, 16), state);
        }

        BlockState get(int wx, int y, int wz) {
            if (!inChunk(wx, wz)) return Blocks.AIR.defaultBlockState();
            int index = Math.floorDiv(y, 16) - minSection;
            if (index < 0 || index >= sections.length) return Blocks.AIR.defaultBlockState();
            return sections[index].getBlockState(Math.floorMod(wx, 16), Math.floorMod(y, 16), Math.floorMod(wz, 16));
        }

        void leaf(int wx, int y, int wz, BlockState state) {
            if (get(wx, y, wz).isAir()) set(wx, y, wz, state);
        }
    }

    static void generate(LevelChunkSection[] sections, int minSection, int chunkX, int chunkZ) {
        Cell cell = new Cell(sections, minSection, chunkX, chunkZ);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                column(cell, chunkX * 16 + x, chunkZ * 16 + z);
            }
        }
        structures(cell);
        vegetation(cell);
    }

    private static void column(Cell cell, int wx, int wz) {
        int ground = cell.height(wx, wz);
        double slope = Math.max(Math.max(Math.abs(cell.height(wx + 1, wz) - ground), Math.abs(cell.height(wx - 1, wz) - ground)),
                Math.max(Math.abs(cell.height(wx, wz + 1) - ground), Math.abs(cell.height(wx, wz - 1) - ground)));
        BlockState top = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState under = Blocks.DIRT.defaultBlockState();
        float r = hash(wx, wz, 5);
        if (ground >= 96) {
            top = Blocks.SNOW_BLOCK.defaultBlockState();
            under = Blocks.STONE.defaultBlockState();
        } else if (ground >= 86 || slope >= 3) {
            top = r < 0.25f ? Blocks.COBBLESTONE.defaultBlockState() : r < 0.35f ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            under = Blocks.STONE.defaultBlockState();
        } else if (ground <= WATER_LEVEL + 1) {
            top = ground <= WATER_LEVEL - 1 && r < 0.3f ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
            under = Blocks.SAND.defaultBlockState();
        }
        double pathDistance = pathDistance(wx, wz);
        if (ground > WATER_LEVEL + 1 && ground < 84 && slope < 3) {
            if (pathDistance < 1.5) {
                top = r < 0.12f ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
            } else if (pathDistance < 2.4 && r < 0.45f) {
                top = Blocks.COARSE_DIRT.defaultBlockState();
            }
        }
        for (int y = ground - 5; y <= ground; y++) {
            cell.set(wx, y, wz, y == ground ? top : y >= ground - 2 ? under : Blocks.STONE.defaultBlockState());
        }
        for (int y = ground - 12; y < ground - 5; y++) cell.set(wx, y, wz, Blocks.STONE.defaultBlockState());
        for (int y = ground + 1; y <= WATER_LEVEL; y++) cell.set(wx, y, wz, Blocks.WATER.defaultBlockState());
    }

    // ------------------------------------------------------------------ structures

    private static void structures(Cell cell) {
        // Stone plaza under the avatar.
        for (int x = -9; x <= 9; x++) {
            for (int z = -13; z <= 5; z++) {
                double d = Math.hypot(x - PLAZA_X, z - PLAZA_Z);
                if (d > PLAZA_RADIUS) continue;
                float r = hash(x, z, 77);
                BlockState block;
                if (d > PLAZA_RADIUS - 1.2) block = Blocks.POLISHED_ANDESITE.defaultBlockState();
                else if (r < 0.10f) block = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                else if (r < 0.2f) block = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                else block = Blocks.STONE_BRICKS.defaultBlockState();
                cell.set(x, BASE, z, block);
                for (int y = BASE + 1; y <= BASE + 6; y++) cell.set(x, y, z, Blocks.AIR.defaultBlockState());
            }
        }
        // Lantern posts around the plaza and along the path.
        double[][] posts = {{-6, -1}, {6, -1}, {-5, -9}, {5, -10}, {4.2, -18}, {0.6, -30}, {-6, -42}, {-8, -60}};
        for (double[] p : posts) post(cell, (int) Math.round(p[0]), (int) Math.round(p[1]));
        // Campfire corner with log seats.
        cell.set(-5, BASE + 1, -5, Blocks.CAMPFIRE.defaultBlockState());
        for (int[] seat : new int[][]{{-7, -5}, {-3, -5}, {-5, -7}}) {
            if (inPlazaZone(seat[0], seat[1])) cell.set(seat[0], BASE + 1, seat[1], Blocks.OAK_FENCE.defaultBlockState());
        }
        pavilion(cell);
    }

    private static void post(Cell cell, int x, int z) {
        int y = cell.height(x, z);
        if (y <= WATER_LEVEL) return;
        if (inPlazaZone(x, z)) y = BASE;
        cell.set(x, y + 1, z, Blocks.OAK_FENCE.defaultBlockState());
        cell.set(x, y + 2, z, Blocks.OAK_FENCE.defaultBlockState());
        cell.set(x, y + 3, z, Blocks.LANTERN.defaultBlockState());
    }

    private static void pavilion(Cell cell) {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState edge = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState pillar = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        BlockState beam = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState();
        int ox = PAVILION_X, oz = PAVILION_Z + 27;
        for (int x = -7; x <= 7; x++) {
            for (int z = -35; z <= -19; z++) {
                int wx = ox + x, wz = oz + z;
                cell.set(wx, BASE, wz, Math.abs(x) == 7 || z == -35 || z == -19 ? edge : brick);
                for (int y = BASE + 1; y <= BASE + 11; y++) cell.set(wx, y, wz, Blocks.AIR.defaultBlockState());
            }
        }
        for (int y = 64; y <= 70; y++) {
            for (int[] c : new int[][]{{-6, -32}, {6, -32}, {-6, -22}, {6, -22}}) {
                cell.set(ox + c[0], y, oz + c[1], pillar);
            }
        }
        for (int x = -6; x <= 6; x++) {
            cell.set(ox + x, 70, oz - 32, beam);
            cell.set(ox + x, 70, oz - 22, beam);
        }
        for (int z = -32; z <= -22; z++) {
            cell.set(ox - 6, 70, oz + z, beam);
            cell.set(ox + 6, 70, oz + z, beam);
        }
        for (int x = -7; x <= 7; x++) {
            for (int z = -33; z <= -21; z++) {
                cell.set(ox + x, 71, oz + z, plank);
                if (Math.abs(x) == 7 || z == -33 || z == -21) cell.set(ox + x, 72, oz + z, slab);
            }
        }
        for (int x : new int[]{-5, 5}) {
            for (int z : new int[]{-31, -23}) cell.set(ox + x, 69, oz + z, Blocks.LANTERN.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ vegetation

    private static boolean freeForPlants(int x, int z) {
        boolean sightline = Math.abs(x) <= 13 && z >= -14 && z <= 26;
        return !sightline && !inPlazaZone(x, z) && !inPavilionZone(x, z) && pathDistance(x, z) > 2.6;
    }

    private static void vegetation(Cell cell) {
        int x0 = cell.chunkX * 16, z0 = cell.chunkZ * 16;
        // Trees, one jittered candidate per cell so spacing stays natural.
        for (int gx = Math.floorDiv(x0 - 6, CELL); gx <= Math.floorDiv(x0 + 21, CELL); gx++) {
            for (int gz = Math.floorDiv(z0 - 6, CELL); gz <= Math.floorDiv(z0 + 21, CELL); gz++) {
                int tx = gx * CELL + (int) (hash(gx, gz, 101) * CELL);
                int tz = gz * CELL + (int) (hash(gx, gz, 103) * CELL);
                int ground = cell.height(tx, tz);
                if (ground <= WATER_LEVEL + 1 || ground >= 88) continue;
                if (!freeForPlants(tx, tz)) continue;
                double forest = fbm(tx * 0.028, tz * 0.028, 53, 3);
                double chance = forest > 0.5 ? 0.9 : forest > 0.44 ? 0.35 : 0.07;
                if (hash(gx, gz, 107) > chance) continue;
                tree(cell, tx, ground + 1, tz, hash(gx, gz, 109), hash(gx, gz, 113));
            }
        }
        // Ground cover.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = x0 + x, wz = z0 + z;
                int ground = cell.height(wx, wz);
                if (ground <= WATER_LEVEL || ground >= 84 || !freeForPlants(wx, wz)) {
                    if (ground < WATER_LEVEL && ground >= WATER_LEVEL - 3 && hash(wx, wz, 131) < 0.04f) {
                        cell.set(wx, WATER_LEVEL + 1, wz, Blocks.LILY_PAD.defaultBlockState());
                    }
                    continue;
                }
                if (!cell.get(wx, ground + 1, wz).isAir() || !cell.get(wx, ground, wz).is(Blocks.GRASS_BLOCK)) continue;
                float r = hash(wx, wz, 127);
                double meadow = noise(wx * 0.09, wz * 0.09, 61);
                if (meadow > 0.66 && r < 0.38f) {
                    cell.set(wx, ground + 1, wz, flower(hash(wx, wz, 137), noise(wx * 0.05, wz * 0.05, 67)));
                } else if (r < 0.16f) {
                    cell.set(wx, ground + 1, wz, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (r < 0.19f && fbm(wx * 0.03, wz * 0.03, 53, 3) > 0.46) {
                    cell.set(wx, ground + 1, wz, Blocks.FERN.defaultBlockState());
                } else if (r < 0.196f) {
                    boulder(cell, wx, ground, wz);
                }
            }
        }
    }

    private static BlockState flower(float pick, double zone) {
        Block block;
        if (zone > 0.62) block = pick < 0.5f ? Blocks.CORNFLOWER : Blocks.AZURE_BLUET;
        else if (zone > 0.4) block = pick < 0.5f ? Blocks.POPPY : Blocks.OXEYE_DAISY;
        else block = pick < 0.6f ? Blocks.DANDELION : Blocks.OXEYE_DAISY;
        return block.defaultBlockState();
    }

    private static void boulder(Cell cell, int x, int ground, int z) {
        BlockState moss = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState rock = Blocks.COBBLESTONE.defaultBlockState();
        cell.set(x, ground + 1, z, moss);
        if (hash(x, z, 149) < 0.7f) cell.set(x + 1, ground + 1, z, rock);
        if (hash(x, z, 151) < 0.5f) cell.set(x, ground + 1, z + 1, rock);
        if (hash(x, z, 157) < 0.4f) cell.set(x, ground + 2, z, moss);
    }

    private static void tree(Cell cell, int x, int base, int z, float variety, float size) {
        int ground = base - 1;
        double lakeDistance = Math.hypot(x - LAKE_X, z - LAKE_Z);
        if (ground >= 74) {
            spruce(cell, x, base, z, size);
        } else if (lakeDistance < 46 && variety < 0.7f) {
            cherry(cell, x, base, z, size);
        } else if (variety < 0.62f) {
            oak(cell, x, base, z, size);
        } else {
            birch(cell, x, base, z, size);
        }
    }

    private static void blob(Cell cell, int cx, int cy, int cz, double rx, double ry, double rz, BlockState leaves, int seed) {
        int ix = (int) Math.ceil(rx), iy = (int) Math.ceil(ry), iz = (int) Math.ceil(rz);
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -iy; dy <= iy; dy++) {
                for (int dz = -iz; dz <= iz; dz++) {
                    double d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
                    if (d > 1.0) continue;
                    if (d > 0.72 && hash(cx + dx * 7 + dy, cz + dz * 5 - dy, seed) < 0.3f) continue;
                    cell.leaf(cx + dx, cy + dy, cz + dz, leaves);
                }
            }
        }
    }

    private static void oak(Cell cell, int x, int base, int z, float size) {
        int h = 5 + (int) (size * 3);
        double r = 2.8 + size * 1.0;
        blob(cell, x, base + h, z, r, 2.6, r, Blocks.OAK_LEAVES.defaultBlockState(), 171);
        blob(cell, x + 1, base + h + 2, z, r - 1.2, 1.6, r - 1.2, Blocks.OAK_LEAVES.defaultBlockState(), 173);
        for (int y = 0; y <= h + 1; y++) cell.set(x, base + y, z, Blocks.OAK_LOG.defaultBlockState());
    }

    private static void birch(Cell cell, int x, int base, int z, float size) {
        int h = 6 + (int) (size * 3);
        blob(cell, x, base + h, z, 2.3, 2.9, 2.3, Blocks.BIRCH_LEAVES.defaultBlockState(), 181);
        for (int y = 0; y <= h + 1; y++) cell.set(x, base + y, z, Blocks.BIRCH_LOG.defaultBlockState());
    }

    private static void cherry(Cell cell, int x, int base, int z, float size) {
        int h = 4 + (int) (size * 3);
        double r = 3.6 + size * 1.2;
        blob(cell, x, base + h + 1, z, r, 2.5, r, Blocks.CHERRY_LEAVES.defaultBlockState(), 191);
        blob(cell, x + 2, base + h + 2, z - 1, r - 1.3, 1.9, r - 1.3, Blocks.CHERRY_LEAVES.defaultBlockState(), 193);
        for (int y = 0; y <= h + 1; y++) cell.set(x, base + y, z, Blocks.CHERRY_LOG.defaultBlockState());
        // Fallen petals under the canopy.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 16 || hash(x + dx, z + dz, 197) > 0.22f) continue;
                int g = cell.height(x + dx, z + dz);
                if (g > WATER_LEVEL && cell.get(x + dx, g + 1, z + dz).isAir() && cell.get(x + dx, g, z + dz).is(Blocks.GRASS_BLOCK)) {
                    cell.set(x + dx, g + 1, z + dz, Blocks.PINK_PETALS.defaultBlockState());
                }
            }
        }
    }

    private static void spruce(Cell cell, int x, int base, int z, float size) {
        int h = 8 + (int) (size * 5);
        int[] radii = {0, 1, 2, 1, 2, 3, 2, 3};
        for (int y = 0; y < h; y++) cell.set(x, base + y, z, Blocks.SPRUCE_LOG.defaultBlockState());
        for (int i = 0; i < h - 1; i++) {
            int radius = radii[Math.min(radii.length - 1, i * radii.length / Math.max(1, h - 1))];
            int y = base + h - i;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius + 1) continue;
                    cell.leaf(x + dx, y, z + dz, Blocks.SPRUCE_LEAVES.defaultBlockState());
                }
            }
        }
    }
}

package name.vantaclient.client.util;

/** Deterministic, biome-aware placement policy for distant blocky tree impostors. */
public final class FarTerrainVegetation {
    private FarTerrainVegetation() {}

    public static boolean shouldPlaceTree(String biomePath, int worldX, int worldZ) {
        String biome = biomePath == null ? "" : biomePath;
        double density;
        if (biome.contains("ocean") || biome.contains("river") || biome.contains("beach")
                || biome.contains("desert") || biome.contains("badlands")
                || biome.contains("mushroom_fields") || biome.contains("barren")) {
            return false;
        } else if (biome.contains("dark_forest") || biome.contains("old_growth")
                || biome.contains("jungle") || biome.contains("taiga")
                || biome.contains("forest") || biome.contains("grove")
                || biome.contains("mangrove")) {
            density = 0.66;
        } else if (biome.contains("swamp") || biome.contains("wooded")) {
            density = 0.48;
        } else if (biome.contains("savanna") || biome.contains("meadow")
                || biome.contains("plains") || biome.contains("snowy_plains")) {
            density = 0.10;
        } else {
            density = 0.24;
        }
        return unitHash(worldX, worldZ) < density;
    }

    public static boolean isConifer(String biomePath) {
        String biome = biomePath == null ? "" : biomePath;
        return biome.contains("taiga") || biome.contains("grove")
                || biome.contains("snowy") || biome.contains("old_growth")
                || biome.contains("spruce");
    }

    public static int trunkColor(String biomePath) {
        String biome = biomePath == null ? "" : biomePath;
        if (biome.contains("birch")) return 0xFFE0D5AD;
        if (biome.contains("jungle") || biome.contains("mangrove")) return 0xFF705035;
        if (biome.contains("spruce") || biome.contains("taiga")) return 0xFF59412B;
        return 0xFF765238;
    }

    private static double unitHash(int x, int z) {
        long value = ((long) x * 0x9E3779B97F4A7C15L)
                ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}

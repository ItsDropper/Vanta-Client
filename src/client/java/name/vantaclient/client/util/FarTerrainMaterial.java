package name.vantaclient.client.util;

/**
 * Minecraft-like distant surface materials for FarView.
 *
 * The close world remains vanilla-rendered. This palette is only used by the
 * coarse far-terrain mesh, where biome identity and broad height bands matter
 * more than block-level texture detail.
 */
public final class FarTerrainMaterial {
    private FarTerrainMaterial() {}

    public static int colorFor(String biomePath, int height, int seaLevel,
                               int grassColor, int waterColor, int worldX, int worldZ) {
        String biome = biomePath == null ? "" : biomePath;
        int color;

        if (height <= seaLevel - 18) {
            color = blend(0xFF244B69, opaque(waterColor), 0.42f);
        } else if (height <= seaLevel - 5) {
            color = blend(0xFF3A7F9C, opaque(waterColor), 0.52f);
        } else if (height <= seaLevel + 2) {
            color = isBadlands(biome) ? 0xFFC18A55 : 0xFFC2B17B;
        } else if (isBadlands(biome)) {
            color = badlandsColor(height, worldX, worldZ);
        } else if (isDesert(biome)) {
            color = height > seaLevel + 52 ? 0xFFB99A68 : 0xFFD8C58A;
        } else if (isSnowy(biome) && height > seaLevel - 5) {
            color = height > seaLevel + 72 ? 0xFFE5E9ED : 0xFFD5DCE0;
        } else if (isMountainous(biome) && height > seaLevel + 58) {
            color = height > seaLevel + 112 ? 0xFFE1E4E2 : 0xFF888985;
        } else if (isSavanna(biome)) {
            color = blend(opaque(grassColor), 0xFFB7A36A, 0.24f);
        } else if (isSwamp(biome)) {
            color = blend(opaque(grassColor), 0xFF526D45, 0.30f);
        } else {
            color = opaque(grassColor);
        }

        return applySurfaceVariation(color, worldX, worldZ);
    }

    private static boolean isBadlands(String biome) {
        return biome.contains("badlands");
    }

    private static boolean isDesert(String biome) {
        return biome.contains("desert");
    }

    private static boolean isSnowy(String biome) {
        return biome.contains("snowy") || biome.contains("frozen")
                || biome.contains("ice_spikes") || biome.equals("grove")
                || biome.contains("jagged_peaks") || biome.contains("frozen_peaks");
    }

    private static boolean isMountainous(String biome) {
        return biome.contains("peaks") || biome.contains("stony")
                || biome.contains("windswept") || biome.contains("mountain");
    }

    private static boolean isSavanna(String biome) {
        return biome.contains("savanna");
    }

    private static boolean isSwamp(String biome) {
        return biome.contains("swamp") || biome.contains("mangrove");
    }

    private static int badlandsColor(int height, int x, int z) {
        int band = Math.floorMod(height / 5 + Math.floorDiv(x, 13)
                - Math.floorDiv(z, 17), 6);
        return switch (band) {
            case 0 -> 0xFFB85E3F;
            case 1 -> 0xFFD08A52;
            case 2 -> 0xFF9E503D;
            case 3 -> 0xFFE0B36C;
            case 4 -> 0xFFB96A49;
            default -> 0xFF8D493B;
        };
    }

    private static int applySurfaceVariation(int color, int x, int z) {
        long value = ((long) Math.floorDiv(x, 8) << 32)
                ^ (Math.floorDiv(z, 8) & 0xFFFFFFFFL) ^ 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        int offset = (int) (value & 7L) - 3;
        int red = clamp(((color >>> 16) & 0xFF) + offset);
        int green = clamp(((color >>> 8) & 0xFF) + offset);
        int blue = clamp((color & 0xFF) + offset);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static int blend(int first, int second, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int red = Math.round(((first >>> 16) & 0xFF) * (1.0f - t)
                + ((second >>> 16) & 0xFF) * t);
        int green = Math.round(((first >>> 8) & 0xFF) * (1.0f - t)
                + ((second >>> 8) & 0xFF) * t);
        int blue = Math.round((first & 0xFF) * (1.0f - t) + (second & 0xFF) * t);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}

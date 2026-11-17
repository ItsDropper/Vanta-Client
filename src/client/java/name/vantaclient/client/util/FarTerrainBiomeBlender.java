package name.vantaclient.client.util;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Smooths the procedural distant-terrain palette across the same 4-block biome
 * sampling grid used by Minecraft. The close chunk renderer remains authoritative.
 */
final class FarTerrainBiomeBlender {
    private static final int BIOME_BLEND_RADIUS = 1;

    private FarTerrainBiomeBlender() {}

    static SurfaceColors sample(BiomeSource source, Climate.Sampler sampler,
                                int worldX, int height, int worldZ, int seaLevel) {
        int centerQuartX = QuartPos.fromBlock(worldX);
        int quartY = QuartPos.fromBlock(height);
        int centerQuartZ = QuartPos.fromBlock(worldZ);
        double totalWeight = 0.0;
        double terrainRed = 0.0;
        double terrainGreen = 0.0;
        double terrainBlue = 0.0;
        double waterRed = 0.0;
        double waterGreen = 0.0;
        double waterBlue = 0.0;

        for (int dz = -BIOME_BLEND_RADIUS; dz <= BIOME_BLEND_RADIUS; dz++) {
            for (int dx = -BIOME_BLEND_RADIUS; dx <= BIOME_BLEND_RADIUS; dx++) {
                int quartX = centerQuartX + dx;
                int quartZ = centerQuartZ + dz;
                int sampleX = QuartPos.toBlock(quartX);
                int sampleZ = QuartPos.toBlock(quartZ);
                Holder<Biome> holder = source.getNoiseBiome(quartX, quartY, quartZ, sampler);
                Biome biome = holder.value();
                String biomePath = holder.unwrapKey()
                        .map(key -> key.identifier().getPath()).orElse("");
                int terrain = FarTerrainMaterial.colorFor(
                        biomePath, height, seaLevel,
                        biome.getGrassColor(sampleX, sampleZ), biome.getWaterColor(),
                        sampleX, sampleZ);
                int water = biome.getWaterColor();
                double weight = 1.0 / (dx * dx + dz * dz + 0.2);
                totalWeight += weight;
                terrainRed += ((terrain >>> 16) & 0xFF) * weight;
                terrainGreen += ((terrain >>> 8) & 0xFF) * weight;
                terrainBlue += (terrain & 0xFF) * weight;
                waterRed += ((water >>> 16) & 0xFF) * weight;
                waterGreen += ((water >>> 8) & 0xFF) * weight;
                waterBlue += (water & 0xFF) * weight;
            }
        }

        int terrain = packColor(terrainRed, terrainGreen, terrainBlue, totalWeight);
        int water = packColor(waterRed, waterGreen, waterBlue, totalWeight);
        return new SurfaceColors(terrain, blend(0xFF28566D, water, 0.58));
    }

    static int blendColors(int[] colors, double[] weights) {
        if (colors == null || weights == null || colors.length == 0
                || colors.length != weights.length) {
            throw new IllegalArgumentException("Biome colors and weights must be non-empty and have equal lengths.");
        }

        double totalWeight = 0.0;
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        for (int i = 0; i < colors.length; i++) {
            double weight = weights[i];
            if (!Double.isFinite(weight) || weight <= 0.0) continue;
            int color = colors[i];
            totalWeight += weight;
            red += ((color >>> 16) & 0xFF) * weight;
            green += ((color >>> 8) & 0xFF) * weight;
            blue += (color & 0xFF) * weight;
        }
        if (totalWeight <= 0.0) return opaqueRgb(colors[0]);
        return 0xFF000000
                | (clamp((int) Math.round(red / totalWeight)) << 16)
                | (clamp((int) Math.round(green / totalWeight)) << 8)
                | clamp((int) Math.round(blue / totalWeight));
    }

    private static int packColor(double red, double green, double blue, double totalWeight) {
        if (totalWeight <= 0.0) return 0xFF000000;
        return 0xFF000000
                | (clamp((int) Math.round(red / totalWeight)) << 16)
                | (clamp((int) Math.round(green / totalWeight)) << 8)
                | clamp((int) Math.round(blue / totalWeight));
    }

    private static int blend(int first, int second, double amount) {
        double t = Math.max(0.0, Math.min(1.0, amount));
        int red = (int) Math.round(((first >>> 16) & 0xFF) * (1.0 - t)
                + ((second >>> 16) & 0xFF) * t);
        int green = (int) Math.round(((first >>> 8) & 0xFF) * (1.0 - t)
                + ((second >>> 8) & 0xFF) * t);
        int blue = (int) Math.round((first & 0xFF) * (1.0 - t)
                + (second & 0xFF) * t);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int opaqueRgb(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }

    record SurfaceColors(int terrainColor, int waterColor) {}
}

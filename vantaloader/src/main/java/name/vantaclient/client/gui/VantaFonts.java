package name.vantaclient.client.gui;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks the Poppins raster density that matches how big text really lands on screen.
 *
 * <p>Minecraft samples font atlases without filtering, so a glyph rasterised at 4x and drawn at
 * 1x is decimated into blocky, shimmering strokes. Each Vanta font therefore has variants
 * ({@code name_x1 .. name_x6}) rasterised at different densities; the closest one to the number of
 * device pixels per font unit is used so glyphs map close to 1:1 onto the screen.
 */
public final class VantaFonts {
    private static final int[] DENSITIES = {1, 2, 3, 4, 6};
    private static final Map<Identifier, Identifier[]> VARIANTS = new ConcurrentHashMap<>();

    private VantaFonts() {}

    /** Index into {@link #DENSITIES} best suited to {@code devicePixelsPerUnit}. */
    public static int densityIndex(float devicePixelsPerUnit) {
        float wanted = Float.isFinite(devicePixelsPerUnit) ? devicePixelsPerUnit : 1.0f;
        for (int i = 0; i < DENSITIES.length; i++) {
            if (DENSITIES[i] >= wanted * 0.92f) return i;
        }
        return DENSITIES.length - 1;
    }

    /** Variant of a {@code vanta:} font for the given screen density; other fonts pass through. */
    public static Identifier variant(Identifier base, float devicePixelsPerUnit) {
        if (base == null || !"vanta".equals(base.getNamespace())) return base;
        if (base.getPath().contains("_x")) return base;
        int index = densityIndex(devicePixelsPerUnit);
        Identifier[] list = VARIANTS.computeIfAbsent(base, id -> {
            Identifier[] created = new Identifier[DENSITIES.length];
            for (int i = 0; i < created.length; i++) {
                created[i] = Identifier.fromNamespaceAndPath("vanta", id.getPath() + "_x" + DENSITIES[i]);
            }
            return created;
        });
        return list[index];
    }
}

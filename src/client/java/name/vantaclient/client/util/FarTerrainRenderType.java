package name.vantaclient.client.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Opaque terrain pipeline. Unlike the vanilla debug-quad pipeline, this writes
 * depth, so distant terrain correctly occludes later entity rendering.
 */
public final class FarTerrainRenderType {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(
                            "dropperutils", "pipeline/far_terrain"))
                    .withoutBlend()
                    .withDepthWrite(true)
                    .withCull(false)
                    .build()
    );

    public static final RenderType TYPE = RenderType.create(
            "dropperutils_far_terrain",
            RenderSetup.builder(PIPELINE)
                    .bufferSize(RenderType.BIG_BUFFER_SIZE)
                    .createRenderSetup()
    );

    private FarTerrainRenderType() {}

    public static void initialize() {
        // Calling this forces pipeline registration before the first world draw.
    }
}

package name.vantaclient.mixin;

import name.vantaclient.client.feature.GroundFireHeightFeature;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adjusts only vanilla ground-fire model geometry. The first-person fire overlay
 * is rendered by a separate path and is intentionally unaffected.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class GroundFireHeightMixin {
    @ModifyVariable(
            method = "putQuadData",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private BakedQuad vanta$scaleGroundFireQuad(BakedQuad quad) {
        if (!GroundFireHeightFeature.INSTANCE.isEnabled()) return quad;

        float scale = GroundFireHeightFeature.getHeightScale();
        if (Math.abs(scale - 1.0f) < 0.0001f || !isFireSprite(quad.sprite())) return quad;

        return new BakedQuad(
                scalePosition(quad.position0(), scale),
                scalePosition(quad.position1(), scale),
                scalePosition(quad.position2(), scale),
                scalePosition(quad.position3(), scale),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                quad.tintIndex(), quad.direction(), quad.sprite(), quad.shade(), quad.lightEmission());
    }

    private static boolean isFireSprite(TextureAtlasSprite sprite) {
        Identifier id = sprite.contents().name();
        if (!"minecraft".equals(id.getNamespace())) return false;

        String path = id.getPath();
        return path.equals("block/fire_0") || path.equals("block/fire_1")
                || path.equals("block/soul_fire_0") || path.equals("block/soul_fire_1");
    }

    private static Vector3f scalePosition(Vector3fc position, float scale) {
        return new Vector3f(position.x(), position.y() * scale, position.z());
    }
}

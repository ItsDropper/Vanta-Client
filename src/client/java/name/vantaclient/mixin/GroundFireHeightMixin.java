package name.vantaclient.mixin;

import name.vantaclient.client.feature.GroundFireHeightFeature;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.GeometryBakedModel;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Scales ground-fire block-model geometry vertically, leaving UVs untouched so the
 * vanilla flame texture animation continues to run normally.
 */
@Mixin(GeometryBakedModel.class)
public abstract class GroundFireHeightMixin {
    @Inject(method = "getQuads", at = @At("RETURN"), cancellable = true)
    private void vanta$scaleGroundFireQuads(
            net.minecraft.core.Direction side,
            CallbackInfoReturnable<List<BakedQuad>> cir) {
        if (!GroundFireHeightFeature.INSTANCE.isEnabled()) return;

        float scale = GroundFireHeightFeature.getHeightScale();
        if (Math.abs(scale - 1.0f) < 0.0001f) return;

        List<BakedQuad> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        List<BakedQuad> transformed = null;
        for (int i = 0; i < original.size(); i++) {
            BakedQuad quad = original.get(i);
            if (!isFireSprite(quad.sprite())) continue;
            if (transformed == null) transformed = new ArrayList<>(original);
            transformed.set(i, scaleQuad(quad, scale));
        }
        if (transformed != null) cir.setReturnValue(transformed);
    }

    private static boolean isFireSprite(TextureAtlasSprite sprite) {
        Identifier id = sprite.contents().getId();
        if (!"minecraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return path.equals("block/fire_0") || path.equals("block/fire_1")
                || path.equals("block/soul_fire_0") || path.equals("block/soul_fire_1");
    }

    private static BakedQuad scaleQuad(BakedQuad quad, float scale) {
        return new BakedQuad(
                scalePosition(quad.position0(), scale),
                scalePosition(quad.position1(), scale),
                scalePosition(quad.position2(), scale),
                scalePosition(quad.position3(), scale),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                quad.tintIndex(), quad.face(), quad.sprite(), quad.shade(), quad.lightEmission());
    }

    private static Vector3f scalePosition(org.joml.Vector3fc position, float scale) {
        return new Vector3f(position.x(), position.y() * scale, position.z());
    }
}

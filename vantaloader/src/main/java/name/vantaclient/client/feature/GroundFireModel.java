package name.vantaclient.client.feature;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Predicate;

/**
 * Applies the ground fire height through Fabric's rendering API.
 *
 * Fabric's Indigo renderer (and Sodium) mesh chunk blocks through {@link BlockStateModel#emitQuads}
 * and never call vanilla's ModelBlockRenderer, so geometry has to be changed on the model itself.
 * Every fire and soul fire block state model is wrapped; the wrapper reads the current scale on every
 * emit, so the setting takes effect as soon as the affected chunk sections are rebuilt.
 */
public final class GroundFireModel implements BlockStateModel {
    /** Counts emits that were actually scaled; read by the in-game test. */
    public static final java.util.concurrent.atomic.AtomicInteger EMITS = new java.util.concurrent.atomic.AtomicInteger();

    private final BlockStateModel delegate;

    private GroundFireModel(BlockStateModel delegate) {
        this.delegate = delegate;
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, modelContext) -> {
            BlockState state = modelContext.state();
            return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) ? new GroundFireModel(model) : model;
        }));
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        float scale = GroundFireHeightFeature.isActive() ? GroundFireHeightFeature.getHeightScale() : 1.0f;
        if (!Float.isFinite(scale) || Math.abs(scale - 1.0f) <= 0.0001f) {
            delegate.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        QuadTransform transform = quad -> {
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.pos(vertex, quad.x(vertex), quad.y(vertex) * scale, quad.z(vertex));
            }
            return true;
        };
        EMITS.incrementAndGet();
        emitter.pushTransform(transform);
        try {
            delegate.emitQuads(emitter, level, pos, state, random, cullTest);
        } finally {
            emitter.popTransform();
        }
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        // The scale is part of the geometry, so the delegate's cache key cannot be reused as is.
        return null;
    }

    @Override
    public TextureAtlasSprite particleSprite(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return delegate.particleSprite(level, pos, state);
    }

    @Override
    public TextureAtlasSprite particleIcon() {
        return delegate.particleIcon();
    }

    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> output) {
        delegate.collectParts(random, output);
    }
}

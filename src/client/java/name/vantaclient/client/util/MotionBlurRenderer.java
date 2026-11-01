package name.vantaclient.client.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.mixin.PostChainAccessor;
import name.vantaclient.mixin.PostPassAccessor;
import name.vantaclient.mixin.ShaderManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Temporal frame-blending motion blur implemented as a native Minecraft post-chain.
 *
 * The effect runs against Minecraft's main render target at the end of the world pass,
 * before HUD rendering, rather than copying whichever raw OpenGL framebuffer happens
 * to be bound. Persistent post-chain targets retain the previous frame.
 */
public final class MotionBlurRenderer {
    private static final Identifier EFFECT_ID =
            Identifier.fromNamespaceAndPath("dropperutils", "motion_blur");
    private static final String UNIFORM_BLOCK = "MotionBlurUniforms";
    private static final int UBO_SIZE = 16;
    private static final int UBO_USAGE = 130; // UNIFORM | MAP_WRITE

    private static PostChain cachedChain;
    private static GpuBuffer blendFactorUbo;
    private static Method createBufferMethod;
    private static boolean loadFailed;
    private static boolean runtimeFailed;
    private static boolean historyPrimed;
    private static long previousFrameNanos;
    private static Object previousLevel;
    private static int previousWidth = -1;
    private static int previousHeight = -1;

    private MotionBlurRenderer() {}

    public static void reset() {
        historyPrimed = false;
        previousFrameNanos = 0L;
        previousLevel = null;
        previousWidth = -1;
        previousHeight = -1;
        loadFailed = false;
        runtimeFailed = false;
    }

    public static void render(GraphicsResourceAllocator allocator) {
        if (runtimeFailed) return;

        Minecraft client = Minecraft.getInstance();
        DropperUtilsConfig config = DropperUtilsConfig.get();
        if (!config.motionBlur || client.player == null || client.level == null || client.screen != null) {
            reset();
            return;
        }

        float retention = clamp(config.motionBlurStrength, 0.0f, 0.95f);
        if (retention <= 0.0f) {
            reset();
            return;
        }

        PostChain chain = loadChain(client);
        if (chain == null) return;

        try {
            installUniformBuffer(chain);

            var mainTarget = client.getMainRenderTarget();
            int width = mainTarget.width;
            int height = mainTarget.height;
            if (width <= 0 || height <= 0) {
                reset();
                return;
            }

            if (previousLevel != client.level || previousWidth != width || previousHeight != height) {
                historyPrimed = false;
                previousFrameNanos = 0L;
                previousLevel = client.level;
                previousWidth = width;
                previousHeight = height;
            }

            long now = System.nanoTime();
            float frameRetention = 0.0f;
            if (historyPrimed && previousFrameNanos != 0L) {
                double elapsed = (now - previousFrameNanos) / 1_000_000_000.0;
                if (elapsed > 0.0 && elapsed <= 0.25) {
                    // Keep the same temporal response at 60, 144, and 300+ FPS.
                    frameRetention = (float) Math.pow(retention, elapsed * 60.0);
                } else {
                    historyPrimed = false;
                }
            }
            previousFrameNanos = now;

            writeBlend(frameRetention, clamp(config.motionBlurIntensity, 0.0f, 1.0f));
            chain.process(mainTarget, allocator);
            historyPrimed = true;
        } catch (Throwable error) {
            runtimeFailed = true;
            System.err.println("[Vanta Client] Motion blur post-processing failed; disabled until toggled again.");
            error.printStackTrace();
        }
    }

    private static PostChain loadChain(Minecraft client) {
        if (loadFailed) return null;
        try {
            ShaderManager.CompilationCache cache =
                    ((ShaderManagerAccessor) client.getShaderManager()).dropperutils$getCompilationCache();
            if (cache == null) return null;

            PostChain chain = cache.getOrLoadPostChain(EFFECT_ID, LevelTargetBundle.MAIN_TARGETS);
            if (chain != cachedChain) {
                cachedChain = chain;
                blendFactorUbo = null;
                historyPrimed = false;
            }
            return cachedChain;
        } catch (Throwable error) {
            loadFailed = true;
            System.err.println("[Vanta Client] Failed to load motion blur post-effect: " + error.getMessage());
            return null;
        }
    }

    private static void installUniformBuffer(PostChain chain) {
        if (blendFactorUbo == null) {
            blendFactorUbo = createUniformBuffer();
            if (blendFactorUbo == null) {
                throw new IllegalStateException("Could not allocate motion blur uniform buffer");
            }
        }

        List<PostPass> passes = ((PostChainAccessor) chain).dropperutils$getPasses();
        if (passes.isEmpty()) {
            throw new IllegalStateException("Motion blur post-chain contains no passes");
        }

        Map<String, GpuBuffer> uniforms =
                ((PostPassAccessor) passes.get(0)).dropperutils$getCustomUniforms();
        if (uniforms.get(UNIFORM_BLOCK) == blendFactorUbo) return;

        GpuBuffer displaced = uniforms.put(UNIFORM_BLOCK, blendFactorUbo);
        if (displaced != null && displaced != blendFactorUbo) {
            displaced.close();
        }
    }

    private static GpuBuffer createUniformBuffer() {
        Object device = RenderSystem.getDevice();
        Supplier<String> label = () -> "dropperutils:motion_blur_uniforms";
        try {
            if (createBufferMethod == null) {
                createBufferMethod = device.getClass().getMethod(
                        "createBuffer", Supplier.class, int.class, long.class);
            }
            return (GpuBuffer) createBufferMethod.invoke(device, label, UBO_USAGE, (long) UBO_SIZE);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Motion blur uniform-buffer allocation failed", error);
        }
    }

    private static void writeBlend(float retention, float intensity) {
        if (blendFactorUbo == null) return;
        try (GpuBuffer.MappedView view =
                     RenderSystem.getDevice().createCommandEncoder().mapBuffer(blendFactorUbo, false, true)) {
            Std140Builder.intoBuffer(view.data()).putVec4(retention, intensity, 0.0f, 0.0f);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

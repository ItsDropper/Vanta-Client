package name.vantaclient.client.util;

import name.vantaclient.client.platform.ClientPlatform;

import java.lang.reflect.Field;

/**
 * Optional, reflection-only compatibility with Nvidium.
 *
 * Nvidium replaces Sodium's vanilla chunk terrain renderer. Its public API
 * exposes section visibility and region transforms, but not a custom mesh
 * submission interface for third-party LOD geometry. FarView therefore keeps
 * its independent Minecraft render pass while allowing Nvidium to render and
 * GPU-cull ordinary Sodium chunk terrain.
 *
 * There is deliberately no compile-time or runtime dependency on Nvidium.
 */
public final class NvidiumCompatibility {
    private static final String MOD_ID = "nvidium";
    private static final String RENDERER_CLASS = "me.cortex.nvidium.Nvidium";
    private static final String ENABLED_FIELD = "IS_ENABLED";

    private NvidiumCompatibility() {}

    public static boolean isInstalled() {
        return ClientPlatform.isModLoaded(MOD_ID);
    }

    /**
     * Returns true only when Nvidium's own capability checks have enabled its
     * accelerated renderer. Missing/changed internals fail closed.
     */
    public static boolean isRendererActive() {
        if (!isInstalled()) return false;
        try {
            ClassLoader loader = NvidiumCompatibility.class.getClassLoader();
            Class<?> renderer = Class.forName(RENDERER_CLASS, true, loader);
            Field enabled = renderer.getField(ENABLED_FIELD);
            return enabled.getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static String getStatusLine() {
        if (!isInstalled()) {
            return "FarView uses its native terrain pass; Nvidium is optional and not installed";
        }
        if (isRendererActive()) {
            return "Nvidium active for Sodium chunks; FarView LOD uses its independent render pass";
        }
        return "Nvidium detected but its accelerated renderer is inactive; FarView remains independent";
    }
}

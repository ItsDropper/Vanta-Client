package name.vantaclient.api;

/**
 * Implement this interface to contribute native settings to Vanta Client.
 *
 * Register the implementation as a Fabric entrypoint named {@code vanta} in
 * the integrating mod's fabric.mod.json. Vanta discovers these entrypoints
 * after normal client initialization, so the returned descriptor can bind
 * directly to the mod's initialized configuration model.
 */
@FunctionalInterface
public interface VantaClientIntegration {
    /**
     * Creates the settings descriptor for the declaring Fabric mod.
     * The descriptor ID must exactly match that mod's Fabric mod ID.
     */
    ModDescriptor createDescriptor();
}

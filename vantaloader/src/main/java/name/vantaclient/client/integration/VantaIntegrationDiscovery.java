package name.vantaclient.client.integration;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.api.VantaClientIntegration;
import net.fabricmc.loader.api.FabricLoader;

/** Discovers native Vanta settings providers declared by installed Fabric mods. */
public final class VantaIntegrationDiscovery {
    private static boolean discovered;

    private VantaIntegrationDiscovery() {}

    /**
     * Runs once after client entrypoints have initialized. Providers are isolated:
     * one broken integration must not prevent other mods from contributing settings.
     */
    public static synchronized void discover() {
        if (discovered) return;
        discovered = true;

        for (var container : FabricLoader.getInstance()
                .getEntrypointContainers("vanta", VantaClientIntegration.class)) {
            String modId = container.getProvider().getMetadata().getId();
            if (modId.equals("dropperutils")) continue;
            try {
                VantaClientIntegration integration = container.getEntrypoint();
                ModDescriptor descriptor = integration.createDescriptor();
                if (descriptor == null) {
                    throw new IllegalStateException("Integration returned a null descriptor");
                }
                if (!modId.equals(descriptor.id())) {
                    throw new IllegalArgumentException("Descriptor id '" + descriptor.id()
                            + "' does not match declaring mod id '" + modId + "'");
                }
                VantaClientApi.registerMod(descriptor);
            } catch (RuntimeException | LinkageError exception) {
                System.err.println("[Vanta Client] Integration discovery failed for "
                        + modId + ": " + safeMessage(exception));
            }
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}

package name.vantaclient.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

/**
 * First-stage world sharing through Minecraft's existing integrated-server LAN support.
 *
 * This deliberately does not implement Internet invitations or expose a public address.
 * Remote peer hosting requires a separate authenticated signalling and transport layer.
 */
public final class VantaLanHostingService {
    public enum State {
        NO_WORLD,
        NOT_HOSTED,
        HOSTED,
        FAILED
    }

    public record Status(State state, int port) {}

    private VantaLanHostingService() {}

    public static Status status() {
        MinecraftServer server = currentServer();
        if (server == null) return new Status(State.NO_WORLD, -1);
        if (!server.isPublished()) return new Status(State.NOT_HOSTED, -1);
        return new Status(State.HOSTED, server.getPort());
    }

    /**
     * Opens the active singleplayer world to the local network on an ephemeral port.
     * This method must be called from the client thread, as it delegates to Minecraft's
     * integrated-server API.
     */
    public static Status start(GameType gameType, boolean allowCommands) {
        MinecraftServer server = currentServer();
        if (server == null) return new Status(State.NO_WORLD, -1);
        if (server.isPublished()) return new Status(State.HOSTED, server.getPort());
        if (gameType == null) return new Status(State.FAILED, -1);

        try {
            if (!server.publishServer(gameType, allowCommands, 0) || !server.isPublished()) {
                return new Status(State.FAILED, -1);
            }
            int port = server.getPort();
            return port > 0 && port <= 65_535
                    ? new Status(State.HOSTED, port)
                    : new Status(State.FAILED, -1);
        } catch (RuntimeException exception) {
            System.err.println("[Vanta Client] Could not open the integrated server to LAN: "
                    + exception.getMessage());
            return new Status(State.FAILED, -1);
        }
    }

    private static MinecraftServer currentServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getSingleplayerServer();
    }
}

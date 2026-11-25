package name.vantaclient.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

/**
 * Publishes the active singleplayer world through Minecraft's integrated-server LAN support.
 */
public final class VantaLanHostingService {
    public enum State {
        NO_WORLD,
        NOT_HOSTED,
        HOSTED,
        FAILED
    }

    public record Status(State state, int port, String detail) {
        public Status(State state, int port) {
            this(state, port, "");
        }
    }

    private VantaLanHostingService() {}

    public static Status status() {
        MinecraftServer server = currentServer();
        if (server == null) return new Status(State.NO_WORLD, -1);
        return publishedStatus(server);
    }

    /**
     * Opens the active singleplayer world to the local network on an ephemeral port.
     * This method must be called from the client thread, as it delegates to Minecraft's
     * integrated-server API.
     */
    public static Status start(GameType gameType, boolean allowCommands) {
        MinecraftServer server = currentServer();
        if (server == null) return new Status(State.NO_WORLD, -1, "Open a singleplayer world first.");
        if (server.isPublished()) return publishedStatus(server);
        if (gameType == null) {
            return new Status(State.FAILED, -1, "Select a valid game mode before opening the world to LAN.");
        }

        try {
            if (!server.publishServer(gameType, allowCommands, 0)) {
                return new Status(State.FAILED, -1,
                        "Minecraft refused to publish the integrated server to LAN.");
            }
            if (!server.isPublished()) {
                return new Status(State.FAILED, -1,
                        "Minecraft reported that LAN publishing succeeded, but the integrated server is not published.");
            }
            return publishedStatus(server);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            System.err.println("[Vanta Client] Could not open the integrated server to LAN: " + message);
            return new Status(State.FAILED, -1,
                    "Could not open the integrated server to LAN"
                            + (message == null || message.isBlank() ? "." : ": " + message));
        }
    }

    private static Status publishedStatus(MinecraftServer server) {
        if (!server.isPublished()) {
            return new Status(State.NOT_HOSTED, -1, "The integrated server is not open to LAN.");
        }

        try {
            int port = server.getPort();
            if (port < 1 || port > 65_535) {
                return new Status(State.FAILED, -1,
                        "Minecraft reports the world as open to LAN, but returned an invalid LAN port ("
                                + port + "). Close and reopen the world to LAN, then try again.");
            }
            return new Status(State.HOSTED, port, "LAN hosting is active on port " + port + ".");
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return new Status(State.FAILED, -1,
                    "Minecraft reports the world as open to LAN, but its LAN port could not be read"
                            + (message == null || message.isBlank() ? "." : ": " + message));
        }
    }

    private static MinecraftServer currentServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getSingleplayerServer();
    }
}

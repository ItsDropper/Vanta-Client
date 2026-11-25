package name.vantaclient.client.init;

import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.EventBus;
import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.screens.TitleScreen;
import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import name.vantaclient.client.init.Keybinds;
import name.vantaclient.client.init.FeatureKeybinds;

/** VantaLoader-native event integration; intentionally has no Fabric API linkage. */
public final class VantaLoaderEvents {
    private static final Set<UUID> HOST_PLAYERS = new HashSet<>();
    private static volatile MinecraftServer trackedServer;
    private static boolean remoteGuestConnected;

    private VantaLoaderEvents() {}

    public static void register(EventBus eventBus) {
        ClientBootstrap.initializeFeatures();
        eventBus.subscribe(ClientTickEvent.class, 0, event -> {
            if (event.phase() == ClientTickEvent.Phase.START) {
                if (!Keybinds.isNativeRegistered()) {
                    Keybinds.registerNative();
                    FeatureKeybinds.registerNative();
                }
                ClientPerformanceMetrics.beginClientTick();
                return;
            }
            Minecraft client = (Minecraft) event.client();
            updateRemoteConnectionLifecycle(client);
            if (client.screen instanceof TitleScreen && client.level == null) {
                VantaLobbyWorld.start();
                VantaLobbyWorld.tick();
                name.vantaclient.client.gui.title.VantaTitleUi.tick();
            }
            ClientTickHandler.tick(client);
            VantaRemoteSessionManager.tick(client);
            ClientPerformanceMetrics.endClientTick();
        });
    }

    /**
     * Replaces Fabric's play-connection callbacks using vanilla server/client state.
     * Server player inspection is scheduled on the integrated-server thread; the
     * guest transition is observed on the client tick thread.
     */
    private static void updateRemoteConnectionLifecycle(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server != trackedServer) {
            trackedServer = server;
            synchronized (HOST_PLAYERS) {
                HOST_PLAYERS.clear();
            }
        }

        if (server != null) {
            server.execute(() -> {
                if (server != trackedServer) return;
                Set<UUID> present = new HashSet<>();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    UUID id = player.getUUID();
                    present.add(id);
                    boolean joined;
                    synchronized (HOST_PLAYERS) {
                        joined = HOST_PLAYERS.add(id);
                    }
                    if (joined) {
                        VantaRemoteSessionManager.onHostPlayerJoined(player.connection, server);
                    }
                }
                synchronized (HOST_PLAYERS) {
                    HOST_PLAYERS.retainAll(present);
                }
            });
        }

        boolean connectedToRemoteWorld = server == null
                && client.player != null
                && client.getConnection() != null;
        if (connectedToRemoteWorld != remoteGuestConnected) {
            remoteGuestConnected = connectedToRemoteWorld;
            if (connectedToRemoteWorld) {
                VantaRemoteSessionManager.onGuestJoined();
            } else {
                VantaRemoteSessionManager.onGuestDisconnected();
            }
        }
    }
}

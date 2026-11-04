package name.vantaclient.client.integration.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns opt-in remote world sessions independently of the UI screen.
 *
 * The integrated server remains authoritative. Guests connect through a loopback
 * TCP bridge and then use Minecraft's ordinary online-mode authentication flow.
 * Relay credentials are memory-only and are never written to client preferences.
 */
public final class VantaRemoteSessionManager {
    public record PendingGuest(String requestId, String playerName, boolean approving) {}

    public record Snapshot(
            String hostStatus,
            String roomCode,
            Instant roomExpiresAt,
            boolean hostBusy,
            boolean hostActive,
            List<PendingGuest> pendingGuests,
            int connectedGuests,
            String guestStatus,
            boolean guestBusy,
            boolean guestActive) {}

    private static final Object LOCK = new Object();
    private static final ExecutorService IO = Executors.newFixedThreadPool(12, task -> {
        Thread thread = new Thread(task, "Vanta remote session I/O");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, PendingGuest> PENDING = new LinkedHashMap<>();
    private static final Map<String, TcpStreamTunnel> HOST_TUNNELS = new LinkedHashMap<>();

    private static long revision;
    private static long hostGeneration;
    private static long guestGeneration;
    private static boolean hostBusy;
    private static boolean guestBusy;
    private static boolean shuttingDown;
    private static String hostStatus = "No remote host session is running.";
    private static String guestStatus = "Enter your relay address and invite code to join.";
    private static HostSession hostSession;
    private static MinecraftServer hostedServer;
    private static GuestSession guestSession;
    private static VantaRelayClient.GuestDecisionConnection pendingGuestDecision;
    private static boolean guestWorldJoined;
    private static long guestConnectDeadlineNanos;

    private VantaRemoteSessionManager() {}

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            HostSession host = hostSession;
            return new Snapshot(
                    hostStatus,
                    host == null ? "" : host.room.code(),
                    host == null ? null : host.room.expiresAt(),
                    hostBusy,
                    host != null,
                    List.copyOf(PENDING.values()),
                    HOST_TUNNELS.size(),
                    guestStatus,
                    guestBusy,
                    guestSession != null);
        }
    }

    public static long revision() {
        synchronized (LOCK) {
            return revision;
        }
    }

    /** Creates a short-lived room and authenticated host control socket. */
    public static void startHosting(String endpoint, MinecraftServer server, int serverPort) {
        final String normalizedEndpoint;
        try {
            normalizedEndpoint = VantaRelayClient.normalizeBaseUrl(endpoint);
        } catch (IllegalArgumentException exception) {
            updateHostStatus(exception.getMessage());
            return;
        }
        if (server == null || serverPort < 1 || serverPort > 65_535) {
            updateHostStatus("Open the integrated server to LAN before starting remote hosting.");
            return;
        }

        final long generation;
        synchronized (LOCK) {
            if (shuttingDown || hostBusy || hostSession != null) return;
            if (guestBusy || guestSession != null) {
                hostStatus = "Disconnect the active remote guest session before hosting a world.";
                bumpRevisionLocked();
                return;
            }
            generation = ++hostGeneration;
            hostBusy = true;
            hostedServer = server;
            hostStatus = "Creating a private invite room…";
            PENDING.clear();
            HOST_TUNNELS.clear();
            bumpRevisionLocked();
        }

        executeIo(() -> {
            VantaRelayClient.HostControlConnection control = null;
            try {
                VantaRelayClient relay = new VantaRelayClient(normalizedEndpoint);
                VantaRelayClient.Room room = relay.createRoom();
                VantaRelayClient.HostControlConnection opened = relay.connectHostControl(
                        room,
                        event -> onHostEvent(generation, event),
                        error -> onHostControlError(generation, error));
                control = opened;

                HostSession created = new HostSession(relay, room, opened, serverPort, server);
                boolean accepted;
                synchronized (LOCK) {
                    accepted = generation == hostGeneration && hostBusy && !shuttingDown;
                    if (accepted) {
                        hostSession = created;
                        hostBusy = false;
                        hostStatus = "Invite ready. Every guest must be approved by you.";
                        bumpRevisionLocked();
                    }
                }
                if (!accepted) {
                    opened.close();
                } else if (!opened.isOpen()) {
                    onHostControlError(generation,
                            new IOException("Relay host control closed during session startup."));
                }
            } catch (Exception exception) {
                if (control != null) control.close();
                synchronized (LOCK) {
                    if (generation == hostGeneration) {
                        hostBusy = false;
                        hostSession = null;
                        hostedServer = null;
                        hostStatus = safeMessage(exception, "Could not start remote hosting.");
                        bumpRevisionLocked();
                    }
                }
            }
        });
    }

    public static void approveGuest(String requestId) {
        final HostSession session;
        final long generation;
        synchronized (LOCK) {
            session = hostSession;
            PendingGuest pending = PENDING.get(requestId);
            if (session == null || pending == null || pending.approving()) return;
            PENDING.put(requestId, new PendingGuest(
                    pending.requestId(), pending.playerName(), true));
            generation = hostGeneration;
            hostStatus = "Approving " + pending.playerName() + " and opening their stream…";
            bumpRevisionLocked();
        }

        executeIo(() -> {
            TcpStreamTunnel tunnel = null;
            try {
                session.control.approve(requestId);
                WebSocketDuplexConnection remote =
                        session.relay.connectHostData(session.room, requestId);
                tunnel = TcpStreamTunnel.connectToLoopbackServer(
                        session.serverPort, remote,
                        () -> onHostTunnelClosed(generation, requestId));

                synchronized (LOCK) {
                    if (generation != hostGeneration || hostSession != session) {
                        tunnel.close();
                        return;
                    }
                    HOST_TUNNELS.put(requestId, tunnel);
                    PENDING.remove(requestId);
                    if (tunnel.isClosed()) HOST_TUNNELS.remove(requestId);
                    hostStatus = "The approved guest stream is active.";
                    bumpRevisionLocked();
                }
            } catch (Exception exception) {
                if (tunnel != null) tunnel.close();
                synchronized (LOCK) {
                    if (generation == hostGeneration && hostSession == session) {
                        PENDING.remove(requestId);
                        hostStatus = "Could not open the guest stream: "
                                + safeMessage(exception, "relay connection failed");
                        bumpRevisionLocked();
                    }
                }
            }
        });
    }

    public static void rejectGuest(String requestId) {
        final HostSession session;
        synchronized (LOCK) {
            session = hostSession;
            if (session == null || !PENDING.containsKey(requestId)) return;
            PENDING.remove(requestId);
            bumpRevisionLocked();
        }
        executeIo(() -> {
            try {
                session.control.reject(requestId, "Host declined the request.");
            } catch (IOException exception) {
                synchronized (LOCK) {
                    if (hostSession == session) {
                        hostStatus = "Could not send the rejection: "
                                + safeMessage(exception, "relay connection failed");
                        bumpRevisionLocked();
                    }
                }
            }
        });
    }

    /** Stops remote forwarding but leaves the local Minecraft world open. */
    public static void stopHosting() {
        final HostSession old;
        final List<TcpStreamTunnel> tunnels;
        synchronized (LOCK) {
            ++hostGeneration;
            old = hostSession;
            hostSession = null;
            hostedServer = null;
            hostBusy = false;
            PENDING.clear();
            tunnels = new ArrayList<>(HOST_TUNNELS.values());
            HOST_TUNNELS.clear();
            hostStatus = "Remote hosting stopped. Your local world remains open.";
            bumpRevisionLocked();
        }
        executeIo(() -> {
            if (old != null) old.control.close();
            tunnels.forEach(TcpStreamTunnel::close);
        });
    }

    /** Requests host approval, then opens Minecraft against a local-only TCP endpoint. */
    public static void join(String endpoint, String roomCode, String playerName, Screen parent) {
        final String normalizedEndpoint;
        try {
            normalizedEndpoint = VantaRelayClient.normalizeBaseUrl(endpoint);
        } catch (IllegalArgumentException exception) {
            updateGuestStatus(exception.getMessage());
            return;
        }
        if (parent == null) {
            updateGuestStatus("Could not open the Minecraft connection screen.");
            return;
        }

        final long generation;
        synchronized (LOCK) {
            if (shuttingDown || guestBusy || guestSession != null) return;
            if (hostBusy || hostSession != null) {
                guestStatus = "Stop remote hosting before joining another world.";
                bumpRevisionLocked();
                return;
            }
            generation = ++guestGeneration;
            guestBusy = true;
            guestWorldJoined = false;
            guestStatus = "Requesting access from the host…";
            bumpRevisionLocked();
        }

        executeIo(() -> {
            VantaRelayClient.GuestDecisionConnection decision = null;
            LoopbackTcpBridge bridge = null;
            AtomicReference<WebSocketDuplexConnection> unclaimedData = new AtomicReference<>();
            try {
                VantaRelayClient relay = new VantaRelayClient(normalizedEndpoint);
                VantaRelayClient.JoinTicket ticket = relay.requestJoin(roomCode, playerName);
                decision = relay.connectGuestControl(ticket);
                synchronized (LOCK) {
                    if (generation != guestGeneration || !guestBusy) {
                        decision.close();
                        return;
                    }
                    pendingGuestDecision = decision;
                    guestStatus = "Waiting for the host to approve your request…";
                    bumpRevisionLocked();
                }

                long remainingMillis = Math.max(1_000L, Math.min(
                        Duration.ofMinutes(2).toMillis(),
                        Duration.between(Instant.now(), ticket.expiresAt()).toMillis()));
                VantaRelayClient.RelayEvent response =
                        decision.awaitDecision(Duration.ofMillis(remainingMillis));
                decision.close();
                synchronized (LOCK) {
                    if (pendingGuestDecision == decision) pendingGuestDecision = null;
                }
                if (!"approved".equals(response.type())) {
                    throw new IOException("The host declined your join request.");
                }

                WebSocketDuplexConnection remote = relay.connectGuestData(ticket);
                unclaimedData.set(remote);
                AtomicBoolean streamClaimed = new AtomicBoolean();
                bridge = LoopbackTcpBridge.listen(() -> {
                    if (!streamClaimed.compareAndSet(false, true)) {
                        throw new IOException("This invite already has an active Minecraft connection.");
                    }
                    LoopbackTcpBridge.DuplexConnection claimed = unclaimedData.getAndSet(null);
                    if (claimed == null) throw new IOException("The relay stream is no longer available.");
                    return claimed;
                }, 1);

                GuestSession created = new GuestSession(bridge, unclaimedData);
                synchronized (LOCK) {
                    if (generation != guestGeneration || !guestBusy || shuttingDown) {
                        created.close();
                        return;
                    }
                    guestSession = created;
                    guestBusy = false;
                    guestWorldJoined = false;
                    guestConnectDeadlineNanos = System.nanoTime()
                            + TimeUnit.SECONDS.toNanos(90);
                    guestStatus = "Approved. Connecting Minecraft through your configured relay…";
                    bumpRevisionLocked();
                }

                Minecraft minecraft = Minecraft.getInstance();
                LoopbackTcpBridge guestBridge = bridge;
                minecraft.execute(() -> {
                    synchronized (LOCK) {
                        if (generation != guestGeneration || guestSession == null
                                || guestSession.bridge != guestBridge) return;
                    }
                    String localAddress = "127.0.0.1:" + guestBridge.port();
                    ServerAddress address = new ServerAddress("127.0.0.1", guestBridge.port());
                    ServerData serverData = new ServerData(
                            "Vanta Remote World", localAddress, ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(
                            parent, minecraft, address, serverData, false, null);
                });
            } catch (Exception exception) {
                if (decision != null) decision.close();
                if (bridge != null) bridge.close();
                WebSocketDuplexConnection remaining = unclaimedData.getAndSet(null);
                closeQuietly(remaining);
                synchronized (LOCK) {
                    if (generation == guestGeneration) {
                        guestBusy = false;
                        pendingGuestDecision = null;
                        guestSession = null;
                        guestStatus = safeMessage(exception, "Could not join the remote world.");
                        bumpRevisionLocked();
                    }
                }
            }
        });
    }

    /** True while a join is waiting for approval or the local Minecraft connection is still being established. */
    public static boolean shouldCancelPendingGuestJoin() {
        synchronized (LOCK) {
            return guestBusy || (guestSession != null && !guestWorldJoined);
        }
    }

    public static void stopJoining() {
        final GuestSession old;
        final VantaRelayClient.GuestDecisionConnection decision;
        synchronized (LOCK) {
            ++guestGeneration;
            old = guestSession;
            guestSession = null;
            decision = pendingGuestDecision;
            pendingGuestDecision = null;
            guestBusy = false;
            guestWorldJoined = false;
            guestStatus = "Remote join cancelled.";
            bumpRevisionLocked();
        }
        executeIo(() -> {
            if (decision != null) decision.close();
            if (old != null) old.close();
        });
    }

    public static void onGuestJoined() {
        synchronized (LOCK) {
            if (guestSession == null) return;
            guestWorldJoined = true;
            guestStatus = "Connected to the host through the configured relay.";
            bumpRevisionLocked();
        }
    }

    public static void onGuestDisconnected() {
        final GuestSession old;
        synchronized (LOCK) {
            if (guestSession == null) return;
            old = guestSession;
            guestSession = null;
            guestWorldJoined = false;
            guestStatus = "Remote world connection closed.";
            bumpRevisionLocked();
        }
        executeIo(old::close);
    }

    public static void tick(Minecraft minecraft) {
        boolean stopHost;
        boolean stopGuest;
        synchronized (LOCK) {
            MinecraftServer current = minecraft.getSingleplayerServer();
            stopHost = hostSession != null
                    && (current == null || current != hostedServer || !current.isPublished()
                    || Instant.now().isAfter(hostSession.room.expiresAt()));
            stopGuest = guestSession != null && !guestWorldJoined
                    && System.nanoTime() >= guestConnectDeadlineNanos
                    && guestSession.bridge.activeConnections() == 0;
        }
        if (stopHost) stopHosting();
        if (stopGuest) {
            updateGuestStatus("Minecraft could not connect before the invite expired. Request a new invite.");
            onGuestDisconnected();
        }
    }

    public static boolean isHosting() {
        synchronized (LOCK) {
            return hostBusy || hostSession != null;
        }
    }

    public static void shutdown() {
        final HostSession oldHost;
        final GuestSession oldGuest;
        final VantaRelayClient.GuestDecisionConnection oldDecision;
        final List<TcpStreamTunnel> tunnels;
        synchronized (LOCK) {
            if (shuttingDown) return;
            shuttingDown = true;
            ++hostGeneration;
            ++guestGeneration;
            oldHost = hostSession;
            oldGuest = guestSession;
            oldDecision = pendingGuestDecision;
            tunnels = new ArrayList<>(HOST_TUNNELS.values());
            hostSession = null;
            guestSession = null;
            hostedServer = null;
            pendingGuestDecision = null;
            hostBusy = false;
            guestBusy = false;
            PENDING.clear();
            HOST_TUNNELS.clear();
            bumpRevisionLocked();
        }
        IO.execute(() -> {
            if (oldHost != null) oldHost.control.close();
            if (oldGuest != null) oldGuest.close();
            if (oldDecision != null) oldDecision.close();
            tunnels.forEach(TcpStreamTunnel::close);
        });
        IO.shutdown();
    }

    private static void onHostEvent(long generation, VantaRelayClient.RelayEvent event) {
        synchronized (LOCK) {
            if (generation != hostGeneration || hostSession == null) return;
            if ("join_request".equals(event.type())) {
                if (event.requestId() == null || event.playerName() == null) return;
                PENDING.put(event.requestId(), new PendingGuest(
                        event.requestId(), event.playerName(), false));
                hostStatus = "A player is requesting access to your world.";
            } else if ("request_error".equals(event.type())) {
                hostStatus = "Relay request error: " + safeMessage(
                        new IOException(event.reason()), "unknown request error");
            }
            bumpRevisionLocked();
        }
    }

    private static void onHostControlError(long generation, Throwable error) {
        final HostSession failedSession;
        final List<TcpStreamTunnel> tunnels;
        synchronized (LOCK) {
            if (generation != hostGeneration || hostSession == null) return;
            ++hostGeneration;
            failedSession = hostSession;
            hostSession = null;
            hostedServer = null;
            hostBusy = false;
            PENDING.clear();
            tunnels = new ArrayList<>(HOST_TUNNELS.values());
            HOST_TUNNELS.clear();
            hostStatus = "Relay control connection lost. Remote guests were disconnected; "
                    + "your local world remains open. "
                    + safeMessage(error, "connection closed");
            bumpRevisionLocked();
        }
        executeIo(() -> {
            failedSession.control.close();
            tunnels.forEach(TcpStreamTunnel::close);
        });
    }

    private static void onHostTunnelClosed(long generation, String requestId) {
        synchronized (LOCK) {
            if (generation != hostGeneration) return;
            if (HOST_TUNNELS.remove(requestId) != null) {
                hostStatus = "A remote guest disconnected.";
                bumpRevisionLocked();
            }
        }
    }

    private static void updateHostStatus(String message) {
        synchronized (LOCK) {
            hostBusy = false;
            hostStatus = message == null || message.isBlank()
                    ? "Could not start remote hosting." : message;
            bumpRevisionLocked();
        }
    }

    private static void updateGuestStatus(String message) {
        synchronized (LOCK) {
            guestBusy = false;
            guestStatus = message == null || message.isBlank()
                    ? "Could not join the remote world." : message;
            bumpRevisionLocked();
        }
    }

    private static String safeMessage(Throwable error, String fallback) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) return fallback;
        String message = error.getMessage().replaceAll("[\\r\\n\\t]", " ").trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private static void bumpRevisionLocked() {
        revision++;
    }

    private static void executeIo(Runnable work) {
        try {
            synchronized (LOCK) {
                if (shuttingDown) return;
            }
            IO.execute(work);
        } catch (RejectedExecutionException exception) {
            updateHostStatus("Remote session executor is closed.");
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Session teardown is best-effort; other connection resources still close.
        }
    }

    private static final class HostSession {
        private final VantaRelayClient relay;
        private final VantaRelayClient.Room room;
        private final VantaRelayClient.HostControlConnection control;
        private final int serverPort;
        @SuppressWarnings("unused")
        private final MinecraftServer server;

        private HostSession(VantaRelayClient relay, VantaRelayClient.Room room,
                            VantaRelayClient.HostControlConnection control,
                            int serverPort, MinecraftServer server) {
            this.relay = relay;
            this.room = room;
            this.control = control;
            this.serverPort = serverPort;
            this.server = server;
        }
    }

    private static final class GuestSession {
        private final LoopbackTcpBridge bridge;
        private final AtomicReference<WebSocketDuplexConnection> unclaimedData;

        private GuestSession(LoopbackTcpBridge bridge,
                             AtomicReference<WebSocketDuplexConnection> unclaimedData) {
            this.bridge = bridge;
            this.unclaimedData = unclaimedData;
        }

        private void close() {
            bridge.close();
            WebSocketDuplexConnection remote = unclaimedData.getAndSet(null);
            closeQuietly(remote);
        }
    }
}

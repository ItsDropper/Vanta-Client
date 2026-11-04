package name.vantaclient.client.integration.net;

import name.vantaclient.client.config.VantaClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * TCP bridge. Because integrated servers accept unauthenticated logins, a profile
 * is persisted only after Minecraft installs a non-expired, Mojang-verified
 * player chat session. Relay credentials are memory-only and never persisted.
 */
public final class VantaRemoteSessionManager {
    public record PendingGuest(String requestId, String playerName, String playerUUID,
                                boolean previouslyWhitelisted, boolean approving) {}
    public record ConnectedGuest(String requestId, String playerName, String claimedPlayerUUID,
                                 String authenticatedPlayerUUID, boolean identityVerified) {}

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
    private static final Map<String, ConnectedGuest> ACTIVE_GUESTS = new LinkedHashMap<>();
    /** Server-observed profiles received while an approved TCP tunnel is still being registered. */
    private static final Map<String, String> AUTHENTICATED_PROFILES = new LinkedHashMap<>();
    /** Stops an approval worker from registering a tunnel after a profile mismatch was detected. */
    private static final Set<String> REJECTED_PROFILE_REQUESTS = new HashSet<>();

    private static long revision;
    private static long hostGeneration;
    private static long guestGeneration;
    private static boolean hostBusy;
    private static boolean guestBusy;
    private static boolean shuttingDown;
    private static String hostStatus = "No remote host session is running.";
    private static String guestStatus = "Enter your relay address and invite code to join.";
    private static String guestTransportLabel = "secure WebSocket fallback";
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
                    ACTIVE_GUESTS.size(),
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

    public static List<ConnectedGuest> connectedGuests() {
        synchronized (LOCK) {
            return List.copyOf(ACTIVE_GUESTS.values());
        }
    }

    public static List<String> whitelistedPlayerUUIDs() {
        synchronized (LOCK) {
            if (hostedServer == null) return List.of();
            Set<String> ids = VantaClientPreferences.get().remoteWorldWhitelists
                    .getOrDefault(worldKey(hostedServer), Set.of());
            return ids.stream().sorted().toList();
        }
    }

    /** Removes a profile from this save's whitelist and kicks that profile if it is connected. */
    public static void revokeWhitelistedPlayer(String profileUUID) {
        if (!isProfileUUID(profileUUID)) return;
        String normalized = profileUUID.toLowerCase(Locale.ROOT);
        List<String> toKick = new ArrayList<>();
        synchronized (LOCK) {
            if (hostedServer == null) return;
            Set<String> ids = VantaClientPreferences.get().remoteWorldWhitelists
                    .get(worldKey(hostedServer));
            if (ids == null || !ids.remove(normalized)) return;
            for (ConnectedGuest guest : ACTIVE_GUESTS.values()) {
                // Only a server-verified profile can match a saved whitelist
                // entry. Never let an untrusted UUID claim trigger a revocation kick.
                if (normalized.equals(guest.authenticatedPlayerUUID())) {
                    toKick.add(guest.requestId());
                }
            }
            bumpRevisionLocked();
        }
        VantaClientPreferences.save();
        toKick.forEach(VantaRemoteSessionManager::kickGuest);
    }

    /**
     * Correlates an integrated-server player with an explicitly approved relay request.
     * The login UUID alone is not proof on an integrated server: Minecraft accepts
     * unauthenticated logins there. A UUID is persisted only after the server has
     * validated and installed the player's Mojang-signed chat-session public key.
     *
     * This method is also called after ServerGamePacketListenerImpl processes a
     * chat-session update, because that packet can arrive after the JOIN callback.
     */
    public static void onHostPlayerJoined(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        if (handler == null || handler.player == null || server == null || !server.isSingleplayer()) return;

        // The integrated server is also published to the LAN. Only correlate
        // connections created by this remote-session manager's fixed 127.0.0.1
        // tunnel; an unrelated LAN join must never satisfy a pending relay request.
        if (!(handler.getRemoteAddress() instanceof java.net.InetSocketAddress remoteAddress)
                || remoteAddress.getAddress() == null
                || !remoteAddress.getAddress().isLoopbackAddress()) return;
        String actualUUID = handler.player.getUUID().toString().toLowerCase(Locale.ROOT);
        String actualName = handler.player.getGameProfile().name();
        var chatSession = handler.player.getChatSession();
        boolean verifiedMinecraftSession = chatSession != null && !chatSession.hasExpired();

        // Never attribute the singleplayer owner to a remote invite.
        var localProfileUUID = Minecraft.getInstance().getUser().getProfileId();
        if (localProfileUUID != null && localProfileUUID.toString().equalsIgnoreCase(actualUUID)) return;

        String requestId = null;
        boolean rejectedClaim = false;
        boolean saved = false;
        synchronized (LOCK) {
            if (hostedServer != server || hostSession == null) return;

            List<PendingGuest> pendingUUIDMatches = PENDING.values().stream()
                    .filter(guest -> guest.playerUUID() != null
                            && guest.playerUUID().equalsIgnoreCase(actualUUID))
                    .toList();
            List<ConnectedGuest> activeUUIDMatches = ACTIVE_GUESTS.values().stream()
                    .filter(guest -> guest.claimedPlayerUUID() != null
                            && guest.claimedPlayerUUID().equalsIgnoreCase(actualUUID))
                    .toList();

            if (pendingUUIDMatches.size() + activeUUIDMatches.size() == 1) {
                requestId = !pendingUUIDMatches.isEmpty()
                        ? pendingUUIDMatches.getFirst().requestId()
                        : activeUUIDMatches.getFirst().requestId();
            } else if (pendingUUIDMatches.size() + activeUUIDMatches.size() > 1) {
                return;
            } else {
                List<PendingGuest> pendingNameMatches = PENDING.values().stream()
                        .filter(guest -> guest.playerName().equalsIgnoreCase(actualName))
                        .toList();
                List<ConnectedGuest> activeNameMatches = ACTIVE_GUESTS.values().stream()
                        .filter(guest -> guest.playerName().equalsIgnoreCase(actualName))
                        .toList();
                if (pendingNameMatches.size() + activeNameMatches.size() != 1) return;

                if (!pendingNameMatches.isEmpty()) {
                    PendingGuest candidate = pendingNameMatches.getFirst();
                    requestId = candidate.requestId();
                    rejectedClaim = candidate.playerUUID() != null;
                } else {
                    ConnectedGuest candidate = activeNameMatches.getFirst();
                    requestId = candidate.requestId();
                    rejectedClaim = candidate.claimedPlayerUUID() != null;
                }
                if (rejectedClaim) {
                    REJECTED_PROFILE_REQUESTS.add(requestId);
                    PENDING.remove(requestId);
                    AUTHENTICATED_PROFILES.remove(requestId);
                    hostStatus = "A guest's Minecraft profile did not match the approved identity.";
                }
            }

            if (!rejectedClaim && requestId != null && verifiedMinecraftSession) {
                AUTHENTICATED_PROFILES.put(requestId, actualUUID);
                ConnectedGuest active = ACTIVE_GUESTS.get(requestId);
                if (active != null) {
                    ACTIVE_GUESTS.put(requestId, new ConnectedGuest(
                            active.requestId(), actualName, active.claimedPlayerUUID(),
                            actualUUID, true));
                }
                Set<String> ids = VantaClientPreferences.get().remoteWorldWhitelists
                        .computeIfAbsent(worldKey(server), ignored -> new HashSet<>());
                saved = ids.add(actualUUID);
                hostStatus = saved
                        ? "Verified the Mojang account key for " + actualName
                                + " and saved this profile for the world."
                        : "Verified the Mojang account key for " + actualName + ".";
            } else if (!rejectedClaim && requestId != null) {
                hostStatus = actualName
                        + " is connected, but Minecraft has not supplied a valid account key; "
                        + "this profile will not be saved.";
            }
            bumpRevisionLocked();
        }

        if (rejectedClaim) {
            handler.disconnect(Component.literal(
                    "Vanta: the Minecraft profile does not match the identity approved by the world host."));
            kickGuest(requestId);
            return;
        }
        if (saved) VantaClientPreferences.save();
    }

    private static String worldKey(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString();
    }

    private static boolean isProfileUUID(String value) {
        return value != null && value.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
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
            ACTIVE_GUESTS.clear();
            AUTHENTICATED_PROFILES.clear();
            REJECTED_PROFILE_REQUESTS.clear();
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
        final PendingGuest approvedRequest;
        synchronized (LOCK) {
            session = hostSession;
            PendingGuest pending = PENDING.get(requestId);
            if (session == null || pending == null || pending.approving()) return;
            approvedRequest = new PendingGuest(
                    pending.requestId(), pending.playerName(), pending.playerUUID(),
                    pending.previouslyWhitelisted(), true);
            PENDING.put(requestId, approvedRequest);
            generation = hostGeneration;
            hostStatus = "Approving " + pending.playerName() + " and opening their stream…";
            bumpRevisionLocked();
        }
        final String approvedPlayerName = approvedRequest.playerName();
        final String approvedPlayerUUID = approvedRequest.playerUUID();

        final boolean relayOnly = VantaClientPreferences.get().remoteMaximumIpPrivacy;
        executeIo(() -> {
            TcpStreamTunnel tunnel = null;
            try {
                session.control.approve(requestId);
                LoopbackTcpBridge.DuplexConnection remote;
                String transport;
                WebRtcPeerSession peer = null;
                try {
                    List<VantaRelayClient.IceServerConfig> iceServers =
                            session.relay.fetchHostIceServers(session.room, requestId);
                    peer = WebRtcPeerSession.connectHost(
                            session.relay, session.room, requestId, iceServers, relayOnly);
                } catch (IOException negotiationFailure) {
                    // A failed negotiation can use the WebSocket fallback because
                    // the guest's WebRTC negotiation also fails before it returns.
                }

                if (peer != null) {
                    try {
                        // This is a separate commit point in the relay. Do not fall
                        // back after it fails: the guest may already be using its
                        // established DataChannel and cannot safely switch transports.
                        session.relay.confirmHostPeerConnected(session.room, requestId);
                    } catch (IOException confirmationFailure) {
                        peer.close();
                        throw confirmationFailure;
                    }
                    remote = peer;
                    transport = peer.isUsingTurnRelay()
                            ? "WebRTC through secure relay" : "direct WebRTC";
                } else {
                    remote = session.relay.connectHostData(session.room, requestId);
                    transport = "secure WebSocket fallback";
                }
                tunnel = TcpStreamTunnel.connectToLoopbackServer(
                        session.serverPort, remote,
                        () -> onHostTunnelClosed(generation, requestId));

                synchronized (LOCK) {
                    if (generation != hostGeneration || hostSession != session) {
                        tunnel.close();
                        return;
                    }
                    if (REJECTED_PROFILE_REQUESTS.remove(requestId)) {
                        tunnel.close();
                        PENDING.remove(requestId);
                        AUTHENTICATED_PROFILES.remove(requestId);
                        return;
                    }
                    HOST_TUNNELS.put(requestId, tunnel);
                    PENDING.remove(requestId);
                    if (tunnel.isClosed()) {
                        HOST_TUNNELS.remove(requestId);
                        ACTIVE_GUESTS.remove(requestId);
                        AUTHENTICATED_PROFILES.remove(requestId);
                    } else {
                        String authenticatedUUID = AUTHENTICATED_PROFILES.get(requestId);
                        ACTIVE_GUESTS.put(requestId, new ConnectedGuest(
                                requestId, approvedPlayerName, approvedPlayerUUID,
                                authenticatedUUID, authenticatedUUID != null));
                    }
                    hostStatus = "Guest connected using " + transport + ".";
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

    /** Disconnects one active guest without closing the host's integrated world. */
    public static void kickGuest(String requestId) {
        final TcpStreamTunnel tunnel;
        synchronized (LOCK) {
            tunnel = HOST_TUNNELS.remove(requestId);
            if (tunnel == null) return;
            ACTIVE_GUESTS.remove(requestId);
            AUTHENTICATED_PROFILES.remove(requestId);
            REJECTED_PROFILE_REQUESTS.remove(requestId);
            PENDING.remove(requestId);
            hostStatus = "Disconnected " + ACTIVE_GUESTS.size() + " remaining guest(s).";
            bumpRevisionLocked();
        }
        executeIo(tunnel::close);
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
            ACTIVE_GUESTS.clear();
            AUTHENTICATED_PROFILES.clear();
            REJECTED_PROFILE_REQUESTS.clear();
            hostStatus = "Remote hosting stopped. Your local world remains open.";
            bumpRevisionLocked();
        }
        executeIo(() -> {
            if (old != null) old.control.close();
            tunnels.forEach(TcpStreamTunnel::close);
        });
    }

    /** Requests host approval, then opens Minecraft against a local-only TCP endpoint. */
    public static void join(String endpoint, String roomCode, Screen parent) {
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

        Minecraft minecraft = Minecraft.getInstance();
        String playerName = minecraft.getUser().getName();
        String playerUUID = minecraft.getUser().getProfileId() == null
                ? null : minecraft.getUser().getProfileId().toString();
        final boolean relayOnly = VantaClientPreferences.get().remoteMaximumIpPrivacy;
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
            AtomicReference<LoopbackTcpBridge.DuplexConnection> unclaimedData = new AtomicReference<>();
            try {
                VantaRelayClient relay = new VantaRelayClient(normalizedEndpoint);
                VantaRelayClient.JoinTicket ticket = relay.requestJoin(roomCode, playerName, playerUUID);
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

                LoopbackTcpBridge.DuplexConnection remote;
                String transport;
                try {
                    List<VantaRelayClient.IceServerConfig> iceServers =
                            relay.fetchGuestIceServers(ticket);
                    WebRtcPeerSession peer = WebRtcPeerSession.connectGuest(relay, ticket, iceServers, relayOnly);
                    remote = peer;
                    transport = peer.isUsingTurnRelay()
                            ? "WebRTC through secure relay" : "direct WebRTC";
                } catch (IOException peerFailure) {
                    remote = relay.connectGuestData(ticket);
                    transport = "secure WebSocket fallback";
                }
                unclaimedData.set(remote);
                synchronized (LOCK) {
                    if (generation == guestGeneration) guestTransportLabel = transport;
                }
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
                    guestStatus = "Approved. Connecting Minecraft using " + transport + "…";
                    bumpRevisionLocked();
                }

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
                LoopbackTcpBridge.DuplexConnection remaining = unclaimedData.getAndSet(null);
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
            guestStatus = "Connected to the host using " + guestTransportLabel + ".";
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
            ACTIVE_GUESTS.clear();
            AUTHENTICATED_PROFILES.clear();
            REJECTED_PROFILE_REQUESTS.clear();
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
                String claimedUUID = isProfileUUID(event.playerUUID())
                        ? event.playerUUID().toLowerCase(Locale.ROOT) : null;
                boolean previouslyWhitelisted = claimedUUID != null
                        && hostedServer != null
                        && VantaClientPreferences.get().remoteWorldWhitelists
                                .getOrDefault(worldKey(hostedServer), Set.of()).contains(claimedUUID);
                PENDING.put(event.requestId(), new PendingGuest(
                        event.requestId(), event.playerName(), claimedUUID,
                        previouslyWhitelisted, false));
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
            ACTIVE_GUESTS.clear();
            AUTHENTICATED_PROFILES.clear();
            REJECTED_PROFILE_REQUESTS.clear();
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
                ACTIVE_GUESTS.remove(requestId);
                AUTHENTICATED_PROFILES.remove(requestId);
                REJECTED_PROFILE_REQUESTS.remove(requestId);
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
        private final AtomicReference<LoopbackTcpBridge.DuplexConnection> unclaimedData;

        private GuestSession(LoopbackTcpBridge bridge,
                             AtomicReference<LoopbackTcpBridge.DuplexConnection> unclaimedData) {
            this.bridge = bridge;
            this.unclaimedData = unclaimedData;
        }

        private void close() {
            bridge.close();
            LoopbackTcpBridge.DuplexConnection remote = unclaimedData.getAndSet(null);
            closeQuietly(remote);
        }
    }
}

package name.vantaclient.client.integration.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Client for Vanta's self-hostable HTTPS/WSS room relay.
 *
 * Tokens are deliberately memory-only. The relay endpoint is user-configured;
 * there is no default third-party service or public endpoint baked into the client.
 */
public final class VantaRelayClient {
    private static final Gson GSON = new Gson();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(12);

    public record Room(String code, String hostToken, Instant expiresAt) {}
    /** Short-lived credentials returned by the relay for STUN/TURN discovery. */
    public record IceServerConfig(List<String> urls, String username, String credential) {}
    public record JoinTicket(String roomCode, String requestId, String guestToken,
                             Instant expiresAt) {}
    /** Wire DTO: keep the relay's ISO-8601 expiry as text; Gson does not need to reflect into java.time. */
    public record RelayEvent(String type, String requestId, String playerName,
                             String playerUUID, String reason, String expiresAt) {}
    public record RelaySignal(String type, String sdp, String candidate,
                              String sdpMid, Integer sdpMLineIndex) {}

    private static final int MAX_SIGNAL_SDP_CHARS = 48 * 1024;
    private static final int MAX_SIGNAL_CANDIDATE_CHARS = 4 * 1024;
    private static final int MAX_SIGNAL_MESSAGE_CHARS = 64 * 1024;

    private record CreateRoomResponse(String roomCode, String hostToken, String expiresAt) {}
    private record JoinResponse(String requestId, String guestToken, String expiresAt) {}
    private record ControlMessage(String type, String requestId, String reason) {}

    private final URI baseUri;
    private final HttpClient httpClient;

    public VantaRelayClient(String endpoint) {
        this.baseUri = parseBaseUri(endpoint);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String endpoint() {
        return baseUri.toString();
    }

    /** Validates and normalizes an endpoint, allowing plain HTTP only for local development. */
    public static String normalizeBaseUrl(String endpoint) {
        return parseBaseUri(endpoint).toString();
    }

    public Room createRoom() throws IOException {
        JsonObject response = post("/v1/rooms", "{}");
        String code = requiredString(response, "roomCode");
        String token = requiredString(response, "hostToken");
        if (!code.matches("[A-Z2-7]{20,32}") || token.length() < 40) {
            throw new IOException("Relay returned invalid room credentials.");
        }
        return new Room(code, token, parseInstant(response.get("expiresAt").getAsString()));
    }

    public JoinTicket requestJoin(String roomCode, String playerName) throws IOException {
        return requestJoin(roomCode, playerName, null);
    }

    /**
     * Sends the local Minecraft profile UUID as an untrusted correlation claim.
     * The host saves it only after vanilla validates and installs the matching
     * Mojang-signed player chat-session key on the integrated server.
     */
    public JoinTicket requestJoin(String roomCode, String playerName, String playerUUID)
            throws IOException {
        String normalizedCode = validateRoomCode(roomCode);
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IOException("Enter a valid Minecraft player name.");
        }
        if (playerUUID != null && !playerUUID.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IOException("The local Minecraft profile UUID is invalid.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("playerName", playerName);
        if (playerUUID != null) {
            body.addProperty("playerUUID", playerUUID.toLowerCase(java.util.Locale.ROOT));
        }
        JsonObject response = post("/v1/rooms/" + normalizedCode + "/requests", GSON.toJson(body));
        String requestId = requiredString(response, "requestId");
        String token = requiredString(response, "guestToken");
        if (!requestId.matches("[A-Za-z0-9_-]{12,32}") || token.length() < 40) {
            throw new IOException("Relay returned invalid guest credentials.");
        }
        return new JoinTicket(normalizedCode, requestId, token,
                parseInstant(response.get("expiresAt").getAsString()));
    }

    public HostControlConnection connectHostControl(Room room,
                                                     Consumer<RelayEvent> events,
                                                     Consumer<Throwable> errors) throws IOException {
        requireRoom(room);
        String path = "/v1/host/control/" + room.code();
        WebSocketJsonListener listener = new WebSocketJsonListener(events, errors);
        java.net.http.WebSocket socket = connectWebSocket(path, room.hostToken(), listener);
        return new HostControlConnection(socket, listener);
    }

    public GuestDecisionConnection connectGuestControl(JoinTicket ticket) throws IOException {
        requireTicket(ticket);
        GuestDecisionListener listener = new GuestDecisionListener();
        String path = "/v1/guest/control/" + ticket.roomCode() + "/" + ticket.requestId();
        java.net.http.WebSocket socket = connectWebSocket(path, ticket.guestToken(), listener);
        return new GuestDecisionConnection(socket, listener);
    }

    /** Fetches request-scoped ICE servers after the host has approved the guest. */
    public List<IceServerConfig> fetchHostIceServers(Room room, String requestId) throws IOException {
        requireRoom(room);
        String id = validateRequestId(requestId);
        return parseIceServers(get("/v1/rooms/" + room.code() + "/requests/" + id + "/host-ice-servers",
                room.hostToken()));
    }

    /** Fetches request-scoped ICE servers using the guest's one-use bearer token. */
    public List<IceServerConfig> fetchGuestIceServers(JoinTicket ticket) throws IOException {
        requireTicket(ticket);
        return parseIceServers(get("/v1/rooms/" + ticket.roomCode() + "/requests/"
                + ticket.requestId() + "/ice-servers", ticket.guestToken()));
    }

    public WebSocketDuplexConnection connectHostData(Room room, String requestId) throws IOException {
        requireRoom(room);
        String path = "/v1/host/data/" + room.code() + "/" + validateRequestId(requestId);
        return WebSocketDuplexConnection.connect(httpClient, webSocketUri(path), room.hostToken());
    }

    public WebSocketDuplexConnection connectGuestData(JoinTicket ticket) throws IOException {
        requireTicket(ticket);
        String path = "/v1/guest/data/" + ticket.roomCode() + "/" + ticket.requestId();
        return WebSocketDuplexConnection.connect(httpClient, webSocketUri(path), ticket.guestToken());
    }

    /**
     * Marks this approved request as an established P2P guest after ICE has
     * opened the DataChannel. The host bearer token is required; the invite
     * code alone cannot activate a peer session.
     */
    public void confirmHostPeerConnected(Room room, String requestId) throws IOException {
        requireRoom(room);
        String id = validateRequestId(requestId);
        String path = "/v1/rooms/" + room.code() + "/requests/" + id + "/peer-connected";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + room.hostToken())
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Confirming the peer connection was interrupted.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Could not confirm the peer connection with the relay.", exception);
        }
        if (response.statusCode() != 200) {
            String code = "HTTP " + response.statusCode();
            try {
                JsonObject error = GSON.fromJson(response.body(), JsonObject.class);
                if (error != null && error.has("error")) code = error.get("error").getAsString();
            } catch (RuntimeException ignored) {
                // Do not expose an arbitrary service response body.
            }
            throw new IOException("Relay did not activate the peer session: " + code);
        }
    }

    public SignalConnection connectHostSignal(Room room, String requestId) throws IOException {
        requireRoom(room);
        String path = "/v1/host/signal/" + room.code() + "/" + validateRequestId(requestId);
        SignalListener listener = new SignalListener(true);
        java.net.http.WebSocket socket = connectWebSocket(path, room.hostToken(), listener);
        return new SignalConnection(socket, listener, true);
    }

    public SignalConnection connectGuestSignal(JoinTicket ticket) throws IOException {
        requireTicket(ticket);
        String path = "/v1/guest/signal/" + ticket.roomCode() + "/" + ticket.requestId();
        SignalListener listener = new SignalListener(false);
        java.net.http.WebSocket socket = connectWebSocket(path, ticket.guestToken(), listener);
        return new SignalConnection(socket, listener, false);
    }

    private JsonObject get(String path, String bearerToken) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Relay request was interrupted.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Could not reach the configured Vanta relay.", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = "HTTP " + response.statusCode();
            try {
                JsonObject error = GSON.fromJson(response.body(), JsonObject.class);
                if (error != null && error.has("error")) code = error.get("error").getAsString();
            } catch (RuntimeException ignored) {
                // Do not echo arbitrary service response bodies into the UI.
            }
            throw new IOException("Relay rejected ICE configuration: " + code);
        }
        try {
            JsonObject object = GSON.fromJson(response.body(), JsonObject.class);
            if (object == null) throw new IOException("Relay returned an empty ICE configuration.");
            return object;
        } catch (RuntimeException exception) {
            throw new IOException("Relay returned malformed ICE configuration.", exception);
        }
    }

    private static List<IceServerConfig> parseIceServers(JsonObject response) throws IOException {
        if (!response.has("iceServers") || !response.get("iceServers").isJsonArray()) {
            throw new IOException("Relay returned an invalid ICE server list.");
        }
        List<IceServerConfig> result = new ArrayList<>();
        for (var element : response.getAsJsonArray("iceServers")) {
            if (!element.isJsonObject()) throw new IOException("Relay returned an invalid ICE server.");
            JsonObject item = element.getAsJsonObject();
            if (!item.has("urls") || !item.get("urls").isJsonArray()) {
                throw new IOException("Relay returned an ICE server without URLs.");
            }
            List<String> urls = new ArrayList<>();
            for (var url : item.getAsJsonArray("urls")) {
                if (!url.isJsonPrimitive() || !url.getAsJsonPrimitive().isString()) {
                    throw new IOException("Relay returned an invalid ICE URL.");
                }
                String value = url.getAsString().trim();
                if (!(value.startsWith("stun:") || value.startsWith("stuns:")
                        || value.startsWith("turn:") || value.startsWith("turns:"))
                        || value.length() > 512) {
                    throw new IOException("Relay returned an unsupported ICE URL.");
                }
                urls.add(value);
            }
            if (urls.isEmpty() || urls.size() > 8) {
                throw new IOException("Relay returned an invalid ICE URL count.");
            }
            String username = item.has("username") && !item.get("username").isJsonNull()
                    ? item.get("username").getAsString() : null;
            String credential = item.has("credential") && !item.get("credential").isJsonNull()
                    ? item.get("credential").getAsString() : null;
            if ((username == null) != (credential == null)
                    || (username != null && (username.length() > 256 || credential.length() > 512))) {
                throw new IOException("Relay returned invalid TURN credentials.");
            }
            result.add(new IceServerConfig(List.copyOf(urls), username, credential));
            if (result.size() > 8) throw new IOException("Relay returned too many ICE servers.");
        }
        return List.copyOf(result);
    }

    private JsonObject post(String path, String body) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Relay request was interrupted.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Could not reach the configured Vanta relay.", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = "HTTP " + response.statusCode();
            try {
                JsonObject error = GSON.fromJson(response.body(), JsonObject.class);
                if (error != null && error.has("error")) code = error.get("error").getAsString();
            } catch (RuntimeException ignored) {
                // Do not echo arbitrary server response bodies into the UI.
            }
            throw new IOException("Relay rejected the request: " + code);
        }
        try {
            JsonObject object = GSON.fromJson(response.body(), JsonObject.class);
            if (object == null) throw new IOException("Relay returned an empty response.");
            return object;
        } catch (RuntimeException exception) {
            throw new IOException("Relay returned malformed JSON.", exception);
        }
    }

    private java.net.http.WebSocket connectWebSocket(String path, String token,
                                                      java.net.http.WebSocket.Listener listener)
            throws IOException {
        try {
            return httpClient.newWebSocketBuilder()
                    .connectTimeout(SOCKET_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .buildAsync(webSocketUri(path), listener)
                    .get(SOCKET_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Relay connection was interrupted.", exception);
        } catch (ExecutionException | TimeoutException | CompletionException exception) {
            throw new IOException("Could not establish a secure relay connection.", exception);
        }
    }

    private URI webSocketUri(String path) {
        String scheme = baseUri.getScheme().equals("https") ? "wss" : "ws";
        return URI.create(scheme + "://" + baseUri.getRawAuthority() + path);
    }

    private static URI parseBaseUri(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 256) {
            throw new IllegalArgumentException("Enter your relay's HTTPS address.");
        }
        final URI parsed;
        try {
            parsed = URI.create(endpoint.trim()).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Relay address is not a valid URL.", exception);
        }
        String scheme = parsed.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || parsed.getHost() == null || parsed.getUserInfo() != null
                || parsed.getQuery() != null || parsed.getFragment() != null
                || !(parsed.getPath().isEmpty() || parsed.getPath().equals("/"))) {
            throw new IllegalArgumentException("Relay address must be an HTTPS origin without a path.");
        }
        if ("http".equalsIgnoreCase(scheme) && !isLoopbackHost(parsed.getHost())) {
            throw new IllegalArgumentException("Remote relays must use HTTPS. Plain HTTP is allowed only on this device.");
        }
        try {
            return new URI(scheme.toLowerCase(), null, parsed.getHost(), parsed.getPort(),
                    "/", null, null);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Relay address is invalid.", exception);
        }
    }

    private static boolean isLoopbackHost(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                || host.equals("::1") || host.equals("[::1]");
    }

    private static String validateRoomCode(String value) throws IOException {
        String code = value == null ? "" : value.trim().toUpperCase();
        if (!code.matches("[A-Z2-7]{20,32}")) throw new IOException("Invite code is invalid.");
        return code;
    }

    private static String validateRequestId(String value) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9_-]{12,32}")) {
            throw new IOException("Relay request is invalid.");
        }
        return value;
    }

    private static void requireRoom(Room room) throws IOException {
        if (room == null || room.hostToken() == null || room.hostToken().length() < 40) {
            throw new IOException("Host session is not initialized.");
        }
        validateRoomCode(room.code());
    }

    private static void requireTicket(JoinTicket ticket) throws IOException {
        if (ticket == null || ticket.guestToken() == null || ticket.guestToken().length() < 40) {
            throw new IOException("Guest session is not initialized.");
        }
        validateRoomCode(ticket.roomCode());
        validateRequestId(ticket.requestId());
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            throw new IOException("Relay response is missing " + name + ".");
        }
        return object.get(name).getAsString();
    }

    private static Instant parseInstant(String value) throws IOException {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IOException("Relay returned an invalid expiry time.", exception);
        }
    }

    private static RelayEvent parseEvent(String value) {
        return GSON.fromJson(value, RelayEvent.class);
    }

    public static final class HostControlConnection implements AutoCloseable {
        private final java.net.http.WebSocket socket;
        private final WebSocketJsonListener listener;

        private HostControlConnection(java.net.http.WebSocket socket,
                                       WebSocketJsonListener listener) {
            this.socket = socket;
            this.listener = listener;
        }

        public boolean isOpen() {
            return listener.isOpen();
        }

        public void approve(String requestId) throws IOException {
            sendAndAwait(new ControlMessage("approve", validateRequestId(requestId), null),
                    "approved");
        }

        public void reject(String requestId, String reason) throws IOException {
            sendAndAwait(new ControlMessage("reject", validateRequestId(requestId),
                    reason == null ? "" : reason.substring(0, Math.min(80, reason.length()))),
                    "rejected");
        }

        private void sendAndAwait(ControlMessage message, String expectedState) throws IOException {
            java.util.concurrent.CompletableFuture<RelayEvent> acknowledgement =
                    listener.expectAcknowledgement(message.requestId());
            try {
                socket.sendText(GSON.toJson(message), true).get(5, TimeUnit.SECONDS);
                RelayEvent response = acknowledgement.get(5, TimeUnit.SECONDS);
                if ("request_error".equals(response.type())) {
                    throw new IOException("Relay rejected the host decision: "
                            + (response.reason() == null ? "request_error" : response.reason()));
                }
                if (!"request_updated".equals(response.type())
                        || !expectedState.equals(response.reason())) {
                    throw new IOException("Relay returned an invalid host decision acknowledgement.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Relay host decision was interrupted.", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new IOException("Relay did not confirm the host decision.", exception);
            } finally {
                listener.removeAcknowledgement(message.requestId(), acknowledgement);
            }
        }

        @Override
        public void close() {
            listener.failAcknowledgements(new IOException("Relay host control connection closed."));
            try {
                socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "host closed")
                        .get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                socket.abort();
            }
        }
    }

    public static final class GuestDecisionConnection implements AutoCloseable {
        private final java.net.http.WebSocket socket;
        private final GuestDecisionListener listener;

        private GuestDecisionConnection(java.net.http.WebSocket socket,
                                         GuestDecisionListener listener) {
            this.socket = socket;
            this.listener = listener;
        }

        public RelayEvent awaitDecision(Duration timeout) throws IOException {
            try {
                RelayEvent event = listener.decision.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (event == null || !("approved".equals(event.type()) || "rejected".equals(event.type()))) {
                    throw new IOException("Relay returned an unexpected approval response.");
                }
                return event;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Waiting for host approval was interrupted.", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new IOException("The host did not approve the invite in time.", exception);
            }
        }

        @Override
        public void close() {
            try {
                socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "guest decision received")
                        .get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                socket.abort();
            }
        }
    }

    /**
     * Authenticated, request-scoped SDP/ICE signalling. The host may send one
     * offer; the guest may send one answer. Either peer can trickle ICE. The
     * server's ready event is required before starting negotiation.
     */
    public static final class SignalConnection implements AutoCloseable {
        private final java.net.http.WebSocket socket;
        private final SignalListener listener;
        private final boolean hostSide;

        private SignalConnection(java.net.http.WebSocket socket, SignalListener listener,
                                  boolean hostSide) {
            this.socket = socket;
            this.listener = listener;
            this.hostSide = hostSide;
        }

        public void awaitReady(Duration timeout) throws IOException {
            listener.awaitReady(timeout);
        }

        public RelaySignal receive(Duration timeout) throws IOException {
            return listener.receive(timeout);
        }

        public void sendOffer(String sdp) throws IOException {
            if (!hostSide) throw new IOException("Only the host may send the WebRTC offer.");
            send(new RelaySignal("offer", sdp, null, null, null));
        }

        public void sendAnswer(String sdp) throws IOException {
            if (hostSide) throw new IOException("Only the guest may send the WebRTC answer.");
            send(new RelaySignal("answer", sdp, null, null, null));
        }

        public void sendIce(String candidate, String sdpMid, int sdpMLineIndex)
                throws IOException {
            send(new RelaySignal("ice", null, candidate, sdpMid, sdpMLineIndex));
        }

        public boolean isOpen() {
            return listener.isOpen();
        }

        private void send(RelaySignal signal) throws IOException {
            validateSignal(signal, hostSide, false);
            if (!listener.isOpen()) throw new IOException("Relay signalling connection is closed.");
            try {
                socket.sendText(GSON.toJson(signal), true).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Relay signalling send was interrupted.", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new IOException("Could not send WebRTC signalling message.", exception);
            }
        }

        @Override
        public void close() {
            listener.closeLocally();
            try {
                socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "signalling complete")
                        .get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                socket.abort();
            }
        }
    }

    private static final class SignalListener implements java.net.http.WebSocket.Listener {
        private static final Set<String> ALLOWED_FIELDS =
                Set.of("type", "sdp", "candidate", "sdpMid", "sdpMLineIndex");

        private final boolean hostSide;
        private final StringBuilder text = new StringBuilder();
        private final BlockingQueue<Object> incoming = new ArrayBlockingQueue<>(128);
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private final AtomicReference<IOException> failure = new AtomicReference<>();
        private volatile boolean connected;
        private volatile boolean locallyClosed;

        private SignalListener(boolean hostSide) {
            this.hostSide = hostSide;
        }

        private boolean isOpen() {
            return connected && !locallyClosed && failure.get() == null;
        }

        private void awaitReady(Duration timeout) throws IOException {
            try {
                ready.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Waiting for both WebRTC signalling peers was interrupted.", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new IOException("The other peer did not become ready for WebRTC negotiation.", exception);
            }
        }

        private RelaySignal receive(Duration timeout) throws IOException {
            final Object event;
            try {
                event = incoming.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Waiting for WebRTC signalling was interrupted.", exception);
            }
            if (event instanceof IOException error) throw error;
            if (event instanceof RelaySignal signal) return signal;
            IOException error = failure.get();
            if (error != null) throw error;
            throw new IOException("Timed out waiting for a WebRTC signalling message.");
        }

        private void closeLocally() {
            locallyClosed = true;
            ready.completeExceptionally(new IOException("WebRTC signalling was closed."));
        }

        private void fail(Throwable cause, java.net.http.WebSocket socket) {
            IOException error = cause instanceof IOException io
                    ? io : new IOException("WebRTC signalling connection failed.", cause);
            if (!failure.compareAndSet(null, error)) return;
            ready.completeExceptionally(error);
            incoming.clear();
            incoming.offer(error);
            if (!locallyClosed) socket.abort();
        }

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            connected = true;
            webSocket.request(1);
        }

        @Override
        public synchronized java.util.concurrent.CompletionStage<?> onText(
                java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            if (text.length() + data.length() > MAX_SIGNAL_MESSAGE_CHARS) {
                fail(new IOException("Relay signalling message exceeded its size limit."), webSocket);
                return CompletableFuture.completedFuture(null);
            }
            text.append(data);
            if (last) {
                try {
                    JsonObject object = JsonParser.parseString(text.toString()).getAsJsonObject();
                    for (String key : object.keySet()) {
                        if (!ALLOWED_FIELDS.contains(key)) {
                            throw new IOException("Relay sent an unknown signalling field.");
                        }
                    }
                    RelaySignal signal = GSON.fromJson(object, RelaySignal.class);
                    validateSignal(signal, hostSide, true);
                    if ("ready".equals(signal.type())) {
                        ready.complete(null);
                    } else if (!incoming.offer(signal)) {
                        throw new IOException("Relay signalling receive queue is full.");
                    }
                } catch (RuntimeException | IOException exception) {
                    fail(exception, webSocket);
                } finally {
                    text.setLength(0);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            connected = false;
            fail(error, webSocket);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(
                java.net.http.WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            if (!locallyClosed) fail(new IOException("The other peer closed WebRTC signalling."), webSocket);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void validateSignal(RelaySignal signal, boolean hostSide, boolean incoming)
            throws IOException {
        if (signal == null || signal.type() == null) {
            throw new IOException("Relay sent an empty signalling message.");
        }
        String type = signal.type();
        if ("ready".equals(type) && incoming) {
            if (hasSignalPayload(signal)) throw new IOException("Relay sent an invalid ready event.");
            return;
        }
        if ("offer".equals(type) && !hostSide && incoming) {
            validateSdp(signal);
            return;
        }
        if ("answer".equals(type) && hostSide && incoming) {
            validateSdp(signal);
            return;
        }
        if ("offer".equals(type) && hostSide && !incoming) {
            validateSdp(signal);
            return;
        }
        if ("answer".equals(type) && !hostSide && !incoming) {
            validateSdp(signal);
            return;
        }
        if ("ice".equals(type)) {
            if (signal.candidate() == null || !signal.candidate().startsWith("candidate:")
                    || signal.candidate().length() > MAX_SIGNAL_CANDIDATE_CHARS
                    || signal.sdpMid() == null || signal.sdpMid().isBlank()
                    || signal.sdpMid().length() > 256 || signal.sdpMLineIndex() == null
                    || signal.sdpMLineIndex() < 0 || signal.sdpMLineIndex() > 255
                    || signal.sdp() != null) {
                throw new IOException("Relay sent an invalid ICE candidate.");
            }
            return;
        }
        throw new IOException("Relay sent a signalling message for the wrong peer role.");
    }

    private static void validateSdp(RelaySignal signal) throws IOException {
        if (signal.sdp() == null || signal.sdp().isBlank()
                || signal.sdp().length() > MAX_SIGNAL_SDP_CHARS
                || !signal.sdp().contains("v=0") || signal.candidate() != null
                || signal.sdpMid() != null || signal.sdpMLineIndex() != null) {
            throw new IOException("Relay sent an invalid SDP description.");
        }
    }

    private static boolean hasSignalPayload(RelaySignal signal) {
        return signal.sdp() != null || signal.candidate() != null || signal.sdpMid() != null
                || signal.sdpMLineIndex() != null;
    }

    private static final class WebSocketJsonListener implements java.net.http.WebSocket.Listener {
        private final Consumer<RelayEvent> events;
        private final Consumer<Throwable> errors;
        private final StringBuilder text = new StringBuilder();
        private volatile boolean connected;
        private final java.util.concurrent.ConcurrentHashMap<String,
                java.util.concurrent.CompletableFuture<RelayEvent>> acknowledgements =
                new java.util.concurrent.ConcurrentHashMap<>();

        private WebSocketJsonListener(Consumer<RelayEvent> events, Consumer<Throwable> errors) {
            this.events = events == null ? event -> {} : events;
            this.errors = errors == null ? error -> {} : errors;
        }

        private java.util.concurrent.CompletableFuture<RelayEvent> expectAcknowledgement(
                String requestId) throws IOException {
            java.util.concurrent.CompletableFuture<RelayEvent> future =
                    new java.util.concurrent.CompletableFuture<>();
            if (acknowledgements.putIfAbsent(requestId, future) != null) {
                throw new IOException("A decision for this guest is already being processed.");
            }
            return future;
        }

        private void removeAcknowledgement(
                String requestId,
                java.util.concurrent.CompletableFuture<RelayEvent> future) {
            acknowledgements.remove(requestId, future);
        }

        private boolean isOpen() {
            return connected;
        }

        private void failAcknowledgements(Throwable error) {
            for (var entry : acknowledgements.entrySet()) {
                if (acknowledgements.remove(entry.getKey(), entry.getValue())) {
                    entry.getValue().completeExceptionally(error);
                }
            }
        }

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            connected = true;
            webSocket.request(1);
        }

        @Override
        public synchronized java.util.concurrent.CompletionStage<?> onText(
                java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            if (text.length() + data.length() > 4 * 1024) {
                IOException error = new IOException("Relay control message exceeded its size limit.");
                failAcknowledgements(error);
                errors.accept(error);
                webSocket.abort();
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            text.append(data);
            if (last) {
                try {
                    RelayEvent event = parseEvent(text.toString());
                    if (event == null || event.type() == null) throw new IOException("Invalid relay event.");
                    if (event.requestId() != null
                            && ("request_updated".equals(event.type())
                            || "request_error".equals(event.type()))) {
                        java.util.concurrent.CompletableFuture<RelayEvent> acknowledgement =
                                acknowledgements.get(event.requestId());
                        if (acknowledgement != null) acknowledgement.complete(event);
                    }
                    events.accept(event);
                } catch (RuntimeException | IOException exception) {
                    failAcknowledgements(exception);
                    errors.accept(exception);
                    webSocket.abort();
                } finally {
                    text.setLength(0);
                }
            }
            webSocket.request(1);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            connected = false;
            failAcknowledgements(error);
            errors.accept(error);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(
                java.net.http.WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            IOException error = new IOException("Relay host control connection closed.");
            failAcknowledgements(error);
            errors.accept(error);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private static final class GuestDecisionListener implements java.net.http.WebSocket.Listener {
        private final java.util.concurrent.CompletableFuture<RelayEvent> decision =
                new java.util.concurrent.CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public synchronized java.util.concurrent.CompletionStage<?> onText(
                java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            if (text.length() + data.length() > 4 * 1024) {
                decision.completeExceptionally(new IOException("Relay response exceeded its size limit."));
                webSocket.abort();
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            text.append(data);
            if (last) {
                try {
                    RelayEvent event = parseEvent(text.toString());
                    if (event == null || event.type() == null) throw new IllegalStateException("Invalid relay response.");
                    if ("approved".equals(event.type()) || "rejected".equals(event.type())) {
                        decision.complete(event);
                    } else {
                        decision.completeExceptionally(new IOException("Unexpected relay response."));
                    }
                } catch (RuntimeException exception) {
                    decision.completeExceptionally(exception);
                    webSocket.abort();
                } finally {
                    text.setLength(0);
                }
            }
            webSocket.request(1);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            decision.completeExceptionally(error);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(
                java.net.http.WebSocket webSocket, int statusCode, String reason) {
            if (!decision.isDone()) {
                decision.completeExceptionally(new IOException("Relay closed before host approval."));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
